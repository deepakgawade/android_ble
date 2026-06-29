# StateFlow vs SharedFlow vs Channel

Think of them as three different kinds of streams, each designed for a different job.

---

## 1. `StateFlow` — the current value holder

**Flutter analogy:** `ValueNotifier<T>` / `BehaviorSubject` (from RxDart)

- Always holds **one current value** (must be initialized)
- New collectors immediately get the **latest value** (replay = 1, always)
- Emitting the same value twice is **deduplicated** — no duplicate emit
- Best for: **UI state** that the screen needs to show right now

**In this project — `BleDeviceSession.kt:40-42`**
```kotlin
private val _connectionState = MutableStateFlow<BleState>(BleState.Idle)
val connectionState: StateFlow<BleState> = _connectionState.asStateFlow()
```
The connection state (`Idle → Connecting → Connected`) is always available. When a new screen opens, it immediately gets the current connection state without waiting for the next update.

**In this project — `SensorViewModel.kt:52-53`**
```kotlin
private val _uiState = MutableStateFlow(SensorUiState())
val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()
```
The entire UI (loading, reading, error) is one `StateFlow`. Compose collects it with `collectAsState()` and re-renders on each update — exactly like `setState()` in Flutter.

---

## 2. `SharedFlow` — the broadcast event bus

**Flutter analogy:** `StreamController.broadcast()` / `PublishSubject` (RxDart)

- Does **not** hold a value — no initial value required
- You control replay with `replay = N` (how many past events new collectors see)
- Multiple collectors can listen simultaneously (broadcast)
- Best for: **one-shot events** that multiple parts of the app need to react to

**In this project — `BleDeviceSession.kt:50-53`**
```kotlin
private val _notificationChannel = MutableSharedFlow<GattNotification>(
    replay = 0, extraBufferCapacity = 32
)
val notificationChannel: SharedFlow<GattNotification> = _notificationChannel.asSharedFlow()
```
BLE characteristic notifications arrive from hardware. `replay = 0` means: don't replay old sensor readings to a new subscriber — stale sensor data is useless. `extraBufferCapacity = 32` means: buffer up to 32 notifications if the collector is busy, instead of dropping them.

---

## 3. `Channel` — the work queue (point-to-point pipe)

**Flutter analogy:** A `Queue` or a single-consumer `StreamController` (non-broadcast)

- Like a **message queue** — one producer, one consumer
- Items wait in the buffer until consumed (not dropped like SharedFlow can)
- Only **one collector** should process it (unlike SharedFlow)
- Best for: **backpressure** — when the producer is faster than the consumer

**In this project — `SensorViewModel.kt:76`**
```kotlin
observeSensorData()
    .buffer(Channel.UNLIMITED)  // Channel used as a buffer strategy
    .collect { reading -> ... }
```
`Channel.UNLIMITED` here is used as a **buffer size constant** for `.buffer()`. It tells the flow: "don't slow down the BLE hardware sensor stream even if the UI update (`.collect`) is momentarily busy — queue everything." This prevents dropped sensor readings when the main thread is busy rendering.

---

## Side-by-side summary

| | `StateFlow` | `SharedFlow` | `Channel` |
|---|---|---|---|
| Holds current value | Yes (always) | No | No |
| Replay to new collector | Always 1 | Configurable (0, N) | None |
| Multiple collectors | Yes | Yes | No (single consumer) |
| Deduplication | Yes | No | No |
| Flutter analogy | `ValueNotifier` | `StreamController.broadcast()` | `Queue` / single `StreamController` |
| Project usage | UI state, connection state | BLE hardware notifications | Flow backpressure buffer |

---

## The private/public pattern

```kotlin
// Private mutable — only this class can push values
private val _uiState = MutableStateFlow(SensorUiState())

// Public read-only — exposed to collectors
val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()
```

This is identical to Flutter's `_controller` + `.stream` pattern — expose only the read side to the outside world.
