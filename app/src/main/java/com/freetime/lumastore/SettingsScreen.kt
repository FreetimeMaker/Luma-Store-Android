package com.freetime.lumastore

import me.free_time.design.freetimeGlass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Intent
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.shape.RoundedCornerShape
import com.freetime.lumastore.data.AppRepository
import com.freetime.lumastore.data.AppSource

@Composable
fun SettingsScreen(repository: AppRepository, onBack: () -> Unit, onSourcesChanged: () -> Unit) {
    var sourceList by remember { mutableStateOf(repository.sources) }
    var sourceName by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var addSourceError by remember { mutableStateOf<String?>(null) }
    var editingSource by remember { mutableStateOf<AppSource?>(null) }
    var repositoryImportValue by remember { mutableStateOf("") }
    var priorityRevision by remember { mutableIntStateOf(0) }
    val sourceAddFailed = stringResource(R.string.source_add_failed)
    val context = LocalContext.current
    var transferMessage by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val backupExported = stringResource(R.string.backup_exported)
    val appListExported = stringResource(R.string.app_list_exported)
    val backupImported = stringResource(R.string.backup_imported)
    val backupFailed = stringResource(R.string.backup_failed, "%s")

    val enabledStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            repository.sources.forEach { this[it.name] = repository.isSourceEnabled(it) }
        }
    }

    val qrScanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            repositoryImportValue = result.data?.getStringExtra("SCAN_RESULT")
                ?: result.data?.dataString
                ?: repositoryImportValue
        }
    }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = pendingExport
        if (uri != null && content != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) } }
                .onSuccess { transferMessage = if (content.contains("luma-store-app-list")) appListExported else backupExported }
                .onFailure { transferMessage = backupFailed.replace("%s", it.message ?: "") }
        }
        pendingExport = null
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() }
                .mapCatching { repository.importBackup(it).getOrThrow() }
                .onSuccess {
                    transferMessage = backupImported
                    sourceList = repository.sources
                    repository.sources.forEach { source -> enabledStates[source.name] = repository.isSourceEnabled(source) }
                    onSourcesChanged()
                }
                .onFailure { transferMessage = backupFailed.replace("%s", it.message ?: "") }
        }
    }

    fun refreshSources() {
        sourceList = repository.sources
        sourceList.forEach { enabledStates[it.name] = repository.isSourceEnabled(it) }
    }

    fun applySourceState(source: AppSource, enabled: Boolean) {
        enabledStates[source.name] = enabled
        repository.setSourceEnabled(source, enabled)
        onSourcesChanged()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sources)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.manage_sources_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            sourceList.forEach { source ->
                SourceCard(
                    repository = repository,
                    source = source,
                    enabled = enabledStates[source.name] ?: repository.isSourceEnabled(source),
                    removable = repository.isCustomSource(source),
                    editable = repository.isCustomSource(source),
                    onEnabledChange = { checked -> applySourceState(source, checked) },
                    onEdit = { editingSource = source },
                    onRemove = {
                        if (repository.removeCustomSource(source)) {
                            enabledStates.remove(source.name)
                            refreshSources()
                            onSourcesChanged()
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (sourceList.none { enabledStates[it.name] ?: repository.isSourceEnabled(it) }) {
                Text(
                    stringResource(R.string.no_source_enabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.source_priority), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.source_priority_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            key(priorityRevision) {
                repository.sourcePriority().forEachIndexed { index, name ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { repository.moveSource(name, -1); priorityRevision++ }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                        }
                        IconButton(onClick = { repository.moveSource(name, 1); priorityRevision++ }, enabled = index < repository.sourcePriority().lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.add_another_source),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.fdroid_source_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                sourceName,
                { sourceName = it; addSourceError = null },
                label = { Text(stringResource(R.string.name)) },
                placeholder = { Text(stringResource(R.string.source_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                sourceUrl,
                { sourceUrl = it; addSourceError = null },
                label = { Text(stringResource(R.string.repository_url)) },
                placeholder = { Text(stringResource(R.string.repository_url_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
            )
            addSourceError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    repository.addCustomSource(sourceName, sourceUrl)
                        .onSuccess { source ->
                            sourceName = ""
                            sourceUrl = ""
                            addSourceError = null
                            enabledStates[source.name] = true
                            refreshSources()
                            onSourcesChanged()
                        }
                        .onFailure { addSourceError = it.message ?: sourceAddFailed }
                },
                enabled = sourceName.isNotBlank() && sourceUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_source))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.import_repository), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.import_repository_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = repositoryImportValue,
                onValueChange = { repositoryImportValue = it; addSourceError = null },
                label = { Text(stringResource(R.string.repository_or_qr_value)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent("com.google.zxing.client.android.SCAN").apply {
                            putExtra("SCAN_MODE", "QR_CODE_MODE")
                        }
                        runCatching { qrScanner.launch(intent) }
                            .onFailure { addSourceError = it.message }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.scan_qr_code)) }
                Button(
                    onClick = {
                        repository.importRepository(repositoryImportValue)
                            .onSuccess { source ->
                                repositoryImportValue = ""
                                addSourceError = null
                                enabledStates[source.name] = repository.isSourceEnabled(source)
                                refreshSources()
                                onSourcesChanged()
                            }
                            .onFailure { addSourceError = it.message ?: sourceAddFailed }
                    },
                    enabled = repositoryImportValue.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.import_repository)) }
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.discover_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            listOf(
                "new" to R.string.show_new_apps,
                "recent" to R.string.show_recently_updated,
                "favorites" to R.string.show_favorites,
                "privacy" to R.string.show_privacy_collection,
                "games" to R.string.show_games_collection
            ).forEach { (key, label) ->
                var enabled by remember(key) { mutableStateOf(repository.discoverSectionEnabled(key)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            repository.setDiscoverSectionEnabled(key, it)
                            onSourcesChanged()
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.cache), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.cache_description, repository.cachedAppCount()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    repository.clearAppCache()
                    transferMessage = context.getString(R.string.cache_cleared)
                    onSourcesChanged()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.clear_cache)) }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.backup_and_transfer), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.backup_and_transfer_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    pendingExport = repository.exportBackup()
                    createDocument.launch("luma-store-backup.json")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.export_backup)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { openBackup.launch(arrayOf("application/json", "text/json", "text/plain")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.import_backup)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    pendingExport = repository.exportAppList()
                    createDocument.launch("luma-store-app-list.json")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.export_app_list)) }
            transferMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(96.dp))
        }
    }

    editingSource?.let { source ->
        EditSourceDialog(
            source = source,
            repository = repository,
            onDismiss = { editingSource = null },
            onSaved = { oldSource, newSource ->
                enabledStates.remove(oldSource.name)
                enabledStates[newSource.name] = repository.isSourceEnabled(newSource)
                editingSource = null
                refreshSources()
                onSourcesChanged()
            }
        )
    }
}

@Composable
private fun EditSourceDialog(
    source: AppSource,
    repository: AppRepository,
    onDismiss: () -> Unit,
    onSaved: (AppSource, AppSource) -> Unit
) {
    var name by remember(source) { mutableStateOf(source.name) }
    var url by remember(source) { mutableStateOf(source.indexUrl) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    val editFailed = stringResource(R.string.source_edit_failed)
    val dialogShape = RoundedCornerShape(28.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.freetimeGlass(dialogShape, interactive = false),
        shape = dialogShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.edit_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text(stringResource(R.string.repository_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    repository.updateCustomSource(source, name, url)
                        .onSuccess { onSaved(source, it) }
                        .onFailure { error = it.message ?: editFailed }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun SourceCard(
    repository: AppRepository,
    source: AppSource,
    enabled: Boolean,
    removable: Boolean,
    editable: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .freetimeGlass(shape, interactive = false),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (removable) {
                        Text(
                            stringResource(R.string.custom_source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        source.indexUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    repository.sourceHealth(source)?.let { health ->
                        val checked = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(health.checkedAt))
                        Text(
                            if (health.successful) stringResource(R.string.source_health_ok, health.appCount, checked)
                            else stringResource(R.string.source_health_error, checked),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (health.successful) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Switch(enabled, onEnabledChange)
            }
            if (editable || removable) {
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (editable) {
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.edit))
                        }
                    }
                    if (removable) {
                        TextButton(onClick = onRemove) { Text(stringResource(R.string.remove)) }
                    }
                }
            }
        }
    }
}
