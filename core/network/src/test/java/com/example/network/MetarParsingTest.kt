package com.example.network

import com.example.model.FlightCategory
import com.example.network.dto.MetarDto
import com.example.network.mapper.toDomain
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

class MetarParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Real getMetar response for KSFO, captured from the live API.
    private val rawResponse = """
        [{"icaoId":"KSFO","receiptTime":"2026-07-05T10:59:10.413Z","obsTime":1783248960,"reportTime":"2026-07-05T11:00:00.000Z","temp":13.9,"dewp":11.1,"wdir":250,"wspd":7,"visib":"10+","altim":1015.7,"slp":1015.6,"qcField":12,"metarType":"METAR","rawOb":"METAR KSFO 051056Z 25007KT 10SM FEW002 OVC014 14/11 A2999 RMK AO2 SLP156 T01390111 ${'$'}","lat":37.6196,"lon":-122.3656,"elev":2,"name":"San Francisco Intl, CA, US","cover":"OVC","clouds":[{"cover":"FEW","base":200},{"cover":"OVC","base":1400}],"fltCat":"MVFR"}]
    """.trimIndent()

    @Test
    fun `parses live KSFO payload without throwing`() {
        val dtos = json.decodeFromString(ListSerializer(MetarDto.serializer()), rawResponse)

        assertEquals(1, dtos.size)
        val dto = dtos.first()

        assertEquals("KSFO", dto.icaoId)
        assertEquals(1783248960L, dto.observationEpochSeconds)
        assertEquals(13.9, dto.tempC)
        assertEquals(11.1, dto.dewpointC)
        assertEquals(250, dto.windDirDegrees)
        assertEquals(7, dto.windSpeedKt)
        assertEquals("10+", dto.visibilityStatuteMi)
        assertEquals(1015.7, dto.altimeterInHg)
        assertEquals("MVFR", dto.flightCategory)
    }

    @Test
    fun `maps live KSFO payload to domain correctly`() {
        val dto = json.decodeFromString(ListSerializer(MetarDto.serializer()), rawResponse).first()
        val metar = dto.toDomain()

        assertEquals("KSFO", metar.icaId)
        assertEquals("1783248960", metar.observationTime)
        assertEquals(13.9, metar.tempC)
        assertEquals(11.1, metar.devpointC)
        assertEquals(250, metar.windDirDegrees)
        assertEquals(7, metar.windSpeedKt)
        assertEquals(10.0, metar.visibilityStatusMi)
        assertEquals(1015.7, metar.altimeterInHg)
        assertEquals(FlightCategory.MVFR, metar.flightCategory)
    }
}
