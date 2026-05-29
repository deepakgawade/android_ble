package com.example.harry_android.domain.usecase

import com.example.harry_android.domain.model.SensorReading
import com.example.harry_android.domain.repository.ISensorRepository
import kotlinx.coroutines.flow.Flow

class ObserveSensorDataUseCase(
    private val repository: ISensorRepository
){
    operator fun invoke(): Flow<SensorReading> = repository.observeSensorData()

}