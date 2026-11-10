package com.freetime.lumastore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freetime.lumastore.data.DeveloperFundingSettings
import com.freetime.lumastore.data.DeveloperRepository
import com.freetime.lumastore.data.DeveloperSession
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeveloperFundingEditor(
    initial: DeveloperFundingSettings?,
    repository: DeveloperRepository,
    session: DeveloperSession,
    active: Boolean,
    onBack: () -> Unit,
    onSaved: (DeveloperFundingSettings) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.fundingEditorActivity() }
    val state = remember(activity) { ViewModelProvider(activity)[DeveloperFundingEditorState::class.java] }
    val userId = session.userId
    val draft = remember(state, userId) { state.draft(userId, initial) }
    val saving = draft.saving
    val editable = !saving && !draft.saved
    val currentOnSaved by rememberUpdatedState(onSaved)
    var resumed by remember(activity) {
        mutableStateOf(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, resumed, draft.saved, userId) {
        if (active && resumed && state.consumeSaved(userId, draft)) {
            val fields = draft.normalizedFields()
            currentOnSaved(DeveloperFundingSettings(
                developerId = userId,
                donateUrl = fields[0].ifBlank { null },
                liberapay = fields[1].ifBlank { null },
                opencollective = fields[2].ifBlank { null },
                cryptoAddresses = JsonObject(draft.normalizedCrypto().mapValues { JsonPrimitive(it.value) })
            ))
        }
    }
    val back = rememberEditorBackAction(draft.dirty, saving || draft.saved, active,
        onDiscard = { state.leave(userId, draft); onBack() },
        onBack = { state.leave(userId, draft); onBack() })
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.developer_funding)) }, navigationIcon = {
            TextButton(onClick = back) { Text(stringResource(R.string.back)) }
        })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item { OutlinedTextField(draft.donate, { draft.donate = it }, enabled = editable, label = { Text(stringResource(R.string.donation_url)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(draft.liberapay, { draft.liberapay = it }, enabled = editable, label = { Text(stringResource(R.string.liberapay)) }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(draft.collective, { draft.collective = it }, enabled = editable, label = { Text(stringResource(R.string.open_collective)) }, modifier = Modifier.fillMaxWidth()) }
            item { Text(stringResource(R.string.crypto_wallets), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items((developerCryptoOptions + draft.crypto.keys).distinct(), key = { it }) { key ->
                OutlinedTextField(draft.crypto[key].orEmpty(), { value -> draft.crypto = draft.crypto + (key to value) },
                    enabled = editable, label = { Text(key.replace("::", " · ")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            draft.error?.let { message -> item {
                Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                draft.errorDetails?.let { Text(stringResource(R.string.developer_error_details, it), color = MaterialTheme.colorScheme.error) }
            } }
            item {
                Button(
                    enabled = draft.dirty && editable,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { state.save(userId, repository, session) }
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.save))
                }
            }
        }
    }
}

private fun Context.fundingEditorActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.fundingEditorActivity()
    else -> error("DeveloperFundingEditor requires a ComponentActivity")
}
