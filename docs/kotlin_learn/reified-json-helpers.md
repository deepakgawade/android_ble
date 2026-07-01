# Reified JSON Helpers

## The Problem: Type Erasure

In Kotlin (and Java), **generic types are erased at runtime**. This is a JVM limitation called *type erasure*.

```kotlin
// T is gone at runtime — compiler has no idea what T is here
fun <T> parseJson(json: String): T {
    return Json.decodeFromString<T>(json)  // ❌ COMPILE ERROR
}
```

Think of it like Flutter's `dynamic` — you lose the type info.

## The Fix: `inline` + `reified`

`reified` tells the compiler: *"stamp a real copy of this function for each type, so T survives at runtime."*

```kotlin
inline fun <reified T> parseJson(json: String): T {
    return Json.decodeFromString<T>(json)  // ✅ T is real at runtime
}

// Call site:
val book: Book = parseJson(jsonString)       // T = Book, inferred automatically
val device: ScannedDevice = parseJson(json)  // T = ScannedDevice
```

---

## How it's Already Used in This Project

### 1. kotlinx.serialization uses `reified` internally

`NetworkModule.kt` sets up the Retrofit converter, and Retrofit calls `Json.decodeFromString<BookDto>()` behind the scenes — that function in kotlinx.serialization is:

```kotlin
// Inside the library (you don't write this)
inline fun <reified T> Json.decodeFromString(string: String): T
```

So every time Retrofit deserializes `BookDto`, reified is at work.

### 2. `filterDomainType<T>()` in `InlineFunctionExamples.kt`

Already written as a reified helper:

```kotlin
inline fun <reified T> List<Any>.filterDomainType(): List<T> =
    filterIsInstance<T>()
```

`filterIsInstance<T>()` needs the real type at runtime — only possible because of `reified`.

---

## How Reified JSON Helpers Can Be Used in This Project

### A. Safe JSON parse wrapper (useful for BLE data or mock testing)

```kotlin
inline fun <reified T> String.fromJson(): T =
    Json.decodeFromString(this)

inline fun <reified T> T.toJson(): String =
    Json.encodeToString(this)

// Usage:
val book: Book = someJsonString.fromJson()
val json: String = book.toJson()
```

### B. Generic API response wrapper (avoids repeating error handling)

`HarryApiService` returns `BookDto` directly. You could wrap all calls generically:

```kotlin
inline fun <reified T> safeNetworkCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(e)
    }

// In HarryRemoteRepository:
val result: Result<BookDto> = safeNetworkCall { apiService.getBook() }
```

### C. BLE characteristic JSON decoder (if your BLE device sends JSON payloads)

```kotlin
inline fun <reified T> ByteArray.decodeAsJson(): T? =
    try {
        Json.decodeFromString<T>(this.toString(Charsets.UTF_8))
    } catch (e: Exception) { null }

// Usage in GattDecoder:
val reading: SensorReading? = rawBytes.decodeAsJson<SensorReading>()
```

---

## Quick Mental Model (Flutter Analogy)

| Kotlin | Flutter/Dart |
|--------|-------------|
| Type erasure — `T` lost at runtime | `dynamic` — no type info |
| `reified T` | `T.runtimeType` or generics that Dart preserves |
| `inline fun <reified T>` | Code-generation: compiler stamps a version per type |

The key insight: **`reified` only works with `inline` functions** because `inline` copies the function body to the call site, where the compiler knows the concrete type.

---

## Rules to Remember

| Rule | Reason |
|------|--------|
| `reified` requires `inline` | The function must be copy-pasted at call site for the type to be known |
| Cannot use `reified` on class-level generics | Only function-level generics can be reified |
| `reified` enables `T::class`, `is T`, `as T` at runtime | Without it, these would be compile errors |

## Related Topics

- See `inline-functions.md` for `inline`, `noinline`, `crossinline`
- See `type-erasure.md` for why JVM erases generics
- See `variance-in-out-star.md` for how generics behave across type hierarchies
