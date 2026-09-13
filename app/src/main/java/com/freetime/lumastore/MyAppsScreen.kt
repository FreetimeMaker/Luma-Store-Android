package com.freetime.lumastore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.freetime.lumastore.data.AppRepository
import com.freetime.lumastore.data.StoreApp

@Composable
fun MyAppsScreen(
    repository: AppRepository,
    installedAppsRevision: Int,
    installedVersionCode: (String) -> Long?,
    installedVersionName: (String) -> String?,
    openInstalledApp: (String) -> Boolean,
    canInstallPackages: () -> Boolean,
    requestInstallPermission: () -> Unit,
    install: (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> Unit
) {
    val allApps = remember(repository, installedAppsRevision) {
        repository.loadCachedApps()
            .groupBy { it.id }
            .mapNotNull { (_, variants) -> variants.maxByOrNull { it.versionCode } }
    }
    val installed = remember(allApps, installedAppsRevision) {
        allApps.mapNotNull { app ->
            val code = installedVersionCode(app.id) ?: return@mapNotNull null
            InstalledStoreApp(app, code, installedVersionName(app.id))
        }.sortedBy { it.app.name.lowercase() }
    }
    val updates = remember(installed) { installed.filter { it.app.versionCode > it.installedCode } }

    var installingId by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runAction(item: InstalledStoreApp) {
        if (item.app.versionCode <= item.installedCode) {
            if (!openInstalledApp(item.app.id)) error = item.app.name
            return
        }
        if (!canInstallPackages()) {
            requestInstallPermission()
            return
        }
        installingId = item.app.id
        installProgress = 0
        install(
            item.app,
            { installProgress = it },
            {
                installProgress = 100
                installingId = null
            },
            {
                installingId = null
                error = it.message
            }
        )
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.my_apps)) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.updates_count, updates.size),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (updates.isEmpty()) stringResource(R.string.apps_up_to_date)
                        else stringResource(R.string.updates_available_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (updates.isNotEmpty()) {
                items(updates, key = { "update:${it.app.id}" }) { item ->
                    MyAppRow(
                        item = item,
                        installing = installingId == item.app.id,
                        progress = installProgress,
                        actionLabel = stringResource(R.string.update),
                        onAction = { runAction(item) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 92.dp))
                }
                item {
                    Text(
                        stringResource(R.string.installed_apps),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            }

            if (installed.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_installed_apps),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(installed, key = { "installed:${it.app.id}" }) { item ->
                    MyAppRow(
                        item = item,
                        installing = installingId == item.app.id,
                        progress = installProgress,
                        actionLabel = if (item.app.versionCode > item.installedCode) stringResource(R.string.update) else stringResource(R.string.open),
                        onAction = { runAction(item) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 92.dp))
                }
            }
        }
    }
}

@Composable
private fun MyAppRow(
    item: InstalledStoreApp,
    installing: Boolean,
    progress: Int,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.app.iconUrl != null) {
                    AsyncImage(
                        model = item.app.iconUrl,
                        contentDescription = stringResource(R.string.icon_of, item.app.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(item.app.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        item.app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(
                            R.string.installed_and_available_version,
                            item.installedName ?: item.installedCode.toString(),
                            item.app.version
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onAction, enabled = !installing) {
                    Text(if (installing) stringResource(R.string.install_progress, progress) else actionLabel)
                }
            }
            if (installing) {
                Spacer(Modifier.height(6.dp))
                if (progress > 0) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private data class InstalledStoreApp(
    val app: StoreApp,
    val installedCode: Long,
    val installedName: String?
)
