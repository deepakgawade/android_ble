# Flow Operators: map, filter, combine, zip, flatMapLatest

Think of a `Flow` like a Dart `Stream`. Operators are methods you chain on it to transform, filter, or merge data before it reaches the collector.

---

## 1. `map` — transform each item

**Flutter analogy:** `stream.map((x) => ...)`

Converts every emitted value into something else. One-in, one-out.

```kotlin
// General usage
flowOf(1, 2, 3)
    .map { it * 10 }
    .collect { println(it) }  // 10, 20, 30
```

**In this project — `BleSessionManager.kt:147`**
```kotlin
session.connectionState.map { state -> addr to state }
```
Each `BleState` value is transformed into a `Pair<String, BleState>` (address + state) so it can later be combined into a map.

---

## 2. `filter` — drop unwanted items

**Flutter analogy:** `stream.where((x) => ...)`

Only lets through values that match a condition. Items that fail the check are silently dropped.

```kotlin
// General usage
flowOf(1, 2, 3, 4, 5)
    .filter { it % 2 == 0 }
    .collect { println(it) }  // 2, 4
```

**In this project — `ScanBottomSheet.kt:59`**
```kotlin
.filter { it != SheetValue.Expanded }
```
Sheet state changes are filtered — the logic only reacts when the sheet is NOT expanded.

**Bonus — `mapNotNull` (map + filter combined) — `SensorRepositoryImpl.kt:32`**
```kotlin
session.notificationChannel.mapNotNull { notification ->
    // returns null if both temp+humidity aren't ready yet → item is dropped
    val t = latestTemp ?: return@mapNotNull null
    val h = latestHumidity ?: return@mapNotNull null
    SensorReading(t, h, System.currentTimeMillis())
}
```
`mapNotNull` transforms AND filters in one step — if the lambda returns `null`, that emission is skipped. Used here to wait until both temperature and humidity have arrived before emitting a `SensorReading`.

---

## 3. `combine` — merge multiple flows, react to any change

**Flutter analogy:** `Rx.combineLatest([streamA, streamB], ...)` / like `StreamZip` but fires on every update

- Waits until **all** flows have emitted at least one value
- After that, re-emits whenever **any** one of them changes, using the **latest** value from each

```kotlin
// General usage
val nameFlow = flowOf("Deepak")
val scoreFlow = flowOf(100)

combine(nameFlow, scoreFlow) { name, score ->
    "$name: $score"
}.collect { println(it) }  // "Deepak: 100"
```

**In this project — `BleSessionManager.kt:144-149`**
```kotlin
val allConnectionState = _sessions.flatMapLatest { sessionMap ->
    combine(
        sessionMap.map { (addr, session) ->
            session.connectionState.map { state -> addr to state }
        }
    ) { pairs -> pairs.toMap() }
}
```
`combine` merges the `connectionState` flows from **all connected BLE devices** into one `Map<String, BleState>`. When any device changes state, the whole map is re-emitted.

---

## 4. `zip` — pair items one-to-one in order

**Flutter analogy:** Zipping two iterables — like Python's `zip()`

- Pairs the **1st item** from flow A with the **1st item** from flow B, then 2nd with 2nd, etc.
- Waits for both sides before emitting — **slower side controls the pace**
- Stops when the shorter flow ends

```kotlin
// General usage
val letters = flowOf("A", "B", "C")
val numbers = flowOf(1, 2, 3)

letters.zip(numbers) { letter, number ->
    "$letter$number"
}.collect { println(it) }  // A1, B2, C3
```

Not used in this project yet, but useful for: pairing a command flow with an acknowledgement flow, or matching request/response pairs.

Key difference from `combine`:

| | `combine` | `zip` |
|---|---|---|
| Fires when | Either flow updates | Both flows have a new item |
| Pairing | Latest × Latest | 1st × 1st, 2nd × 2nd |
| Use case | Live UI merging | Request/response matching |

---

## 5. `flatMapLatest` — switch to a new flow on each emission

**Flutter analogy:** `switchMap` in RxDart — cancels the previous inner stream when a new value arrives

- When the source emits a new value, it **cancels** the previous inner flow and **starts a new one**
- Only the latest inner flow is ever active

```kotlin
// General usage — like a search box: cancel old search, start new one
searchQuery
    .flatMapLatest { query ->
        api.search(query)  // cancels previous search if user types again
    }
    .collect { results -> showResults(results) }
```

**In this project — `BleSessionManager.kt:144`**
```kotlin
val allConnectionState = _sessions.flatMapLatest { sessionMap ->
    // Every time the session MAP changes (device added/removed),
    // cancel the old combine() and build a new one with the updated sessions
    combine(...) { pairs -> pairs.toMap() }
}
```
When a new BLE device connects (added to `_sessions`), the old `combine()` watching 2 devices is cancelled and a new `combine()` watching 3 devices is created. Without `flatMapLatest`, you'd be observing stale sessions.

---

## The full pipeline in this project visualized

```
BLE Hardware
    │
    ▼ (raw bytes)
notificationChannel (SharedFlow)
    │
    ▼ mapNotNull { decode bytes → SensorReading or null }
    │
    ▼ filter nulls out automatically
    │
    ▼ buffer(Channel.UNLIMITED)   ← don't block BLE thread
    │
    ▼ collect { update _uiState }
    │
    ▼ StateFlow<SensorUiState>
    │
    ▼ collectAsState() in Compose → UI renders
```

---

## Quick reference

| Operator | Input → Output | Cancels previous? | Project usage |
|---|---|---|---|
| `map` | 1 → 1 (transformed) | No | `state → addr to state` |
| `mapNotNull` | 1 → 0 or 1 | No | decode + drop incomplete readings |
| `filter` | 1 → 0 or 1 | No | ignore expanded sheet state |
| `combine` | N flows → 1 | No | merge all device states into one map |
| `zip` | 2 flows → 1 (paired) | No | not used yet |
| `flatMapLatest` | 1 → new Flow | Yes (cancels old) | rebuild combine on session change |
