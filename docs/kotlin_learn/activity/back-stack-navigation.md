# Back Stack Behavior — with how it's used in this project

**Flutter analogy:** Jetpack Compose Navigation's back stack ≈ Flutter's `Navigator` stack. `navigate()` ≈ `Navigator.push()`, `popBackStack()` ≈ `Navigator.pop()`, and the system back gesture/button pops the top entry automatically unless intercepted — same role as `PopScope`/`WillPopScope` (unused in this project).

There's also a lower-level Android concept — the **Activity task back stack**, governed by `android:launchMode` and `Intent.FLAG_ACTIVITY_*` flags — roughly the "multiple engines/hosts" layer Flutter apps don't normally touch. This project has a single Activity with default (`standard`) launch mode and no intent flags anywhere, so that layer never comes into play. There is exactly **one** back stack to reason about: the Compose Navigation one.

---

## 1. Where the stack is set up

`MainActivity.kt:49–103` — one `NavController`, one `NavHost`, no nested graphs:

```kotlin
val navController = rememberNavController()
...
NavHost(navController = navController, startDestination = "menu") {
    composable("home") { ... }
    composable(route = "menu") {
        MenuScreen(
            onNavigationToBook = { navController.navigate("book") },
            onNavigationToHome = { navController.navigate("home") },
            onNavigationToWeatherBrief = { navController.navigate(WeatherBriefRoute.ROUTE) }
        )
    }
    composable(route = "book") { BookScreen() }
    composable(WeatherBriefRoute.ROUTE) { WeatherBriefScreen() }
    composable("sensor/{deviceAddress}") { SensorScreen(navController = navController) }
}
```

| Flutter | This project |
|---|---|
| `Navigator` | `NavController` |
| `Navigator.push(route)` | `navController.navigate(route)` |
| `Navigator.pop()` | `navController.popBackStack()` |
| `MaterialApp(routes: {})` | `NavHost(navController, startDestination) { composable(route) {} }` |
| `WillPopScope`/`PopScope` | `BackHandler` composable (not used anywhere here) |

---

## 2. Route definitions

Mostly raw string literals declared inline in `MainActivity.kt`: `"home"`, `"menu"`, `"book"`, `"sensor/{deviceAddress}"` (path arg, no `NavType` args map — read implicitly from the route string). No sealed-class route model exists anywhere in the project.

The one exception is the weatherbrief feature, which exposes its route as a constant from its `:api` module — necessary because `:app` depends on `feature:weatherbrief:api`, not `:impl`, where the actual composable lives:

`feature/weatherbrief/api/src/main/java/com/example/api/WeatherBriefRoute.kt`
```kotlin
object WeatherBriefRoute {
    const val ROUTE = "weatherbrief"
}
```

This is a Kotlin-module-boundary equivalent of a Dart package exporting a route-name constant instead of the widget implementation itself.

---

## 3. Explicit back-stack manipulation

Very minimal. Repo-wide grep for `popBackStack`, `launchSingleTop`, `popUpTo`, `saveState`, `restoreState`, `inclusive` turns up almost nothing:

- Every `navigate()` call in `MainActivity.kt` (lines 80, 89, 90, 91) is a **plain push** — no `popUpTo`, no `launchSingleTop`, no `inclusive`. Bouncing Menu → Book → Menu → Book repeatedly would stack up duplicate entries, same as calling `push()` in Flutter without `pushReplacement`.
- The **only** explicit pop in the whole codebase — `ui/sensor/SensorScreen.kt:59`:
  ```kotlin
  IconButton(onClick = { navController.popBackStack() }) {
      Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
  }
  ```
  This is the Compose equivalent of `AppBar(leading: BackButton(onPressed: () => Navigator.pop(context)))`.
- `BookScreen` and `WeatherBriefScreen` have `TopAppBar`s with **no `navigationIcon`** at all — leaving those screens relies entirely on the system back gesture/button, which Compose Navigation auto-handles by popping the stack (default behavior, no custom `BackHandler` involved). Same as relying on Flutter's default back-press `pop()` with no custom `AppBar.leading`.

---

## 4. Activity-level back stack

`AndroidManifest.xml` — single `<activity android:name=".MainActivity">`, **no `android:launchMode`** set (defaults to `standard`), no `taskAffinity`, no other activities declared in any module. No `Intent.FLAG_ACTIVITY_*` flags, no `OnBackPressedCallback`, no `onBackPressed()` override, no `BackHandler` composable anywhere — confirmed via repo-wide grep, zero hits. Back handling is 100% delegated to Compose Navigation's default system-back integration, plus the one manual `popBackStack()` noted above.

---

## 5. Walkthrough — Menu → Weather Brief (METAR lookup)

`ui/common/MenuScreen.kt` is a stateless composable — it takes navigation as callbacks (`onNavigationToWeatherBrief: () -> Unit`) rather than holding a `NavController` reference, the same pattern as passing an `onTap` callback down to a Flutter widget instead of giving it `Navigator` access directly:

```kotlin
fun MenuScreen(
    onNavigationToHome: () -> Unit,
    onNavigationToBook: () -> Unit,
    onNavigationToWeatherBrief: () -> Unit,
) {
    ...
    Button(onClick = onNavigationToWeatherBrief, ...) { Text("Weather Brief") }
}
```

Wiring happens in `MainActivity.kt:87–93`, binding `onNavigationToWeatherBrief` to `navController.navigate(WeatherBriefRoute.ROUTE)`. Stack after tapping: `menu → weatherbrief` (plain push, no `popUpTo`/`inclusive`).

`WeatherBriefScreen` (`feature/weatherbrief/impl/.../WeatherBriefScreen.kt:47–48`) has a title-only `TopAppBar`, no back icon:
```kotlin
Scaffold(topBar = { TopAppBar(title = { Text("Weather Brief") }) }) { ... }
```
Getting back to the menu relies purely on the system back gesture/button popping `"weatherbrief"` off the stack — there's no `rememberSaveable`/state-restoration wiring for Menu's scroll or UI state on return, so it comes back in its default (not preserved) state.

Introduced by commit `606ca8f` (scaffolding the aviation-weather modules and wiring METAR-by-ICAO into the menu/NavHost/Hilt DI).

---

## 6. Bottom navigation / multiple back stacks

None present. Repo-wide grep for `BottomNavigation`, `NavigationBar`, `bottomNav` returns zero matches — just the single flat `NavHost` described above, no nested graphs, no multi-stack `saveState`/`restoreState` pattern.

---

## Summary

| Aspect | This project |
|---|---|
| Number of back stacks | 1 (single `NavHost`, single Activity) |
| Route model | Raw string literals, except `WeatherBriefRoute.ROUTE` constant |
| Stack-shaping (`popUpTo`/`launchSingleTop`/`inclusive`) | Not used anywhere — every `navigate()` is a plain push |
| Explicit back arrows | Only `SensorScreen` (`popBackStack()`); `BookScreen`/`WeatherBriefScreen` rely on system back |
| Custom back interception | None (no `BackHandler`, no `OnBackPressedCallback`) |

### Open items (not yet implemented in this project)
- No dedup/`launchSingleTop` guard — repeated navigation to the same screen stacks duplicate entries.
- No back arrow on `BookScreen` or `WeatherBriefScreen` — back only works via system gesture/button.
- No state preservation (`saveState`/`restoreState`) when returning to `MenuScreen`.
