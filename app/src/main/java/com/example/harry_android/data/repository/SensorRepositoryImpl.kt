package com.example.harry_android.data.repository

import com.example.harry_android.data.ble.BleSessionManager
import com.example.harry_android.data.ble.GattDecoder
import com.example.harry_android.data.ble.GattUuid
import com.example.harry_android.domain.model.DeviceInfo
import com.example.harry_android.domain.model.SensorReading
import com.example.harry_android.domain.repository.ISensorRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

class SensorRepositoryImpl @AssistedInject constructor(
    private val sessionManager: BleSessionManager,
    private val decoder: GattDecoder,
    @Assisted private val address: String
) : ISensorRepository {

    private val session = sessionManager.getOrCreate(address)

    override fun observeSensorData(): Flow<SensorReading> {
        var latestTemp: Double? = null
        var latestHumidity: Double? = null

        return session.notificationChannel.mapNotNull { notification ->
            when (notification.uuid) {
                GattUuid.TEMPERATURE -> latestTemp = decoder.decodeTemperature(notification.bytes)
                GattUuid.HUMIDITY -> latestHumidity = decoder.decodeHumidity(notification.bytes)
            }

            val t = latestTemp ?: return@mapNotNull null
            val h = latestHumidity ?: return@mapNotNull null

            SensorReading(t, h, System.currentTimeMillis())
        }.distinctUntilChanged()
    }

    override suspend fun readDeviceInfo(): DeviceInfo {
        TODO("Implement one-shot GATT read via BleDeviceSession.readCharacteristic()")
    }

    @AssistedFactory
    interface Factory {
        fun create(address: String): SensorRepositoryImpl
    }
}