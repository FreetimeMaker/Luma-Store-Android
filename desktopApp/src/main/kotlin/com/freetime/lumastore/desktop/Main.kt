package com.freetime.lumastore.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.freetime.lumastore.shared.LumaStoreCore
import com.freetime.lumastore.shared.currentPlatform

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = LumaStoreCore.appName,
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = LumaStoreCore.appName,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "Kotlin Multiplatform desktop target: ${currentPlatform.name}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = LumaStoreCore.catalogUrl(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
