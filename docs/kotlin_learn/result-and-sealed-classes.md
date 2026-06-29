# Result and Sealed Classes

These are two related but different concepts. This project uses both.

---

## Part 1: Sealed Class — the concept

**Flutter analogy:** A `sealed class` / abstract class with a fixed set of subtypes (like you'd model with `freezed` in Flutter)

A `sealed class` is a class where **all subclasses must be declared in the same file**. The compiler knows every possible subtype, so `when` expressions are exhaustive — the compiler forces you to handle every case.

```kotlin
sealed class Result<T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error<T>(val message: String) : Result<T>()
    object Loading : Result<Nothing>()
}
```

The key power: **`when` on a sealed class is exhaustive** — no `else` needed, and the compiler warns you if you miss a case.

```kotlin
when (result) {
    is Result.Success -> show(result.data)    // smart-cast: result.data available
    is Result.Error   -> showError(result.message)
    is Result.Loading -> showSpinner()
}
```

**Flutter `freezed` equivalent:**
```dart
@freezed
class Result<T> with _$Result<T> {
  factory Result.success(T data) = Success;
  factory Result.error(String message) = Error;
  factory Result.loading() = Loading;
}
```

---

## Part 2: Sealed classes in this project

### `BleState` — `domain/model/BleState.kt:3`

```kotlin
sealed class BleState {
    object Idle : BleState()
    object Connecting : BleState()
    data class Connected(val deviceAddress: String) : BleState()
    data class Error(val message: String) : BleState()
}
```

- `object` = singleton state (no data needed) — like `Idle`, `Connecting`
- `data class` = state with payload — `Connected` carries the address, `Error` carries the message

Used in `SensorViewModel.kt:62-64`:
```kotlin
bleRepo.connectionState.collect { state ->
    _uiState.update { it.copy(connectionState = state) }
    if (state is BleState.Connected) startObserving()  // smart-cast
}
```
`is BleState.Connected` is a **smart cast** — inside the `if` block, the compiler knows `state` is `Connected` and gives you access to `state.deviceAddress` without an explicit cast.

### `ScanError` — `BleConnectionViewModel.kt:32`

```kotlin
sealed class ScanError {
    object BluetoothDisabled : ScanError()
    object PermissionDenied : ScanError()
    data class ScanFailed(val code: Int) : ScanError()
}
```

The UI state holds `val error: ScanError? = null`. When the UI renders it, `when(error)` covers every possible scan failure reason precisely.

---

## Part 3: Kotlin's built-in `Result<T>`

Kotlin has a **built-in** `Result<T>` type in the standard library — a sealed-like wrapper with two states: success (holds a value) or failure (holds a `Throwable`).

| Property / Function | What it does |
|---|---|
| `result.isSuccess` | `true` if no exception |
| `result.isFailure` | `true` if exception thrown |
| `result.getOrNull()` | value if success, `null` if failure |
| `result.exceptionOrNull()` | exception if failure, `null` if success |
| `result.getOrElse { default }` | value or fallback |
| `result.onSuccess { }` | run block if success |
| `result.onFailure { }` | run block if failure |

### `runCatching` — the clean way to wrap exceptions

**Flutter analogy:** wrapping a `try/catch` block into a `Future` that returns either a value or an error

```kotlin
// Instead of:
try {
    val book = apiService.getBook()
    Result.success(book)
} catch (e: Exception) {
    Result.failure(e)
}

// You write:
runCatching { apiService.getBook() }
```

**In this project — `HarryRemoteRepository.kt:11`**
```kotlin
override suspend fun getBook(): Result<Book> = runCatching {
    apiService.getBook().toDomain()
}
```
If `apiService.getBook()` throws any exception (network error, parse error), `runCatching` catches it and wraps it in `Result.failure(exception)`. If it succeeds, returns `Result.success(book)`. Zero try/catch boilerplate.

### Consumed in `BookViewModel.kt:44-47`

```kotlin
val data = harryRemoteRepository.getBook()  // Result<Book>

when {
    data.isSuccess -> _bookViewState.update {
        it.copy(loading = false, book = data.getOrNull(), error = null)
    }
    data.isFailure -> _bookViewState.update {
        it.copy(
            loading = false,
            error = data.exceptionOrNull()?.message ?: "Failed to get book"
        )
    }
}
```

---

## Sealed class vs `Result<T>` — when to use which

| | Custom Sealed Class | Kotlin `Result<T>` |
|---|---|---|
| Error types | Multiple named types (`BluetoothDisabled`, `PermissionDenied`) | Just a `Throwable` |
| Extra states | Yes (`Loading`, `Idle`, `Connecting`) | No — only success/failure |
| Use case | Domain state machines, typed errors | Wrapping exception-throwing APIs |
| In this project | `BleState`, `ScanError` | `getBook()` API call |

**Rule of thumb:** use a custom sealed class when you need **named states or typed errors**. Use `Result<T>` + `runCatching` when you just need to **safely call something that might throw**.
