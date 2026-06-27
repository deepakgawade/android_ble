# Kotlin Inline Functions

Source file: `shared/src/commonMain/kotlin/org/example/kmpgame/InlineFunctions.kt`

---

## What is a Hot Path?

A **hot path** is code that runs very frequently — thousands or millions of times per second.
Examples: a game loop tick, a render callback, a retry loop, a tight data-processing loop.

The key concern is **allocation cost**. Every lambda passed to a non-inline function is compiled
into a heap-allocated anonymous class. At high call frequency this feeds the garbage collector,
causing GC pauses.

`inline` eliminates that cost by copy-pasting the lambda body directly at the call site —
no object is created.

---

## 1. `inline`

The lambda body is copy-pasted at every call site — no anonymous class, no heap allocation.

```kotlin
inline fun <T> retryOnFailure(times: Int, block: () -> T): T? {
    repeat(times) {
        try { return block() } catch (_: Exception) { }
    }
    return null
}
```

**What changes when you remove `inline`:**

| | With `inline` | Without `inline` |
|---|---|---|
| `block` lambda | copy-pasted at call site | heap-allocated anonymous class |
| GC pressure | zero | one allocation per call |
| `reified` type params | allowed | compile error |
| Non-local `return` from `block` | allowed | banned |

**Usage:**
```kotlin
val result = retryOnFailure(3) { httpClient.get(url).body<List<RocketLaunch>>() }
```

---

## 2. `noinline`

When one lambda needs to be stored, returned, or passed to another function, mark it `noinline`.
The other parameters are still inlined.

```kotlin
inline fun <T> fetchOrFallback(
    noinline onError: (Exception) -> T,  // kept as a real object
    block: () -> T                        // still inlined
): T = try {
    block()
} catch (e: Exception) {
    onError(e)
}
```

**Usage:**
```kotlin
val errorLogger: (Exception) -> String = { e -> "Logged: ${e.message}" }
val phrase = fetchOrFallback(onError = errorLogger) {
    error("network unavailable")
}
```

---

## 3. `crossinline`

When a lambda is passed into *another* lambda (a different execution context), use `crossinline`.
The body is still inlined, but bare `return` statements are banned because the compiler
cannot guarantee when or where the inner lambda will run.

```kotlin
inline fun scheduleTask(crossinline action: () -> Unit) {
    val task = { action() }  // wrapping only possible with crossinline
    task()
}
```

**Usage:**
```kotlin
scheduleTask {
    println("Launching rocket sequence…")
    // return@scheduleTask is fine; bare `return` is a compile error
}
```

---

## 4. `reified`

Normally generic type parameters are erased at runtime. `inline` + `reified` preserves `T`
as a real type, enabling `is`-checks, class literals, and `filterIsInstance`.

```kotlin
inline fun <reified T> List<Any>.filterByType(): List<T> = filterIsInstance<T>()
```

**Usage:**
```kotlin
val mixed: List<Any> = listOf(RocketLaunch(1,"Crew-1","2021-01-01T00:00Z",true), "stray string", 42)
val launches: List<RocketLaunch> = mixed.filterByType()
```

---

## Test output explained

```
retryOnFailure → Launch data loaded      // random succeeded on one of the 3 attempts
fetchOrFallback → Logged: network unavailable  // block threw, onError lambda was called
scheduleTask → task executed inside wrapped lambda  // crossinline action ran inside inner lambda
filterByType<RocketLaunch> → [Crew-1, Crew-2]  // reified filtered out String and Int at runtime
```

**Note on `retryOnFailure → null`:** Each attempt has a 50% chance of throwing. If all 3 fail
(12.5% probability) the function returns `null`. This is random behaviour — not caused by
removing `inline`. Removing `inline` only adds a heap allocation per call; it does not change
the retry logic.

---

## Trade-offs

- `inline` increases bytecode size (body is duplicated at every call site).
- Only use it for **small, frequently-called wrappers** — not for 100-line functions.
- The size bloat outweighs allocation savings for large functions.
