# launch vs async & Structured Concurrency

## 1. launch vs async — the one-line difference

| | `launch` | `async` |
|---|---|---|
| Returns | `Job` | `Deferred<T>` (a `Job` that holds a result) |
| Result | fire-and-forget | call `.await()` to get the value |
| Dart analogy | `unawaited(future)` | `Future<T>` you hold and `await` later |
| On exception | propagates to parent immediately | stored inside `Deferred`, thrown at `.await()` |

```kotlin
// launch — you care about side effects, not a return value
val job: Job = viewModelScope.launch {
    stopScanUseCase()   // just run it, result doesn't matter
}

// async — you need the return value
val deferred: Deferred<Boolean> = viewModelScope.async {
    scanRepository.isBluetoothEnabled()   // returns Boolean
}
val enabled: Boolean = deferred.await()   // suspends until result is ready
```

This project uses **only `launch`** — every coroutine is fire-and-forget (UI state updates, BLE commands, flow collection). `async` would appear if you needed parallel work that returns values to be combined.

---

## 2. Structured Concurrency

The central idea that makes coroutines safe:

> **A coroutine can only live as long as the scope that created it.**

### The Job Tree

Every coroutine has a `Job`. `launch` / `async` inside a scope create a **child Job** attached to the parent. This forms a tree:

```
viewModelScope (root Job)
├── scanJob  (BleConnectionViewModel.kt:55)
│   └── inner launch for collect  (line 63)
└── stopScan launch  (line 83)
```

When `viewModelScope` is cancelled (ViewModel cleared), it cancels every node in that tree — top-down, with `CancellationException`. No orphan coroutines.

### How it plays out in `BleConnectionViewModel`

```kotlin
// parent job
scanJob = viewModelScope.launch(dispatchers.io) {

    // child job — automatically attached to parent above
    launch {
        scanForDevices()
            .collect { devices -> _scanState.update { it.copy(devices = devices) } }
    }

    yield()           // give child one turn to register its collect callback
    startScanUseCase()
}
```

Cancel `scanJob` → the child `launch { collect }` is also cancelled automatically. No need to track the inner job manually.

---

## 3. The Three Propagation Rules

**Rule 1 — Child exception → cancels parent (and siblings)**
```kotlin
viewModelScope.launch {
    launch { throw Exception() }   // child A crashes
    launch { delay(1000) }         // child B — also cancelled
}
// viewModelScope itself also receives the exception
```

**Rule 2 — Parent cancellation → cancels all children**
```kotlin
viewModelScope.cancel()   // all children stop at their next suspend point
```

**Rule 3 — Parent waits for all children before completing**
```kotlin
viewModelScope.launch {
    launch { delay(5000) }     // parent doesn't finish...
    launch { delay(3000) }     // ...until both children finish
}
```

---

## 4. SupervisorJob — Break Rule 1

`BleSessionManager.kt:59`:
```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

With a normal `Job`, one child failing cancels the entire scope. With `SupervisorJob`, child failures are **isolated** — sibling coroutines keep running.

```
CoroutineScope(SupervisorJob())
├── scope.launch { session A collect }   ← crashes (BLE error)
├── scope.launch { session B collect }   ← still running ✓
└── scope.launch { session C collect }   ← still running ✓
```

In `BleSessionManager`, each BLE session runs its own `connectionState.collect` coroutine inside this scope. If one session errors out, the other sessions must keep working — `SupervisorJob` enforces exactly that.

**Dart analogy:** A `Zone` with a custom `onError` handler that doesn't kill the zone — errors are isolated per task rather than crashing the whole zone.

---

## 5. `coroutineScope {}` — Temporary Structured Scope

```kotlin
suspend fun doParallelWork() = coroutineScope {   // suspend fun, not a property
    val a = async { fetchTemperature() }
    val b = async { fetchHumidity() }
    SensorReading(a.await(), b.await())   // both run in parallel, both must finish
}
```

- Suspends until **all children** complete
- If either child throws, the other is cancelled and the exception propagates up
- Acts as an atomic unit of work — either finishes or fails together

This is where `async` + `await` actually shines. Not currently in the project but the natural fit for parallel BLE reads (e.g. fetch temperature + humidity simultaneously).

---

## 6. Mental Model: Scope = Lifetime Contract

```
CoroutineScope
│  └── defines: which thread (Dispatcher) + how long (Job)
│
├── launch { }   → Job         (side effect, no value)
│     └── child Job, lives inside parent scope
│
└── async { }    → Deferred<T> (has a value to return)
      └── child Job, same cancellation rules as launch
```

**The key guarantee:** if you never use `GlobalScope` and never `launch` from a raw thread, there can be no coroutine leak. Every coroutine is owned by a scope, and every scope is owned by something with a well-defined lifecycle.

---

## 7. Where Each Pattern Appears in This Project

| Pattern | File | Why |
|---|---|---|
| `viewModelScope.launch` | `SensorViewModel`, `BleConnectionViewModel`, `BookViewModel` | ViewModel owns the lifecycle; cancelled automatically on `onCleared()` |
| `Job` reference + `.cancel()` | `scanJob`, `startSensorJob` | Allows manual early cancellation of long-running scan/observe loops |
| `CoroutineScope(SupervisorJob())` | `BleSessionManager` | App-level singleton; session failures must not kill sibling sessions |
| Nested `launch` inside `launch` | `BleConnectionViewModel:63` | Child coroutine for flow collection; inherits parent's cancellation |
