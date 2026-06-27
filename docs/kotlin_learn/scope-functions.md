# Kotlin Scope Functions: `let`, `run`, `with`, `apply`, `also`

Source files:
- `app/src/main/java/com/example/harry_android/data/ble/BleSessionManager.kt`
- `app/src/main/java/com/example/harry_android/domain/model/ScannedDevice.kt`

---

## The only two things that differ

All five scope functions are the same idea — run a block of code on an object. Only two things vary:

| Function | Refer to object as | Returns |
|---|---|---|
| `let`   | `it`   | lambda result   |
| `run`   | `this` | lambda result   |
| `with`  | `this` | lambda result   |
| `apply` | `this` | the object itself |
| `also`  | `it`   | the object itself |

---

## `apply` — configure an object, get it back

**Dart equivalent: cascade operator `..`**

```dart
// Dart cascade — configure, get the object back
final paint = Paint()
  ..color = Colors.blue
  ..strokeWidth = 2.0;
```

```kotlin
// Kotlin apply — exact same idea
val paint = Paint().apply {
    color = Color.BLUE    // this = paint, no need to write paint.color
    strokeWidth = 2.0f
}
// apply returns the Paint object
```

**In this project — building `ScanSettings` (`BleSessionManager.kt:67`):**

```kotlin
// Original style — builder chain
val settings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
    .build()

// With apply — same result
val settings = ScanSettings.Builder().apply {
    setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
    setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
}.build()
```

**When to use:** Setting up or configuring an object. You want the object itself back at the end.

---

## `let` — null-safe transform, get the result back

**Dart equivalent: null check with a local variable**

```dart
// Dart — null check then use
final name = device.name;
if (name != null) {
  print(name.toUpperCase()); // smart-cast to non-null
}

// Or with a local helper
final upper = device.name?.toUpperCase(); // only if non-null
```

```kotlin
// Kotlin let — same thing, one line
device.name?.let { name ->
    print(name.toUpperCase()) // it (named here as name) is guaranteed non-null
}
```

**In this project — `BleSessionManager.kt:76`:**

```kotlin
result?.rssi?.let { if (it < minRssiThreshold) return }
//            ^^^
// only runs if rssi is non-null
// it = the rssi Int value inside the block
```

**Transform example — get a result from `let`:**

```kotlin
val displayName: String = device.name?.let { it.uppercase() } ?: "Unknown"
// if name is non-null → uppercased string
// if name is null     → skips let, falls through to ?: "Unknown"
```

**When to use:**
- Null checks (the `?.let` pattern)
- Transforming a nullable value into something else
- Scoping a variable to a small block so it doesn't leak into the outer scope

---

## `also` — side effect, get the original object back

**Dart equivalent: there is no direct equivalent — you would break the chain**

```dart
// Dart — logging forces you to break the chain
final device = ScannedDevice(name: 'HRM', address: 'AA:BB:CC');
debugPrint(device.toString()); // side effect
// now continue using device...
```

```kotlin
// Kotlin also — side effect mid-chain, object flows through untouched
val device = ScannedDevice(name = "HRM", address = "AA:BB:CC", rssi = -70)
    .also { Log.d("BLE", "Created device: ${it.address}") }
//   ^^^^
// it = the ScannedDevice
// returns the ScannedDevice unchanged — the chain continues
```

**In this project — logging scan results:**

```kotlin
result?.toScannedDevice()
    ?.also { device ->
        Log.d("BleSessionManager", "Resolved: ${device.name} @ ${device.address}")
    }
    ?.let { device ->
        discovered[device.address] = device
    }
```

`also` logs the device without interrupting the chain. `let` then stores it.

**When to use:** Logging, debugging, assertions, or any side effect mid-chain where the original object must continue flowing unchanged.

---

## `run` — execute a block, get the result back

**Dart equivalent: immediately invoked function expression (IIFE)**

```dart
// Dart IIFE — run a block, get a result
final label = () {
  if (device.rssi > -60) return 'Strong';
  if (device.rssi > -80) return 'Moderate';
  return 'Weak';
}();
```

```kotlin
// Kotlin run — same idea, cleaner syntax
val label = device.run {
    when {
        rssi > -60 -> "Strong"     // this = device, access rssi directly
        rssi > -80 -> "Moderate"
        else       -> "Weak"
    }
}
```

**Null-safe usage:**

```kotlin
val state = bleSession?.run {
    connect()    // this = bleSession (non-null inside the block)
    getState()   // last expression is the return value
}
// state is null if bleSession was null
```

**When to use:** When you want a result from a block of code and `this` is cleaner than `it`. Often used to scope a computation on a nullable object.

---

## `with` — call multiple functions on an object, get the result back

**Dart equivalent: grouping calls on a local variable**

```dart
// Dart — repeated reference to sb
final sb = StringBuffer();
sb.write('Device: ');
sb.write(device.name ?? 'Unknown');
sb.write(' @ ');
sb.write(device.address);
final label = sb.toString();
```

```kotlin
// Kotlin with — no repeated sb reference
val label = with(StringBuilder()) {
    append("Device: ")              // this = StringBuilder
    append(device.name ?: "Unknown")
    append(" @ ")
    append(device.address)
    toString()                      // last expression is returned
}
```

`with` is **not an extension function** — the object goes inside the parentheses, not before the dot. This is the only structural difference from `run`.

```kotlin
// run  — extension, object before the dot
val result = myObject.run { ... }

// with — not extension, object inside the parens
val result = with(myObject) { ... }
```

**When to use:** When you already have a non-null object and want to call several methods on it, reading a result at the end. Prefer `run` on nullable objects (use `?.run`); use `with` on guaranteed non-null objects.

---

## Decision guide

```
Do you need the OBJECT back, or the RESULT of the block?

├── OBJECT back
│   ├── refer to it as `this` → apply    (configure / build objects)
│   └── refer to it as `it`  → also     (side effects / logging)
│
└── RESULT back
    ├── refer to it as `it`              → let    (null check, transform)
    ├── refer to it as `this`, extension → run    (block on nullable, IIFE)
    └── refer to it as `this`, NOT ext  → with   (multiple calls on non-null)
```

---

## Dart vs Kotlin mapping

| Dart pattern | Kotlin equivalent |
|---|---|
| Cascade `..` | `apply` |
| `if (x != null) { use(x) }` | `x?.let { }` |
| IIFE `(() { return ...; })()` | `run { }` |
| Side-effect tap in a chain | `also { }` |
| Multiple calls on a local var | `with(obj) { }` |

---

## Related concepts

- See `inline-functions.md` — `inline` + `reified` for type-safe generics
- See `variance-in-out-star.md` — `out T` on `Flow`/`StateFlow` that scope functions often operate on
