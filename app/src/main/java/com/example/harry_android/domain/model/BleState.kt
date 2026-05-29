package com.example.harry_android.domain.model

sealed class BleState {
    object Idle : BleState()
    object Connecting : BleState()
    data class Connected(val deviceAddress:String) : BleState()
    data class Error(val message: String) : BleState()
}