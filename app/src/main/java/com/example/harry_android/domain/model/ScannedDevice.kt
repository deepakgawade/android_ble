package com.example.harry_android.domain.model

import kotlin.properties.Delegates

data class ScannedDevice(
    val name: String?,
    val address: String,
    val rssi:Int,
)

val ScannedDevice.displayName: String get() = name?.takeIf { it.isNotBlank() }?:"Unknown (${address.takeLast(5)})"

var isScanning: Boolean by Delegates.observable(false){
    _, old, newValue -> println("isScanning: $old -> $newValue")
}

var rssiValue: Int by Delegates.vetoable(-10){
    _, oldValue, newValue -> newValue in -100 ..-30
}
