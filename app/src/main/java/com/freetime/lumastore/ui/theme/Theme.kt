package com.freetime.lumastore.ui.theme

import com.freetime.design.AppTheme
import com.freetime.design.ThemeMode

import android.os.Build
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    largeIncreased = RoundedCornerShape(40.dp),
    extraLarge = RoundedCornerShape(40.dp),
    extraLargeIncreased = RoundedCornerShape(48.dp),
    extraExtraLarge = RoundedCornerShape(56.dp)
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
    oledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    // Match GeoWeather: Material You is always enabled on Android 12+.
    val baseColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) DarkColorScheme else LightColorScheme

    // Preserve Material You's paired foreground colors. Dynamic color already
    // calculates onPrimary/onSecondary/onContainer roles for the actual
    // container luminance; forcing them to black/white can create dark text
    // on dark dynamic containers.
    val themedBase = if (darkTheme && oledMode) baseColorScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color(0xFF101010)
    ) else baseColorScheme
    val colorScheme = themedBase

    AppTheme(
        themeMode = when {
            oledMode -> ThemeMode.OLED
            darkTheme -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        },
        dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        liquidGlassEnabled = true
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            shapes = ExpressiveShapes,
            typography = Typography,
            content = content
        )
    }
}
