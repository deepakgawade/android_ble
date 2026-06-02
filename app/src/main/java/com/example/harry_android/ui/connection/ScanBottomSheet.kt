package com.example.harry_android.ui.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harry_android.domain.model.ScannedDevice
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
    viewModel: BleConnectionViewModel
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        snapshotFlow { sheetState.currentValue }
            .filter { it != SheetValue.Expanded }
            .first()
        viewModel.startScan()
    }

    DisposableEffect(Unit) { onDispose { viewModel.stopScan() } }

    ModalBottomSheet(
        onDismissRequest = { viewModel.stopScan(); onDismiss() },
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        ScanSheetContent(
            onScan = viewModel::startScan,
            state = scanState,
            onStop = viewModel::stopScan,
            onDismiss = { viewModel.stopScan(); onDismiss() },
            onConnect = { address -> viewModel.stopScan(); onConnect(address) }
        )
    }
}

@Composable
private fun ScanSheetContent(
    state: ScanUiState,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 400.dp, max = 400.dp)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Nearby Devices", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onScan) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan")
            }
            OutlinedButton(onClick = onStop) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop")
            }
        }
        AnimatedVisibility(visible = state.error != null) {
            state.error?.let { ScanErrorBanner(error = it, onScan = onScan) }
        }

        when {
            !state.bluetoothEnabled                       -> Text("Bluetooth disabled")
            state.isScanning && state.devices.isEmpty()   -> Text("Scanning…")
            state.devices.isEmpty()                       -> Text("No devices found")
            else -> DeviceList(devices = state.devices, onConnect = onConnect)
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<ScannedDevice>,
    onConnect: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(devices.size) { index ->
            DeviceScanItem(device = devices[index], onConnect = onConnect)
        }
    }
}

@Composable
private fun DeviceScanItem(
    device: ScannedDevice,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { onConnect(device.address) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Connect", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ScanErrorBanner(error: ScanError, onScan:()->Unit, modifier: Modifier= Modifier){
    val message = when (error){
        is ScanError.BluetoothDisabled->"Bluetooth is disabled"
        is ScanError.PermissionDenied->"Bluetooth permission denied"
        is ScanError.ScanFailed->"Scan failed ( code ${error.code})"
    }
    Snackbar(modifier=modifier,
        action = {TextButton(onClick = onScan){Text("Scan")}},
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionContentColor = MaterialTheme.colorScheme.inversePrimary
     ) {
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}