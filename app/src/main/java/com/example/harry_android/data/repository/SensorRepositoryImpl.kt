package com.example.harry_android.data.repository

import com.example.harry_android.data.ble.BleDeviceSession
import com.example.harry_android.data.ble.GattDecoder
import com.example.harry_android.data.ble.GattUuid
import com.example.harry_android.domain.model.DeviceInfo
import com.example.harry_android.domain.model.SensorReading
import com.example.harry_android.domain.repository.ISensorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlin.time.Clock

class SensorRepositoryImpl(
    private val session: BleDeviceSession,
    private val decoder: GattDecoder
): ISensorRepository {
    override fun observeSensorData(): Flow<SensorReading> {
        var latestTemp:Double? = null
        var latestHumidity: Double? = null

        return session.notificationChannel.mapNotNull { notification ->
            when (notification.uuid){
                GattUuid.TEMPERATURE ->
                    latestTemp = decoder.decodeTemperature(notification.bytes)

                GattUuid.HUMIDITY ->
                    latestHumidity = decoder.decodeHumidity(notification.bytes)
            }

            val t =latestTemp?:return@mapNotNull null
            val h =latestHumidity?:return@mapNotNull null

            SensorReading(t,h, System.currentTimeMillis())
        }
            .distinctUntilChanged()
    }

    override suspend fun readDeviceInfo(): DeviceInfo {
        TODO("Implement one-shot GATT read via BleDeviceSession.readCharacteristic()")

    }

    class Factory(private val decoder: GattDecoder){
        fun create(session: BleDeviceSession) = SensorRepositoryImpl(session, decoder)
    }


}