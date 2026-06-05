package com.example.harry_android.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(onNavigationToHome:()->Unit,onNavigationToBook: () -> Unit){

    Scaffold(
        topBar = { TopAppBar(title = { Text("Harry Potter App") }) }
    ) {
        paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues),
            ) {
            Button(onClick = onNavigationToHome, modifier = Modifier.fillMaxWidth()) {
                Text("Home - BLE Sensor")
            }
            Spacer(Modifier.height(16.dp))

            Button(onClick = onNavigationToBook, modifier = Modifier.fillMaxWidth()) {
                Text("Books")
            }


        }
    }


}

@Preview(showBackground = true)
@Composable
private fun MenuScreenPreview() {
    MenuScreen(
        onNavigationToHome = {},
        onNavigationToBook = {}
    )
}
