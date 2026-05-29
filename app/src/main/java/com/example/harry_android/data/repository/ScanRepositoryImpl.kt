package com.example.harry_android.data.repository

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.internal.isLiveLiteralsEnabled
import com.example.harry_android.data.ble.BleDeviceSession
import com.example.harry_android.data.ble.BleSessionManager
import com.example.harry_android.domain.model.ScannedDevice
import com.example.harry_android.domain.repository.IScanRepository
import kotlinx.coroutines.flow.Flow

class ScanRepositoryImpl(
    private val sessionManager: BleSessionManager
): IScanRepository {
    override fun scanForDevices(): Flow<List<ScannedDevice>> {
      return sessionManager.scannedDevices
    }

    override suspend fun isBluetoothEnabled(): Boolean {
        return sessionManager.isBluetoothEnabled()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override suspend fun startScan() = sessionManager.startScan()

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override suspend fun stopScan() = sessionManager.stopScan()

}