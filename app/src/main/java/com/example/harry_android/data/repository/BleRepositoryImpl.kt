package com.example.harry_android.data.repository

import com.example.harry_android.domain.model.BleState
import com.example.harry_android.domain.repository.IBleRepository
import kotlinx.coroutines.flow.StateFlow

class BleRepositoryImpl: IBleRepository {
    override val connectionState: StateFlow<BleState>
        get() = TODO("Not yet implemented")

    override suspend fun connect(devicAddress: String) {
        TODO("Not yet implemented")
    }

    override suspend fun startNotifications() {
        TODO("Not yet implemented")
    }

    override suspend fun stopNotifications() {
        TODO("Not yet implemented")
    }

    override suspend fun disconnect() {
        TODO("Not yet implemented")
    }
}