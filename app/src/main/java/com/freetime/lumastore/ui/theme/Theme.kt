package com.freetime.lumastore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.freetime.design.templates.expressive.FreetimeTheme

/** Freetime's M3 Expressive template, with Material You and the app's OLED override. */
@Composable
fun LumaStoreTheme(
    darkTheme: Boolean = false,
    oledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    FreetimeTheme(
        darkTheme = darkTheme || oledMode,
        dynamicColor = true
    ) {
        if (oledMode) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    background = Color.Black,
                    surface = Color.Black
                ),
                content = content
            )
        } else {
            content()
        }
    }
}
