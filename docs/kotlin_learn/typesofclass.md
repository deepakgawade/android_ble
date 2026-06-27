# Types of Classes in Kotlin — Project Reference

Examples drawn from this project's codebase.

---

## `val` vs `var`

### `val` (read-only) — use by default

`val` declares a property whose reference cannot be reassigned after initialization. The project strongly favors `val` everywhere — domain models, DTOs, and UI state fields are all `val`.

```kotlin
// domain/model/ScannedDevice.kt
data class ScannedDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
)
```

In ViewModels, the `MutableStateFlow` itself is `val` because the _reference_ never changes — only what flows through it:

```kotlin
// ui/sensor/SensorViewModel.kt
private val _uiState = MutableStateFlow(SensorUiState())
val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()
```

### `var` — only when reassignment is required

`var` appears in only a few specific situations in this project:

| File | Declaration | Reason |
|---|---|---|
| `BleDeviceSession.kt` | `private var gatt: BluetoothGatt? = null` | GATT handle is replaced on every reconnect |
| `BleDeviceSession.kt` | `private var servicesDiscovered = CompletableDeferred<Unit>()` | Reset at the start of each new connection |
| `BleDeviceSession.kt` | `private var pendingWrite = CompletableDeferred<Boolean>()` | Replaced before each write operation |
| `BleConnectionViewModel.kt` | `private var scanJob: Job? = null` | Job is cancelled and a new one assigned on each scan |
| `SensorViewModel.kt` | `private var startSensorJob: Job? = null` | Same — coroutine job lifecycle |
| `SensorRepositoryImpl.kt` | `var latestTemp: Double? = null` | Accumulates partial BLE reads before emitting a full `SensorReading` |
| `MainActivity.kt` | `var showSheet by rememberSaveable { mutableStateOf(false) }` | Compose delegate — the `by` keyword requires a mutable variable |

**Rule of thumb:** use `var` only when the variable must be reassigned. If you can model change through a new object (using `copy()`) or through a Flow, prefer `val`.

---

## `data class`

A `data class` is a class whose primary purpose is holding data. The compiler auto-generates:
- `equals()` / `hashCode()` — based on constructor properties
- `toString()` — readable representation
- `copy()` — creates a modified clone with selected fields changed
- `componentN()` functions — enables destructuring

### Domain models

All domain model files use data classes because they are pure value holders with no behavior:

```kotlin
// domain/model/SensorReading.kt
data class SensorReading(
    val temperature: Double,
    val humidity: Double,
    val timeStamp: Long
)

// domain/model/DeviceInfo.kt
data class DeviceInfo(
    val manufactureName: String,
    val modelNumber: String,
    val firmwareVersion: String,
    val hardwareVersion: String
)

// domain/model/Book.kt
data class Book(
    val number: Int,
    val title: String,
    val originalTitle: String,
    val releaseDate: String,
    val description: String,
    val pages: Int,
    val coverUrl: String
)
```

### UI state

UI state objects are data classes so the ViewModel can use `copy()` to produce immutable updates:

```kotlin
// ui/sensor/SensorViewModel.kt
data class SensorUiState(
    val reading: SensorReading? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isNotifying: Boolean = false,
    val connectionState: BleState = BleState.Idle
)

// Updated immutably inside the ViewModel:
_uiState.update { it.copy(reading = reading, isLoading = false, error = null) }
```

### DTOs (Data Transfer Objects)

`BookDto` is a data class annotated for JSON serialization. It also carries a mapping method `toDomain()` to convert to the domain model — behavior is allowed in data classes:

```kotlin
// data/remote/dto/BookDto.kt
@Serializable
data class BookDto(
    @SerialName("number")        val number: Int,
    @SerialName("title")         val title: String,
    @SerialName("originalTitle") val originalTitle: String,
    @SerialName("releaseDate")   val releaseDate: String,
    @SerialName("description")   val description: String,
    @SerialName("pages")         val pages: Int,
    @SerialName("cover")         val coverUrl: String
) {
    fun toDomain() = Book(number, title, originalTitle, releaseDate, description, pages, coverUrl)
}
```

### Data classes inside sealed classes

When a sealed class variant needs to carry a payload, it is declared as a `data class`:

```kotlin
data class Connected(val deviceAddress: String) : BleState()
data class ScanFailed(val code: Int) : ScanError()
```

---

## `sealed class`

A `sealed class` defines a **closed hierarchy** — all subclasses must be in the same package. This lets the compiler enforce exhaustive `when` expressions (no `else` branch needed).

Use sealed classes to model a fixed set of states or outcomes.

### `BleState` — connection lifecycle

```kotlin
// domain/model/BleState.kt
sealed class BleState {
    object Idle : BleState()                              // no connection, no payload
    object Connecting : BleState()                        // in progress, no payload
    data class Connected(val deviceAddress: String) : BleState()  // success + address
    data class Error(val message: String) : BleState()            // failure + reason
}
```

Consumed in `SensorViewModel.kt`:
```kotlin
bleRepo.connectionState.collect { state ->
    _uiState.update { it.copy(connectionState = state) }
    if (state is BleState.Connected) startObserving()
}
```

### `ScanError` — BLE scan failure reasons

```kotlin
// ui/connection/BleConnectionViewModel.kt
sealed class ScanError {
    object BluetoothDisabled : ScanError()   // adapter is off
    object PermissionDenied : ScanError()    // runtime permission missing
    data class ScanFailed(val code: Int) : ScanError()  // hardware error code
}
```

Produced inside `startScan()`:
```kotlin
.catch { e ->
    val error = when (e) {
        is SecurityException -> ScanError.PermissionDenied
        else -> ScanError.ScanFailed(-1)
    }
    _scanState.update { it.copy(error = error, isScanning = false) }
}
```

### Sealed class subtype patterns

| Subtype | When to use |
|---|---|
| `object` | State carries no data (e.g., `Idle`, `Connecting`) |
| `data class` | State carries a payload (e.g., `Connected(address)`, `Error(message)`) |
| `class` | State needs mutable fields or custom constructor logic (rare) |
