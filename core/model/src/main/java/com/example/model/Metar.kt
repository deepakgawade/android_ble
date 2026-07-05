package com.example.model

data class Metar(
    val icaId: String,
    val observationTime: String,
    val tempC: Double?,
    val devpointC:Double?,
    val windDirDegrees: Int?,
    val windSpeedKt: Int?,
    val visibilityStatusMi: Double?,
    val altimeterInHg: Double?,
    val rawObservation: String,
    val flightCategory: FlightCategory

)
