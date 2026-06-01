package com.example.harry_android.domain.usecase

import com.example.harry_android.domain.repository.IBleRepository

class ControlNotificationUseCase(private val repository: IBleRepository) {
    suspend fun start() = repository.startNotifications()
    suspend fun stop() = repository.stopNotifications()
}