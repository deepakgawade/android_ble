package com.example.harry_android

import com.example.harry_android.core.delegates.LoggingDelegate
import com.example.harry_android.core.inlinefunctions.runInlineFunctionDemo
import com.example.harry_android.data.ble.GattDecoder
import com.example.harry_android.data.ble.decodeTemperature
import com.example.harry_android.domain.model.isScanning
import com.example.harry_android.domain.model.rssiValue
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun check_decodeTemperature(){
       val arrayByte: ByteArray = byteArrayOf(0x12, 0x05)
        val decoder: GattDecoder = GattDecoder()

        println(arrayByte.decodeTemperature())
        println(decoder.decodeTemperature(arrayByte))

    }
    @Test
    fun check_observable(){
        isScanning = true
        isScanning = false
        isScanning = true

        rssiValue = 50
        println(rssiValue)
        rssiValue = -60
        println(rssiValue)

    }
    @Test
    fun check_custom_delegate(){
        var name: String by LoggingDelegate("Teju")

        name = "Deepak"

        val x = name
    }
    @Test
    fun check_inline(){
        runInlineFunctionDemo()
    }
}