package com.example.weatherbrief.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AviationWeatherRepository
import com.example.data.WeatherResult
import com.example.model.Metar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeatherBriefViewState(
    val icao: String = "",
    val loading: Boolean = false,
    val metar: Metar? = null,
    val noData: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WeatherBriefViewModel @Inject constructor(
    private val repository: AviationWeatherRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherBriefViewState())
    val state: StateFlow<WeatherBriefViewState> = _state.asStateFlow()

    fun onIcaoChanged(icao: String) {
        _state.update { it.copy(icao = icao) }
    }

    fun searchMetar() {
        val icao = _state.value.icao.trim()
        if (icao.isEmpty()) return

        _state.update { it.copy(loading = true, error = null, noData = false) }
        viewModelScope.launch {
            when (val result = repository.getMetar(icao)) {
                is WeatherResult.Success -> _state.update { it.copy(loading = false, metar = result.data) }
                is WeatherResult.NoData -> _state.update { it.copy(loading = false, metar = null, noData = true) }
                is WeatherResult.Error -> _state.update { it.copy(loading = false, metar = null, error = result.message) }
            }
        }
    }
}
