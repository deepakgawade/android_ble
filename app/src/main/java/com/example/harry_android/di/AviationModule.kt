package com.example.harry_android.di

import com.example.data.AviationWeatherRepository
import com.example.network.AviationWeatherApi
import com.example.network.buildAviationWeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AviationModule {
    @Provides
    @Singleton
    fun provideAviationWeatherApi(): AviationWeatherApi = buildAviationWeatherApi()

    @Provides
    @Singleton
    fun provideProvideAviationWeatherRepository(api: AviationWeatherApi) =
        AviationWeatherRepository(api)
}