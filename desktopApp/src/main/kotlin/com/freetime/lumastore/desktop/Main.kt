package com.freetime.lumastore.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.freetime.lumastore.shared.LumaStoreCore
import com.freetime.lumastore.shared.SharedStoreApp
import java.awt.Desktop
import java.net.URI

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = LumaStoreCore.appName,
    ) {
        SharedStoreApp(
            onOpenUrl = { url ->
                runCatching {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(URI(url))
                    }
                }
            },
        )
    }
}
