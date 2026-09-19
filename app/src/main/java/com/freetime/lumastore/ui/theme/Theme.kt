package com.freetime.lumastore.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = LumaBlueLight,
    onPrimary = Color(0xFF00344A),
    primaryContainer = LumaBlueDark,
    onPrimaryContainer = Color(0xFFC6E7FF),
    secondary = LumaGreenLight,
    onSecondary = Color(0xFF243600),
    secondaryContainer = LumaGreenDark,
    onSecondaryContainer = Color(0xFFD5F5AC),
    tertiary = LumaGreenLight,
    background = LumaSurfaceDark,
    surface = LumaSurfaceDark,
    surfaceVariant = LumaSurfaceVariantDark,
    onSurface = Color(0xFFE1E3E4),
    onSurfaceVariant = Color(0xFFC2C7CB)
)

private val LightColorScheme = lightColorScheme(
    primary = LumaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE9FF),
    onPrimaryContainer = Color(0xFF001E2D),
    secondary = LumaGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF2C2),
    onSecondaryContainer = Color(0xFF182900),
    tertiary = LumaGreenDark,
    background = LumaSurfaceLight,
    surface = Color.White,
    surfaceVariant = LumaSurfaceVariantLight,
    onSurface = Color(0xFF191C1E),
    onSurfaceVariant = Color(0xFF42484C)
)

@Composable
fun LumaStoreTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // Match GeoWeather: Material You is always enabled on Android 12+.
    val baseColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) DarkColorScheme else LightColorScheme

    // Keep all standard foreground roles readable regardless of the dynamic Material You palette.
    val foreground = if (darkTheme) Color.White else Color.Black
    val secondaryForeground = foreground.copy(alpha = 0.78f)
    val colorScheme = baseColorScheme.copy(
        onBackground = foreground,
        onSurface = foreground,
        onSurfaceVariant = secondaryForeground,
        inverseOnSurface = if (darkTheme) Color.Black else Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
