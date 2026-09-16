package com.freetime.lumastore.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class SharedStoreScreen {
    DISCOVER,
    SEARCH,
    SOURCES,
}

enum class StoreSection {
    DISCOVER,
    SEARCH,
    SOURCES,
}

typealias StoreInstaller = (
    app: StoreApp,
    onProgress: (Int) -> Unit,
    onReady: () -> Unit,
    onError: (Throwable) -> Unit,
) -> Unit

@Composable
fun SharedStoreApp(
    repository: SharedStoreRepository? = null,
    onOpenUrl: (String) -> Unit = {},
) {
    val storeRepository = repository ?: remember { SharedStoreRepository() }
    var screen by remember { mutableStateOf(SharedStoreScreen.DISCOVER) }
    var selectedApp by remember { mutableStateOf<StoreApp?>(null) }
    var apps by remember { mutableStateOf(emptyList<StoreApp>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(storeRepository, refreshKey) {
        loading = true
        error = null
        runCatching { storeRepository.loadApps() }
            .onSuccess { apps = it }
            .onFailure { throwable -> error = throwable.message ?: throwable::class.simpleName ?: "Unknown error" }
        loading = false
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            selectedApp?.let { app ->
                AppDetailsScreen(
                    app = app,
                    onBack = { selectedApp = null },
                    onOpenUrl = onOpenUrl,
                    installer = null,
                )
            } ?: Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = screen == SharedStoreScreen.DISCOVER,
                            onClick = { screen = SharedStoreScreen.DISCOVER },
                            icon = { Text("D") },
                            label = { Text("Discover") },
                        )
                        NavigationBarItem(
                            selected = screen == SharedStoreScreen.SEARCH,
                            onClick = { screen = SharedStoreScreen.SEARCH },
                            icon = { Text("S") },
                            label = { Text("Search") },
                        )
                        NavigationBarItem(
                            selected = screen == SharedStoreScreen.SOURCES,
                            onClick = { screen = SharedStoreScreen.SOURCES },
                            icon = { Text("R") },
                            label = { Text("Sources") },
                        )
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                ) {
                    StoreHeader(
                        appCount = apps.size,
                        platform = currentPlatform,
                        refreshing = loading,
                        onRefresh = { refreshKey++ },
                    )

                    when (screen) {
                        SharedStoreScreen.DISCOVER -> CatalogContent(
                            apps = apps,
                            loading = loading,
                            error = error,
                            emptyText = "No apps are available from the enabled sources.",
                            onAppClick = { selectedApp = it },
                        )

                        SharedStoreScreen.SEARCH -> SearchScreen(
                            apps = apps,
                            loading = loading,
                            error = error,
                            onAppClick = { selectedApp = it },
                        )

                        SharedStoreScreen.SOURCES -> SourcesScreen(
                            sources = storeRepository.sources(),
                            onSourceChanged = { name, enabled ->
                                storeRepository.setSourceEnabled(name, enabled)
                                refreshKey++
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SharedStoreSection(
    section: StoreSection,
    repository: SharedStoreRepository,
    onOpenUrl: (String) -> Unit = {},
    installer: StoreInstaller? = null,
    onSourcesChanged: () -> Unit = {},
) {
    var selectedApp by remember(section) { mutableStateOf<StoreApp?>(null) }
    var apps by remember { mutableStateOf(emptyList<StoreApp>()) }
    var loading by remember { mutableStateOf(section != StoreSection.SOURCES) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    if (section != StoreSection.SOURCES) {
        LaunchedEffect(repository, section, refreshKey) {
            loading = true
            error = null
            runCatching { repository.loadApps() }
                .onSuccess { apps = it }
                .onFailure { throwable -> error = throwable.message ?: throwable::class.simpleName ?: "Unknown error" }
            loading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        selectedApp?.let { app ->
            AppDetailsScreen(
                app = app,
                onBack = { selectedApp = null },
                onOpenUrl = onOpenUrl,
                installer = installer,
            )
        } ?: Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        ) {
            if (section != StoreSection.SOURCES) {
                StoreHeader(
                    appCount = apps.size,
                    platform = currentPlatform,
                    refreshing = loading,
                    onRefresh = { refreshKey++ },
                )
            }

            when (section) {
                StoreSection.DISCOVER -> CatalogContent(
                    apps = apps,
                    loading = loading,
                    error = error,
                    emptyText = "No apps are available from the enabled sources.",
                    onAppClick = { selectedApp = it },
                )

                StoreSection.SEARCH -> SearchScreen(
                    apps = apps,
                    loading = loading,
                    error = error,
                    onAppClick = { selectedApp = it },
                )

                StoreSection.SOURCES -> SourcesScreen(
                    sources = repository.sources(),
                    onSourceChanged = { name, enabled ->
                        repository.setSourceEnabled(name, enabled)
                        onSourcesChanged()
                    },
                )
            }
        }
    }
}

@Composable
private fun StoreHeader(
    appCount: Int,
    platform: LumaPlatform,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = LumaStoreCore.appName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${platform.apiValue.replaceFirstChar { it.uppercase() }} · $appCount apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRefresh, enabled = !refreshing) {
            Text(if (refreshing) "Loading…" else "Refresh")
        }
    }
}

@Composable
private fun SearchScreen(
    apps: List<StoreApp>,
    loading: Boolean,
    error: String?,
    onAppClick: (StoreApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) apps else apps.filter { app ->
            app.name.lowercase().contains(normalized) ||
                app.id.lowercase().contains(normalized) ||
                app.summary.lowercase().contains(normalized) ||
                app.categories.any { it.lowercase().contains(normalized) }
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search apps") },
    )
    Spacer(Modifier.height(12.dp))
    CatalogContent(
        apps = filtered,
        loading = loading,
        error = error,
        emptyText = if (query.isBlank()) "No apps are available." else "No apps match your search.",
        onAppClick = onAppClick,
    )
}

@Composable
private fun CatalogContent(
    apps: List<StoreApp>,
    loading: Boolean,
    error: String?,
    emptyText: String,
    onAppClick: (StoreApp) -> Unit,
) {
    when {
        loading && apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null && apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Could not load apps: $error")
        }
        apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText)
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(apps, key = { "${it.id}:${it.sourceName}" }) { app ->
                AppCard(app = app, onClick = { onAppClick(app) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AppCard(app: StoreApp, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.width(52.dp).height(52.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = app.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                if (app.summary.isNotBlank()) {
                    Text(
                        text = app.summary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = buildString {
                        append(app.sourceName)
                        if (app.version.isNotBlank()) append(" · ${app.version}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourcesScreen(
    sources: List<AppSource>,
    onSourceChanged: (String, Boolean) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Sources",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Text(
                text = "Enable Luma Store or F-Droid-compatible repositories. Changes refresh the catalog immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        items(sources, key = { it.name }) { source ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = source.enabled,
                    onCheckedChange = { onSourceChanged(source.name, it) },
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun AppDetailsScreen(
    app: StoreApp,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    installer: StoreInstaller?,
) {
    var installing by remember(app.id) { mutableStateOf(false) }
    var progress by remember(app.id) { mutableIntStateOf(0) }
    var installMessage by remember(app.id) { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Text("Back")
            }
            Text(
                app.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (app.summary.isNotBlank()) {
                Text(app.summary, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                buildString {
                    append(app.id)
                    if (app.version.isNotBlank()) append(" · ${app.version}")
                    append(" · ${app.sourceName}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (app.description.isNotBlank()) {
            item {
                Text("About", style = MaterialTheme.typography.titleLarge)
                Text(app.description, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (app.categories.isNotEmpty()) {
            item { DetailLine("Categories", app.categories.joinToString()) }
        }
        app.license?.let { license -> item { DetailLine("License", license) } }
        app.authorName?.let { author -> item { DetailLine("Author", author) } }
        if (app.antiFeatures.isNotEmpty()) {
            item { DetailLine("Anti-features", app.antiFeatures.joinToString()) }
        }
        item { DetailLine("Source", if (app.closedSource) "Closed source" else "Open source") }

        if (app.downloadUrl.isNotBlank()) {
            item {
                if (installer != null) {
                    Button(
                        onClick = {
                            installing = true
                            progress = 0
                            installMessage = null
                            installer(
                                app,
                                { progress = it.coerceIn(0, 100) },
                                {
                                    installing = false
                                    installMessage = "Installer opened."
                                },
                                { error ->
                                    installing = false
                                    installMessage = error.message ?: "Installation failed."
                                },
                            )
                        },
                        enabled = !installing,
                    ) {
                        Text(if (installing) "Downloading $progress%" else "Install / Update")
                    }
                } else {
                    Button(onClick = { onOpenUrl(app.downloadUrl) }) {
                        Text("Open download")
                    }
                }
                installMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        app.websiteUrl?.let { url ->
            item {
                TextButton(onClick = { onOpenUrl(url) }) { Text("Website") }
            }
        }
        app.sourceCodeUrl?.let { url ->
            item {
                TextButton(onClick = { onOpenUrl(url) }) { Text("Source code") }
            }
        }
        app.issueTrackerUrl?.let { url ->
            item {
                TextButton(onClick = { onOpenUrl(url) }) { Text("Issue tracker") }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
