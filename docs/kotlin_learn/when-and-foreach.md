# Kotlin `when` and `forEach`

Source files:
- `app/src/main/java/com/example/harry_android/ui/sensor/SensorScreen.kt`
- `app/src/main/java/com/example/harry_android/data/repository/SensorRepositoryImpl.kt`
- `app/src/main/java/com/example/harry_android/data/ble/BleSessionManager.kt`

---

## `when` — Kotlin's supercharged `switch`

`when` replaces `switch` in Kotlin. Two big upgrades over Dart's `switch`:
1. It is an **expression** — it returns a value directly, no `return` needed in each branch
2. It can match on **types** — works perfectly with sealed classes

---

### 1. Basic — matching a value

**Dart:**
```dart
switch (errorCode) {
  case 1: return 'Scan failed';
  case 2: return 'Adapter off';
  default: return 'Unknown error';
}
```

**Kotlin:**
```kotlin
// when is an expression — assign the result directly
val message = when (errorCode) {
    1    -> "Scan failed"
    2    -> "Adapter off"
    else -> "Unknown error"
}
```

No `break` needed. Each branch is `value -> result`.

---

### 2. On sealed classes — type matching

This is the most common use in this project. `SensorScreen.kt:121`:

```kotlin
val label = when (state) {
    is BleState.Connected  -> "Connected"
    is BleState.Connecting -> "Connecting..."
    is BleState.Error      -> "Error"
    else                   -> "Disconnected"
}

val containerColor = when (state) {
    is BleState.Connected  -> MaterialTheme.colorScheme.primaryContainer
    is BleState.Connecting -> MaterialTheme.colorScheme.secondaryContainer
    is BleState.Error      -> MaterialTheme.colorScheme.errorContainer
    else                   -> MaterialTheme.colorScheme.surfaceVariant
}
```

**Dart equivalent (Dart 3 sealed class):**
```dart
final label = switch (state) {
  Connected()  => 'Connected',
  Connecting() => 'Connecting...',
  Error()      => 'Error',
  _            => 'Disconnected',
};
```

**Smart cast inside `is` branches** — after `is BleState.Error`, Kotlin knows the exact type:

```kotlin
val detail = when (state) {
    is BleState.Error -> state.message  // state is smart-cast to BleState.Error here
    else -> ""
}
```

**Dart equivalent:**
```dart
final detail = switch (state) {
  Error(:final message) => message,  // destructured
  _ => '',
};
```

---

### 3. Without an argument — replaces if-else chains

When there is no subject value, each branch is a boolean condition. `SensorScreen.kt:226`:

```kotlin
text = when {
    !isConnected -> "Waiting for connection"
    isNotifying  -> "Receiving data every 2s"
    else         -> "Notifications stopped"
}
```

**Dart equivalent:**
```dart
// Nested ternaries — less readable with 3+ conditions
final text = !isConnected ? 'Waiting for connection'
    : isNotifying ? 'Receiving data every 2s'
    : 'Notifications stopped';
```

`when { }` is cleaner than chained ternaries for 3+ conditions.

---

### 4. Matching on a UUID — `SensorRepositoryImpl.kt:33`

```kotlin
when (notification.uuid) {
    GattUuid.TEMPERATURE -> {
        latestTemp = notification.bytes.decodeTemperature()
    }
    GattUuid.HUMIDITY -> {
        latestHumidity = decoder.decodeHumidity(notification.bytes)
    }
    // no else — other UUIDs are intentionally ignored
}
```

---

### 5. Multiple values per branch and ranges

```kotlin
when (rssi) {
    in -50..0       -> "Excellent"          // range check
    in -70..-51     -> "Good"
    in -90..-71     -> "Weak"
    -100, -99, -98  -> "Almost dead"        // multiple values, one branch
    else            -> "No signal"
}
```

**Dart does not have built-in range matching in switch** — you would need `if-else if`.

---

### `when` as statement vs expression

```kotlin
// As expression — result is assigned
val label: String = when (state) {
    is BleState.Connected -> "Connected"
    else -> "Disconnected"
}

// As statement — result is discarded, used for side effects
when (state) {
    is BleState.Connected -> startNotifications()
    is BleState.Error     -> showErrorDialog(state.message)
    else                  -> { /* do nothing */ }
}
```

When used as an **expression**, `else` is required unless the compiler can prove all branches are covered (e.g. an exhaustive sealed class).

---

## `forEach` — Kotlin's "each"

Kotlin has no separate `each` keyword. The equivalent is `forEach` (a standard library extension on any `Iterable`).

---

### 1. Basic `forEach`

`BleSessionManager.kt:175`:

```kotlin
_sessions.value.values.forEach { it.disconnect() }
```

**Dart equivalent:**
```dart
_sessions.values.forEach((session) => session.disconnect());
// or
for (final session in _sessions.values) {
  session.disconnect();
}
```

---

### 2. Named parameter — when `it` is ambiguous

```kotlin
// it works, but naming is clearer in nested lambdas
scannedDevices.forEach { device ->
    Log.d("BLE", "Found: ${device.name} @ ${device.address}")
}
```

**Dart equivalent:**
```dart
scannedDevices.forEach((device) {
  debugPrint('Found: ${device.name} @ ${device.address}');
});
```

---

### 3. `forEachIndexed` — index + item together

```kotlin
devices.forEachIndexed { index, device ->
    println("$index: ${device.name} (${device.rssi} dBm)")
}
```

**Dart equivalent (Dart 3):**
```dart
for (final (index, device) in devices.indexed) {
  print('$index: ${device.name} (${device.rssi} dBm)');
}
```

---

### 4. `for..in` — when you need `break` or `continue`

`forEach` cannot use `break` or `continue`. Switch to `for..in` when you need them:

```kotlin
// forEach — no break possible
devices.forEach { device ->
    if (device.rssi < -90) return@forEach  // ← this is continue, not break
    connect(device)
}

// for..in — break and continue work normally
for (device in devices) {
    if (device.rssi < -90) break   // stops the entire loop
    connect(device)
}
```

**Dart equivalent:**
```dart
for (final device in devices) {
  if (device.rssi < -90) break;
  connect(device);
}
```

---

### 5. Common collection operations alongside `forEach`

```kotlin
val names = devices
    .filter { it.rssi > -80 }                  // like Dart .where()
    .map { it.name ?: "Unknown" }              // like Dart .map()
    .also { Log.d("BLE", "Count: ${it.size}") } // side effect mid-chain

names.forEach { println(it) }
```

**Dart equivalent:**
```dart
final names = devices
    .where((d) => d.rssi > -80)
    .map((d) => d.name ?? 'Unknown')
    .toList();

names.forEach(print);
```

---

## Quick reference

| Kotlin | Dart equivalent | Notes |
|---|---|---|
| `when (x) { A -> ... }` | `switch (x) { case A: ... }` | No `break`, `when` is an expression |
| `when { condition -> ... }` | `if / else if / else` | No subject — each branch is a boolean |
| `is BleState.Error` in `when` | `case Error()` in `switch` | Smart-casts inside the branch |
| `in 0..100` in `when` | No direct equivalent | Range matching built-in |
| `forEach { }` | `.forEach((e) { })` | Cannot `break` |
| `forEachIndexed { i, e -> }` | `for (var (i,e) in list.indexed)` | Index + element together |
| `for (x in list)` | `for (final x in list)` | Supports `break` and `continue` |

---

## Related concepts

- See `scope-functions.md` — `let`, `also`, `apply` are often used inside `when` branches
- See `typesofclass.md` — sealed classes make `when` exhaustive (no `else` needed)
