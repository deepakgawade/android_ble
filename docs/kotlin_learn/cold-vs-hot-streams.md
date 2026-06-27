# Cold vs Hot Streams

The key difference is **when does emission start, and does a new subscriber see past events?**

| | Cold | Hot |
|---|---|---|
| Starts when | A collector subscribes | Already running independently |
| Past events | Each collector starts fresh | Subscriber joins mid-stream |
| Dart analogy | `Stream` from a generator / `StreamController` (single-sub) | `StreamController.broadcast()` or `BehaviorSubject` (rxdart) |

---

## How the project uses both

### Hot Streams — `StateFlow` (always-on state holders)

```kotlin
// BleSessionManager.kt:47-48
private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

// BleDeviceSession.kt:40-42
private val _connectionState = MutableStateFlow<BleState>(BleState.Idle)
val connectionState: StateFlow<BleState> = _connectionState.asStateFlow()
```

`MutableStateFlow` is hot — it holds a **current value** and lives independently of subscribers. When the BLE scan callback fires (`onScanResult`), it pushes to `_scannedDevices` regardless of whether the UI is listening. A new subscriber immediately receives the latest state.

**Dart equivalent**: `ValueNotifier<T>` or `BehaviorSubject` from rxdart.

---

### Hot Stream — `SharedFlow` (event bus for BLE notifications)

```kotlin
// BleDeviceSession.kt:50-53
private val _notificationChannel = MutableSharedFlow<GattNotification>(
    replay = 0, extraBufferCapacity = 32
)
val notificationChannel: SharedFlow<GattNotification> = _notificationChannel.asSharedFlow()
```

`MutableSharedFlow` with `replay=0` is hot — the BLE hardware fires `onCharacteristicChanged`, and it emits into this channel **regardless of subscribers**. The `extraBufferCapacity = 32` prevents dropped BLE packets when the collector is momentarily slow.

**Dart equivalent**: `StreamController.broadcast()` with a buffer — events are dropped if nobody is listening (with `replay=0`).

---

### Cold Stream — `Flow` (sensor data pipeline)

```kotlin
// SensorRepositoryImpl.kt:28-53
override fun observeSensorData(): Flow<SensorReading> {
    return session.notificationChannel.mapNotNull { notification ->
        // transform raw bytes → SensorReading
    }.distinctUntilChanged()
}
```

`observeSensorData()` returns a cold `Flow`. **Nothing runs until `.collect{}` is called.** The transformation pipeline (mapNotNull, distinctUntilChanged) only activates when `SensorViewModel` calls:

```kotlin
// SensorViewModel.kt:72-83
observeSensorData()
    .onStart { controlNotifications.start() }  // side effect on subscribe
    .buffer(Channel.UNLIMITED)
    .collect { reading -> ... }
```

`.onStart { controlNotifications.start() }` is only possible because `Flow` is cold — you can hook into the subscription moment. In a hot stream, there is no "subscription start."

**Dart equivalent**: A `Stream` returned by an `async*` generator function.

---

## The flow through this architecture

```
BLE Hardware (hot, always firing)
       ↓  onCharacteristicChanged()
_notificationChannel: SharedFlow  ← hot, fire-and-forget events
       ↓  .mapNotNull { }
observeSensorData(): Flow         ← cold, transforms on subscription
       ↓  .collect { }
_uiState: MutableStateFlow        ← hot, UI reads latest value
       ↓  .asStateFlow()
SensorScreen (Compose)            ← recomposes on each emission
```

The critical insight: `notificationChannel` (hot `SharedFlow`) feeds `observeSensorData()` (cold `Flow`). When the cold flow is collected, it "taps into" the already-running hot stream. If nothing is collecting `observeSensorData()`, BLE packets still arrive in `notificationChannel` but are silently dropped (since `replay=0`).

---

## Why this design?

- **`StateFlow` for connection state / device list** — UI always needs the *current* value, even if it subscribes late (e.g., screen rotation). StateFlow caches it.
- **`SharedFlow` for BLE notifications** — sensor packets are time-sensitive events, not state. There's no meaningful "current temperature" before you subscribe.
- **Cold `Flow` for the data pipeline** — the transform chain (decode bytes → filter nulls → deduplicate) should only run when the UI actually needs data. Cold flow gives you free lifecycle management: cancel the coroutine, the pipeline stops.
