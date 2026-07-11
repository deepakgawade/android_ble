# Why ViewModel Survives Rotation

**Flutter analogy:** there's no exact equivalent, because Flutter rarely destroys/recreates widget state on rotation the way an Android `Activity` does. The closest mental model: imagine `MaterialApp` (the engine/window) got torn down and rebuilt on every rotation, but some object you deliberately hoisted *above* it — a singleton `ChangeNotifier` held in a static/global — kept living across that rebuild untouched. `ViewModel` is Android's built-in, framework-supported version of "hoist state above the thing that gets destroyed."

---

## 1. The problem it solves

A rotation (or any config change without `android:configChanges` override — see [[activity-lifecycle]]) runs the **full** `onDestroy()` → `onCreate()` cycle on the `Activity`. Every object created inside `setContent {}` — every composable's local state — normally dies with it, unless it's `rememberSaveable` (Bundle-backed) or a `ViewModel`.

`rememberSaveable` only works for small, `Parcelable`/primitive state (booleans, strings, ints) — it round-trips through a `Bundle`, so it can't hold things like an in-flight coroutine, a repository reference, or a large in-memory list. `ViewModel` exists for exactly that case: state and in-flight work that must survive the Activity being torn down and rebuilt, without being serialized.

---

## 2. The actual mechanism

It is **not** magic serialization — the same `ViewModel` object instance survives. The trick is *where* it's stored:

1. Every `ComponentActivity` (and `Fragment`, and — via Navigation-Compose — every `NavBackStackEntry`) has a `ViewModelStore`: essentially a `HashMap<String, ViewModel>`.
2. Before `Activity.onDestroy()` runs during a config change, Android calls `Activity.onRetainNonConfigurationInstance()` internally. `ComponentActivity` uses this hook to hand its `ViewModelStore` (the map, not the Activity) off to the **new** Activity instance that's about to be created — it survives in memory across the destroy/create boundary, untouched.
3. `hiltViewModel()` (or plain `viewModel()`) checks that store first: "does a `ViewModel` already exist under this key?" If yes, it returns the **existing instance** instead of constructing a new one. Only `onCreate()`/composition re-runs — the `ViewModel` itself is never re-constructed.
4. The `ViewModelStore` — and everything in it — is only truly cleared (`ViewModel.onCleared()` called) when the owning scope is *actually* finishing, not just rotating: e.g. the Activity finishes for good, or (in this project's case) the `NavBackStackEntry` is popped off the back stack — see [[back-stack-navigation]].

So "survives rotation" really means: *the map that holds it survives, because the platform explicitly hands that map to the next Activity instance before destroying the old one.*

---

## 3. How this project uses it

`hiltViewModel()` is called as a default parameter or inline inside a `composable {}` block for every screen (`MainActivity.kt:56, 87` for `BleConnectionViewModel`; `SensorScreen.kt:50` for `SensorViewModel`; `BookScreen.kt:39` for `BookViewModel`; `WeatherBriefScreen.kt:30` for `WeatherBriefViewModel`):

```kotlin
composable("home") {
    val viewModel: BleConnectionViewModel = hiltViewModel()
    ...
}
```

Because this call happens *inside* a `composable {}` lambda, Navigation-Compose scopes the `ViewModel` to that destination's `NavBackStackEntry`, **not** to the Activity. This matters for two separate lifetimes at once:

| Trigger | What happens to the `ViewModelStore` | Result |
|---|---|---|
| Rotation | Activity's own store is handed to the new Activity instance (mechanism above) | Every screen's `ViewModel` survives — `BleConnectionViewModel`, `SensorViewModel`, etc. keep their state, in-flight coroutines, and injected repositories |
| Navigating away (e.g. `popBackStack()` off `"sensor/{deviceAddress}"`) | The `NavBackStackEntry` for that route is destroyed, and *its* `ViewModelStore` is cleared with it | `SensorViewModel.onCleared()` fires — the ViewModel does **not** survive leaving the screen, even without rotation |

This is the same idea noted in [[activity-lifecycle]] §2: *"`hiltViewModel()`-scoped ViewModels survive rotation because they're tied to the `NavBackStackEntry`, not the Activity instance."* Concretely — the `NavBackStackEntry` object itself is what gets threaded through the config-change survival mechanism (it's stored inside the `NavController`'s own retained state), so a `ViewModel` scoped to it is exactly as rotation-safe as one scoped directly to the Activity, but *additionally* gets cleaned up correctly on navigation, which an Activity-scoped ViewModel would not.

Hilt's role here is separate from the survival mechanism: `@HiltViewModel` + `hiltViewModel()` just handles *constructing* the ViewModel with its dependencies injected (e.g. `BleSessionManager` into `BleConnectionViewModel`) the first time it's created for a given store — Hilt doesn't change when the instance survives or is cleared, that's entirely `ViewModelStore`'s job.

---

## 4. What does *not* survive alongside it — a caveat already flagged in this project

Surviving rotation is not the same as surviving everything. As already documented in [[activity-lifecycle]] §3:

- `BleSessionManager` is a Hilt **singleton** with its own top-level `CoroutineScope`, independent of any `ViewModelStore`. It survives rotation the same way (nothing about it is Activity-scoped at all), but it is *not* torn down by `ViewModel.onCleared()` — `BleConnectionViewModel.onCleared()` only calls `stopScan()`, never `disconnectAll()` (`ui/connection/BleConnectionViewModel.kt:91`).
- On true **process death** (not rotation), the entire `ViewModelStore` hierarchy — Activity's and every `NavBackStackEntry`'s — is gone. `ViewModel`s are reconstructed from scratch on relaunch; only `rememberSaveable`/`savedStateHandle`-backed state survives that, via the Bundle. No `ViewModel` instance itself ever survives process death — "survives rotation" specifically means *config change, process alive*.

---

## Summary

| Question | Answer |
|---|---|
| Does the same object instance survive rotation? | Yes — no serialization involved |
| What makes that possible? | `ComponentActivity` hands its `ViewModelStore` to the next Activity instance via `onRetainNonConfigurationInstance()`, before the old one is destroyed |
| What's this project's scoping? | Per-`NavBackStackEntry` (per-screen), via `hiltViewModel()` inside each `composable {}` — not per-Activity |
| When is a ViewModel actually destroyed here? | When its `NavBackStackEntry` is popped off the back stack, or on true process death — not on rotation |
