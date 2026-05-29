package com.example.harry_android.domain.usecase

import com.example.harry_android.domain.model.ScannedDevice
import com.example.harry_android.domain.repository.IScanRepository
import kotlinx.coroutines.flow.Flow

class ScanForDeviceUseCase (private  val  scanRepository: IScanRepository){
    operator  fun invoke(): Flow<List<ScannedDevice>> = scanRepository.scanForDevices()
}