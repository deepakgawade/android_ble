package com.example.harry_android.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.harry_android.core.dispatcher.DispatcherProvider
import com.example.harry_android.domain.model.BleState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class BleDeviceSession(
    private val context: Context,
    val address: String,
    private val decoder: GattDecoder,
private val encoder: GattEncoder,
    private val dispatchers: DispatcherProvider
    ) {

    private val _connectionState = MutableStateFlow<BleState>(BleState.Idle)

    val connectionState: StateFlow<BleState> = _connectionState.asStateFlow()


    /**
     * SharedFlow that bridges BluetoothGattCallback notifications into
     * the repository layer. extraBufferCapacity prevents dropped notifications
     * when the BLE thread fires faster than the collector.
     */
    private  val _notificationChannel = MutableSharedFlow<GattNotification>(
        replay = 0, extraBufferCapacity = 32
    )
    val notificationChannel: SharedFlow<GattNotification> = _notificationChannel.asSharedFlow()

    private var gatt: BluetoothGatt? = null

    /**
     * Completes when onServicesDiscovered fires with GATT_SUCCESS.
     * Reset on each connect() so re-connections get a fresh gate.
     * startNotifications() and readCharacteristic() await this before
     * touching any characteristic — discoverServices() is async and
     * characteristics return null until it finishes.
     */
    private var servicesDiscovered = CompletableDeferred<Unit>()

    //-----Connection------------

    @androidx.annotation.RequiresPermission(
        android.Manifest.permission.BLUETOOTH_CONNECT
    )
    suspend fun connect() =  withContext(dispatchers.io){
        servicesDiscovered = CompletableDeferred() //reset gate for this connection
        _connectionState.value = BleState.Connecting
        val device = context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(address)
        gatt = device.connectGatt(context,false,gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @androidx.annotation.RequiresPermission(
        android.Manifest.permission.BLUETOOTH_CONNECT
    )
    suspend  fun disconnect() = withContext(dispatchers.io)  {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = BleState.Idle
    }

    ///---------- Notification --------
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun startNotifications() = withContext(dispatchers.io){
        servicesDiscovered.await()

        val g = gatt ?: return@withContext

        listOf(GattUuid.TEMPERATURE, GattUuid.HUMIDITY).forEach { uUID -> g.getCharacteristic(
            GattUuid.SENSOR_SERVICE, uUID)?.let  { characteristic ->  g.setCharacteristicNotification(characteristic, true)
            characteristic.getDescriptor(GattUuid.CCCD)?.let { cccd ->

                g.writeDescriptor(cccd,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE ) // we must check for the os version before running this
            }} }
        g.getCharacteristic(GattUuid.SENSOR_SERVICE, GattUuid.CONTROL)?.let{ctrl ->

            g.writeCharacteristic(ctrl, encoder.encodeControl(start = true),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        }

    }


    ///-----------------Read/ Write---------------
    /**
     * One-shot GATT read. Awaits service discovery, then suspends until
     * onCharacteristicRead fires. Used by DeviceInfoRepositoryImpl to read
     * DIS string characteristics.
     */


    suspend fun readCharacteristic(service: UUID, char:UUID): ByteArray?{
        servicesDiscovered.await()
        TODO("Implement one-shot GATT read via suspendCancellableCoroutine")
        //return byteArrayOf(0x48, 0x65, 0x6C)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun writeCharacteristic(service: UUID, char: UUID, value: ByteArray) = withContext(dispatchers.io){
        servicesDiscovered.await()
        gatt?.getCharacteristic(service, char)?.let{
            characteristic ->
            gatt?.writeCharacteristic(characteristic,value,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        }
    }


    ///-----GATT CALLBACK-----------
    /// this call back will hold all the events regarding connection state, service discovery, read/writes
    private val gattCallback = object: BluetoothGattCallback(){
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when(newState){
                BluetoothProfile.STATE_CONNECTED ->{
                    _connectionState.value = BleState.Connected(gatt.device.address)
                    gatt.discoverServices() //on every connect serviceDiscovery will takes place.
                }

                BluetoothProfile.STATE_DISCONNECTED->{
                    _connectionState.value = BleState.Idle
                    gatt.close()
                }
            }

            if(status != BluetoothGatt.GATT_SUCCESS){
                _connectionState.value = BleState.Error("GATT error: status $status")
            }


            super.onConnectionStateChange(gatt, status, newState)
        }

        /**
         * Fires after discoverServices() completes.
         * Completes the servicesDiscovered gate so startNotifications() and
         * readCharacteristic() can proceed. On failure, completes exceptionally
         * so callers receive the error through the coroutine cancellation path.
         */
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {

            if(status == BluetoothGatt.GATT_SUCCESS){
                servicesDiscovered.complete(Unit)
            }else{
                val error = IllegalStateException("Service discovery failed: status $status")
                servicesDiscovered.completeExceptionally(error)
                _connectionState.value = BleState.Error("Service discovery failed: status $status")
            }
            super.onServicesDiscovered(gatt, status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            _notificationChannel.tryEmit(GattNotification(characteristic.uuid, value))
            super.onCharacteristicChanged(gatt, characteristic, value)
        }

    }

}
data class GattNotification(val uuid: UUID, val bytes: ByteArray)

private fun BluetoothGatt.getCharacteristic(service:UUID, char: UUID): BluetoothGattCharacteristic? = getService(service)?.getCharacteristic(char)