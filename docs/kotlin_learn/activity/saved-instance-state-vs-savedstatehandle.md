# onSaveInstanceState vs SavedStateHandle

**Flutter analogy:** neither has a clean 1:1 mapping since Flutter doesn't kill your engine on rotation, but the closest is `RestorationMixin`/`RestorableProperty` (Flutter's state-restoration API for process death) — `SavedStateHandle` is Android's version of that, just scoped to a `ViewModel` instead of a `State<T>`.

---

## 1. Core difference

Both exist to survive **process death** (not just rotation — `ViewModel` already survives rotation on its own for free, see [[viewmodel-survives-rotation]]). The difference is *where* the data lives and *how* you interact with it.

| | `onSaveInstanceState` | `SavedStateHandle` |
|---|---|---|
| Owner | `Activity`/`Fragment` — you override the callback | `ViewModel` — injected, no override needed |
| Storage | `Bundle`, manually `put`/`get` | Also a `Bundle` under the hood, exposed as a `Map`-like/`StateFlow`-friendly API |
| When it's written | System calls `onSaveInstanceState(Bundle)` before your Activity *might* be killed (backgrounding, rotation, low memory) | Same underlying trigger, but plumbing is automatic — `ViewModel` registers with the `SavedStateRegistry` for you |
| What can go in it | Only `Parcelable`/primitives, small amounts (IPC-transported, ~1MB cliff) | Same size/type constraints — still a `Bundle` — but accessed as `SavedStateHandle["key"]` or `getStateFlow("key", default)` |
| Where you use it | Directly in `Activity`/`Fragment` code (rare in Compose apps) | Inside a `ViewModel` constructor — the idiomatic Compose/ViewModel-first way |
| Compose equivalent | `rememberSaveable` (wraps the same Bundle mechanism) | `SavedStateHandle` |

**Push vs pull:** `onSaveInstanceState` is a **push** model — you manually save right before death and manually restore in `onCreate(savedInstanceState: Bundle?)`. `SavedStateHandle` is a **pull/reactive** model — you read/write it like a map or a `StateFlow`, and the framework hooks it into the save/restore lifecycle for you, no override needed.

---

## 2. How this project actually uses it

`ui/sensor/SensorViewModel.kt:37-44`:
```kotlin
class SensorViewModel @Inject constructor(
    ...
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val deviceAddress: String = checkNotNull(savedStateHandle["deviceAddress"])
```

This isn't primarily about process-death survival here — Navigation-Compose automatically populates `SavedStateHandle` with the nav-graph args (`"sensor/{deviceAddress}"`, declared in `MainActivity.kt:100`), so `SavedStateHandle` doubles as **how a Hilt ViewModel reads its own nav args** without needing a `NavBackStackEntry` reference passed in manually. As a side effect, `deviceAddress` also survives process death for free — no extra code needed.

`onSaveInstanceState` is **not overridden anywhere** in this codebase (confirmed via grep). Instead, the project uses `rememberSaveable` — e.g. `showSheet` in `MainActivity.kt:58` (see [[activity-lifecycle]] §2) — which is the Compose-native way of getting the same Bundle-backed survival without touching the raw Activity callback directly.

---

## Summary

| Question | Answer |
|---|---|
| Do both survive process death? | Yes — both are backed by the same `Bundle`/`SavedStateRegistry` mechanism |
| Which is idiomatic in a ViewModel-first, Compose app? | `SavedStateHandle` (ViewModel) / `rememberSaveable` (composable-local state) |
| Does this project override `onSaveInstanceState`? | No |
| Where is `SavedStateHandle` used here, and why? | `SensorViewModel`, to read the `deviceAddress` nav argument — process-death survival is a free side effect, not the primary reason |
