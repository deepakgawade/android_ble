package com.example.harry_android.di

import com.example.harry_android.data.remote.api.HarryApiService
import com.example.harry_android.data.repository.HarryRemoteRepository
import com.example.harry_android.domain.repository.IRemoteRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    @Singleton
    abstract fun bindRemoteRepository(impl: HarryRemoteRepository): IRemoteRepository

    companion object {
        private const val BASE_URL = "https://potterapi-fedeperin.vercel.app"

        private val json = Json { ignoreUnknownKeys = true }

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

        @Provides
        @Singleton
        fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
            Retrofit.Builder().baseUrl(BASE_URL).client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

        @Provides
        @Singleton
        fun provideHarryApiService(retrofit: Retrofit): HarryApiService = retrofit.create(
            HarryApiService::class.java)
    }
}