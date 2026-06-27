package com.example.harry_android.core.inlinefunctions

import com.example.harry_android.domain.model.Book
import com.example.harry_android.domain.model.ScannedDevice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// =============================================================================
// 1. inline — BLE GATT retry helper
// =============================================================================
//
// BLE GATT reads are a hot-path: the caller may invoke this on every
// characteristic notification. Without `inline`, the `block` lambda is
// heap-allocated on every call → GC pressure. `inline` copy-pastes the body
// at the call site → zero allocation.
//
// Also enables non-local return: if `block()` succeeds, `return` exits the
// enclosing function directly without going through a callback wrapper.

inline fun <T> retryGattRead(times: Int, block: () -> T): T? {
    repeat(times) {
        try {
            return block()
        } catch (_: Exception) {
            // transient BLE error — retry
        }
    }
    return null
}

// Usage:
//   val temperature = retryGattRead(3) { gattDecoder.decodeTemperature(bytes) }
//   val humidity    = retryGattRead(3) { gattDecoder.decodeHumidity(bytes) }


// =============================================================================
// 2. noinline — Harry Potter API call with a storable error handler
// =============================================================================
//
// `onError` must be stored and potentially passed to a logging service, so it
// cannot be inlined (inlined lambdas cannot be stored as objects). Marking it
// `noinline` keeps it as a real heap object while still inlining `block`.

inline fun <T> safeApiCall(
    noinline onError: (Exception) -> T,   // stored → noinline
    block: () -> T                         // inlined at call site
): T = try {
    block()
} catch (e: Exception) {
    onError(e)
}

// Usage:
//   val book: Book? = safeApiCall(
//       onError = { e -> null.also { Log.e("Harry", e.message ?: "unknown") } }
//   ) {
//       apiService.getBook().toDomain()
//   }


// =============================================================================
// 3. crossinline — ViewModel IO launcher
// =============================================================================
//
// `action` is passed into a new coroutine lambda (a different execution
// context). `inline` still eliminates the allocation for the outer lambda, but
// `crossinline` bans bare `return` from `action` because the compiler cannot
// know when the coroutine will actually execute the body.

inline fun CoroutineScope.launchOnIo(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    crossinline action: suspend () -> Unit
) {
    launch(dispatcher) {
        action()           // action() wrapped inside a new coroutine lambda
    }
}

// Usage (inside a ViewModel):
//   viewModelScope.launchOnIo(dispatcher.io) {
//       val result = harryRemoteRepository.getBook()
//       // bare `return` here is a compile error — use return@launchOnIo
//   }


// =============================================================================
// 4. reified — type-safe filtering of mixed domain events
// =============================================================================
//
// Generic type parameters are erased at runtime. `reified` + `inline`
// preserves T as a real type, so `filterIsInstance<T>()` works without
// reflection or extra Class<T> parameters.

inline fun <reified T> List<Any>.filterDomainType(): List<T> =
    filterIsInstance<T>()

// Usage:
//   val domainEvents: List<Any> = listOf(
//       ScannedDevice(name = "HRM-1", address = "AA:BB:CC:DD:EE:FF", rssi = -70),
//       Book(number = 1, title = "The Philosopher's Stone", ...),
//       ScannedDevice(name = null, address = "11:22:33:44:55:66", rssi = -85),
//       "stray debug string",
//   )
//   val devices: List<ScannedDevice> = domainEvents.filterDomainType()
//   val books:   List<Book>          = domainEvents.filterDomainType()


// =============================================================================
// Demo — ties all four helpers together (call from a test or scratch main)
// =============================================================================

fun runInlineFunctionDemo() {
    // --- 1. inline: retryGattRead ---
    val fakeBytes = byteArrayOf(0xEB.toByte(), 0x00)
    val temperature = retryGattRead(times = 3) {
        val raw = (fakeBytes[0].toInt() and 0xff) or (fakeBytes[1].toInt() shl 8)
        raw.toShort() / 10.0
    }
    println("retryGattRead → temperature: $temperature °C")  // 23.5

    // --- 2. noinline: safeApiCall ---
    val errorLogger: (Exception) -> String = { e -> "Logged: ${e.message}" }
    val apiResult: String = safeApiCall(onError = errorLogger) {
        error("network unavailable")
    }
    println("safeApiCall → $apiResult")  // Logged: network unavailable

    // --- 3. crossinline: launchOnIo ---
    //  (CoroutineScope needed at runtime — shown as comment only here)
    //  viewModelScope.launchOnIo { harryRemoteRepository.getBook() }
    println("launchOnIo → wraps action in a coroutine without heap-allocating the outer lambda")

    // --- 4. reified: filterDomainType ---
    val domainEvents: List<Any> = listOf(
        ScannedDevice(name = "HRM-1", address = "AA:BB:CC:DD:EE:FF", rssi = -70),
        ScannedDevice(name = null,    address = "11:22:33:44:55:66", rssi = -85),
        "stray debug string",
        42,
    )
    val devices: List<ScannedDevice> = domainEvents.filterDomainType()
    println("filterDomainType<ScannedDevice> → ${devices.map { it.address }}")
    // [AA:BB:CC:DD:EE:FF, 11:22:33:44:55:66]
}
