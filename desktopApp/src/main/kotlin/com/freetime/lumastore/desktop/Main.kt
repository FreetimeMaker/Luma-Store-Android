package com.freetime.lumastore.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.freetime.lumastore.shared.DesktopSourceStateStore
import com.freetime.lumastore.shared.LumaStoreCore
import com.freetime.lumastore.shared.SharedStoreApp
import com.freetime.lumastore.shared.SharedStoreRepository
import java.awt.Desktop
import java.net.URI

fun main() {
    val repository = SharedStoreRepository(DesktopSourceStateStore())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = LumaStoreCore.appName,
        ) {
            var developerOpen by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (developerOpen) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { developerOpen = false }) {
                                    Text("Back to Store")
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                DesktopDeveloperScreen()
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            SharedStoreApp(
                                repository = repository,
                                onOpenUrl = { url ->
                                    runCatching {
                                        if (Desktop.isDesktopSupported()) {
                                            Desktop.getDesktop().browse(URI(url))
                                        }
                                    }
                                },
                            )
                            Button(
                                onClick = { developerOpen = true },
                                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                            ) {
                                Text("Developer")
                            }
                        }
                    }
                }
            }
        }
    }
}
