package com.example.harry_android.domain.repository

import com.example.harry_android.domain.model.BleState
import kotlinx.coroutines.flow.StateFlow

interface IBleRepository {
    val connectionState: StateFlow<BleState>
    suspend fun connect(devicAddress:String)
    suspend fun startNotifications()
    suspend fun stopNotifications()
    suspend fun disconnect()
}