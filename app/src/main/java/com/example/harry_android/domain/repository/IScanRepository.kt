package com.example.harry_android.domain.repository

import com.example.harry_android.domain.model.ScannedDevice
import kotlinx.coroutines.flow.Flow

interface IScanRepository {
    fun scanForDevices(): Flow<List<ScannedDevice>>
    suspend fun isBluetoothEnabled(): Boolean

    suspend fun startScan(): Unit
    suspend fun stopScan(): Unit
}