package com.example.weatherbrief.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Metar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherBriefScreen(viewModel: WeatherBriefViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WeatherBriefScreenContent(
        state = state,
        onIcaoChanged = viewModel::onIcaoChanged,
        onSearch = viewModel::searchMetar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherBriefScreenContent(
    state: WeatherBriefViewState,
    onIcaoChanged: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Weather Brief") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = state.icao,
                onValueChange = onIcaoChanged,
                label = { Text("ICAO code, e.g. KSFO") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                Text("Get METAR")
            }
            Spacer(Modifier.height(24.dp))

            when {
                state.loading -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator()
                }
                state.error != null -> Text("Error: ${state.error}")
                state.noData -> Text("No data available for \"${state.icao}\"")
                state.metar != null -> MetarCard(state.metar)
            }
        }
    }
}

@Composable
private fun MetarCard(metar: Metar) {
    Column {
        Text(metar.icaId, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Flight category: ${metar.flightCategory}")
        Text("Observed: ${metar.observationTime}")
        metar.tempC?.let { Text("Temp: $it °C") }
        if (metar.windDirDegrees != null && metar.windSpeedKt != null) {
            Text("Wind: ${metar.windDirDegrees}° at ${metar.windSpeedKt} kt")
        }
        metar.visibilityStatusMi?.let { Text("Visibility: $it sm") }
        metar.altimeterInHg?.let { Text("Altimeter: $it inHg") }
        Spacer(Modifier.height(8.dp))
        Text(metar.rawObservation, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
private fun WeatherBriefScreenPreview() {
    WeatherBriefScreenContent(state = WeatherBriefViewState(icao = "KSFO"), onIcaoChanged = {}, onSearch = {})
}
