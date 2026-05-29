package com.example.harry_android.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.util.Log
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.harry_android.core.dispatcher.DispatcherProvider
import com.example.harry_android.domain.model.BleState
import com.example.harry_android.domain.model.ScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.emptyMap

class BleSessionManager(
private  val context: Context,
    private val decoder: GattDecoder,
    private val encoder: GattEncoder,
    private val dispatchers: DispatcherProvider
) {

    private val bluetoothAdapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter


    private val minRssiThreshold = -60

    //---Scan State-----
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()


    //temp scan devices adn collect the devices
    private val discovered = mutableMapOf<String, ScannedDevice>()
    private var scanCallback: ScanCallback? = null

    //----Session Registry----
    private  val _sessions = MutableStateFlow<Map<String, BleDeviceSession>>(emptyMap())// Streamcontroller or BehaviouSubject with seeded <T>
    val session: StateFlow<Map<String, BleDeviceSession>> = _sessions.asStateFlow() //stream

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)// this will be used to manage the MutableStateflow and StateFlow , after application is closed it will clean all subscription and streams

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun startScan(){
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return//short hand for if(data!=nul)
        //{data.call()}else{return null}
        discovered.clear()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE).build()

        scanCallback = object: ScanCallback(){
            @RequiresApi(Build.VERSION_CODES.R)
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.rssi?.let { if (it < minRssiThreshold) return }

               result?.toScannedDevice()?.let{device ->
                   discovered[device.address] = device
               }

                _scannedDevices.value = discovered.values.sortedWith ( compareByDescending<ScannedDevice> { it.name  }.thenByDescending {
                    it.rssi
                } )


            }

            override fun onScanFailed(errorCode: Int) {
                _scannedDevices.value = emptyList()
            }

        }
            .also { scanner.startScan(null, settings, it) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan(){
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanCallback = null
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private fun ScanResult.toScannedDevice(): ScannedDevice{
        val deviceNameFromDevice = device.name?.takeIf { it.isNotBlank() }
        val deviceNameFromRecord = scanRecord?.deviceName?.takeIf { it.isNotBlank() }
        val name = deviceNameFromDevice ?: deviceNameFromRecord ?: parseNameFromRawBytes(scanRecord?.bytes)

        if (name == null) {
            Log.d("BleSessionManager", "Unknown device — address=${device.address}, rssi=$rssi, device.name=${device.name}, scanRecord.deviceName=${scanRecord?.deviceName}")
        } else {
            Log.d("BleSessionManager", "Resolved name from raw bytes: $name, address=${device.address}")
        }

        return ScannedDevice(
            name = name,
            address = device.address,
            rssi = rssi,
        )
    }

    private fun parseNameFromRawBytes(bytes: ByteArray?): String? {
        if (bytes == null) return null
        var i = 0
        while (i < bytes.size) {
            val len = bytes[i].toInt() and 0xFF
            if (len == 0 || i + len >= bytes.size) break
            val type = bytes[i + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) { // Shortened or Complete Local Name
                return String(bytes, i + 2, len - 1, Charsets.UTF_8).takeIf { it.isNotBlank() }
            }
            i += len + 1
        }
        return null
    }
    /**
     * Aggregated connection state across all active sessions.
     * Reacts dynamically — each time the session map changes, the inner
     * combine() is rebuilt to track the new set of devices.
     */
    val allConnectionState: StateFlow<Map<String, BleState>> = _sessions.flatMapLatest {
        sessionMap ->
        if(sessionMap.isEmpty()) flowOf(emptyMap())
        else combine(sessionMap.map{(addr, session)-> session.connectionState.map{state -> addr to state}})
        {pairs -> pairs.toMap()
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun getOrCreate(address: String): BleDeviceSession{
        _sessions.value[address]?.let{ return it }

        val session = BleDeviceSession(context, address, decoder,encoder, dispatchers)

        scope.launch {

            session.connectionState.collect { state ->
                if(state is BleState.Idle || state is BleState.Error){
                    _sessions.update { it - address }
                }

            }
        }
        _sessions.update { it + (address to session) }
        return session
    }

    fun remove(address: String){
        _sessions.update { it - address }
    }
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun disconnectAll(){
        _sessions.value.values.forEach { it.disconnect() }
    }

}