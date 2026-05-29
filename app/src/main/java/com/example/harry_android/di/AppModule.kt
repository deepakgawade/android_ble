package com.example.harry_android.di

import android.content.Context
import com.example.harry_android.core.dispatcher.DefaultDispatcherProvider
import com.example.harry_android.core.dispatcher.DispatcherProvider
import com.example.harry_android.data.ble.BleSessionManager
import com.example.harry_android.data.ble.GattDecoder
import com.example.harry_android.data.ble.GattEncoder
import com.example.harry_android.data.repository.ScanRepositoryImpl
import com.example.harry_android.domain.repository.IScanRepository
import com.example.harry_android.domain.usecase.ScanForDeviceUseCase
import com.example.harry_android.domain.usecase.StartScanUseCase
import com.example.harry_android.domain.usecase.StopScanUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideGattDecoder(): GattDecoder = GattDecoder()

    @Provides @Singleton
    fun provideGattEncoder(): GattEncoder = GattEncoder()

    @Provides @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides @Singleton
    fun provideBleSessionManager(
        @ApplicationContext context: Context,
        decoder: GattDecoder,
        encoder: GattEncoder,
        dispatchers: DispatcherProvider
    ): BleSessionManager = BleSessionManager(context, decoder, encoder, dispatchers)

    @Provides @Singleton
    fun provideScanRepository(sessionManager: BleSessionManager): IScanRepository =
        ScanRepositoryImpl(sessionManager)

    @Provides
    fun provideStartScanUseCase(repo: IScanRepository): StartScanUseCase = StartScanUseCase(repo)

    @Provides
    fun provideStopScanUseCase(repo: IScanRepository): StopScanUseCase = StopScanUseCase(repo)

    @Provides
    fun provideScanForDeviceUseCase(repo: IScanRepository): ScanForDeviceUseCase = ScanForDeviceUseCase(repo)
}
