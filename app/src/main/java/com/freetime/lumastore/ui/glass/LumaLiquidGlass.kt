package com.freetime.lumastore.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.freetime.design.LiquidGlassRoot
import com.freetime.design.liquidGlass
import com.freetime.design.liquidGlassCapsule

/**
 * Luma Store compatibility aliases backed by the shared Freetime Design library.
 * This mirrors GeoWeather 4.4.1 and keeps existing screen call sites compatible
 * while sharing the exact same Liquid Glass implementation and settings.
 */
@Composable
fun LumaGlassRoot(content: @Composable () -> Unit) {
    LiquidGlassRoot { content() }
}

@Composable
fun Modifier.lumaGlass(shape: Shape, interactive: Boolean = true): Modifier =
    liquidGlass(shape = shape, interactive = interactive)

@Composable
fun Modifier.lumaGlassCapsule(interactive: Boolean = true): Modifier =
    liquidGlassCapsule(interactive = interactive)

// Compatibility shims for existing Luma Store screens. Freetime Design owns the backdrop.
@Composable
fun rememberLumaBackdrop(): Unit = Unit

fun Modifier.lumaBackdropSource(backdrop: Unit): Modifier = this

@Composable
fun Modifier.lumaLiquidGlass(
    backdrop: Unit,
    shape: Shape,
    interactive: Boolean = true,
): Modifier = liquidGlass(shape = shape, interactive = interactive)
