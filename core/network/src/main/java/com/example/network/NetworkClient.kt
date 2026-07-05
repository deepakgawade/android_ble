package com.example.network

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val BASE_URL   = "https://aviationweather.gov/"
private const val USER_AGENT = "com.example.harry_android/1.0 (contact: you@example.com)"

fun buildAviationWeatherApi(): AviationWeatherApi{

    val userAgentInterceptor = Interceptor{chain -> chain.proceed(chain.request().newBuilder().header("User-Agent",USER_AGENT).build())
    }
    val logging = HttpLoggingInterceptor().apply{
        level = HttpLoggingInterceptor.Level.BODY
    }

    val client = OkHttpClient.Builder().addInterceptor(userAgentInterceptor ).addInterceptor(logging).build()

   val json = Json { ignoreUnknownKeys = true }

    return Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(AviationWeatherApi::class.java)
}