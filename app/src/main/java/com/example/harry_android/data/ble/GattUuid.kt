package com.example.harry_android.data.ble

import java.util.UUID

object GattUuid {
    // ── Device Information Service (Bluetooth SIG standard) ──────────────
    val DIS_SERVICE       = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
    val MANUFACTURER_NAME = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")
    val MODEL_NUMBER      = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB")
    val FIRMWARE_REVISION = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")
    val HARDWARE_REVISION = UUID.fromString("00002A27-0000-1000-8000-00805F9B34FB")

    // ── Sensor Data Service (custom) ─────────────────────────────────────
    val SENSOR_SERVICE    = UUID.fromString("12345678-1234-1234-1234-123456789ABC")
    val TEMPERATURE       = UUID.fromString("12345678-1234-1234-1234-123456789ABD")
    val HUMIDITY          = UUID.fromString("12345678-1234-1234-1234-123456789ABE")
    val CONTROL           = UUID.fromString("12345678-1234-1234-1234-123456789ABF")

    // ── Client Characteristic Configuration Descriptor ────────────────────
    val CCCD              = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}