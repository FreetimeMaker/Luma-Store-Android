package com.freetime.lumastore

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** The caller owns URI permission and removes this dialog after either callback. */
@Composable
fun ProfileImageCropDialog(uri: Uri, onDismiss: () -> Unit, onCropped: (ByteArray) -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(uri) { mutableStateOf<Int?>(null) }
    var saving by remember(uri) { mutableStateOf(false) }
    var zoom by rememberSaveable(uri.toString()) { mutableFloatStateOf(1f) }
    var centerX by rememberSaveable(uri.toString()) { mutableFloatStateOf(0.5f) }
    var centerY by rememberSaveable(uri.toString()) { mutableFloatStateOf(0.5f) }
    LaunchedEffect(uri) {
        try {
            bitmap = withContext(Dispatchers.IO) { decodeProfileCrop(resolver, uri) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = R.string.profile_crop_load_error
        } catch (_: OutOfMemoryError) {
            error = R.string.profile_crop_load_error
        }
    }
    val previewDescription = stringResource(R.string.profile_crop_preview)
    val zoomDescription = stringResource(R.string.profile_crop_zoom)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_crop_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.profile_crop_instructions))
                val image = bitmap
                if (image == null && error == null) CircularProgressIndicator()
                if (image != null) {
                    // Both renderers sample this exact floating-point rectangle, with filtering.
                    val crop = profileCropRect(image, zoom, centerX, centerY)
                    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
                    Canvas(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .semantics { contentDescription = previewDescription }
                            .pointerInput(image, zoom, saving) {
                                if (!saving) detectDragGestures { change, drag ->
                                    change.consume()
                                    val side = min(image.width, image.height) / zoom
                                    centerX = (centerX - drag.x / size.width * side / image.width)
                                        .coerceIn(side / (2 * image.width), 1 - side / (2 * image.width))
                                    centerY = (centerY - drag.y / size.height * side / image.height)
                                        .coerceIn(side / (2 * image.height), 1 - side / (2 * image.height))
                                }
                            }
                    ) {
                        drawIntoCanvas { canvas ->
                            drawProfileCrop(canvas.nativeCanvas, image, crop, size.width, paint)
                        }
                    }
                    Text(zoomDescription)
                    Slider(
                        value = zoom,
                        onValueChange = { value ->
                            zoom = value
                            val side = min(image.width, image.height) / value
                            centerX = centerX.coerceIn(side / (2 * image.width), 1 - side / (2 * image.width))
                            centerY = centerY.coerceIn(side / (2 * image.height), 1 - side / (2 * image.height))
                        },
                        valueRange = 1f..4f,
                        enabled = !saving,
                        modifier = Modifier.semantics { contentDescription = zoomDescription }
                    )
                }
                error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (saving) CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(enabled = bitmap != null && !saving, onClick = {
                val image = bitmap ?: return@TextButton
                val crop = profileCropRect(image, zoom, centerX, centerY)
                saving = true
                error = null
                scope.launch {
                    val bytes = try {
                        withContext(Dispatchers.Default) {
                            val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                            try {
                                drawProfileCrop(android.graphics.Canvas(output), image, crop, 512f,
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                                ByteArrayOutputStream().use { stream ->
                                    check(output.compress(Bitmap.CompressFormat.JPEG, 85, stream))
                                    ensureActive()
                                    stream.toByteArray()
                                }
                            } finally {
                                // This bitmap has never been handed to Compose or another thread.
                                output.recycle()
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = R.string.profile_crop_save_error
                        null
                    } catch (_: OutOfMemoryError) {
                        error = R.string.profile_crop_save_error
                        null
                    } finally {
                        saving = false
                    }
                    if (bytes != null) onCropped(bytes)
                }
            }) { Text(stringResource(R.string.profile_crop_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_crop_cancel)) }
        }
    )
}

private fun profileCropRect(bitmap: Bitmap, zoom: Float, x: Float, y: Float): RectF {
    val side = min(bitmap.width, bitmap.height) / zoom
    val left = (x * bitmap.width - side / 2).coerceIn(0f, bitmap.width - side)
    val top = (y * bitmap.height - side / 2).coerceIn(0f, bitmap.height - side)
    return RectF(left, top, left + side, top + side)
}

private fun drawProfileCrop(
    canvas: android.graphics.Canvas, bitmap: Bitmap, crop: RectF, edge: Float, paint: Paint
) {
    val checkpoint = canvas.save()
    try {
        canvas.clipRect(0f, 0f, edge, edge)
        canvas.drawColor(android.graphics.Color.BLACK)
        val matrix = Matrix()
        matrix.setRectToRect(crop, RectF(0f, 0f, edge, edge), Matrix.ScaleToFit.FILL)
        canvas.drawBitmap(bitmap, matrix, paint)
    } finally {
        canvas.restoreToCount(checkpoint)
    }
}

private fun decodeProfileCrop(resolver: ContentResolver, uri: Uri): Bitmap {
    fun open() = requireNotNull(resolver.openInputStream(uri))
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    // Cap both axes, including very wide panoramas, before allocating pixel storage.
    while ((bounds.outWidth.toLong() + sample - 1) / sample > 2048 ||
        (bounds.outHeight.toLong() + sample - 1) / sample > 2048) sample *= 2
    val orientation = try {
        open().use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    } catch (_: java.io.IOException) {
        // Older platform EXIF readers do not support every bitmap-decodable format.
        ExifInterface.ORIENTATION_NORMAL
    }
    val decoded = open().use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        })
    } ?: error("Image decode failed")
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    // Only unpublished intermediates are recycled. Published images are GC-managed:
    // Compose may still hold a rendering reference after this dialog leaves composition.
    try {
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        return oriented
    } catch (failure: Throwable) {
        decoded.recycle()
        throw failure
    }
}
