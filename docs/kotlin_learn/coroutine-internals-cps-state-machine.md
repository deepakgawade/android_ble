# Coroutine Internals: CPS & State Machine

## 1. The Problem: Async Without Blocking Threads

In Dart, `async/await` compiles your function into a state machine under the hood — Kotlin does the exact same thing, just with more explicit machinery.

---

## 2. CPS — Continuation Passing Style

A **Continuation** is "the rest of the computation after this point."

In normal code:
```kotlin
val result = doWork()  // blocks here
useResult(result)      // runs after
```

In CPS, `useResult` is passed *into* `doWork` as a callback — it's the continuation:
```kotlin
doWork { result -> useResult(result) }
```

Kotlin's `suspend` keyword is syntactic sugar over CPS. The compiler secretly adds a `Continuation<T>` parameter to every `suspend fun`:

```kotlin
// What you write:
suspend fun connect(): Unit

// What the compiler generates (simplified):
fun connect(continuation: Continuation<Unit>): Any
```

The return type becomes `Any` because the function can either:
- Return `COROUTINE_SUSPENDED` — suspending, "call me back later"
- Return the actual result immediately — fast-path, no actual suspension

---

## 3. State Machine — How suspend Points Become Labels

Every `suspend` call inside a function becomes a **state label**. The compiler splits your function at each `suspend` point.

Example from `BleDeviceSession.setNotifications()`:

```kotlin
private suspend fun setNotifications(enable: Boolean) = withContext(dispatchers.io) {
    servicesDiscovered.await()   // suspend point 1
    // ...
    pendingWrite.await()         // suspend point 2
    // ...
    pendingWrite.await()         // suspend point 3
}
```

The compiler generates roughly this (decompiled pseudo-code):

```kotlin
fun setNotifications(enable: Boolean, cont: Continuation<Unit>): Any {
    val sm = cont as? SetNotificationsStateMachine ?: SetNotificationsStateMachine(cont)

    when (sm.label) {
        0 -> {
            sm.label = 1
            val result = servicesDiscovered.await(sm)
            if (result == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
        }
        1 -> {
            sm.label = 2
            val result = pendingWrite.await(sm)
            if (result == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
        }
        2 -> { /* ... */ }
    }
}
```

**The `Continuation` object IS the state machine.** It holds:
- `label` — which suspend point to resume at
- Local variables at the time of suspension (captured as fields)

This is exactly like Dart's `async*` generators — each `yield`/`await` is a state in a switch statement.

---

## 4. How It Plays Out in This Project

### `CompletableDeferred` — The Explicit Continuation Gate

**Dart analogy:** `Completer<void>` + `completer.complete()`

```kotlin
// BleDeviceSession.kt
private var servicesDiscovered = CompletableDeferred<Unit>()

// In setNotifications() — coroutine parks here
servicesDiscovered.await()

// In onServicesDiscovered() callback — BLE thread resumes the coroutine
servicesDiscovered.complete(Unit)
```

Flow:
1. `setNotifications()` hits `await()` → returns `COROUTINE_SUSPENDED`, thread is freed
2. Android BLE OS fires `onServicesDiscovered()` on a BLE internal thread
3. `.complete(Unit)` calls the stored `Continuation.resumeWith(Result.success(Unit))`
4. The dispatcher re-queues `setNotifications` at `label = 1` on `dispatchers.io`

This is the bridge between the **callback world** (Android BLE API) and the **coroutine world**.

---

### `pendingWrite` — Serializing BLE Writes

GATT requires sequential writes — you can't send the next write until the previous one ACKs.

```kotlin
// BleDeviceSession.kt — in setNotifications()
pendingWrite = CompletableDeferred()
g.writeDescriptor(cccd, cccdValue)   // fire-and-forget to hardware
pendingWrite.await()                  // park until hardware ACKs

// BleDeviceSession.kt — in onDescriptorWrite() callback
pendingWrite.complete(status == BluetoothGatt.GATT_SUCCESS)
```

`CompletableDeferred.await()` turns the async callback into sequential-looking code. The coroutine suspends (gives up its thread) and resumes only when the hardware ACKs.

---

### `Flow` — Continuous State Machine

```kotlin
// SensorRepositoryImpl.kt
return session.notificationChannel     // hot SharedFlow — already running
    .mapNotNull { notification -> /* decode */ }
    .distinctUntilChanged()
```

A `Flow` is a **lazy, cold state machine**. Each operator wraps the previous one as a decorator. When `collect {}` is called in the ViewModel, the chain connects and the state machine starts running. Each emission is a "resume" of the chain's internal continuation.

| Flow type | Dart analogy | Behavior |
|---|---|---|
| `Flow` (cold) | `Stream` (single-subscription) | Starts fresh per collector |
| `SharedFlow` (hot) | `StreamController.broadcast()` | Runs independently; collectors join mid-stream |
| `StateFlow` (hot) | `ValueNotifier` / `BehaviorSubject` | Always holds latest value; replays to new collectors |

---

### `viewModelScope.launch` — Coroutine Lifecycle

```kotlin
// BleConnectionViewModel.kt
scanJob = viewModelScope.launch(dispatchers.io) {
    launch {                  // child coroutine
        scanForDevices().collect { devices -> _scanState.update { it.copy(devices = devices) } }
    }
    yield()                   // cooperative suspend — gives child coroutine one turn first
    startScanUseCase()
}
```

- `viewModelScope` is tied to the ViewModel lifecycle. When the ViewModel is cleared, all coroutines in the scope cancel via **structured concurrency** — the scope traverses its child Job tree and injects `CancellationException` at the next suspend point of each child.
- `yield()` is a pure state-machine trick: suspends and immediately re-queues, giving the child coroutine's state machine one turn to initialize its `collect` callback *before* `startScanUseCase()` fires the BLE hardware scan.

---

## 5. The Full Mental Model

```
Your suspend fun
      │
      ▼  (Kotlin compiler)
State machine class (one per suspend fun)
  ├── label: Int          ← current suspend point
  ├── local vars as fields ← captured state
  └── parent: Continuation ← who to resume when done
      │
      ▼  (at each suspend point)
Returns COROUTINE_SUSPENDED
Thread is freed back to the pool
      │
      ▼  (when event fires — BLE callback, network, timer)
continuation.resumeWith(result)
      │
      ▼  (Dispatcher re-queues the state machine)
State machine runs from label N+1
```

**Key insight:** No thread blocks. The `suspend` function is just a regular function that returns early and is re-called later at the correct label. All the "blocking" is just a label check at the top of the generated `when` statement.

---

## 6. Quick Reference

| Concept | Kotlin | Dart |
|---|---|---|
| Suspend function | `suspend fun foo()` | `Future<void> foo() async` |
| Await a future | `deferred.await()` | `await completer.future` |
| One-shot async bridge | `CompletableDeferred` | `Completer` |
| Cancel propagation | Structured concurrency via `Job` tree | `CancelToken` / scope cancellation |
| State machine label | compiler-generated `when (label)` | compiler-generated `_state` switch |
| Free thread on suspend | Yes — dispatcher pool | Yes — Dart event loop |
