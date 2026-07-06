# Activity Lifecycle — with edge cases from this project

**Flutter analogy:** roughly `AppLifecycleState` (`resumed`/`inactive`/`paused`/`detached`), but applied per-`Activity` window instead of per-engine.

---

## 1. The standard callbacks

```
onCreate() → onStart() → onResume() → [RESUMED - visible & interactive]
                ↑              ↓
           onRestart()      onPause()
                ↑              ↓
            onStop() ←──────────
                ↓
           onDestroy()
```

| Callback | Fires when | Flutter equivalent |
|---|---|---|
| `onCreate` | Activity object created, `setContent {}` runs | Engine / `runApp()` init |
| `onStart`/`onResume` | Becomes visible/interactive | `resumed` |
| `onPause` | Partially obscured (dialog, split-screen) | `inactive` |
| `onStop` | Fully hidden (home button, another app on top) | `paused` |
| `onDestroy` | Finishing, or OS reclaiming memory | `detached` |
| `onRestart` | Coming back from `onStop` without full destroy | n/a (Flutter engine usually persists) |

**In this project — `MainActivity.kt:39`**
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ... }
    }
}
```
This app is **single-Activity** — like a single Flutter `Activity` hosting one `FlutterView`. Every screen (menu, book, sensor, weatherbrief) is a Compose destination inside one `NavHost`, not a separate Activity. So the lifecycle events above happen once, at the app-window level, and Compose navigation just swaps content within it.

---

## 2. Edge case — config change (rotation) destroys and recreates the Activity

`AndroidManifest.xml:27` declares no `android:configChanges` override on `MainActivity`, so a rotation runs the **full** destroy → recreate cycle, not a lightweight callback.

**In this project:**

- `setContent {}` reruns from scratch, so `BlePermissionHandler(onPermissionsGranted = {})` (`MainActivity.kt:47`) recomposes fresh, and its `LaunchedEffect(Unit) { launcher.launch(...) }` (`BlePermissionHandler.kt:32`) **re-fires the permission request on every rotation**. If permission is already granted, Android auto-resolves it with no visible dialog — but if it's still pending/denied, the user sees a re-prompt each time.
- `rememberSaveable { mutableStateOf(false) }` for `showSheet` (`MainActivity.kt:58`) *does* survive rotation via the saved-instance-state Bundle — this is rotation-safe UI state, equivalent to Flutter's `PageStorage` / `RestorableProperty`.
- `hiltViewModel()`-scoped ViewModels (`BleConnectionViewModel`, `SensorViewModel`) survive rotation because they're tied to the `NavBackStackEntry`, not the Activity instance — same idea as keeping a `ChangeNotifier` alive above a rebuilt widget tree in Flutter.

---

## 3. Edge case — BLE state lives *outside* the Activity/ViewModel lifecycle entirely

This is the one most worth understanding for a BLE app. `BleSessionManager` (`data/ble/BleSessionManager.kt:34`) is a singleton with its own scope:

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

This scope is **not** `viewModelScope` and is not tied to any Activity — it lives as long as the process does.

- **Good**: an active GATT connection (`BleDeviceSession`, `data/ble/BleDeviceSession.kt`) survives Activity destroy/recreate on rotation — the `BluetoothGatt` object isn't touched by `onCreate`/`onDestroy` at all.
- **Risk**: nothing ties `BleDeviceSession.connect()` / `disconnect()` to `onStop`/`onDestroy`. `BleConnectionViewModel.onCleared()` only calls `stopScan()` (`ui/connection/BleConnectionViewModel.kt:91`) — it never calls `disconnectAll()`. So if the user backgrounds the app or the Activity is destroyed (not just rotated), an active sensor connection keeps running with no lifecycle owner responsible for tearing it down. `disconnectAll()` exists on `BleSessionManager.kt:174` but nothing in the codebase currently calls it.
- **Process death**: if Android kills the whole process while backgrounded (the true Flutter analogue is engine/isolate termination, not just `paused`), `BleSessionManager` itself is destroyed and recreated as a brand-new Hilt singleton on relaunch. All in-memory `BleDeviceSession`s and scan results are gone — `savedInstanceState` can restore simple UI booleans, but it can't restore a live `BluetoothGatt` connection. The user lands back on the sensor screen showing "Disconnected" with no reconnection attempt.

---

## 4. Edge case — scanning isn't lifecycle-paused

`startScan()` in `BleSessionManager` has no link to `onPause`/`onStop`. As long as `BleConnectionViewModel` (scoped to the `"home"` nav entry) isn't cleared, a scan keeps running even if the user presses Home and the Activity goes into `onStop`. On Android 8+, background BLE scanning from a non-foreground app is throttled/restricted by the OS, so this can silently degrade rather than crash — worth knowing if scan results seem to stop updating after backgrounding.

---

## 5. Counter-example — lifecycle-aware collection done correctly

Contrast with the above: `SensorScreen` correctly uses `collectAsStateWithLifecycle()` instead of plain `collectAsState()`.

**In this project — `SensorScreen.kt:52`**
```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```
This automatically stops collecting the flow when the Activity drops below `STARTED` (e.g. `onStop`) and resumes on `onStart`, avoiding wasted recomposition/collection while invisible — the Compose-idiomatic equivalent of cancelling a `StreamSubscription` in a Flutter `dispose()` / re-subscribing in `didChangeAppLifecycleState`.

---

## Summary — what survives what

| Event | `rememberSaveable` UI state | Hilt ViewModel (nav-scoped) | `BleSessionManager` / active GATT |
|---|---|---|---|
| Rotation | survives | survives | survives (untouched) |
| Backgrounding (`onStop`), no process kill | survives | survives | still running, unmanaged |
| Process death | survives (Bundle-restored) | recreated | lost, no reconnect logic |

### Open items (not yet implemented in this project)
- No call to `disconnectAll()` on Activity stop/destroy or process-death handling.
- No reconnection logic after a process-death restart.
- No pausing of `startScan()` when the app backgrounds.
