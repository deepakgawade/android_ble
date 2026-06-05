package com.example.harry_android.ui.sensor

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.harry_android.core.dispatcher.DispatcherProvider
import com.example.harry_android.data.repository.BleRepositoryImpl
import com.example.harry_android.data.repository.SensorRepositoryImpl
import com.example.harry_android.domain.model.BleState
import com.example.harry_android.domain.model.SensorReading
import com.example.harry_android.domain.usecase.ControlNotificationUseCase
import com.example.harry_android.domain.usecase.ObserveSensorDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SensorUiState(
    val reading: SensorReading? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isNotifying: Boolean = false,
    val connectionState: BleState = BleState.Idle
)

@SuppressLint("MissingPermission")
@HiltViewModel
class SensorViewModel @Inject constructor(
    private val sensorRepoFactory: SensorRepositoryImpl.Factory,
    private val bleRepoFactory: BleRepositoryImpl.Factory,
    private val dispatchers: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val deviceAddress: String = checkNotNull(savedStateHandle["deviceAddress"])

    private val sensorRepo = sensorRepoFactory.create(deviceAddress)
    private val bleRepo = bleRepoFactory.create(deviceAddress)

    private val observeSensorData = ObserveSensorDataUseCase(sensorRepo)
    private val controlNotifications = ControlNotificationUseCase(bleRepo)

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    private var startSensorJob: Job? = null

    init {
        viewModelScope.launch(dispatchers.io)  {
            bleRepo.connect(deviceAddress)
        }
        viewModelScope.launch {
            bleRepo.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state is BleState.Connected) startObserving()
            }
        }
    }

    private fun startObserving() {
        if (startSensorJob?.isActive == true) return

        startSensorJob = viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isNotifying = true) }
            observeSensorData()
                .onStart { controlNotifications.start() }
                .buffer(Channel.UNLIMITED)
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { reading ->
                    _uiState.update {
                        it.copy(reading = reading, isLoading = false, error = null)
                    }
                }
        }
    }

    fun startNotification() {
        startObserving()
    }

    fun stopNotifications() {
        viewModelScope.launch { controlNotifications.stop() }
        startSensorJob?.cancel()
        startSensorJob = null
        _uiState.update { it.copy(isNotifying = false) }
    }

    override fun onCleared() {
        stopNotifications()
    }
}
