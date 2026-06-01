package com.example.harry_android.ui.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.harry_android.core.dispatcher.DispatcherProvider
import com.example.harry_android.data.repository.BleRepositoryImpl
import com.example.harry_android.data.repository.SensorRepositoryImpl
import com.example.harry_android.domain.model.BleState
import com.example.harry_android.domain.model.SensorReading
import com.example.harry_android.domain.usecase.ControlNotificationUseCase
import com.example.harry_android.domain.usecase.ObserveSensorDataUseCase
import com.example.harry_android.ui.connection.ScanUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SensorUiState(
    val reading: SensorReading? =null,
    val isLoading: Boolean =true,
    val error: String?  = null,
    val isNotifying: Boolean =false,
    val connectionState: BleState = BleState.Idle
)

class SensorViewModel(
    private val sensorRepoFactory: SensorRepositoryImpl.Factory,
    private val bleRepoFactory: BleRepositoryImpl.Factory,
    private val dispatchers: DispatcherProvider,
    private val deviceAddress: String
): ViewModel() {

    // Per-device instances created from factories — each ViewModel owns repos for one device
    private val sensorRepo = sensorRepoFactory.create(deviceAddress)
    private val bleRepo = bleRepoFactory.create(deviceAddress)

    private val observeSensorData = ObserveSensorDataUseCase(sensorRepo)

    private val controlNotifications = ControlNotificationUseCase(bleRepo)

    private val _uiState = MutableStateFlow(SensorUiState())

    val uiState: StateFlow<SensorUiState> =  _uiState.asStateFlow()


    init {
        viewModelScope.launch {
            bleRepo.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }

                if(state is BleState.Connected) startObserving()
            }
        }
    }

    private fun startObserving(){

        viewModelScope

    }






}