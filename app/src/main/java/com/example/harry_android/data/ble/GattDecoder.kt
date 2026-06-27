package com.example.harry_android.data.ble

class GattDecoder {

    /**
     * Decodes a 2-byte little-endian int16_t scaled by ×10 into °C.
     *
     * Protocol: int16 LE, value = raw / 10.0
     * Example:  EB 00 → raw 235 → 23.5 °C
     * Negative: F0 FF → raw (int16) -16 → -1.6 °C
     */
    fun decodeTemperature(bytes: ByteArray): Double{
        require(bytes.size>=2){"Temperature requires 2 bytes, got ${bytes.size}"}
        val raw  = (bytes[0].toInt() and 0xff) or (bytes[1].toInt() shl 8)
        return raw.toShort()/10.0
    }


    /**
     * Decodes a 2-byte little-endian uint16_t scaled by ×10 into % RH.
     *
     * Protocol: uint16 LE, value = raw / 10.0
     * Example:  26 02 → raw 550 → 55.0 %
     */
    fun decodeHumidity(bytes: ByteArray): Double{
        require(bytes.size>=2){"Humidity requires 2 bytes, got ${bytes.size}"}
        val raw = (bytes[0].toInt() and 0xff) or (bytes[1].toInt() shl 8)

        return raw/10.0
    }
    /**
     * Decodes raw UTF-8 bytes into a String.
     * DIS characteristics have no null terminator.
     */
    fun decodeString(bytes: ByteArray): String =String(bytes, Charsets.UTF_8)
}

fun ByteArray.decodeTemperature(): Double{
    require(size>=2){"Temperature requires 2 bytes, got $size"}
    val raw = (this[0].toInt() and 0xff) or (this[1].toInt() shl 8)
    return  raw.toShort()/10.0

}