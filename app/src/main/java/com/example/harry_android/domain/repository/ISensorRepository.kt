package com.example.harry_android.domain.repository

import com.example.harry_android.domain.model.DeviceInfo
import com.example.harry_android.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow

interface ISensorRepository {
    /** Hot Flow; collecting starts GATT notifications. Cancellation stops them. */
    fun observeSensorData(): Flow<SensorReading>
    suspend fun readDeviceInfo(): DeviceInfo
}

