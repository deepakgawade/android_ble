package com.example.data

import com.example.model.Metar
import com.example.network.AviationWeatherApi
import com.example.network.mapper.toDomain

sealed interface WeatherResult<out T>{
    data class Success<T>(val data:T): WeatherResult<T>
    data object NoData: WeatherResult<Nothing>
    data class Error(val message: String): WeatherResult<Nothing>
}
class AviationWeatherRepository(private val api: AviationWeatherApi) {

    suspend fun getMetar(icao:String): WeatherResult<Metar> = try {
        val  response =  api.getMetar(icao)
        when {
            response.code() == 204      -> WeatherResult.NoData
            response.isSuccessful       -> {
                val metar = response.body()?.firstOrNull()?.toDomain()
                if (metar != null) WeatherResult.Success(metar) else WeatherResult.NoData
            }
            else -> WeatherResult.Error("HTTP ${response.code()}")
        }
    }catch (e: Exception){
        WeatherResult.Error(e.message ?: "Unknown error")
    }
}