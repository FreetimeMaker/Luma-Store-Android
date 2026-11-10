package com.freetime.lumastore

import android.net.Uri
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freetime.lumastore.data.DeveloperProfileSettings
import com.freetime.lumastore.data.DeveloperRepository
import com.freetime.lumastore.data.DeveloperSession
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeveloperProfileEditor(
    initial: DeveloperProfileSettings?,
    session: DeveloperSession,
    repository: DeveloperRepository,
    active: Boolean,
    onBack: () -> Unit,
    onSaved: (DeveloperProfileSettings) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var current = context
        while (current is ContextWrapper && current !is ComponentActivity) current = current.baseContext
        requireNotNull(current as? ComponentActivity) { "Profile editor requires ComponentActivity" }
    }
    val state = remember(activity, session.userId) {
        ViewModelProvider(activity, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DeveloperProfileEditorState(context.applicationContext, repository, session) as T
            }
        })["profile-editor:${session.userId}", DeveloperProfileEditorState::class.java]
    }
    state.open(initial)
    var resumed by remember(activity) { mutableStateOf(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    val currentSaved by rememberUpdatedState(onSaved)
    fun finishSaved() {
        val values = state.values
        val savedProfile = (initial ?: DeveloperProfileSettings(session.userId)).copy(
            displayName = values[0],
            bio = values[1].ifBlank { null },
            websiteUrl = values[2].ifBlank { null },
            githubUrl = values[3].ifBlank { null },
            gitlabUrl = values[4].ifBlank { null },
            avatarUrl = values[5].ifBlank { null }
        )
        state.discard()
        currentSaved(savedProfile)
    }
    LaunchedEffect(state.saved, state.busy, state.cleanupPending, state.cleanupRunning, active, resumed) {
        if (state.saved && !state.busy && !state.cleanupRunning && !state.cleanupPending && active && resumed) {
            finishSaved()
        }
    }
    var name by state.field(0)
    var bio by state.field(1)
    var website by state.field(2)
    var github by state.field(3)
    var gitlab by state.field(4)
    var avatar by state.field(5)
    val draftFile = state.draftFile
    val busy = state.busy
    val error = state.error
    var confirmRemove by remember { mutableStateOf(false) }
    val leave = {
        if (state.saved) finishSaved() else {
            state.discard()
            onBack()
        }
    }
    val back = rememberEditorBackAction(state.dirty, busy, active,
        onDiscard = { state.discard(); onBack() }, onBack = leave)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.selectImage(uri.toString())
    }
    val editable = !busy
    state.cropUri?.let { uri ->
        ProfileImageCropDialog(Uri.parse(uri), onDismiss = { state.cropUri = null }, onCropped = state::stage)
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.profile_remove_title)) },
            text = { Text(stringResource(R.string.profile_remove_message)) },
            confirmButton = { TextButton(onClick = {
                confirmRemove = false
                state.remove()
            }) { Text(stringResource(R.string.profile_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.developer_profile)) }, navigationIcon = {
            TextButton(onClick = back) { Text(stringResource(R.string.back)) }
        })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DeveloperAvatar(draftFile?.let(::File) ?: avatar, name, 96.dp) }
            item { OutlinedTextField(name, { name = it }, enabled = editable, label = { Text(stringResource(R.string.developer_name)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(bio, { bio = it }, enabled = editable, label = { Text(stringResource(R.string.developer_bio)) }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
            item {
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = editable, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.choose_profile_image))
                }
                Text(stringResource(R.string.profile_image_draft_hint), style = MaterialTheme.typography.bodySmall)
                if (avatar.isNotBlank() || draftFile != null) {
                    TextButton(onClick = { confirmRemove = true }, enabled = editable) { Text(stringResource(R.string.profile_remove)) }
                }
            }
            item { OutlinedTextField(avatar, { avatar = it }, enabled = editable && draftFile == null, label = { Text(stringResource(R.string.profile_image_url)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(website, { website = it }, enabled = editable, label = { Text(stringResource(R.string.website)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(github, { github = it }, enabled = editable, label = { Text(stringResource(R.string.github)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(gitlab, { gitlab = it }, enabled = editable, label = { Text(stringResource(R.string.gitlab)) }, modifier = Modifier.fillMaxWidth()) }
            error?.let { message -> item {
                Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                state.errorDetails?.let { Text(stringResource(R.string.developer_error_details, it), color = MaterialTheme.colorScheme.error) }
            } }
            if (state.cleanupPending) {
                if (state.saved) {
                    item { Text(stringResource(R.string.profile_saved_cleanup_failed), color = MaterialTheme.colorScheme.error) }
                }
                state.cleanupFailure?.let { message ->
                    if (!state.saved || message != R.string.profile_saved_cleanup_failed) {
                        item { Text(stringResource(message), color = MaterialTheme.colorScheme.error) }
                    }
                }
                state.cleanupFailureDetails?.let { details -> item {
                    Text(stringResource(R.string.developer_error_details, details), color = MaterialTheme.colorScheme.error)
                } }
                item {
                    OutlinedButton(
                        onClick = state::retryCleanup,
                        enabled = !busy && !state.cleanupRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.cleanupRunning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.profile_retry_cleanup))
                    }
                }
                if (state.saved) {
                    item {
                        TextButton(onClick = leave, enabled = !busy && active && resumed, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.back))
                        }
                    }
                }
            }
            item {
                Button(
                    enabled = name.isNotBlank() && !busy && !state.cleanupRunning && state.dirty,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = state::save
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.save))
                }
            }
        }
    }
}
