package com.example.harry_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.harry_android.ui.common.BlePermissionHandler
import com.example.harry_android.ui.connection.BleConnectionViewModel
import com.example.harry_android.ui.connection.ScanBottomSheet
import com.example.harry_android.ui.theme.Harry_androidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Harry_androidTheme {
                val viewModel: BleConnectionViewModel = hiltViewModel()
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var showSheet by remember { mutableStateOf(false) }

                BlePermissionHandler(onPermissionsGranted = {})

                Scaffold(modifier = Modifier.fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.onSurface)) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(onClick = { showSheet = true }) {
                            Text("Scan for Devices")
                        }
                    }

                    if (showSheet) {
                        ScanBottomSheet(
                            sheetState = sheetState,
                            onDismiss = { showSheet = false },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}
