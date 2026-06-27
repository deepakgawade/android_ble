# Kotlin Type Erasure

Source file: `app/src/main/java/com/example/harry_android/core/inlinefunctions/InlineFunctionExamples.kt`

---

## Dart has NO type erasure — that is the key difference

In Dart, generics are **reified** — the type is real and available at runtime:

```dart
// Dart — this works perfectly at runtime
void checkType<T>(List<dynamic> list) {
  if (list is List<String>) print('It is a List<String>');  // ✅ works
  list.whereType<T>().toList();                              // ✅ works
}
```

In Kotlin (JVM), generics are **erased** at runtime. The `<T>` disappears after compilation:

```kotlin
// Kotlin — T is gone at runtime
fun <T> check(list: List<Any>) {
    if (list is List<String>) { }  // ❌ compile warning — check is always true, T is erased
    list.filterIsInstance<T>()     // ❌ compile error — T is unknown at runtime
}
```

---

## Why does this happen?

Java added generics in 2004 — years after the JVM was designed. To stay backward-compatible with old bytecode, the JVM erases generic type arguments at compile time. Kotlin runs on the same JVM and inherits this limitation.

```
Source code:    List<ScannedDevice>    List<Book>    List<BleState>
                         ↓  compiler erases  ↓
JVM bytecode:        List               List           List
```

At runtime the JVM sees only a raw `List` — the type argument is gone entirely.

---

## What breaks because of type erasure

```kotlin
// You want a function that filters a mixed list by type — but this fails
fun <T> List<Any>.onlyOfType(): List<T> {
    return filterIsInstance<T>()  // ❌ ERROR: T is erased — compiler cannot filter by it
}
```

**Flutter analogy:** Imagine Dart suddenly forgetting what `<T>` means the moment the function runs. You passed `String` as T, but by the time the body executes, the runtime only knows `Object?`. That is exactly what the JVM does with every generic.

---

## The fix: `inline` + `reified`

`inline` copies the function body to the call site at compile time.  
`reified` tells the compiler: *also copy the actual type argument* into that copied body.

```kotlin
// ✅ T is preserved at runtime — baked into the bytecode at each call site
inline fun <reified T> List<Any>.filterDomainType(): List<T> =
    filterIsInstance<T>()
```

What the compiler generates under the hood:

```kotlin
// You write:
val devices = domainEvents.filterDomainType<ScannedDevice>()

// Compiler generates (conceptually):
val devices = domainEvents.filterIsInstance<ScannedDevice>()
//                                          ^^^^^^^^^^^^^ concrete type baked in — not a variable
```

`T` is no longer a runtime variable. It becomes the literal class `ScannedDevice` in the bytecode at the call site. No erasure — because there is nothing left to erase.

---

## In this project — `InlineFunctionExamples.kt:96`

```kotlin
inline fun <reified T> List<Any>.filterDomainType(): List<T> =
    filterIsInstance<T>()

// Usage:
val domainEvents: List<Any> = listOf(
    ScannedDevice(name = "HRM-1", address = "AA:BB:CC:DD:EE:FF", rssi = -70),
    Book(number = 1, title = "The Philosopher's Stone"),
    ScannedDevice(name = null,    address = "11:22:33:44:55:66", rssi = -85),
    "stray debug string",
    42,
)

val devices: List<ScannedDevice> = domainEvents.filterDomainType()  // reified → ScannedDevice
val books:   List<Book>          = domainEvents.filterDomainType()  // reified → Book
```

**Flutter/Dart equivalent** — works natively, no tricks needed:

```dart
extension FilterByType on List<dynamic> {
  List<T> filterDomainType<T>() => whereType<T>().toList();
}

final devices = domainEvents.filterDomainType<ScannedDevice>(); // just works in Dart
final books   = domainEvents.filterDomainType<Book>();
```

Dart gives you this for free. In Kotlin you opt-in with `inline` + `reified`.

---

## Why you cannot use `reified` everywhere

`reified` requires `inline`. `inline` copies the function body to every call site — which is fine for small functions but bloats bytecode if the function is large.

You also cannot use `reified` on:
- Class-level type parameters — only function-level
- Non-inline functions — the two are inseparable

```kotlin
// ❌ Cannot do this — class-level T is always erased
class Repository<reified T>

// ✅ Only on inline functions
inline fun <reified T> ...
```

---

## Comparison table

| | Dart | Kotlin (without reified) | Kotlin (with reified) |
|---|---|---|---|
| `is T` check at runtime | ✅ works | ❌ erased | ✅ works |
| `filterIsInstance<T>()` | ✅ works | ❌ erased | ✅ works |
| `T::class` (get class of T) | ✅ works | ❌ erased | ✅ works |
| Extra boilerplate needed | None | Pass `Class<T>` manually | `inline` + `reified` |

---

## One-line memory trick

> Dart reifies generics automatically. Kotlin erases them. `inline` + `reified` is you asking Kotlin to do what Dart does by default — but only at that one call site.

---

## Related concepts

- See `inline-functions.md` — `inline` is required for `reified` to work
- See `variance-in-out-star.md` — variance (`out T`) is a separate tool for subtype safety, not for runtime type access
