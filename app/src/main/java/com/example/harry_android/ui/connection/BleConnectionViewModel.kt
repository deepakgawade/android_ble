package com.example.harry_android.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.harry_android.core.dispatcher.DispatcherProvider
import com.example.harry_android.domain.model.BleState
import com.example.harry_android.domain.model.ScannedDevice
import com.example.harry_android.domain.repository.IScanRepository
import com.example.harry_android.domain.usecase.ScanForDeviceUseCase
import com.example.harry_android.domain.usecase.StartScanUseCase
import com.example.harry_android.domain.usecase.StopScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import javax.inject.Inject

data class ScanUiState(
    val devices: List<ScannedDevice> = emptyList(),
    val bluetoothEnabled: Boolean = true,
    val isScanning: Boolean = false,
    val connectingAddress: String? = null,
    val error: ScanError? = null,
    val connectionState: BleState = BleState.Idle
)

sealed class ScanError {
    object BluetoothDisabled : ScanError()
    object PermissionDenied : ScanError()
    data class ScanFailed(val code: Int) : ScanError()
}

@HiltViewModel
class BleConnectionViewModel @Inject constructor(
    private val scanForDevices: ScanForDeviceUseCase,
    private val startScanUseCase: StartScanUseCase,
    private val stopScanUseCase: StopScanUseCase,
    private val dispatchers: DispatcherProvider,
    private val scanRepository: IScanRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanUiState())
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch(dispatchers.io) {
            if (!scanRepository.isBluetoothEnabled()) {
                _scanState.update { it.copy(bluetoothEnabled = false, error = ScanError.BluetoothDisabled) }
                return@launch
            }
            _scanState.update { it.copy(isScanning = true, error = null, devices = emptyList()) }

            // Collect first so the scan callback is registered before hardware scan starts
            launch {
                scanForDevices()
                    .catch { e ->
                        val error = when (e) {
                            is SecurityException -> ScanError.PermissionDenied
                            else -> ScanError.ScanFailed(-1)
                        }
                        _scanState.update { it.copy(error = error, isScanning = false) }
                    }
                    .collect { devices -> _scanState.update { it.copy(devices = devices) } }
            }

            // Yield so the child coroutine initialises its callback before hardware scan fires
            yield()

            startScanUseCase()
        }
    }

    fun stopScan() {
        viewModelScope.launch(dispatchers.io) {
            stopScanUseCase()
        }
        scanJob?.cancel()
        scanJob = null
        _scanState.update { it.copy(isScanning = false) }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
