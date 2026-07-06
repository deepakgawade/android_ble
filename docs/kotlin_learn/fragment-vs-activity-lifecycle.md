# Fragment Lifecycle vs Activity Lifecycle

This project doesn't use Fragments (single-Activity + Compose `NavHost`), but the concept shows up constantly in Android tutorials and legacy code, so it's worth knowing.

---

## 1. Key difference: Fragments have no window of their own

An `Activity` owns a window and is managed directly by the OS. A `Fragment` has **no window** — it's a reusable chunk of UI/behavior that must be hosted inside an Activity (or another Fragment).

**Flutter analogy:** if `Activity` is like the whole Flutter `Engine`/root, a `Fragment` is closer to a `StatefulWidget` that has its own long-lived controller-like lifecycle, nested inside that one Activity.

---

## 2. The extra callbacks

Fragments have everything an Activity has, plus lifecycle stages for their **view**, which can be destroyed and recreated independently of the Fragment object itself:

```
onAttach() → onCreate() → onCreateView() → onViewCreated()
    → onStart() → onResume() → [RESUMED]
    → onPause() → onStop()
    → onDestroyView()   ← view destroyed, Fragment instance still alive
    → onDestroy() → onDetach()
```

---

## 3. Side-by-side

| Stage | Activity | Fragment |
|---|---|---|
| Owns a window | Yes | No — draws into its host Activity's window |
| Created by | OS (launcher, `startActivity`) | Host Activity/FragmentManager, added to a container |
| View vs instance separation | No — `onCreate`/`onDestroy` is it | Yes — `onCreateView`/`onDestroyView` can fire multiple times while the Fragment instance survives (e.g. when placed in a ViewPager and swiped off-screen) |
| Backstack | Managed by the OS task stack | Managed by `FragmentManager`, independent of the Activity backstack |
| Config change (rotation) | Whole Activity destroyed/recreated (unless `configChanges` overridden) | Fragment's view is destroyed/recreated; the Fragment instance itself can survive (old `retainInstance` API, or a Fragment-scoped ViewModel) |
| Lifecycle owner for ViewModel/LiveData | `this` (the Activity) | `viewLifecycleOwner` — **not** `this` (the Fragment) |

---

## 4. The classic Fragment bug

Using the wrong lifecycle owner is the textbook Fragment memory-leak: observing with the Fragment's own lifecycle instead of `viewLifecycleOwner` keeps observers alive after `onDestroyView`, because the Fragment instance can outlive its view (e.g. kept in a backstack but currently off-screen).

```kotlin
// Leaks past onDestroyView — Fragment instance lifecycle, not the view's
viewModel.uiState.observe(this) { ... }

// Correct — tied to the view, cleared on onDestroyView
viewModel.uiState.observe(viewLifecycleOwner) { ... }
```

---

## 5. Why this project sidesteps the problem

This app uses Jetpack Compose navigation (`NavHost` in `MainActivity.kt`) instead of the Fragment system. Compose's `hiltViewModel()` scoped to a `NavBackStackEntry` replaces what a Fragment-scoped ViewModel would have done — same survive-rotation / cleared-on-pop behavior — but without the `onCreateView`/`onDestroyView`/`viewLifecycleOwner` split, since Compose doesn't have a separate "view" object that gets torn down independently of its backstack entry.

The nearest thing to the "view outlives vs. doesn't outlive" distinction in this codebase is `collectAsStateWithLifecycle()` in `SensorScreen.kt:52` — it stops collecting when the Activity drops below `STARTED`, which is Compose's version of respecting `viewLifecycleOwner` rather than a longer-lived owner.

See also: [Activity Lifecycle — with edge cases from this project](activity/activity-lifecycle.md)
