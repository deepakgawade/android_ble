package com.example.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MetarDto(
    @SerialName("icaoId") val icaoId:String,
    @SerialName("obsTime") val  observationEpochSeconds: Long,
    @SerialName("temp") val tempC: Double? = null,
    @SerialName("dewp") val dewpointC: Double? =null,
    @SerialName("wdir") val windDirDegrees: Int? = null,
    @SerialName("wspd") val windSpeedKt: Int? = null,
    @SerialName("visib") val visibilityStatuteMi: String? =null,
    @SerialName("altim") val altimeterInHg: Double? = null,
    @SerialName("rawOb") val rawObservation: String = "",
    @SerialName("fltCat") val flightCategory: String?=null,
)