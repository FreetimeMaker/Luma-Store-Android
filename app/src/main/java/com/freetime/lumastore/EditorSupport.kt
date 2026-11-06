package com.freetime.lumastore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.freetime.lumastore.data.DeveloperFailureKind
import com.freetime.lumastore.data.classifyDeveloperFailure

class EditorNavigationGuard {
    var interceptor: ((() -> Unit) -> Unit)? = null

    fun request(destination: () -> Unit) {
        interceptor?.invoke(destination) ?: destination()
    }
}

val LocalEditorNavigationGuard = staticCompositionLocalOf<EditorNavigationGuard?> { null }

/** One exit path for toolbar back, system back and the shell's tab/rail navigation. */
@Composable
fun rememberEditorBackAction(
    dirty: Boolean,
    busy: Boolean,
    active: Boolean,
    onDiscard: () -> Unit,
    onBack: () -> Unit
): () -> Unit {
    val guard = LocalEditorNavigationGuard.current
    val currentDirty by rememberUpdatedState(dirty)
    val currentBusy by rememberUpdatedState(busy)
    val discard by rememberUpdatedState(onDiscard)
    val back by rememberUpdatedState(onBack)
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showBusy by remember { mutableStateOf(false) }
    val request = remember {
        { destination: () -> Unit ->
            when {
                currentBusy -> showBusy = true
                currentDirty -> pending = destination
                else -> destination()
            }
        }
    }
    DisposableEffect(guard, active) {
        if (active) guard?.interceptor = request
        onDispose {
            if (guard != null && guard.interceptor === request) guard.interceptor = null
        }
    }
    BackHandler(enabled = active) { request { back() } }
    if (pending != null && active) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val destination = pending
                    pending = null
                    discard()
                    destination?.invoke()
                }) { Text(stringResource(R.string.editor_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.editor_keep_editing)) }
            }
        )
    }
    if (showBusy && active) {
        AlertDialog(
            onDismissRequest = { showBusy = false },
            text = { Text(stringResource(R.string.editor_wait_for_save)) },
            confirmButton = { TextButton(onClick = { showBusy = false }) { Text(stringResource(android.R.string.ok)) } }
        )
    }
    return { request { back() } }
}

fun developerFailureMessage(failure: Throwable, fallback: Int): Int = when (classifyDeveloperFailure(failure)) {
    DeveloperFailureKind.SESSION_EXPIRED -> R.string.developer_error_session
    DeveloperFailureKind.ACCESS_DENIED -> R.string.developer_error_access
    DeveloperFailureKind.BUCKET_MISSING -> R.string.developer_error_bucket
    DeveloperFailureKind.IMAGE_FORMAT -> R.string.developer_error_format
    DeveloperFailureKind.IMAGE_TOO_LARGE -> R.string.developer_error_size
    DeveloperFailureKind.NETWORK -> R.string.developer_error_network
    DeveloperFailureKind.UNKNOWN -> fallback
}

@Composable
fun DeveloperAvatar(model: Any?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        if (model != null && model != "") {
            AsyncImage(model = model, contentDescription = name, modifier = Modifier.size(size), contentScale = ContentScale.Crop)
        }
    }
}
