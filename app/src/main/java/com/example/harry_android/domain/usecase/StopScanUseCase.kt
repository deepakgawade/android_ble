package com.example.harry_android.domain.usecase

import com.example.harry_android.domain.repository.IScanRepository

class StopScanUseCase(private val scanRepository: IScanRepository) {

    suspend operator fun invoke(): Unit = scanRepository.stopScan()
}