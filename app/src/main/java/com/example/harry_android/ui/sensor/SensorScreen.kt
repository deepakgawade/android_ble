package com.example.harry_android.ui.sensor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.harry_android.domain.model.BleState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(
    navController: NavController,
    viewModel: SensorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor Dashboard") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ConnectionStatusChip(
                        state = uiState.connectionState,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )

        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                NotificationControlCard(
                    isNotifying = uiState.isNotifying,
                    isConnected = uiState.connectionState is BleState.Connected,
                    onStop = viewModel::stopNotifications,
                    onStart = viewModel::startNotification
                )
            }
            item {
                SensorReadingCard(
                    label = "Temperature",
                    value = uiState.reading?.temperature?.let { "%.1f".format(it) },
                    unit = "°C",
                    isLoading = uiState.isLoading && uiState.connectionState is BleState.Connected,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
            item {
                SensorReadingCard(
                    label = "Humidity",
                    value = uiState.reading?.humidity?.let { "%.1f".format(it) },
                    unit = "% RH",
                    isLoading = uiState.isLoading && uiState.connectionState is BleState.Connected,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
            uiState.error?.let { error ->
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusChip(state: BleState, modifier: Modifier = Modifier) {
    val label = when (state) {
        is BleState.Connected -> "Connected"
        is BleState.Connecting -> "Connecting..."
        is BleState.Error -> "Error"
        else -> "Disconnected"
    }
    val containerColor = when (state) {
        is BleState.Connected -> MaterialTheme.colorScheme.primaryContainer
        is BleState.Connecting -> MaterialTheme.colorScheme.secondaryContainer
        is BleState.Error -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (state) {
        is BleState.Connected -> MaterialTheme.colorScheme.onPrimaryContainer
        is BleState.Connecting -> MaterialTheme.colorScheme.onSecondaryContainer
        is BleState.Error -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (state is BleState.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(8.dp),
                    color = contentColor,
                    strokeWidth = 1.5.dp
                )
            } else {
                Canvas(Modifier.size(6.dp)) { drawCircle(contentColor) }
            }

            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

@Composable
private fun SensorReadingCard(
    label: String,
    value: String?,
    unit: String,
    isLoading: Boolean,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            if (isLoading && value == null) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value ?: "-",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

    }
}

@Composable
private fun NotificationControlCard(
    isNotifying: Boolean,
    isConnected: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier

) {
    OutlinedCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sensor Notifications", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        !isConnected -> "Waiting  for connection"
                        isNotifying -> "Receiving data every 2s"
                        else -> "Notifications stopped"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            }
            if (isConnected) {
                Spacer(Modifier.width(12.dp))
                if (isNotifying) {
                    FilledTonalButton(onClick = onStop) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                } else {
                    Button(onClick = onStart) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start")
                    }
                }
            }
        }
    }
}