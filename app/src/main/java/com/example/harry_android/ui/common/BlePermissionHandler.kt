package com.example.harry_android.ui.common

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// ui/common/BlePermissionHandler.kt

/**
 * Requests BLE permissions on first composition.
 * Android 12+ requires BLUETOOTH_SCAN + BLUETOOTH_CONNECT.
 * Pre-12 requires BLUETOOTH + ACCESS_FINE_LOCATION (for scan).
 * onPermissionsGranted is called only when ALL permissions are granted.
 */
@Composable
fun BlePermissionHandler(onPermissionsGranted: () -> Unit) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) onPermissionsGranted()
    }

    LaunchedEffect(Unit) { launcher.launch(permissions.toTypedArray()) }
}