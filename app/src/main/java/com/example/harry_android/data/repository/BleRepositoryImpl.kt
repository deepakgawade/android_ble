package com.example.harry_android.data.repository

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.harry_android.data.ble.BleSessionManager
import com.example.harry_android.domain.model.BleState
import com.example.harry_android.domain.repository.IBleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.StateFlow

class BleRepositoryImpl @AssistedInject constructor(
    private val sessionManager: BleSessionManager,
    @Assisted val address: String
) : IBleRepository {

    private val session = sessionManager.getOrCreate(address)

    override val connectionState: StateFlow<BleState>
        get() = session.connectionState

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connect(devicAddress: String) {
        session.connect()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun startNotifications() {
        session.startNotifications()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun stopNotifications() {
        session.stopNotifications()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun disconnect() {
        session.disconnect()
    }

    @AssistedFactory
    interface Factory {
        fun create(address: String): BleRepositoryImpl
    }
}