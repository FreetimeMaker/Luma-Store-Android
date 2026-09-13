package com.freetime.lumastore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freetime.lumastore.data.AppRepository
import com.freetime.lumastore.data.AppSource

@Composable
fun SettingsScreen(repository: AppRepository, onBack: () -> Unit, onSourcesChanged: () -> Unit) {
    var sourceList by remember { mutableStateOf(repository.sources) }
    var sourceName by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var addSourceError by remember { mutableStateOf<String?>(null) }
    val sourceAddFailed = stringResource(R.string.source_add_failed)
    val enabledStates = remember { mutableStateMapOf<String, Boolean>().apply { repository.sources.forEach { this[it.name] = repository.isSourceEnabled(it) } } }
    fun refreshSources() { sourceList = repository.sources; sourceList.forEach { enabledStates[it.name] = repository.isSourceEnabled(it) } }

    Scaffold(Modifier.fillMaxSize()) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.configure_luma_store), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.manage_sources), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.manage_sources_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            sourceList.forEach { source ->
                SourceCard(source, enabledStates[source.name] ?: true, repository.isCustomSource(source), { checked -> enabledStates[source.name] = checked; repository.setSourceEnabled(source, checked); onSourcesChanged() }, { if (repository.removeCustomSource(source)) { enabledStates.remove(source.name); refreshSources(); onSourcesChanged() } })
                Spacer(Modifier.height(10.dp))
            }
            if (enabledStates.values.none { it }) { Text(stringResource(R.string.no_source_enabled), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(12.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.add_another_source), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.fdroid_source_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(sourceName, { sourceName = it; addSourceError = null }, label = { Text(stringResource(R.string.name)) }, placeholder = { Text(stringResource(R.string.source_name_placeholder)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(sourceUrl, { sourceUrl = it; addSourceError = null }, label = { Text(stringResource(R.string.repository_url)) }, placeholder = { Text(stringResource(R.string.repository_url_placeholder)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            addSourceError?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { repository.addCustomSource(sourceName, sourceUrl).onSuccess { source -> sourceName = ""; sourceUrl = ""; addSourceError = null; enabledStates[source.name] = true; refreshSources(); onSourcesChanged() }.onFailure { addSourceError = it.message ?: sourceAddFailed } }, enabled = sourceName.isNotBlank() && sourceUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_source)) }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SourceCard(source: AppSource, enabled: Boolean, removable: Boolean, onEnabledChange: (Boolean) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (removable) Text(stringResource(R.string.custom_source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(source.indexUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(enabled, onEnabledChange)
        }
        if (removable) TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.remove)) }
    } }
}
