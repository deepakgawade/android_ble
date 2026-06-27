# Extension Functions & Extension Properties

An extension function/property lets you add behaviour to a class **you don't own** (or don't want
to pollute) without subclassing it.

Syntax: `fun ReceiverType.methodName(...)`

---

## Already in this project

### `launchOnIo` — `InlineFunctionExamples.kt:72`

```kotlin
inline fun CoroutineScope.launchOnIo(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    crossinline action: suspend () -> Unit
) { launch(dispatcher) { action() } }
```

Extends `CoroutineScope` (a class you don't own) with a convenience launcher.
Every ViewModel can call `viewModelScope.launchOnIo { }`.

### `filterDomainType` — `InlineFunctionExamples.kt:96`

```kotlin
inline fun <reified T> List<Any>.filterDomainType(): List<T> = filterIsInstance<T>()
```

Extension on `List<Any>` that filters to a specific domain type safely, using reified generics.

---

## Where they can be applied in this project

### 1. `ByteArray` — replace `GattDecoder` class methods

**Current approach** — `GattDecoder.kt`

```kotlin
class GattDecoder {
    fun decodeTemperature(bytes: ByteArray): Double { ... }
}
// Caller:
gattDecoder.decodeTemperature(bytes)
```

**With extension functions** — the `ByteArray` itself gains the behaviour, no object needed:

```kotlin
fun ByteArray.decodeTemperature(): Double {
    require(size >= 2) { "Temperature requires 2 bytes, got $size" }
    val raw = (this[0].toInt() and 0xff) or (this[1].toInt() shl 8)
    return raw.toShort() / 10.0
}

fun ByteArray.decodeHumidity(): Double {
    require(size >= 2) { "Humidity requires 2 bytes, got $size" }
    val raw = (this[0].toInt() and 0xff) or (this[1].toInt() shl 8)
    return raw / 10.0
}

fun ByteArray.decodeGattString(): String = String(this, Charsets.UTF_8)

// Caller:
bytes.decodeTemperature()   // reads naturally at the use site
```

**Extension on `Boolean`** — replaces `GattEncoder`:

```kotlin
fun Boolean.toControlBytes(): ByteArray {
    val value = if (this) 0x0001 else 0x0000
    return byteArrayOf((value and 0xff).toByte(), ((value shr 8) and 0xff).toByte())
}

// Caller:
true.toControlBytes()
```

---

### 2. `ScannedDevice` — extension properties

```kotlin
// Derived from existing fields — no stored state
val ScannedDevice.displayName: String
    get() = name?.takeIf { it.isNotBlank() } ?: "Unknown (${address.takeLast(5)})"

val ScannedDevice.isStrongSignal: Boolean
    get() = rssi >= -70
```

The data class stays untouched. `ScanBottomSheet.kt` calls `device.displayName` directly.

---

### 3. `BookDto.toDomain()` — extension instead of member

**Current** — `toDomain()` lives inside `BookDto`, coupling the DTO to the domain model.

```kotlin
// BookDto.kt
data class BookDto(...) {
    fun toDomain() = Book(...)   // DTO knows about domain — tight coupling
}
```

**With extension function** — mapping lives at the repository boundary where it's used:

```kotlin
// Remove toDomain() from BookDto class, add in HarryRemoteRepository.kt:
fun BookDto.toDomain() = Book(
    number = number,
    title = title,
    originalTitle = originalTitle,
    releaseDate = releaseDate,
    description = description,
    pages = pages,
    coverUrl = coverUrl
)
```

The DTO stays a pure data container. The mapping is scoped to the layer that needs it.

---

### 4. `SensorReading` — extension properties for display

```kotlin
val SensorReading.temperatureLabel: String
    get() = "%.1f °C".format(temperature)

val SensorReading.humidityLabel: String
    get() = "%.1f %%".format(humidity)
```

`SensorScreen.kt` uses `reading.temperatureLabel` directly instead of formatting inline in the UI.

---

## Key rules

| Rule | Detail |
|---|---|
| No backing field | Extension properties can't store state — only `get()`/`set()` backed by existing members |
| No override | They don't override member functions; if a class has `fun foo()`, the extension `fun T.foo()` is shadowed by the member |
| Resolved statically | Called on the **declared type**, not the runtime type — unlike virtual dispatch |
| Scope them | Keep extensions near where they're used; only promote to a shared file if reused across 3+ files |

---

## When to use each

| Use a **member function** when | Use an **extension function** when |
|---|---|
| The function needs access to private state | The class is from a library (`ByteArray`, `CoroutineScope`) |
| It's core behaviour of the class | It's presentation/mapping logic that belongs at a layer boundary |
| It's part of the public contract | It's a convenience used in one layer only |
