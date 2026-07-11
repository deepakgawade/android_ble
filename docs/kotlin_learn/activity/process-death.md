# Process Death — Scenarios and What Actually Survives

**Flutter analogy:** rotation ≈ your widget tree rebuilding while the Dart isolate keeps running. Process death ≈ the entire Flutter engine/isolate being torn down — closer to a fresh cold start than anything Flutter developers deal with routinely, since Flutter apps rarely get killed mid-session the way Android apps do.

---

## 1. What it actually is

Not a crash, not an exception — the Android OS decides your app's entire **process** (not just the `Activity`) is expendable and kills it outright, the same way a Linux OOM killer reaps a background process.

This is fundamentally different from rotation. Rotation destroys/recreates the `Activity` object *while the process stays alive* — see [[activity-lifecycle]]. Process death kills the **process itself**, so every static field, every singleton, every `ViewModel` instance, everything in memory is gone. Nothing survives except what was explicitly written to disk beforehand.

---

## 2. What triggers it

| Scenario | Typical cause |
|---|---|
| User backgrounds the app (home button) and it sits there a while | System reclaims memory under pressure — LRU-killed like any background process |
| Another memory-heavy app launches | OS kills backgrounded apps to make room, even if yours was only backgrounded seconds ago on a low-RAM device |
| Developer option "Don't keep activities" enabled | Forces process death the instant you background — the standard way to *test* this deliberately |
| `adb shell am kill <package>` while backgrounded | Manual simulation, same effect |
| Long background duration | OS more aggressively reclaims apps that haven't been foregrounded recently |

**Testing gotcha:** with a debugger attached (running from Android Studio), the OS generally won't kill your process — this is why process-death bugs are notoriously easy to miss in normal dev testing. You have to explicitly use "Don't keep activities" or `adb kill` to reproduce it.

---

## 3. What survives vs what doesn't

| Mechanism | Survives process death? | Why |
|---|---|---|
| `rememberSaveable` / `onSaveInstanceState` / `SavedStateHandle` | **Yes** | Written to a `Bundle` that `ActivityManager` persists to disk *before* the kill — restored into the new process's `onCreate(savedInstanceState)` |
| `ViewModel` instance | **No** | Lives in a `ViewModelStore` held in memory only — the whole process is gone, so the store is gone. A fresh `ViewModel` is constructed on relaunch (Hilt injects it again from scratch) |
| Hilt singletons (`@Singleton`) | **No** | Recreated as a brand-new instance when the process restarts — any in-memory state they held is gone |
| In-flight coroutines, open sockets/connections | **No** | The process running them no longer exists |

This is the distinction already flagged in [[viewmodel-survives-rotation]]: "survives rotation" specifically means *config change, process alive*. Process death is the scenario where even the `ViewModel` instance doesn't help — only Bundle-backed state does.

---

## 4. How this plays out in this project — active BLE connection lost on relaunch

This is the gap already flagged in `activity-lifecycle.md` §3, walked through concretely:

1. User is on `SensorScreen`, connected to a device via `BleSessionManager` — a Hilt singleton with its own `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (`data/ble/BleSessionManager.kt:34`).
2. User backgrounds the app; OS kills the process under memory pressure.
3. User relaunches. `MainActivity` restarts fresh. `SensorViewModel` is reconstructed by Hilt, and `SavedStateHandle["deviceAddress"]` **does** correctly restore the nav arg (see [[saved-instance-state-vs-savedstatehandle]]) — Navigation-Compose's saved state round-trips through the Bundle.
4. But `BleSessionManager` is recreated as a **brand-new singleton** with no memory of the previous `BluetoothGatt` connection — the native BLE connection itself doesn't survive a process kill either.
5. Result: the user lands back on the sensor screen with the correct `deviceAddress` restored, but showing "Disconnected." There's no reconnection logic anywhere in the codebase — `disconnectAll()` exists at `BleSessionManager.kt:174` but nothing calls it, and nothing calls a reconnect either.

So in this app, `SavedStateHandle`/`rememberSaveable` do their job — simple UI/nav state survives — but the actual BLE session, the thing the user cares about, does not. There's currently no code path that detects "we came back after process death, try reconnecting to `deviceAddress`."

---

## Summary

| Question | Answer |
|---|---|
| Does the process die on every backgrounding? | No — only under memory pressure, long background duration, or forced via dev options/`adb` |
| Does a `ViewModel` instance survive process death? | No — only `SavedStateHandle`/`rememberSaveable`-backed data does, via the restored `Bundle` |
| Does an active BLE (`BluetoothGatt`) connection survive? | No — native connection state is lost along with the process |
| Does this project detect/handle reconnection after process death? | Not currently — `deviceAddress` is restored, but no reconnect is attempted |

### Open items (not yet implemented in this project)
- No detection of "returned after process death" vs. a fresh navigation to `SensorScreen`.
- No automatic reconnect attempt using the restored `deviceAddress`.
- No call to `disconnectAll()` on process teardown (same gap noted in [[activity-lifecycle]]).
