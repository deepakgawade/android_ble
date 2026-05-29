package com.example.harry_android.domain.model

///data clas. internally creates the method like copy with , equals(),
data class SensorReading(
    val temperature: Double,
    val humidity:Double,
    val timeStamp: Long
)
