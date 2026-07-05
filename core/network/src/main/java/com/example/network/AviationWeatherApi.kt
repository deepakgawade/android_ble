package com.example.network

import com.example.network.dto.MetarDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query


interface AviationWeatherApi {
    @GET("api/data/metar")
    suspend fun getMetar(
        @Query("ids") icao: String,
        @Query("format") format: String = "json",
    ): Response<List<MetarDto>>
}