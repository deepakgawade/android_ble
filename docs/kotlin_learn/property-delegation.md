# Kotlin Property Delegation

Source files:
- `app/src/main/java/com/example/harry_android/domain/model/ScannedDevice.kt`
- `app/src/main/java/com/example/harry_android/data/ble/BleSessionManager.kt`
- `app/src/main/java/com/example/harry_android/data/ble/BleDeviceSession.kt`
- `app/src/main/java/com/example/harry_android/core/delegates/CustomDelegates.kt`

---

## What is a Delegate?

Every Kotlin property has a hidden `get()` and `set()` behind it:

```kotlin
var isScanning: Boolean = false
// Kotlin secretly generates:
//   get() { return field }
//   set(value) { field = value }
```

Normally **you** write that logic. A **delegate** says:

> "Don't ask me how to get/set this — ask **that object** instead."

```kotlin
var isScanning: Boolean by Delegates.observable(false) { _, old, new ->
    println("$old → $new")
}
//                ↑
//         THIS is the delegate object
//         It owns the get() and set() logic
```

The `by` keyword is the handover: "handle this property's get/set **by** using this delegate."

---

## Flutter Analogy

In Dart, when you want custom get/set behavior, you write it yourself every time:

```dart
// You ARE the delegate — writing get/set by hand, every time
bool _isScanning = false;

bool get isScanning => _isScanning;

set isScanning(bool val) {
  print("$_isScanning → $val");
  _isScanning = val;
}
```

In Kotlin, you extract that get/set logic into a **reusable object** (the delegate), then snap it
onto any property with `by`:

```kotlin
// Written once, reused on every property
var isScanning: Boolean  by Delegates.observable(false) { _, old, new -> println("$old → $new") }
var isConnected: Boolean by Delegates.observable(false) { _, old, new -> println("$old → $new") }
var isLoading: Boolean   by Delegates.observable(false) { _, old, new -> println("$old → $new") }
```

No repeated boilerplate.

---

## How it works under the hood

```
isScanning = true
      ↓
Kotlin sees "by" → calls delegate's setValue()
      ↓
delegate stores the new value, runs your lambda
```

```
val x = isScanning
      ↓
Kotlin sees "by" → calls delegate's getValue()
      ↓
delegate returns stored value
```

You never see this happening — it is invisible. You just read and write the property normally.

---

## 1. `by lazy` — deferred one-time initialization

The block runs **once**, on the first access. All subsequent reads return the cached result.
Thread-safe by default.

### Syntax

```kotlin
val foo: Foo by lazy {
    // expensive work here — runs only on first access
    Foo()
}
```

### Flutter equivalent

```dart
// Dart
late final Foo foo = Foo();   // initialized on first use, cached forever
```

### Rule: `val` only

`lazy` is read-only — you can only use it with `val`, not `var`. It initializes once and never
changes.

### Real project example — `BleSessionManager.kt`

```kotlin
// Current code: runs getSystemService() eagerly at constructor time
private val bluetoothAdapter: BluetoothAdapter? =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

// With lazy: deferred until startScan() or isBluetoothEnabled() is first called
private val bluetoothAdapter: BluetoothAdapter? by lazy {
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
}
```

### Real project example — `BleDeviceSession.kt`

```kotlin
// Current code: allocates the callback object at class construction, even before connect()
private val gattCallback = object : BluetoothGattCallback() { ... }

// With lazy: allocated only when connect() first needs it
private val gattCallback by lazy {
    object : BluetoothGattCallback() { ... }
}
```

### Why use `lazy`?

- Android constructors run on the **main thread** during DI injection — any blocking system call
  there can cause jank.
- `lazy` pushes the cost to first actual use, which in this project is always inside a background
  coroutine.
- If the feature is never used in a session, the object is never created at all.

---

## 2. `by Delegates.observable` — react to every change

Runs a lambda **after** every assignment. Old value and new value are both provided.

### Syntax

```kotlin
var foo: T by Delegates.observable(initialValue) { property, old, new ->
    // runs after every assignment to foo
}
```

### Flutter equivalent

```dart
// Dart — custom setter fires on every assignment
bool _isScanning = false;
set isScanning(bool val) {
  print("$_isScanning → $val");   // side effect
  _isScanning = val;
}
```

### Rule: `var` only

`observable` needs to intercept writes, so the property must be `var`.

### Usage — how to change the value

Just assign to it normally. The lambda fires automatically:

```kotlin
var isScanning: Boolean by Delegates.observable(false) { _, old, new ->
    println("isScanning: $old -> $new")
}

// On button click:
fun onScanButtonClicked() {
    isScanning = true    // prints: isScanning: false -> true
}

fun onStopClicked() {
    isScanning = false   // prints: isScanning: true -> false
}
```

You **never** call the lambda manually. Assignment triggers it.

### Real project example

```kotlin
// ScannedDevice.kt
var isScanning: Boolean by Delegates.observable(false) { _, old, newValue ->
    println("isScanning: $old -> $newValue")
}
```

### Unit test — why `println` not `Log.d`

Unit tests in `src/test/` run on a plain JVM — `android.util.Log` does not exist there.

| Environment | `Log.d` available | `println` available |
|---|---|---|
| `src/test/` (unit test — plain JVM) | No | Yes |
| `src/androidTest/` (device test) | Yes | Yes |
| Real app on device | Yes | Yes |

Use `println` inside delegates that are tested with unit tests. Use `Log.d` inside ViewModels and
repositories that run in the Android environment.

### Use cases

- Logging every state transition for debugging
- Syncing one property to another when it changes
- Triggering analytics events on property change

---

## 3. `by Delegates.vetoable` — validate before accepting a change

Runs a lambda **before** every assignment. Return `true` to accept the new value, `false` to
reject it (the old value stays unchanged).

### Syntax

```kotlin
var foo: T by Delegates.vetoable(initialValue) { property, old, new ->
    // return true = accept, false = reject
    new > 0
}
```

### Flutter equivalent

```dart
// Dart — guard in the setter
int _rssiThreshold = -60;
set rssiThreshold(int val) {
  if (val < -100 || val > -30) return;   // veto — old value stays
  _rssiThreshold = val;
}
```

### Rule: `var` only

`vetoable` intercepts writes, so the property must be `var`.

### Usage — how to change the value (and what happens when rejected)

```kotlin
var rssiThreshold: Int by Delegates.vetoable(-60) { _, old, new ->
    val accepted = new in -100..-30
    if (!accepted) println("REJECTED: $new is out of range, keeping $old")
    else println("ACCEPTED: $old -> $new")
    accepted
}

rssiThreshold = -75   // ACCEPTED: -60 -> -75   (valid BLE RSSI)
rssiThreshold = -50   // ACCEPTED: -75 -> -50   (valid BLE RSSI)
rssiThreshold = -200  // REJECTED: -200 is out of range, keeping -50
rssiThreshold = 10    // REJECTED: 10 is out of range, keeping -50

println(rssiThreshold) // -50  (unchanged after rejections)
```

### Real project example — `BleSessionManager.kt`

```kotlin
// Current: plain constant, no validation
private val minRssiThreshold = -60

// With vetoable: if this ever becomes runtime-configurable, bad values are rejected at the
// property level rather than scattered across every caller
var minRssiThreshold: Int by Delegates.vetoable(-60) { _, old, new ->
    val accepted = new in -100..-30
    if (!accepted) println("Invalid RSSI threshold: $new, keeping $old")
    accepted
}
```

### Use cases

- Enforcing valid ranges (RSSI, timeout values, retry counts)
- State machine transitions — only allow legal state changes
- Preventing a second connection while one is already in progress

---

## Quick comparison

| Delegate | Timing | `val` or `var` | Flutter equivalent |
|---|---|---|---|
| `by lazy` | On first **read** | `val` only | `late final` |
| `by observable` | **After** every write | `var` only | Custom setter + side effect |
| `by vetoable` | **Before** every write, can reject | `var` only | Custom setter with `if` guard |

---

## Building a Custom Delegate

### The two interfaces

Every delegate must implement one of these:

| Interface | Used for | Methods required |
|---|---|---|
| `ReadOnlyProperty<R, T>` | `val` (read only) | `getValue()` only |
| `ReadWriteProperty<R, T>` | `var` (read + write) | `getValue()` + `setValue()` |

`R` = the type of the object that owns the property (`Any?` means "any class or top-level").
`T` = the type of the property value.

---

### Flutter analogy

A custom delegate is like extending an abstract class in Dart:

```dart
// Dart — you implement an interface, framework calls your methods
abstract class ValueDelegate<T> {
  T getValue();
  void setValue(T value);
}
```

```kotlin
// Kotlin — same idea
class MyDelegate<T> : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T { ... }
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { ... }
}
```

You implement the interface. Kotlin calls `getValue` on every read and `setValue` on every write —
you never call these methods yourself.

---

### What `thisRef` and `property` give you

```kotlin
override fun getValue(thisRef: Any?, property: KProperty<*>): T {
    //         ↑                    ↑
    //  the object that owns        metadata about the property
    //  the property                (name, type, annotations)
    println(property.name)  // prints the property name as a String
}
```

This is what lets the delegate print the property name automatically:
`println("${property.name}: $old → $new")` — no hardcoding needed.

---

### Step-by-step: `LoggingDelegate<T>`

Logs every **read AND write**. Built-in `observable()` only logs writes.

**Step 1 — create a class, implement `ReadWriteProperty`:**

```kotlin
class LoggingDelegate<T>(initialValue: T) : ReadWriteProperty<Any?, T> {

    private var value: T = initialValue  // store the actual value here

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        println("GET  ${property.name} = $value")
        return value                      // must return the stored value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        println("SET  ${property.name}: ${this.value} → $value")
        this.value = value                // must store the new value
    }
}
```

**Step 2 — use it:**

```kotlin
var connectionCount: Int by LoggingDelegate(0)

connectionCount = 1
// prints: SET  connectionCount: 0 → 1

val x = connectionCount
// prints: GET  connectionCount = 1
```

---

### Step-by-step: `RangedDelegate`

Clamps an `Int` to a valid range instead of rejecting it (unlike `vetoable`).
Useful for RSSI thresholds — `-200` becomes `-100`, not rejected silently.

```kotlin
class RangedDelegate(
    initialValue: Int,
    private val range: IntRange
) : ReadWriteProperty<Any?, Int> {

    private var value: Int = initialValue.coerceIn(range)  // clamp even the initial value

    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        val clamped = value.coerceIn(range)
        if (clamped != value) {
            println("${property.name}: $value out of $range, clamped to $clamped")
        }
        this.value = clamped
    }
}
```

**Usage:**

```kotlin
var rssiThreshold: Int by RangedDelegate(-60, -100..-30)

rssiThreshold = -75    // valid, stored as -75
rssiThreshold = -200   // prints: rssiThreshold: -200 out of -100..-30, clamped to -100
rssiThreshold = 10     // prints: rssiThreshold: 10 out of -100..-30, clamped to -30

println(rssiThreshold) // -30
```

---

### Step-by-step: `NonBlankDelegate`

Rejects blank/empty strings and keeps a fallback.
Useful for device display names that must never be empty.

```kotlin
class NonBlankDelegate(private val fallback: String) : ReadWriteProperty<Any?, String> {

    private var value: String = fallback

    override fun getValue(thisRef: Any?, property: KProperty<*>): String = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        this.value = if (value.isBlank()) {
            println("${property.name}: blank rejected, keeping \"$fallback\"")
            fallback
        } else {
            value
        }
    }
}
```

**Usage:**

```kotlin
var deviceName: String by NonBlankDelegate("Unknown Device")

deviceName = "Harry Sensor"  // stored as "Harry Sensor"
deviceName = ""              // prints: deviceName: blank rejected, keeping "Unknown Device"
deviceName = "   "           // prints: deviceName: blank rejected, keeping "Unknown Device"

println(deviceName)          // "Harry Sensor" — last valid value
```

---

### Helper functions — cleaner call sites

Instead of writing `by RangedDelegate(-60, -100..-30)` everywhere, add a small factory function:

```kotlin
fun <T> logging(initialValue: T) = LoggingDelegate(initialValue)
fun ranged(initialValue: Int, range: IntRange) = RangedDelegate(initialValue, range)
fun nonBlank(fallback: String) = NonBlankDelegate(fallback)

// Now usage reads naturally:
var rssiThreshold: Int    by ranged(-60, -100..-30)
var deviceName: String    by nonBlank("Unknown Device")
var connectionCount: Int  by logging(0)
```

---

### vetoable vs RangedDelegate — when to use which

| | `vetoable` | `RangedDelegate` |
|---|---|---|
| Bad value assigned | Old value silently kept | Value clamped to nearest valid |
| User sees | No feedback unless you add it | Printed warning |
| Best for | State machines, boolean guards | Numeric ranges (RSSI, timeouts) |

---

### Full custom delegate template

```kotlin
class MyDelegate<T>(initialValue: T) : ReadWriteProperty<Any?, T> {

    private var value: T = initialValue

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        // called on every READ
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        // called on every WRITE
        // validate, transform, log, sync — then store
        this.value = value
    }
}
```

The three steps every custom delegate follows:
1. **Store** the value in a private field (`private var value: T`)
2. **Return** it in `getValue`
3. **Validate / transform / log** in `setValue`, then store the result
