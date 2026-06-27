package com.example.harry_android.data.repository

import android.util.Log
import com.example.harry_android.data.ble.BleSessionManager
import com.example.harry_android.data.ble.GattDecoder
import com.example.harry_android.data.ble.GattUuid
import com.example.harry_android.data.ble.decodeTemperature
import com.example.harry_android.domain.model.DeviceInfo
import com.example.harry_android.domain.model.SensorReading
import com.example.harry_android.domain.repository.ISensorRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

private const val TAG = "HarrySensor"

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
                GattUuid.TEMPERATURE -> {
                    //val temp = decoder.decodeTemperature(notification.bytes)
                    val temp = notification.bytes.decodeTemperature()
                    Log.d(TAG, "Temperature decoded: $temp °C")
                    latestTemp = temp
                }
                GattUuid.HUMIDITY -> {
                    val humidity = decoder.decodeHumidity(notification.bytes)
                    Log.d(TAG, "Humidity decoded: $humidity %RH")
                    latestHumidity = humidity
                }
            }

            val t = latestTemp ?: return@mapNotNull null
            val h = latestHumidity ?: return@mapNotNull null

            val reading = SensorReading(t, h, System.currentTimeMillis())
            Log.i(TAG, "SensorReading emitted: temp=${reading.temperature}°C  humidity=${reading.humidity}%RH")
            reading
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