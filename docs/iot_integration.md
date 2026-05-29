# ESP32 BLE Android Integration Plan

**Stack:** Kotlin · Jetpack Compose · MVVM · Manual DI · Coroutines/Flow  
**Device:** ESP32-BLE-Dev running NimBLE-Arduino v1.4.x  
**Author:** Senior Android Engineer  
**Protocol Reference:** GATT over BLE, ATT MTU 512 bytes, max 3 simultaneous clients

---

## Table of Contents

1. [GATT Protocol Reference](#1-gatt-protocol-reference)
2. [Project Architecture](#2-project-architecture)
3. [Project Structure](#3-project-structure)
4. [Theme & Design System](#4-theme--design-system)
5. [Navigation](#5-navigation)
6. [Domain Layer](#6-domain-layer)
   - [Models](#61-models)
   - [Repository Interfaces](#62-repository-interfaces)
   - [Use Cases](#63-use-cases)
7. [Data Layer — BLE Data Source](#7-data-layer--ble-data-source)
   - [GattUuids](#71-gattuuids)
   - [GattDecoder](#72-gattdecoder)
   - [GattEncoder](#73-gattencoder)
   - [BleDeviceSession](#74-bledevicesession)
   - [BleSessionManager](#75-blesessionmanager)
8. [Data Layer — Repositories](#8-data-layer--repositories)
   - [SensorRepositoryImpl](#81-sensorrepositorympl)
   - [DeviceInfoRepositoryImpl](#82-deviceinforepositorympl)
   - [ScanRepositoryImpl](#83-scanrepositorympl)
9. [DI — AppContainer (Manual DI)](#9-di--appcontainer-manual-di)
10. [UI Layer — ViewModels](#10-ui-layer--viewmodels)
    - [SensorViewModel](#101-sensorviewmodel)
    - [DeviceInfoViewModel](#102-deviceinfoviewmodel)
    - [BleConnectionViewModel](#103-bleconnectionviewmodel)
11. [UI Layer — Screens & Composables](#11-ui-layer--screens--composables)
    - [MainActivity](#111-mainactivity)
    - [Sensor Dashboard](#112-sensor-dashboard)
    - [Device Info Screen](#113-device-info-screen)
    - [Scan Bottom Sheet](#114-scan-bottom-sheet)
    - [Shared Components](#115-shared-components)
12. [BLE Permission Handling](#12-ble-permission-handling)
13. [Testability Strategy](#13-testability-strategy)
14. [Key Engineering Decisions](#14-key-engineering-decisions)

---

## 1. GATT Protocol Reference

### Device

| Property | Value |
|---|---|
| Advertised Name | `ESP32-BLE-Dev` |
| ATT MTU | 512 bytes |
| BLE Stack | NimBLE-Arduino v1.4.x |
| Max Simultaneous Clients | 3 |

### Data Transfer Format

All multi-byte numeric values are transmitted **least-significant byte first** (little-endian).

```
Example: temperature 23.5 °C
  Scaled integer:  235  (= 23.5 × 10)
  Hex:             0x00EB
  Bytes on wire:   EB 00   ← LSB first
  Decode:  raw = (bytes[0]) | (bytes[1] << 8) → 235 → 235 / 10.0 = 23.5
```

String characteristics are raw UTF-8 bytes with no null terminator.

### Service 1 — Device Information Service (DIS)

**UUID:** `0x180A`

| Characteristic | UUID | Properties | Format |
|---|---|---|---|
| Manufacturer Name | `0x2A29` | READ | UTF-8 bytes |
| Model Number | `0x2A24` | READ | UTF-8 bytes |
| Firmware Revision | `0x2A26` | READ | UTF-8 bytes |
| Hardware Revision | `0x2A27` | READ | UTF-8 bytes |

### Service 2 — Sensor Data Service

**UUID:** `12345678-1234-1234-1234-123456789ABC`

| Characteristic | UUID | Properties | Format | Range |
|---|---|---|---|---|
| Temperature | `...ABD` | READ, NOTIFY | `int16_t` LE × 10, 2 bytes | 20.0 – 35.0 °C |
| Humidity | `...ABE` | READ, NOTIFY | `uint16_t` LE × 10, 2 bytes | 40.0 – 90.0 % RH |
| Control | `...ABF` | WRITE | `uint16_t` LE, 2 bytes | `0x0001` start / `0x0000` stop |

### Notification Flow

```
Client                          ESP32
  |--- Connect ------------------>|
  |--- Subscribe (temp CCCD) ---->|
  |--- Subscribe (hum  CCCD) ---->|
  |--- Write Control: 01 00 ----->|  ← start
  |<-- Notify temp: EB 00 --------|  23.5 °C  every 2000 ms
  |<-- Notify hum:  26 02 --------|  55.0 %   every 2000 ms
  |--- Write Control: 00 00 ----->|  ← stop
  |--- Disconnect --------------->|
```

---

## 2. Project Architecture

```
┌─────────────────────────────────────────────────┐
│                   UI Layer                       │
│  SensorScreen · DeviceInfoScreen · ScanSheet     │
│  ViewModels → StateFlow<UiState>                 │
└────────────────────┬────────────────────────────┘
                     │ collect / call
┌────────────────────▼────────────────────────────┐
│                Domain Layer                      │
│  Use Cases · Models · Repository Interfaces      │
│  Pure Kotlin — zero Android imports              │
└────────────────────┬────────────────────────────┘
                     │ implements
┌────────────────────▼────────────────────────────┐
│                 Data Layer                       │
│  BleSessionManager (singleton BLE hub)           │
│    ├── BluetoothLeScanner — owns BLE scan        │
│    ├── scannedDevices: StateFlow<List<...>>      │
│    ├── BleDeviceSession[AA:BB:...] — own gatt   │
│    └── BleDeviceSession[FF:EE:...] — own gatt   │
│  Repositories (thin mappers, per-device)         │
│  GattDecoder · GattEncoder · GattUuids           │
│  GattUuids                                       │
└────────────────────┬────────────────────────────┘
                     │ Bluetooth GATT
┌────────────────────▼────────────────────────────┐
│           ESP32-BLE-Dev (NimBLE)                 │
│  DIS 0x180A · Sensor 12345678-...ABC            │
└─────────────────────────────────────────────────┘

DI: Manual — AppContainer (Application-scoped)
```

**Layer rules:**
- Domain has no Android imports — tested with plain JUnit.
- Data layer owns all GATT byte-level logic (codec + transport).
- UI layer owns no BLE or byte decoding logic.
- All cross-layer communication via interfaces and Kotlin `Flow` / `StateFlow`.

---

## 3. Project Structure

```
app/
├── AppContainer.kt               # Manual DI — creates and holds all singletons
├── HarryApp.kt                   # Application subclass; exposes appContainer
│
├── domain/
│   ├── model/
│   │   ├── ScannedDevice.kt      # BLE scan result domain model
│   │   ├── SensorReading.kt      # Temperature + humidity pair
│   │   ├── DeviceInfo.kt         # DIS characteristic values
│   │   └── BleState.kt           # Connection state sealed class
│   ├── repository/
│   │   ├── IBleRepository.kt     # connect / disconnect / notifications
│   │   ├── ISensorRepository.kt  # observe / readOnce
│   │   ├── IDeviceInfoRepository.kt
│   │   └── IScanRepository.kt    # scanForDevices Flow
│   └── usecase/
│       ├── ObserveSensorDataUseCase.kt
│       ├── ReadDeviceInfoUseCase.kt
│       ├── ControlNotificationsUseCase.kt
│       └── ScanForDevicesUseCase.kt
│
├── data/
│   ├── ble/
│   │   ├── BleDeviceSession.kt   # per-device GATT state machine; owns one BluetoothGatt
│   │   ├── BleSessionManager.kt  # singleton registry; creates/tracks BleDeviceSession instances
│   │   ├── GattDecoder.kt        # byte[] → domain models
│   │   ├── GattEncoder.kt        # control commands → byte[]
│   │   ├── GattUuids.kt          # all UUID constants
│   │   └── BleConnectionState.kt
│   └── repository/
│       ├── SensorRepositoryImpl.kt
│       ├── DeviceInfoRepositoryImpl.kt
│       └── ScanRepositoryImpl.kt
│
├── ui/
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Shape.kt
│   ├── navigation/
│   │   ├── AppNavGraph.kt
│   │   └── Screen.kt
│   ├── sensor/
│   │   ├── SensorScreen.kt
│   │   ├── SensorViewModel.kt
│   │   └── components/
│   │       ├── SensorMetricCard.kt
│   │       ├── MiniLineChart.kt
│   │       └── GaugeBar.kt
│   ├── deviceinfo/
│   │   ├── DeviceInfoScreen.kt
│   │   ├── DeviceInfoViewModel.kt
│   │   └── components/
│   │       ├── DisInfoCard.kt
│   │       └── ConnectionCard.kt
│   ├── connection/
│   │   ├── ScanBottomSheet.kt
│   │   ├── BleConnectionViewModel.kt
│   │   └── components/
│   │       ├── DeviceScanItem.kt
│   │       ├── RssiIndicator.kt
│   │       ├── ScanEmptyStates.kt
│   │       └── BleStatusChip.kt
│   └── common/
│       ├── BlePermissionHandler.kt
│       └── components/
│           └── LoadingPlaceholder.kt
│
└── core/
    └── dispatcher/
        ├── DispatcherProvider.kt
        └── DefaultDispatcherProvider.kt
```

---

## 4. Theme & Design System

Material 3 with a custom color scheme. Blue primary reflects "connected" state semantics; teal secondary for humidity/sensor data.

```kotlin
// ui/theme/Color.kt
val BluePrimary              = Color(0xFF185FA5)
val BluePrimaryContainer     = Color(0xFFE6F1FB)
val BlueOnPrimaryContainer   = Color(0xFF0C447C)
val TealSecondary            = Color(0xFF0F6E56)
val TealSecondaryContainer   = Color(0xFFE1F5EE)
val ErrorRed                 = Color(0xFFA32D2D)
val SuccessGreen             = Color(0xFF3B6D11)
```

```kotlin
// ui/theme/Theme.kt
@Composable
fun BleDevTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF85B7EB),
            primaryContainer = Color(0xFF0C447C),
            secondary = Color(0xFF5DCAA5),
            secondaryContainer = Color(0xFF085041),
            error = Color(0xFFF09595),
        )
    } else {
        lightColorScheme(
            primary = BluePrimary,
            primaryContainer = BluePrimaryContainer,
            onPrimaryContainer = BlueOnPrimaryContainer,
            secondary = TealSecondary,
            secondaryContainer = TealSecondaryContainer,
            error = ErrorRed,
        )
    }
    MaterialTheme(colorScheme = colorScheme, typography = BleTypography, shapes = BleShapes, content = content)
}
```

```kotlin
// ui/theme/Type.kt
val BleTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = 0.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = 0.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.15.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.25.sp),
    labelSmall     = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

// ui/theme/Shape.kt
val BleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),   // cards
    large      = RoundedCornerShape(24.dp),   // bottom sheet
    extraLarge = RoundedCornerShape(28.dp),
)
```

---

## 5. Navigation

Type-safe sealed class destinations with a `NavigationBar` persisting across all screens.

```kotlin
// ui/navigation/Screen.kt
sealed class Screen(val route: String) {
    object Sensor     : Screen("sensor")
    object DeviceInfo : Screen("device_info")
    object Settings   : Screen("settings")
}

// ui/navigation/AppNavGraph.kt
@Composable
fun AppNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController, startDestination = Screen.Sensor.route, modifier = modifier) {
        composable(Screen.Sensor.route)     { SensorScreen(navController) }
        composable(Screen.DeviceInfo.route) { DeviceInfoScreen(navController) }
        composable(Screen.Settings.route)   { SettingsScreen() }
    }
}
```

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BleDevTheme {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = { BleBottomNavBar(navController) }
                ) { padding ->
                    AppNavGraph(navController = navController, modifier = Modifier.padding(padding))
                }
            }
        }
    }
}
```

---

## 6. Domain Layer

### 6.1 Models

Pure Kotlin data classes — zero Android imports.

```kotlin
// domain/model/SensorReading.kt
data class SensorReading(
    val temperatureCelsius: Double,   // decoded from int16 LE × 10
    val humidityPercent: Double,      // decoded from uint16 LE × 10
    val timestampMs: Long
)

// domain/model/DeviceInfo.kt
data class DeviceInfo(
    val manufacturerName: String,     // 0x2A29 — "YourCompany"
    val modelNumber: String,          // 0x2A24 — "ESP32-DEV-001"
    val firmwareRevision: String,     // 0x2A26 — "1.0.0"
    val hardwareRevision: String      // 0x2A27 — "ESP32-DEVKIT-V1"
)

// domain/model/BleState.kt
sealed class BleState {
    object Idle        : BleState()
    object Scanning    : BleState()
    object Connecting  : BleState()
    data class Connected(val deviceAddress: String) : BleState()
    data class Error(val message: String)           : BleState()
}

// domain/model/ScannedDevice.kt
data class ScannedDevice(
    val name: String?,           // null for unnamed/unresponding devices
    val address: String,         // MAC address — stable device identity
    val rssi: Int,               // signal strength in dBm (e.g. -62)
    val isKnownEsp32: Boolean    // true when advertised name == "ESP32-BLE-Dev"
)

// Extension: signal quality bucketing for RSSI indicator UI
val ScannedDevice.signalQuality: SignalQuality
    get() = when {
        rssi >= -60 -> SignalQuality.Excellent
        rssi >= -75 -> SignalQuality.Good
        rssi >= -90 -> SignalQuality.Fair
        else        -> SignalQuality.Poor
    }

enum class SignalQuality { Excellent, Good, Fair, Poor }
```

### 6.2 Repository Interfaces

```kotlin
// domain/repository/ISensorRepository.kt
interface ISensorRepository {
    /** Hot Flow; collecting starts GATT notifications. Cancellation stops them. */
    fun observeSensorData(): Flow<SensorReading>
    suspend fun readOnce(): SensorReading
}

// domain/repository/IDeviceInfoRepository.kt
interface IDeviceInfoRepository {
    suspend fun readDeviceInfo(): DeviceInfo
}

// domain/repository/IBleRepository.kt
interface IBleRepository {
    val connectionState: StateFlow<BleState>
    suspend fun connect(deviceAddress: String)
    suspend fun startNotifications()
    suspend fun stopNotifications()
    suspend fun disconnect()
}

// domain/repository/IScanRepository.kt
interface IScanRepository {
    /**
     * Emits a de-duplicated, RSSI-sorted list of discovered BLE devices.
     * Flow is hot — collecting starts a real BLE scan; cancellation stops it.
     * Devices are sorted: known ESP32 targets first, then descending RSSI.
     */
    fun scanForDevices(): Flow<List<ScannedDevice>>

    /** Returns true if the Bluetooth adapter is currently enabled. */
    suspend fun isBluetoothEnabled(): Boolean
}
```

### 6.3 Use Cases

```kotlin
// domain/usecase/ObserveSensorDataUseCase.kt
class ObserveSensorDataUseCase constructor(
    private val repository: ISensorRepository   // interface, not impl
) {
    operator fun invoke(): Flow<SensorReading> = repository.observeSensorData()
}

// domain/usecase/ReadDeviceInfoUseCase.kt
class ReadDeviceInfoUseCase constructor(
    private val repository: IDeviceInfoRepository
) {
    suspend operator fun invoke(): DeviceInfo = repository.readDeviceInfo()
}

// domain/usecase/ControlNotificationsUseCase.kt
class ControlNotificationsUseCase constructor(
    private val bleRepository: IBleRepository
) {
    suspend fun start() = bleRepository.startNotifications()
    suspend fun stop()  = bleRepository.stopNotifications()
}

// domain/usecase/ScanForDevicesUseCase.kt
class ScanForDevicesUseCase constructor(
    private val scanRepository: IScanRepository
) {
    operator fun invoke(): Flow<List<ScannedDevice>> =
        scanRepository.scanForDevices()
}
```

---

## 7. Data Layer — BLE Data Source

### 7.1 GattUuids

Single source of truth for all GATT UUIDs.

```kotlin
// data/ble/GattUuids.kt
object GattUuids {
    // ── Device Information Service (Bluetooth SIG standard) ──────────────
    val DIS_SERVICE       = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
    val MANUFACTURER_NAME = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")
    val MODEL_NUMBER      = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB")
    val FIRMWARE_REVISION = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")
    val HARDWARE_REVISION = UUID.fromString("00002A27-0000-1000-8000-00805F9B34FB")

    // ── Sensor Data Service (custom) ─────────────────────────────────────
    val SENSOR_SERVICE    = UUID.fromString("12345678-1234-1234-1234-123456789ABC")
    val TEMPERATURE       = UUID.fromString("12345678-1234-1234-1234-123456789ABD")
    val HUMIDITY          = UUID.fromString("12345678-1234-1234-1234-123456789ABE")
    val CONTROL           = UUID.fromString("12345678-1234-1234-1234-123456789ABF")

    // ── Client Characteristic Configuration Descriptor ────────────────────
    val CCCD              = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}
```

### 7.2 GattDecoder

All protocol decoding is isolated here. Testable with raw byte arrays — no device required.

```kotlin
// data/ble/GattDecoder.kt
class GattDecoder {

    /**
     * Decodes a 2-byte little-endian int16_t scaled by ×10 into °C.
     *
     * Protocol: int16 LE, value = raw / 10.0
     * Example:  EB 00 → raw 235 → 23.5 °C
     * Negative: F0 FF → raw (int16) -16 → -1.6 °C
     */
    fun decodeTemperature(bytes: ByteArray): Double {
        require(bytes.size >= 2) { "Temperature requires 2 bytes, got ${bytes.size}" }
        val raw = (bytes[0].toInt() and 0xFF) or (bytes[1].toInt() shl 8)
        return raw.toShort() / 10.0   // toShort() preserves int16_t sign
    }

    /**
     * Decodes a 2-byte little-endian uint16_t scaled by ×10 into % RH.
     *
     * Protocol: uint16 LE, value = raw / 10.0
     * Example:  26 02 → raw 550 → 55.0 %
     */
    fun decodeHumidity(bytes: ByteArray): Double {
        require(bytes.size >= 2) { "Humidity requires 2 bytes, got ${bytes.size}" }
        val raw = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        return raw / 10.0
    }

    /**
     * Decodes raw UTF-8 bytes into a String.
     * DIS characteristics have no null terminator.
     */
    fun decodeString(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)
}
```

### 7.3 GattEncoder

Encodes commands sent to the ESP32.

```kotlin
// data/ble/GattEncoder.kt
class GattEncoder {

    /**
     * Encodes the Control characteristic write value.
     *
     * Protocol: uint16 LE
     *   start = 0x0001 → bytes [01, 00]
     *   stop  = 0x0000 → bytes [00, 00]
     */
    fun encodeControl(start: Boolean): ByteArray {
        val value: Int = if (start) 0x0001 else 0x0000
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }
}
```

### 7.4 BleDeviceSession

Per-device GATT state machine. One instance per connected device. Not a DI-managed singleton — created and owned by `BleSessionManager`. Bridges callback-based `BluetoothGattCallback` into Kotlin Flows via `MutableSharedFlow`.

**Owns:** `connect`, `disconnect`, `startNotifications`, `stopNotifications`, `readCharacteristic`, `writeCharacteristic`.

```kotlin
// data/ble/BleDeviceSession.kt
class BleDeviceSession(
    private val context: Context,
    val address: String,
    private val decoder: GattDecoder,
    private val encoder: GattEncoder,
    private val dispatchers: DispatcherProvider
) {
    // ── Public state ──────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow<BleState>(BleState.Idle)
    val connectionState: StateFlow<BleState> = _connectionState.asStateFlow()

    /**
     * SharedFlow that bridges BluetoothGattCallback notifications into
     * the repository layer. extraBufferCapacity prevents dropped notifications
     * when the BLE thread fires faster than the collector.
     */
    private val _notificationChannel = MutableSharedFlow<GattNotification>(
        replay = 0, extraBufferCapacity = 32
    )
    val notificationChannel: SharedFlow<GattNotification> =
        _notificationChannel.asSharedFlow()

    private var gatt: BluetoothGatt? = null

    /**
     * Completes when onServicesDiscovered fires with GATT_SUCCESS.
     * Reset on each connect() so re-connections get a fresh gate.
     * startNotifications() and readCharacteristic() await this before
     * touching any characteristic — discoverServices() is async and
     * characteristics return null until it finishes.
     */
    private var servicesDiscovered = CompletableDeferred<Unit>()

    // ── Connection ────────────────────────────────────────────────────────

    suspend fun connect() = withContext(dispatchers.io) {
        servicesDiscovered = CompletableDeferred()   // reset gate for this connection attempt
        _connectionState.value = BleState.Connecting
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    suspend fun disconnect() = withContext(dispatchers.io) {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = BleState.Idle
    }

    // ── Notifications ─────────────────────────────────────────────────────

    /**
     * Awaits service discovery, then subscribes to Temperature and Humidity
     * CCCDs and writes 0x0001 to Control to start the 2000 ms notification loop.
     */
    suspend fun startNotifications() = withContext(dispatchers.io) {
        servicesDiscovered.await()   // suspend until onServicesDiscovered completes
        val g = gatt ?: return@withContext

        listOf(GattUuids.TEMPERATURE, GattUuids.HUMIDITY).forEach { uuid ->
            g.getCharacteristic(GattUuids.SENSOR_SERVICE, uuid)?.let { char ->
                g.setCharacteristicNotification(char, true)
                char.getDescriptor(GattUuids.CCCD)?.let { cccd ->
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                }
            }
        }

        g.getCharacteristic(GattUuids.SENSOR_SERVICE, GattUuids.CONTROL)?.let { ctrl ->
            ctrl.value = encoder.encodeControl(start = true)
            g.writeCharacteristic(ctrl)
        }
    }

    /** Writes 0x0000 to Control characteristic to stop notifications. */
    suspend fun stopNotifications() = withContext(dispatchers.io) {
        servicesDiscovered.await()
        gatt?.getCharacteristic(GattUuids.SENSOR_SERVICE, GattUuids.CONTROL)?.let { ctrl ->
            ctrl.value = encoder.encodeControl(start = false)
            gatt?.writeCharacteristic(ctrl)
        }
    }

    // ── Read / Write ──────────────────────────────────────────────────────

    /**
     * One-shot GATT read. Awaits service discovery, then suspends until
     * onCharacteristicRead fires. Used by DeviceInfoRepositoryImpl to read
     * DIS string characteristics.
     */
    suspend fun readCharacteristic(service: UUID, char: UUID): ByteArray? {
        servicesDiscovered.await()
        TODO("Implement one-shot GATT read via suspendCancellableCoroutine")
    }

    /** Writes an arbitrary value to any writable characteristic. */
    suspend fun writeCharacteristic(service: UUID, char: UUID, value: ByteArray) =
        withContext(dispatchers.io) {
            servicesDiscovered.await()
            gatt?.getCharacteristic(service, char)?.let { characteristic ->
                characteristic.value = value
                gatt?.writeCharacteristic(characteristic)
            }
        }

    // ── GATT Callback ─────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleState.Connected(g.device.address)
                    g.discoverServices()   // async — onServicesDiscovered fires when done
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleState.Idle
                    g.close()
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = BleState.Error("GATT error: status $status")
            }
        }

        /**
         * Fires after discoverServices() completes.
         * Completes the servicesDiscovered gate so startNotifications() and
         * readCharacteristic() can proceed. On failure, completes exceptionally
         * so callers receive the error through the coroutine cancellation path.
         */
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesDiscovered.complete(Unit)
            } else {
                val error = IllegalStateException("Service discovery failed: status $status")
                servicesDiscovered.completeExceptionally(error)
                _connectionState.value = BleState.Error("Service discovery failed: status $status")
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            char: BluetoothGattCharacteristic
        ) {
            // Clone bytes — the array is reused by the BLE stack
            _notificationChannel.tryEmit(GattNotification(char.uuid, char.value.clone()))
        }
    }
}

/** Value object carrying a raw GATT notification payload. */
data class GattNotification(val uuid: UUID, val bytes: ByteArray)

/** Extension: safely get a characteristic without null-chaining. */
private fun BluetoothGatt.getCharacteristic(service: UUID, char: UUID): BluetoothGattCharacteristic? =
    getService(service)?.getCharacteristic(char)
```

---

### 7.5 BleSessionManager

Singleton BLE hub. Owns both the `BluetoothLeScanner` (scan phase) and the `address → BleDeviceSession` registry (connection phase). RSSI filtering is applied during scanning — devices below threshold never enter `scannedDevices`. Has no GATT methods — all protocol work is delegated to the session.

**Owns:** `startScan`, `stopScan`, `scannedDevices`, `getOrCreate`, `remove`, `disconnectAll`, `sessions` registry, `allConnectionStates` aggregation.

```kotlin
// data/ble/BleSessionManager.kt
class BleSessionManager(
    private val context: Context,
    private val decoder: GattDecoder,
    private val encoder: GattEncoder,
    private val dispatchers: DispatcherProvider
) {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val minRssiThreshold = -80  // dBm — weaker devices are ignored

    // ── Scan state ───────────────────────────────────────────────────────────
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val discovered = mutableMapOf<String, ScannedDevice>()
    private var scanCallback: ScanCallback? = null

    // ── Session registry ─────────────────────────────────────────────────────
    private val _sessions = MutableStateFlow<Map<String, BleDeviceSession>>(emptyMap())
    val sessions: StateFlow<Map<String, BleDeviceSession>> = _sessions.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Scanning ─────────────────────────────────────────────────────────────
    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        discovered.clear()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(GattUuids.SENSOR_SERVICE))
                .build()
        )

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.rssi < minRssiThreshold) return  // RSSI filter at source
                val device = result.toScannedDevice()
                discovered[device.address] = device
                _scannedDevices.value = discovered.values
                    .sortedWith(
                        compareByDescending<ScannedDevice> { it.isKnownEsp32 }
                            .thenByDescending { it.rssi }
                    )
            }

            override fun onScanFailed(errorCode: Int) {
                _scannedDevices.value = emptyList()
            }
        }.also { scanner.startScan(filters, settings, it) }
    }

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanCallback = null
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    private fun ScanResult.toScannedDevice(): ScannedDevice {
        val name = device.name?.takeIf { it.isNotBlank() }
            ?: scanRecord?.deviceName?.takeIf { it.isNotBlank() }
        return ScannedDevice(
            name = name,
            address = device.address,
            rssi = rssi,
            isKnownEsp32 = name == "ESP32-BLE-Dev"
        )
    }

    // ── Session registry ──────────────────────────────────────────────────────

    /**
     * Aggregated connection state across all active sessions.
     * Reacts dynamically — each time the session map changes, the inner
     * combine() is rebuilt to track the new set of devices.
     */
    val allConnectionStates: StateFlow<Map<String, BleState>> = _sessions
        .flatMapLatest { sessionMap ->
            if (sessionMap.isEmpty()) flowOf(emptyMap())
            else combine(
                sessionMap.map { (addr, session) ->
                    session.connectionState.map { state -> addr to state }
                }
            ) { pairs -> pairs.toMap() }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * Returns the existing session for the address, or creates a new one.
     * Starts a coroutine to watch the session's state and remove it from
     * the registry when it disconnects — no manual cleanup needed by callers.
     */
    fun getOrCreate(address: String): BleDeviceSession {
        _sessions.value[address]?.let { return it }

        val session = BleDeviceSession(context, address, decoder, encoder, dispatchers)

        scope.launch {
            session.connectionState.collect { state ->
                if (state is BleState.Idle || state is BleState.Error) {
                    _sessions.update { it - address }
                }
            }
        }

        _sessions.update { it + (address to session) }
        return session
    }

    /** Removes a session from the registry without disconnecting it. */
    fun remove(address: String) {
        _sessions.update { it - address }
    }

    /** Disconnects all active sessions — call on app exit or Bluetooth off. */
    suspend fun disconnectAll() {
        _sessions.value.values.forEach { it.disconnect() }
    }
}
```

**Call path from ViewModel to hardware:**

```
ViewModel.connect(address)
    → BleSessionManager.getOrCreate(address)   // manager creates + registers session
    → BleDeviceSession.connect()               // session opens BluetoothGatt

ViewModel.startNotifications(address)
    → BleSessionManager.getOrCreate(address)   // returns existing session
    → BleDeviceSession.startNotifications()    // session writes CCCD + Control char

ViewModel.disconnect(address)
    → BleSessionManager.getOrCreate(address)
    → BleDeviceSession.disconnect()            // session closes gatt, emits BleState.Idle
    // BleSessionManager observer fires → removes session from map automatically
```

---

## 8. Data Layer — Repositories

### 8.1 SensorRepositoryImpl

Pairs Temperature and Humidity notifications into `SensorReading` objects. Scoped to a single device — receives a `BleDeviceSession` directly rather than looking one up from the manager.

```kotlin
// data/repository/SensorRepositoryImpl.kt
class SensorRepositoryImpl(
    private val session: BleDeviceSession,  // injected directly — no manager, no address
    private val decoder: GattDecoder
) : ISensorRepository {

    /**
     * Merges interleaved Temperature and Humidity notifications from this device's session.
     * Emits a new SensorReading each time BOTH values have been received at least once.
     * distinctUntilChanged suppresses duplicate emissions on unchanged values.
     */
    override fun observeSensorData(): Flow<SensorReading> {
        var latestTemp: Double? = null
        var latestHumidity: Double? = null

        return session.notificationChannel
            .mapNotNull { notification ->
                when (notification.uuid) {
                    GattUuids.TEMPERATURE ->
                        latestTemp = decoder.decodeTemperature(notification.bytes)
                    GattUuids.HUMIDITY ->
                        latestHumidity = decoder.decodeHumidity(notification.bytes)
                }
                val t = latestTemp ?: return@mapNotNull null
                val h = latestHumidity ?: return@mapNotNull null
                SensorReading(t, h, System.currentTimeMillis())
            }
            .distinctUntilChanged()
    }

    override suspend fun readOnce(): SensorReading {
        TODO("Implement one-shot GATT read via BleDeviceSession.readCharacteristic()")
    }

    class Factory(private val decoder: GattDecoder) {
        fun create(session: BleDeviceSession) = SensorRepositoryImpl(session, decoder)
    }
}
```

### 8.2 DeviceInfoRepositoryImpl

Scoped to a single device via its `Factory`. Receives a `BleDeviceSession` directly and reads DIS characteristics through it.

```kotlin
// data/repository/DeviceInfoRepositoryImpl.kt
class DeviceInfoRepositoryImpl(
    private val session: BleDeviceSession,  // injected directly — no manager, no address
    private val decoder: GattDecoder
) : IDeviceInfoRepository {

    /**
     * Reads all four DIS characteristics sequentially.
     * Each is a UTF-8 string with no null terminator (per protocol spec).
     */
    override suspend fun readDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturerName  = readString(GattUuids.DIS_SERVICE, GattUuids.MANUFACTURER_NAME),
            modelNumber       = readString(GattUuids.DIS_SERVICE, GattUuids.MODEL_NUMBER),
            firmwareRevision  = readString(GattUuids.DIS_SERVICE, GattUuids.FIRMWARE_REVISION),
            hardwareRevision  = readString(GattUuids.DIS_SERVICE, GattUuids.HARDWARE_REVISION)
        )
    }

    private suspend fun readString(service: UUID, char: UUID): String {
        val bytes = session.readCharacteristic(service, char) ?: byteArrayOf()
        return decoder.decodeString(bytes)
    }

    class Factory(private val decoder: GattDecoder) {
        fun create(session: BleDeviceSession) = DeviceInfoRepositoryImpl(session, decoder)
    }
}
```

### 8.3 ScanRepositoryImpl

Thin mapper — delegates all scan operations to `BleSessionManager`, which owns the `BluetoothLeScanner`. No hardware interaction here.

```kotlin
// data/repository/ScanRepositoryImpl.kt
class ScanRepositoryImpl(
    private val sessionManager: BleSessionManager
) : IScanRepository {

    override suspend fun isBluetoothEnabled(): Boolean =
        sessionManager.isBluetoothEnabled()

    override fun scanForDevices(): Flow<List<ScannedDevice>> =
        sessionManager.scannedDevices   // reads StateFlow from BleSessionManager

    override fun startScan() = sessionManager.startScan()

    override fun stopScan() = sessionManager.stopScan()
}
```

---

## 9. DI — AppContainer (Manual DI)

All dependency wiring lives in `AppContainer`. The `Application` subclass creates one instance at startup and holds it for the app's lifetime — no framework required.

```kotlin
// AppContainer.kt
class AppContainer(context: Context) {
    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()

    val decoder = GattDecoder()
    val encoder = GattEncoder()

    val bleSessionManager = BleSessionManager(context, decoder, encoder, dispatchers)

    // Factories no longer hold BleSessionManager — session is passed at connect time
    val sensorRepoFactory     = SensorRepositoryImpl.Factory(decoder)
    val deviceInfoRepoFactory = DeviceInfoRepositoryImpl.Factory(decoder)
    val bleRepoFactory        = BleRepositoryImpl.Factory(bleSessionManager)

    // ScanRepositoryImpl is now a thin mapper over BleSessionManager
    val scanRepository: IScanRepository = ScanRepositoryImpl(bleSessionManager)

    val sensorViewModelFactory     = SensorViewModel.Factory(sensorRepoFactory, bleRepoFactory, dispatchers)
    val deviceInfoViewModelFactory = DeviceInfoViewModel.Factory(deviceInfoRepoFactory, bleRepoFactory, dispatchers)
    val bleConnectionViewModelFactory = BleConnectionViewModel.Factory(
        ScanForDevicesUseCase(scanRepository), bleRepoFactory, scanRepository, dispatchers
    )

    // At connect time — nav graph or ViewModel fetches session and creates repo:
    // val session = bleSessionManager.getOrCreate(address)
    // val sensorRepo = sensorRepoFactory.create(session)
}

// HarryApp.kt
class HarryApp : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
```

Register `HarryApp` in `AndroidManifest.xml`:
```xml
<application android:name=".HarryApp" ... >
```

ViewModels are created in the nav graph using each factory's `create()`:

```kotlin
// ui/navigation/AppNavGraph.kt
@Composable
fun AppNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as HarryApp).appContainer

    NavHost(navController, startDestination = Screen.Sensor.route, modifier = modifier) {
        composable(Screen.Sensor.route) {
            val deviceAddress = "AA:BB:CC:DD:EE:FF"   // from nav args in practice
            val vm: SensorViewModel = viewModel(key = deviceAddress, factory = viewModelFactory {
                container.sensorViewModelFactory.create(deviceAddress)
            })
            SensorScreen(navController, vm)
        }
        composable(Screen.DeviceInfo.route) {
            val deviceAddress = "AA:BB:CC:DD:EE:FF"
            val vm: DeviceInfoViewModel = viewModel(key = deviceAddress, factory = viewModelFactory {
                container.deviceInfoViewModelFactory.create(deviceAddress)
            })
            DeviceInfoScreen(navController, vm)
        }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}
```

---

## 10. UI Layer — ViewModels

### 10.1 SensorViewModel

```kotlin
// ui/sensor/SensorViewModel.kt

data class SensorUiState(
    val reading: SensorReading? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isNotifying: Boolean = false,
    val connectionState: BleState = BleState.Idle,
    val history: List<SensorReading> = emptyList()   // rolling 24-reading buffer for chart
)

class SensorViewModel(
    private val sensorRepoFactory: SensorRepositoryImpl.Factory,
    private val bleRepoFactory: BleRepositoryImpl.Factory,
    private val dispatchers: DispatcherProvider,
    private val deviceAddress: String
) : ViewModel() {

    // Per-device instances created from factories — each ViewModel owns repos for one device
    private val sensorRepo = sensorRepoFactory.create(deviceAddress)
    private val bleRepo    = bleRepoFactory.create(deviceAddress)

    private val observeSensorData       = ObserveSensorDataUseCase(sensorRepo)
    private val controlNotifications    = ControlNotificationsUseCase(bleRepo)

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    private val historyBuffer = ArrayDeque<SensorReading>(24)

    init {
        viewModelScope.launch {
            bleRepo.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state is BleState.Connected) startObserving()
            }
        }
    }

    private fun startObserving() {
        viewModelScope.launch(dispatchers.io) {
            controlNotifications.start()
            _uiState.update { it.copy(isNotifying = true) }
            observeSensorData()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { reading ->
                    historyBuffer.addLast(reading)
                    if (historyBuffer.size > 24) historyBuffer.removeFirst()
                    _uiState.update {
                        it.copy(
                            reading = reading,
                            history = historyBuffer.toList(),
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    fun stopNotifications() {
        viewModelScope.launch { controlNotifications.stop() }
        _uiState.update { it.copy(isNotifying = false) }
    }

    fun refreshOnce() {
        viewModelScope.launch(dispatchers.io) { /* trigger one-shot GATT read */ }
    }

    override fun onCleared() { stopNotifications() }

    class Factory(
        private val sensorRepoFactory: SensorRepositoryImpl.Factory,
        private val bleRepoFactory: BleRepositoryImpl.Factory,
        private val dispatchers: DispatcherProvider
    ) {
        fun create(deviceAddress: String) =
            SensorViewModel(sensorRepoFactory, bleRepoFactory, dispatchers, deviceAddress)
    }
}
```

### 10.2 DeviceInfoViewModel

```kotlin
// ui/deviceinfo/DeviceInfoViewModel.kt

data class DeviceInfoUiState(
    val deviceInfo: DeviceInfo? = null,
    val connectionInfo: ConnectionInfo? = null,
    val isNotifying: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

data class ConnectionInfo(
    val address: String,
    val mtu: Int = 512,
    val rssi: Int,
    val notifyIntervalMs: Int = 2000
)

class DeviceInfoViewModel(
    private val deviceInfoRepoFactory: DeviceInfoRepositoryImpl.Factory,
    private val bleRepoFactory: BleRepositoryImpl.Factory,
    private val dispatchers: DispatcherProvider,
    private val deviceAddress: String
) : ViewModel() {

    private val deviceInfoRepo  = deviceInfoRepoFactory.create(deviceAddress)
    private val bleRepo         = bleRepoFactory.create(deviceAddress)

    private val readDeviceInfo          = ReadDeviceInfoUseCase(deviceInfoRepo)
    private val controlNotifications    = ControlNotificationsUseCase(bleRepo)

    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatchers.io) {
            runCatching { readDeviceInfo() }
                .onSuccess { info ->
                    _uiState.update { it.copy(deviceInfo = info, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun toggleNotifications() {
        viewModelScope.launch {
            if (_uiState.value.isNotifying) {
                controlNotifications.stop()
                _uiState.update { it.copy(isNotifying = false) }
            } else {
                controlNotifications.start()
                _uiState.update { it.copy(isNotifying = true) }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch { bleRepo.disconnect() }
    }

    class Factory(
        private val deviceInfoRepoFactory: DeviceInfoRepositoryImpl.Factory,
        private val bleRepoFactory: BleRepositoryImpl.Factory,
        private val dispatchers: DispatcherProvider
    ) {
        fun create(deviceAddress: String) =
            DeviceInfoViewModel(deviceInfoRepoFactory, bleRepoFactory, dispatchers, deviceAddress)
    }
}
```

### 10.3 BleConnectionViewModel

```kotlin
// ui/connection/BleConnectionViewModel.kt

data class ScanUiState(
    val devices: List<ScannedDevice> = emptyList(),
    val isScanning: Boolean = false,
    val bluetoothEnabled: Boolean = true,
    val connectingAddress: String? = null,   // which row shows a progress indicator
    val error: ScanError? = null,
    val connectionState: BleState = BleState.Idle
)

sealed class ScanError {
    object BluetoothDisabled : ScanError()
    object PermissionDenied  : ScanError()
    data class ScanFailed(val code: Int) : ScanError()
}

class BleConnectionViewModel(
    private val scanForDevices: ScanForDevicesUseCase,
    private val bleRepository: IBleRepository,
    private val scanRepository: IScanRepository,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanUiState())
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Mirror BLE connection state and clear connecting spinner on resolution
        viewModelScope.launch {
            bleRepository.connectionState.collect { bleState ->
                _scanState.update { it.copy(connectionState = bleState) }
                if (bleState is BleState.Connected || bleState is BleState.Error) {
                    _scanState.update { it.copy(connectingAddress = null) }
                }
            }
        }
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        viewModelScope.launch {
            if (!scanRepository.isBluetoothEnabled()) {
                _scanState.update { it.copy(bluetoothEnabled = false, error = ScanError.BluetoothDisabled) }
                return@launch
            }
            scanJob = launch(dispatchers.io) {
                _scanState.update { it.copy(isScanning = true, error = null, devices = emptyList()) }
                scanForDevices()
                    .catch { e ->
                        val error = when (e) {
                            is ScanFailedException -> ScanError.ScanFailed(e.errorCode)
                            is SecurityException   -> ScanError.PermissionDenied
                            else                   -> ScanError.ScanFailed(-1)
                        }
                        _scanState.update { it.copy(error = error, isScanning = false) }
                    }
                    .collect { devices ->
                        _scanState.update { it.copy(devices = devices) }
                    }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.update { it.copy(isScanning = false) }
    }

    /**
     * Initiates a GATT connection to the given device address.
     * Sets connectingAddress to show a per-row progress indicator in the UI.
     * Stops the scan first (only one GATT operation at a time).
     */
    fun connect(address: String) {
        viewModelScope.launch(dispatchers.io) {
            _scanState.update { it.copy(connectingAddress = address) }
            stopScan()
            bleRepository.connect(address)
        }
    }

    override fun onCleared() { stopScan() }

    class Factory(
        private val scanForDevices: ScanForDevicesUseCase,
        private val bleRepoFactory: BleRepositoryImpl.Factory,
        private val scanRepository: IScanRepository,
        private val dispatchers: DispatcherProvider
    ) {
        fun create(deviceAddress: String) = BleConnectionViewModel(
            scanForDevices, bleRepoFactory.create(deviceAddress), scanRepository, dispatchers
        )
    }
}
```

---

## 11. UI Layer — Screens & Composables

### 11.1 MainActivity

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BleDevTheme {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = { BleBottomNavBar(navController) }
                ) { padding ->
                    AppNavGraph(navController = navController, modifier = Modifier.padding(padding))
                }
            }
        }
    }
}
```

### 11.2 Sensor Dashboard

```kotlin
// ui/sensor/SensorScreen.kt
@Composable
fun SensorScreen(
    navController: NavController,
    viewModel: SensorViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showScanSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BlePermissionHandler(onPermissionsGranted = { /* ViewModel already listens to connectionState */ })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ESP32 sensor") },
                navigationIcon = {
                    Icon(Icons.Outlined.Bluetooth, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                },
                actions = {
                    BleStatusChip(
                        state = uiState.connectionState,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    if (uiState.connectionState !is BleState.Connected) {
                        IconButton(onClick = { showScanSheet = true }) {
                            Icon(Icons.Outlined.BluetoothSearching, contentDescription = "Scan")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.connectionState is BleState.Connected) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::refreshOnce,
                    icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    text = { Text("Refresh") }
                )
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingPlaceholder(Modifier.padding(padding))
            uiState.error != null -> ErrorCard(uiState.error!!, Modifier.padding(padding))
            else -> SensorContent(
                uiState = uiState,
                modifier = Modifier.padding(padding),
                onStopNotifications = viewModel::stopNotifications
            )
        }
    }

    if (showScanSheet) {
        ScanBottomSheet(sheetState = sheetState, onDismiss = { showScanSheet = false })
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun SensorContent(
    uiState: SensorUiState,
    modifier: Modifier,
    onStopNotifications: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SensorMetricCard(
                label = "Temperature",
                value = uiState.reading?.temperatureCelsius?.let { "%.1f".format(it) } ?: "—",
                unit = "°C",
                history = uiState.history.map { it.temperatureCelsius.toFloat() },
                rangeMin = 20f, rangeMax = 35f,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
        item {
            SensorMetricCard(
                label = "Humidity",
                value = uiState.reading?.humidityPercent?.let { "%.1f".format(it) } ?: "—",
                unit = "% RH",
                history = uiState.history.map { it.humidityPercent.toFloat() },
                rangeMin = 40f, rangeMax = 90f,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }
        if (uiState.isNotifying) {
            item { NotifyingSnackbar(onStop = onStopNotifications) }
        }
    }
}

// ── SensorMetricCard ──────────────────────────────────────────────────────────

/** Displays a single sensor metric with value, unit, and a 24-point history chart. */
@Composable
fun SensorMetricCard(
    label: String,
    value: String,
    unit: String,
    history: List<Float>,
    rangeMin: Float,
    rangeMax: Float,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (history.size > 1) {
                Spacer(Modifier.height(8.dp))
                MiniLineChart(dataPoints = history, modifier = Modifier.fillMaxWidth().height(40.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$rangeMin min", style = MaterialTheme.typography.labelSmall)
                Text("$rangeMax max", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── MiniLineChart ─────────────────────────────────────────────────────────────

/** Custom Canvas composable — no third-party charting dependency. */
@Composable
fun MiniLineChart(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    val points = remember(dataPoints) { dataPoints.takeLast(24) }
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val range = (max - min).coerceAtLeast(0.1f)
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
```

### 11.3 Device Info Screen

```kotlin
// ui/deviceinfo/DeviceInfoScreen.kt
@Composable
fun DeviceInfoScreen(
    navController: NavController,
    viewModel: DeviceInfoViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device info") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            item { DisInfoCard(info = uiState.deviceInfo) }
            item { ConnectionCard(info = uiState.connectionInfo) }
            item {
                NotificationsToggleCard(
                    enabled = uiState.isNotifying,
                    onToggle = viewModel::toggleNotifications
                )
            }
            item {
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}

// ── DisInfoCard ───────────────────────────────────────────────────────────────

/** Renders the four DIS string characteristics in a labelled table. */
@Composable
fun DisInfoCard(info: DeviceInfo?, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text("Device information service", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            if (info == null) {
                repeat(4) { ShimmerRow() }
            } else {
                DisRow("Manufacturer", info.manufacturerName)
                DisRow("Model",        info.modelNumber)
                DisRow("Firmware",     info.firmwareRevision)
                DisRow("Hardware",     info.hardwareRevision)
            }
        }
    }
}

@Composable
private fun DisRow(key: String, value: String) {
    HorizontalDivider(thickness = 0.5.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
```

### 11.4 Scan Bottom Sheet

```kotlin
// ui/connection/ScanBottomSheet.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    viewModel: BleConnectionViewModel
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.startScan() }
    DisposableEffect(Unit) { onDispose { viewModel.stopScan() } }

    // Auto-close sheet when a connection is established
    LaunchedEffect(scanState.connectionState) {
        if (scanState.connectionState is BleState.Connected) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.stopScan(); onDismiss() },
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        ScanSheetContent(
            state = scanState,
            onConnect = viewModel::connect,
            onRetry = viewModel::startScan,
            onDismiss = { viewModel.stopScan(); onDismiss() }
        )
    }
}

@Composable
private fun ScanSheetContent(
    state: ScanUiState,
    onConnect: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Nearby devices", style = MaterialTheme.typography.titleMedium)
            BleStatusChip(state = state.connectionState, isScanning = state.isScanning)
        }

        AnimatedVisibility(visible = state.error != null) {
            state.error?.let { ScanErrorBanner(error = it, onRetry = onRetry) }
        }

        when {
            !state.bluetoothEnabled          -> BluetoothDisabledCard()
            state.isScanning && state.devices.isEmpty() -> ScanningPlaceholder()
            state.devices.isEmpty()          -> NoDevicesFound(onRetry = onRetry)
            else -> DeviceList(
                devices = state.devices,
                connectingAddress = state.connectingAddress,
                onConnect = onConnect
            )
        }
    }
}

// ── DeviceList ────────────────────────────────────────────────────────────────

@Composable
private fun DeviceList(
    devices: List<ScannedDevice>,
    connectingAddress: String?,
    onConnect: (String) -> Unit
) {
    val (knownDevices, otherDevices) = devices.partition { it.isKnownEsp32 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (knownDevices.isNotEmpty()) {
            ScanSectionLabel("ESP32 devices")
            knownDevices.forEach { device ->
                DeviceScanItem(
                    device = device,
                    isConnecting = connectingAddress == device.address,
                    onConnect = { onConnect(device.address) }
                )
            }
        }
        if (otherDevices.isNotEmpty()) {
            ScanSectionLabel("Other nearby devices")
            otherDevices.forEach { device ->
                DeviceScanItem(
                    device = device,
                    isConnecting = connectingAddress == device.address,
                    onConnect = { onConnect(device.address) }
                )
            }
        }
    }
}

@Composable
private fun ScanSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ── DeviceScanItem ────────────────────────────────────────────────────────────

/**
 * Single row in the scan list.
 * Known ESP32 devices get a highlighted primary border.
 * Connecting state replaces the button with a CircularProgressIndicator via AnimatedContent.
 */
@Composable
fun DeviceScanItem(
    device: ScannedDevice,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        border = if (device.isKnownEsp32)
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else
            CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DeviceIconBadge(isKnown = device.isKnownEsp32)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown device",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (device.isKnownEsp32) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RssiIndicator(quality = device.signalQuality)

            AnimatedContent(targetState = isConnecting, label = "connect_btn_${device.address}") { connecting ->
                if (connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    FilledTonalButton(
                        onClick = onConnect,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Connect", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceIconBadge(isKnown: Boolean) {
    val containerColor = if (isKnown) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surfaceVariant
    val iconTint = if (isKnown) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.size(40.dp).background(containerColor, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

// ── Empty / error states ──────────────────────────────────────────────────────

@Composable
fun ScanningPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
        Text("Scanning for BLE devices…", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NoDevicesFound(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.BluetoothSearching, contentDescription = null,
            modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("No devices found", style = MaterialTheme.typography.titleSmall)
        Text(
            "Make sure the ESP32 is powered on and within range.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Scan again")
        }
    }
}

@Composable
fun BluetoothDisabledCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.BluetoothDisabled, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer)
            Column {
                Text("Bluetooth is off", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Enable Bluetooth to scan for devices.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
fun ScanErrorBanner(error: ScanError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val message = when (error) {
        is ScanError.BluetoothDisabled -> "Bluetooth is disabled"
        is ScanError.PermissionDenied  -> "Bluetooth permission denied"
        is ScanError.ScanFailed        -> "Scan failed (code ${error.code})"
    }
    Snackbar(
        modifier = modifier,
        action = { TextButton(onClick = onRetry) { Text("Retry") } },
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionContentColor = MaterialTheme.colorScheme.inversePrimary
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}
```

### 11.5 Shared Components

```kotlin
// ui/common/components/BleStatusChip.kt

/**
 * Status pill shown in TopAppBar and ScanBottomSheet header.
 * isScanning takes visual priority over BleState.Idle.
 * Connecting and Scanning states show a mini CircularProgressIndicator instead of a dot.
 */
@Composable
fun BleStatusChip(
    state: BleState,
    isScanning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val label = when {
        isScanning                    -> "Scanning…"
        state is BleState.Connected   -> "Connected"
        state is BleState.Connecting  -> "Connecting…"
        state is BleState.Error       -> "Error"
        else                          -> "Disconnected"
    }
    val (containerColor, contentColor) = when {
        isScanning                    -> Color(0xFFFAEEDA) to Color(0xFF633806)
        state is BleState.Connected   -> Color(0xFFEAF3DE) to Color(0xFF27500A)
        state is BleState.Connecting  -> Color(0xFFE6F1FB) to Color(0xFF0C447C)
        state is BleState.Error       -> Color(0xFFFCEBEB) to Color(0xFF791F1F)
        else                          -> Color(0xFFF1EFE8) to Color(0xFF5F5E5A)
    }
    Surface(modifier = modifier, shape = CircleShape, color = containerColor) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isScanning || state is BleState.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(8.dp), color = contentColor, strokeWidth = 1.5.dp)
            } else {
                Canvas(Modifier.size(6.dp)) { drawCircle(contentColor) }
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

// ui/connection/components/RssiIndicator.kt

/**
 * Four animated signal bars. Bar height and color reflect SignalQuality.
 *   Excellent (≥ -60 dBm) → 4 bars, primary color
 *   Good      (≥ -75 dBm) → 3 bars, primary color
 *   Fair      (≥ -90 dBm) → 2 bars, tertiary color
 *   Poor      (< -90 dBm) → 1 bar,  error color
 */
@Composable
fun RssiIndicator(quality: SignalQuality, modifier: Modifier = Modifier) {
    val filledBars = when (quality) {
        SignalQuality.Excellent -> 4
        SignalQuality.Good      -> 3
        SignalQuality.Fair      -> 2
        SignalQuality.Poor      -> 1
    }
    val activeColor = when (quality) {
        SignalQuality.Excellent, SignalQuality.Good -> MaterialTheme.colorScheme.primary
        SignalQuality.Fair                          -> MaterialTheme.colorScheme.tertiary
        SignalQuality.Poor                          -> MaterialTheme.colorScheme.error
    }
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier.semantics { contentDescription = "Signal: ${quality.name}" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(5.dp, 8.dp, 11.dp, 14.dp).forEachIndexed { index, height ->
            val color by animateColorAsState(
                targetValue = if (index < filledBars) activeColor else inactiveColor,
                animationSpec = tween(300),
                label = "bar_$index"
            )
            Box(modifier = Modifier.width(3.dp).height(height).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}
```

---

## 12. BLE Permission Handling

```kotlin
// ui/common/BlePermissionHandler.kt

/**
 * Requests BLE permissions on first composition.
 * Android 12+ requires BLUETOOTH_SCAN + BLUETOOTH_CONNECT.
 * Pre-12 requires BLUETOOTH + ACCESS_FINE_LOCATION (for scan).
 * onPermissionsGranted is called only when ALL permissions are granted.
 */
@Composable
fun BlePermissionHandler(onPermissionsGranted: () -> Unit) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) onPermissionsGranted()
    }

    LaunchedEffect(Unit) { launcher.launch(permissions.toTypedArray()) }
}
```

**`AndroidManifest.xml` additions:**
```xml
<!-- BLE hardware requirement -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />

<!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Pre-12 fallback -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
```

---

## 13. Testability Strategy

### Testable dispatcher injection

```kotlin
// core/dispatcher/DispatcherProvider.kt
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main    = Dispatchers.Main
    override val io      = Dispatchers.IO
    override val default = Dispatchers.Default
}

// In tests
class TestDispatcherProvider(val testDispatcher: TestCoroutineDispatcher) : DispatcherProvider {
    override val main    = testDispatcher
    override val io      = testDispatcher
    override val default = testDispatcher
}
```

### Test fakes

```kotlin
class FakeScanRepository : IScanRepository {
    private val flow = MutableSharedFlow<List<ScannedDevice>>()
    suspend fun emit(devices: List<ScannedDevice>) = flow.emit(devices)
    override fun scanForDevices(): Flow<List<ScannedDevice>> = flow
    override suspend fun isBluetoothEnabled() = true
}

class FakeBleRepository : IBleRepository {
    var connectWasCalled = false
    private val state = MutableStateFlow<BleState>(BleState.Idle)
    override val connectionState: StateFlow<BleState> = state.asStateFlow()
    override suspend fun connect(address: String) { connectWasCalled = true }
    override suspend fun startNotifications() {}
    override suspend fun stopNotifications() {}
    override suspend fun disconnect() {}
    suspend fun emitState(s: BleState) = state.emit(s)
}
```

### GattDecoder — unit tests

```kotlin
class GattDecoderTest {
    private val decoder = GattDecoder()

    @Test fun `decodes 23_5 celsius correctly`() =
        assertEquals(23.5, decoder.decodeTemperature(byteArrayOf(0xEB.toByte(), 0x00)), 0.001)

    @Test fun `decodes negative temperature`() =
        assertEquals(-1.6, decoder.decodeTemperature(byteArrayOf(0xF0.toByte(), 0xFF.toByte())), 0.001)

    @Test fun `decodes humidity correctly`() =
        assertEquals(55.0, decoder.decodeHumidity(byteArrayOf(0x26, 0x02)), 0.001)

    @Test fun `decodes UTF-8 manufacturer name`() =
        assertEquals("YourCompany", decoder.decodeString(
            byteArrayOf(0x59, 0x6F, 0x75, 0x72, 0x43, 0x6F, 0x6D, 0x70, 0x61, 0x6E, 0x79)))
}
```

### GattEncoder — unit tests

```kotlin
class GattEncoderTest {
    private val encoder = GattEncoder()

    @Test fun `encodeControl start produces 01 00`() =
        assertArrayEquals(byteArrayOf(0x01, 0x00), encoder.encodeControl(true))

    @Test fun `encodeControl stop produces 00 00`() =
        assertArrayEquals(byteArrayOf(0x00, 0x00), encoder.encodeControl(false))
}
```

### BleConnectionViewModel — unit tests

```kotlin
@ExperimentalCoroutinesApi
class BleConnectionViewModelTest {
    @get:Rule val coroutineRule = MainCoroutineRule()

    private val fakeScanRepo = FakeScanRepository()
    private val fakeBleRepo  = FakeBleRepository()
    private val useCase      = ScanForDevicesUseCase(fakeScanRepo)
    private lateinit var viewModel: BleConnectionViewModel

    @Before fun setup() {
        viewModel = BleConnectionViewModel(
            useCase, fakeBleRepo, fakeScanRepo,
            TestDispatcherProvider(coroutineRule.testDispatcher)
        )
    }

    @Test fun `startScan emits devices`() = runTest {
        val device = ScannedDevice("ESP32-BLE-Dev", "AA:BB:CC:DD:EE:FF", -60, true)
        fakeScanRepo.emit(listOf(device))
        viewModel.startScan()
        advanceUntilIdle()
        assertEquals(1, viewModel.scanState.value.devices.size)
        assertTrue(viewModel.scanState.value.isScanning)
    }

    @Test fun `connect sets connectingAddress`() = runTest {
        viewModel.connect("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()
        assertEquals("AA:BB:CC:DD:EE:FF", viewModel.scanState.value.connectingAddress)
        assertTrue(fakeBleRepo.connectWasCalled)
    }

    @Test fun `connectingAddress cleared on BleState Connected`() = runTest {
        viewModel.connect("AA:BB:CC:DD:EE:FF")
        fakeBleRepo.emitState(BleState.Connected("AA:BB:CC:DD:EE:FF"))
        advanceUntilIdle()
        assertNull(viewModel.scanState.value.connectingAddress)
    }
}
```

### Test coverage matrix

| Component | Test type | Strategy |
|---|---|---|
| `GattDecoder` | Unit | Raw byte arrays; covers LE, sign extension, UTF-8 |
| `GattEncoder` | Unit | Assert exact output bytes |
| `SensorRepositoryImpl` | Unit | `FakeBleDeviceSession` injected directly into constructor; factory creates instance with fake session |
| `ScanRepositoryImpl` | Unit | `FakeBleSessionManager` with stubbed `scannedDevices` StateFlow |
| `ScanForDevicesUseCase` | Unit | `FakeScanRepository` |
| `BleConnectionViewModel` | Unit | `FakeScanRepository` + `FakeBleRepository` |
| `SensorViewModel` | Unit | `TestDispatcherProvider`, fake factories, assert `StateFlow` |
| `SensorScreen` | Compose UI | `ComposeTestRule`, fake ViewModel |
| `DeviceScanItem` | Compose UI | Screenshot test (Paparazzi) |
| `BleDeviceSession` | Integration | Emulated / real device |
| `BleSessionManager` | Integration | Multi-device connect + auto-remove on disconnect |

---

## 14. Key Engineering Decisions

| Decision | Rationale |
|---|---|
| `callbackFlow` for scan & GATT | Bridges Android callback APIs into structured concurrency. Cancellation maps cleanly to `stopScan()` / `gatt.close()` via `awaitClose`. |
| `GattDecoder` is an injectable class | Isolated byte-level protocol logic. No static state. Fully mockable. Tested without any Android dependency. |
| `IBleRepository` interface boundary | Domain layer has zero knowledge of `BluetoothGatt`. Swap to a fake BLE implementation for CI on machines without Bluetooth hardware. |
| `MutableSharedFlow` with `extraBufferCapacity = 32` | BLE callback thread fires at 500 ms; UI collects at its own pace. Buffer prevents notification drops without blocking the BLE thread. |
| `ScanFilter` on `SENSOR_SERVICE` UUID | Only ESP32 peripherals appear in the list. Avoids noise in dense BLE environments. Remove filter during development to see all peripherals. |
| `conflate()` on scan Flow | Drops stale device-list emissions when the UI is slow to recompose, preventing a backlog of list rebuilds. |
| `collectAsStateWithLifecycle()` | Stops Flow collection when the app is backgrounded. Saves battery and prevents unnecessary GATT communication. |
| `ModalBottomSheet` + `skipPartiallyExpanded` | Jumps straight to full height on first device discovery — avoids a half-sheet with an empty list. |
| `AnimatedContent` on connect button | Per-row progress indicator without extra state variables. Address is the animation key, so each row animates independently. |
| `BleDeviceSession` per connection (not singleton) | Each connected device gets its own `BluetoothGatt` handle, `StateFlow`, and `SharedFlow`. Independent lifecycle — one device disconnecting does not affect others. Supports up to 3 simultaneous ESP32 clients per protocol spec. |
| `BleSessionManager` held by `AppContainer` as registry | Single source of truth for all active sessions. Auto-removes a session via a `StateFlow` observer when its state transitions to `Idle` or `Error` — no manual cleanup required from callers. `disconnectAll()` covers app exit and Bluetooth-off events. |
| Nested `Factory` classes for repositories and ViewModels | Allows per-device scoping without any DI framework. `AppContainer` holds the factories; callers call `.create(deviceAddress)` to get an instance bound to one device. Plain Kotlin — no annotation processing required. |
| `DisposableEffect` in `ScanBottomSheet` | Guarantees `stopScan()` fires even if the sheet is dismissed by back-gesture, predictive back, or system interrupt. |
| `ArrayDeque` rolling buffer in `SensorViewModel` | O(1) add/remove for the 24-point chart history. No external dependency. |
| `rememberSaveable` for `showScanSheet` | Sheet visibility survives configuration change (rotation). Avoids re-triggering scan after rotation. |
| `enableEdgeToEdge()` + `navigationBarsPadding()` | Correct gesture navigation support for Android 15+. |
