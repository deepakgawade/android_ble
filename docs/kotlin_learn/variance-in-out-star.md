# Kotlin Variance: `in`, `out`, `*`

Source files:
- `app/src/main/java/com/example/harry_android/data/ble/BleSessionManager.kt`
- `app/src/main/java/com/example/harry_android/core/delegates/CustomDelegates.kt`

---

## The Core Problem (why variance exists)

In Dart, lists are **unsoundly covariant** by default — the compiler lets you do this and it crashes at runtime:

```dart
// Dart — compiles but crashes at runtime
List<Animal> animals = <Dog>[];   // allowed by compiler
animals.add(Cat());               // runtime error!
```

Kotlin learned from this. Generics are **invariant by default** — the compiler blocks the unsafe case.
Variance annotations (`in`, `out`, `*`) let you opt-in only when it is actually safe.

---

## `out` — Producer / Covariant

**Rule:** The class only **produces** T (emits/exposes it), never consumes it.

`out T` means: `Container<Dog>` **is-a** `Container<Animal>` — reading is safe, writing is blocked.

### Flutter analogy — `Stream<T>` and `ValueListenable<T>`

```dart
// Dart — Stream only produces values, you can't push into it
Stream<Dog> dogStream = ...;
Stream<Animal> animalStream = dogStream; // ✅ safe — Stream only emits
```

Kotlin's `Flow` and `StateFlow` do the exact same thing with `out`:

```kotlin
// Kotlin stdlib — Flow only emits, never accepts values
interface Flow<out T>       // out = producer only
interface StateFlow<out T>  // out = producer only

val dogFlow: Flow<Dog> = ...
val animalFlow: Flow<Animal> = dogFlow  // ✅ safe
```

### In this project — `BleSessionManager.kt:47–48`

```kotlin
// WRITE end — invariant, kept private so nobody outside can corrupt state
private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())

// READ end — out T, safe to expose publicly
val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()
```

**Flutter equivalent of this exact pattern:**

```dart
final _scannedDevices = ValueNotifier<List<ScannedDevice>>([]);   // write, private
ValueListenable<List<ScannedDevice>> get scannedDevices => _scannedDevices; // read, public
```

| Kotlin | Flutter |
|---|---|
| `MutableStateFlow<T>` | `ValueNotifier<T>` (read + write) |
| `StateFlow<T>` | `ValueListenable<T>` (read only) |

---

## `in` — Consumer / Contravariant

**Rule:** The class only **consumes** T (accepts it as input), never produces it.

`in T` means: `Container<Animal>` **is-a** `Container<Dog>` — the reverse direction.

### Flutter analogy — `void Function(T)` callbacks

You already use contravariance in Dart with function types:

```dart
// A handler that processes ANY Animal can obviously process a Dog
void Function(Animal) animalHandler = (animal) => print(animal.name);
void Function(Dog) dogHandler = animalHandler; // ✅ safe

// The reverse is NOT safe:
void Function(Dog) dogOnlyHandler = (dog) => dog.fetch();
void Function(Animal) unsafe = dogOnlyHandler; // ❌ what if you pass a Cat?
```

Kotlin `in` applies this same rule to classes:

```kotlin
interface Comparator<in T> {
    fun compare(a: T, b: T): Int
}

val animalSorter: Comparator<Animal> = Comparator { a, b -> a.name.compareTo(b.name) }
val dogSorter: Comparator<Dog> = animalSorter  // ✅ a sorter for Any Animal can sort Dogs
```

---

## `*` — Star Projection (Unknown type)

**Rule:** "I don't know or don't care what T is."

`List<*>` means `List<out Any?>` — you can read `Any?` from it, you cannot write to it.

### Flutter analogy — `Object?` / `dynamic`

```dart
// Dart equivalent — you know it's a Stream of something, just not what
void printStream(Stream<Object?> stream) { ... }
```

In Kotlin, `*` is safer than `dynamic` — the compiler still blocks writes:

```kotlin
fun printFlow(flow: Flow<*>) {
    // can collect and get Any? values — reading is fine
    // cannot emit into it — compiler blocks it
}
```

### In this project — `CustomDelegates.kt:9`

```kotlin
override fun getValue(thisRef: Any?, property: KProperty<*>): T
//                                             ^^^^^^^^^^^
// "some KProperty of some type — the delegate doesn't need to know what type"
```

**Flutter equivalent:**

```dart
// Like a generic widget that accepts any ChangeNotifier, not a specific subtype
class GenericListener extends StatelessWidget {
  final ChangeNotifier notifier; // some notifier of some type
}
```

---

## Side-by-side comparison

| Kotlin | Flutter / Dart equivalent | Safe to read? | Safe to write? |
|---|---|---|---|
| `Flow<out T>` / `StateFlow<out T>` | `Stream<T>` (listen only) | Yes, as `T` | No |
| `Comparator<in T>` | `void Function(T)` callback | No (only `Any?`) | Yes, as `T` |
| `Flow<*>` | `Stream<Object?>` | Yes, as `Any?` | No |
| `MutableStateFlow<T>` | `ValueNotifier<T>` | Yes | Yes |

---

## One-line memory trick

| Keyword | Remember as | Subtype flows |
|---|---|---|
| `out` | **output only** — like `Stream`, you read from it | UP (Dog → Animal) |
| `in`  | **input only** — like a callback, you write into it | DOWN (Animal → Dog) |
| `*`   | **unknown** — like `Object?`, read `Any?`, can't write | neither |

---

## Why `MutableStateFlow` is invariant (no `in`/`out`)

`MutableStateFlow<T>` both reads AND writes — it cannot be `out` (that would block writes) and cannot be `in` (that would block reads). It is invariant by design, which is why it must always be kept `private` and exposed only as the read-only `StateFlow<out T>`.

```kotlin
// This pattern appears in every ViewModel and BleSessionManager in this project:
private val _state = MutableStateFlow<T>(initial)   // invariant — private
val state: StateFlow<T> = _state.asStateFlow()       // out T — public
```
