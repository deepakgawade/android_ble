package com.example.network.mapper

import com.example.model.FlightCategory
import com.example.model.Metar
import com.example.network.dto.MetarDto

fun MetarDto.toDomain() = Metar(
    icaId = icaoId,
    observationTime = observationEpochSeconds.toString(),
    tempC = tempC,
    devpointC = dewpointC,
    windDirDegrees = windDirDegrees,
    windSpeedKt = windSpeedKt,
    visibilityStatusMi = visibilityStatuteMi?.trimEnd('+')?.toDoubleOrNull(),
    altimeterInHg = altimeterInHg,
    rawObservation = rawObservation,
    flightCategory =  when(flightCategory){
        "VFR" -> FlightCategory.VFR
        "MVFR"-> FlightCategory.MVFR
        "IFR" -> FlightCategory.IFR
        "LIFR"-> FlightCategory.LIFR
        else -> FlightCategory.UNKOWN
    }
)