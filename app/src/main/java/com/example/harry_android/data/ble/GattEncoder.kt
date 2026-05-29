package com.example.harry_android.data.ble

class GattEncoder {

    fun encodeControl(start: Boolean):ByteArray{
        val value: Int = if (start) 0x0001 else 0x0000
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
        )/// Value fron beg endian converted to little endian
    }
}