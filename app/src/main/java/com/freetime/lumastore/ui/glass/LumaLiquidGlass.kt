package com.freetime.lumastore.ui.glass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.rememberLiquidState
import kotlinx.coroutines.launch

val LocalLumaBackdrop = staticCompositionLocalOf<LiquidState?> { null }

@Composable
fun rememberLumaBackdrop(): LiquidState? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) rememberLiquidState() else null

// fletchmckee/liquid captures the backdrop itself, exactly like GeoWeather.
fun Modifier.lumaBackdropSource(backdrop: LiquidState?): Modifier = this

@Composable
fun LumaGlassRoot(content: @Composable () -> Unit) {
    val liquidState = rememberLumaBackdrop()
    CompositionLocalProvider(LocalLumaBackdrop provides liquidState) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
fun Modifier.lumaLiquidGlass(
    backdrop: LiquidState?,
    shape: Shape,
    interactive: Boolean = true,
): Modifier {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fallback = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDarkTheme) 0.82f else 0.76f)
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkTheme) 0.46f else 0.58f)

    if (backdrop == null) {
        return this.clip(shape).background(fallback).border(1.dp, outline, shape)
    }

    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    val touch = remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }

    val glass = this
        .clip(shape)
        .background(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                alpha = if (isDarkTheme) 0.24f else 0.30f
            )
        )
        .liquid(backdrop) {
            this.shape = shape
            this.frost = if (isDarkTheme) 18.dp else 20.dp
            this.curve = if (isDarkTheme) 0.62f else 0.58f
            this.refraction = if (isDarkTheme) 0.24f else 0.22f
            this.dispersion = if (isDarkTheme) 0.42f else 0.38f
            this.saturation = if (isDarkTheme) 0.58f else 0.54f
            this.contrast = if (isDarkTheme) 1.28f else 1.34f
        }
        .border(0.75.dp, outline, shape)
        .graphicsLayer {
            val scale = lerp(1f, 1.025f, press.value)
            scaleX = scale
            scaleY = scale
        }

    if (!interactive) return glass

    return glass.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touch.value = down.position
            scope.launch { press.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 340f)) }
            var pressed = true
            while (pressed) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (change == null) pressed = false
                else {
                    touch.value = change.position
                    pressed = change.pressed
                }
            }
            scope.launch { press.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 300f)) }
        }
    }
}
