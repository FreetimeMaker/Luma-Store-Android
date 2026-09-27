package com.freetime.lumastore.ui.theme

import androidx.compose.runtime.Composable
import com.freetime.design.AppTheme
import com.freetime.design.ThemeMode

/**
 * Luma Store theme powered by Freetime Core 3.0.
 *
 * AppTheme provides Material 3 Expressive components/motion and Material You
 * dynamic colors on Android 12+. Older Android versions use the Material 3
 * fallback color scheme. Liquid Glass is reserved for the floating bottom nav.
 */
@Composable
fun LumaStoreTheme(
    darkTheme: Boolean = false,
    oledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    AppTheme(
        themeMode = when {
            oledMode -> ThemeMode.OLED
            darkTheme -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        },
        dynamicColor = true,
        floatingBottomNavigationGlassEnabled = true,
        content = content
    )
}
