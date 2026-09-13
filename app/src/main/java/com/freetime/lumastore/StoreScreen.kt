package com.freetime.lumastore

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.freetime.lumastore.data.AppRepository
import com.freetime.lumastore.data.StoreApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppAction { INSTALL, UPDATE, OPEN }
private enum class StoreView { APPS, UPDATES }
private enum class SourceCodeFilter { ALL, OPEN_SOURCE, CLOSED_SOURCE }

private const val SOURCE_PREFERENCES = "luma_store_source_preferences"
private const val SOURCE_KEY_PREFIX = "source_"

@Composable
fun StoreScreen(
    repository: AppRepository,
    installedAppsRevision: Int,
    installedVersionCode: (String) -> Long?,
    installedVersionName: (String) -> String?,
    openInstalledApp: (String) -> Boolean,
    canInstallPackages: () -> Boolean,
    requestInstallPermission: () -> Unit,
    install: (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val sourcePreferences = remember(context) {
        context.applicationContext.getSharedPreferences(SOURCE_PREFERENCES, Context.MODE_PRIVATE)
    }

    val initialCachedApps = remember(repository) { repository.loadCachedApps() }
    var apps by remember { mutableStateOf(initialCachedApps) }
    var loading by remember { mutableStateOf(initialCachedApps.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedSourceFilter by remember { mutableStateOf<String?>(null) }
    var sourceCodeFilter by remember { mutableStateOf(SourceCodeFilter.ALL) }
    var selectedAppId by remember { mutableStateOf<String?>(null) }
    var selectedScreenshotUrl by remember { mutableStateOf<String?>(null) }
    var installingKey by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var storeView by remember { mutableStateOf(StoreView.APPS) }
    val selectedSources = remember { mutableStateMapOf<String, String>() }
    val categoryListState = rememberLazyListState()
    val categoryScrollScope = rememberCoroutineScope()
    val storeListState = rememberLazyListState()

    LaunchedEffect(refreshKey) {
        if (apps.isEmpty()) loading = true else refreshing = true
        error = null
        val result = withContext(Dispatchers.IO) { repository.loadApps() }
        result.onSuccess { apps = it }.onFailure {
            if (apps.isEmpty()) error = it.message ?: context.getString(R.string.app_sources_load_error)
        }
        loading = false
        refreshing = false
    }

    val variantsById = remember(apps) { apps.groupBy { it.id } }
    val availableSources = remember(apps) {
        apps.map { it.sourceName }.distinct().sortedBy { it.lowercase() }
    }

    fun matchesSourceCodeFilter(app: StoreApp): Boolean = when (sourceCodeFilter) {
        SourceCodeFilter.ALL -> true
        SourceCodeFilter.OPEN_SOURCE -> !app.closedSource
        SourceCodeFilter.CLOSED_SOURCE -> app.closedSource
    }

    LaunchedEffect(apps) {
        variantsById.forEach { (id, variants) ->
            val savedSource = sourcePreferences.getString(sourcePreferenceKey(id), null)
            if (savedSource != null && variants.any { it.sourceName == savedSource }) {
                selectedSources[id] = savedSource
            } else {
                selectedSources.remove(id)
                if (savedSource != null) sourcePreferences.edit().remove(sourcePreferenceKey(id)).apply()
            }
        }
        if (selectedSourceFilter != null && selectedSourceFilter !in availableSources) selectedSourceFilter = null
    }

    fun selectSource(appId: String, sourceName: String) {
        selectedSources[appId] = sourceName
        sourcePreferences.edit().putString(sourcePreferenceKey(appId), sourceName).apply()
    }

    val sourceSelectionSnapshot = selectedSources.toMap()
    val selectedApps = remember(variantsById, selectedSourceFilter, sourceCodeFilter, sourceSelectionSnapshot) {
        variantsById.mapNotNull { (id, variants) ->
            val matchingVariants = variants.filter { variant ->
                val sourceMatches = selectedSourceFilter == null || variant.sourceName == selectedSourceFilter
                val sourceCodeMatches = when (sourceCodeFilter) {
                    SourceCodeFilter.ALL -> true
                    SourceCodeFilter.OPEN_SOURCE -> !variant.closedSource
                    SourceCodeFilter.CLOSED_SOURCE -> variant.closedSource
                }
                sourceMatches && sourceCodeMatches
            }
            if (matchingVariants.isEmpty()) null else {
                val selectedSource = sourceSelectionSnapshot[id]
                matchingVariants.firstOrNull { it.sourceName == selectedSource }
                    ?: matchingVariants.maxByOrNull { it.versionCode }
            }
        }.sortedBy { it.name.lowercase() }
    }

    val installedCodes = remember(selectedApps, installedAppsRevision) {
        selectedApps.associate { it.id to installedVersionCode(it.id) }
    }
    val installedNames = remember(selectedApps, installedAppsRevision) {
        selectedApps.associate { it.id to installedVersionName(it.id) }
    }
    val updateCount = remember(selectedApps, installedCodes) {
        selectedApps.count { app ->
            val installedCode = installedCodes[app.id]
            installedCode != null && app.versionCode > installedCode
        }
    }
    val categories = remember(selectedApps) {
        selectedApps.flatMap { it.categories }.distinct().sortedBy { it.lowercase() }
    }
    val filtered = remember(selectedApps, query, selectedCategory, storeView, installedCodes) {
        selectedApps.filter { app ->
            val matchesQuery = query.isBlank() ||
                app.name.contains(query, true) ||
                app.id.contains(query, true) ||
                app.summary.contains(query, true) ||
                app.description.contains(query, true) ||
                app.categories.any { it.contains(query, true) }
            val matchesCategory = selectedCategory == null || selectedCategory in app.categories
            val matchesView = when (storeView) {
                StoreView.APPS -> true
                StoreView.UPDATES -> {
                    val installedCode = installedCodes[app.id]
                    installedCode != null && app.versionCode > installedCode
                }
            }
            matchesQuery && matchesCategory && matchesView
        }
    }

    val sourceCodeText = when (sourceCodeFilter) {
        SourceCodeFilter.ALL -> stringResource(R.string.open_and_closed_source)
        SourceCodeFilter.OPEN_SOURCE -> stringResource(R.string.open_source)
        SourceCodeFilter.CLOSED_SOURCE -> stringResource(R.string.closed_source)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        LazyColumn(
            state = storeListState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item(key = "store_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .graphicsLayer {
                            val collapseProgress = if (storeListState.firstVisibleItemIndex > 0) {
                                1f
                            } else {
                                (storeListState.firstVisibleItemScrollOffset / 220f).coerceIn(0f, 1f)
                            }
                            val scale = 1f - collapseProgress * 0.06f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.store_tagline, repository.sources.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (refreshing) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = storeView == StoreView.APPS,
                                onClick = { storeView = StoreView.APPS },
                                label = { Text(stringResource(R.string.apps)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = storeView == StoreView.UPDATES,
                                onClick = { storeView = StoreView.UPDATES },
                                label = { Text(stringResource(R.string.updates_count, updateCount)) }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(if (storeView == StoreView.UPDATES) stringResource(R.string.search_updates) else stringResource(R.string.search_apps)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.source_code), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(selected = sourceCodeFilter == SourceCodeFilter.ALL, onClick = { sourceCodeFilter = SourceCodeFilter.ALL }, label = { Text(stringResource(R.string.all_apps)) }) }
                        item { FilterChip(selected = sourceCodeFilter == SourceCodeFilter.OPEN_SOURCE, onClick = { sourceCodeFilter = SourceCodeFilter.OPEN_SOURCE }, label = { Text(stringResource(R.string.open_source)) }) }
                        item { FilterChip(selected = sourceCodeFilter == SourceCodeFilter.CLOSED_SOURCE, onClick = { sourceCodeFilter = SourceCodeFilter.CLOSED_SOURCE }, label = { Text(stringResource(R.string.closed_source)) }) }
                    }

                    if (availableSources.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { FilterChip(selected = selectedSourceFilter == null, onClick = { selectedSourceFilter = null }, label = { Text(stringResource(R.string.all_sources)) }) }
                            items(availableSources, key = { it }) { sourceName ->
                                FilterChip(
                                    selected = selectedSourceFilter == sourceName,
                                    onClick = { selectedSourceFilter = if (selectedSourceFilter == sourceName) null else sourceName },
                                    label = { Text(sourceName) }
                                )
                            }
                        }
                    }

                    if (categories.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    categoryScrollScope.launch {
                                        categoryListState.animateScrollToItem((categoryListState.firstVisibleItemIndex - 3).coerceAtLeast(0))
                                    }
                                },
                                enabled = categoryListState.canScrollBackward
                            ) { Text("‹") }
                            LazyRow(
                                state = categoryListState,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                item { FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text(stringResource(R.string.all_categories)) }) }
                                items(categories, key = { it }) { category ->
                                    FilterChip(
                                        selected = selectedCategory == category,
                                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                                        label = { Text(category) }
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    categoryScrollScope.launch {
                                        categoryListState.animateScrollToItem((categoryListState.firstVisibleItemIndex + 3).coerceAtMost(categories.size))
                                    }
                                },
                                enabled = categoryListState.canScrollForward
                            ) { Text("›") }
                        }
                    }
                }
            }

            when {
                loading -> item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.loading_sources))
                    }
                }

                error != null -> item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: stringResource(R.string.unknown_error))
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { refreshKey++ }) { Text(stringResource(R.string.try_again)) }
                    }
                }

                filtered.isEmpty() -> item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (storeView == StoreView.UPDATES) stringResource(R.string.no_updates_available)
                            else stringResource(R.string.no_apps_found),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (storeView == StoreView.UPDATES) stringResource(R.string.apps_up_to_date)
                            else stringResource(R.string.no_apps_for_filters),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    item(key = "results_summary") {
                        Text(
                            stringResource(
                                R.string.store_results_summary,
                                filtered.size,
                                selectedSourceFilter ?: stringResource(R.string.all_sources),
                                sourceCodeText
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(filtered, key = { it.id }) { app ->
                        val installedCode = installedCodes[app.id]
                        val installedName = installedNames[app.id]
                        val action = when {
                            installedCode == null -> AppAction.INSTALL
                            app.versionCode > installedCode -> AppAction.UPDATE
                            else -> AppAction.OPEN
                        }
                        val currentInstallKey = variantKey(app)

                        AppListItem(
                            app = app,
                            action = action,
                            installedVersionName = installedName,
                            installing = installingKey == currentInstallKey,
                            progress = installProgress,
                            onOpenDetails = { selectedAppId = app.id },
                            onAction = {
                                if (action == AppAction.OPEN) {
                                    if (!openInstalledApp(app.id)) error = context.getString(R.string.app_not_launchable, app.name)
                                } else if (!canInstallPackages()) {
                                    requestInstallPermission()
                                } else {
                                    installingKey = currentInstallKey
                                    installProgress = 0
                                    install(
                                        app,
                                        { installProgress = it },
                                        { installProgress = 100; installingKey = null },
                                        {
                                            installingKey = null
                                            error = context.getString(R.string.download_failed, it.message ?: context.getString(R.string.unknown_error))
                                        }
                                    )
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 82.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    selectedAppId?.let { appId ->
        val variants = variantsById[appId].orEmpty()
            .filter(::matchesSourceCodeFilter)
            .filter { selectedSourceFilter == null || it.sourceName == selectedSourceFilter }
            .sortedBy { it.sourceName.lowercase() }
        val selectedSource = selectedSources[appId]
        val app = variants.firstOrNull { it.sourceName == selectedSource } ?: variants.maxByOrNull { it.versionCode }

        if (app != null) {
            val installedCode = installedCodes[app.id] ?: installedVersionCode(app.id)
            val installedName = installedNames[app.id] ?: installedVersionName(app.id)
            val action = when {
                installedCode == null -> AppAction.INSTALL
                app.versionCode > installedCode -> AppAction.UPDATE
                else -> AppAction.OPEN
            }
            val currentInstallKey = variantKey(app)
            val actionLabel = when (action) {
                AppAction.INSTALL -> stringResource(R.string.install)
                AppAction.UPDATE -> stringResource(R.string.update)
                AppAction.OPEN -> stringResource(R.string.open)
            }

            AppDetailsScreen(
                app = app,
                variants = variants,
                installedVersionName = installedName,
                actionLabel = actionLabel,
                installing = installingKey == currentInstallKey,
                progress = installProgress,
                onBack = { selectedAppId = null },
                onAction = {
                    if (action == AppAction.OPEN) {
                        if (!openInstalledApp(app.id)) {
                            error = context.getString(R.string.app_not_launchable, app.name)
                        }
                    } else if (!canInstallPackages()) {
                        requestInstallPermission()
                    } else {
                        installingKey = currentInstallKey
                        installProgress = 0
                        install(
                            app,
                            { installProgress = it },
                            { installProgress = 100; installingKey = null },
                            {
                                installingKey = null
                                error = context.getString(
                                    R.string.download_failed,
                                    it.message ?: context.getString(R.string.unknown_error)
                                )
                            }
                        )
                    }
                },
                onSourceSelected = { variant -> selectSource(appId, variant.sourceName) },
                onScreenshotSelected = { selectedScreenshotUrl = it },
                onOpenUri = { uriHandler.openUri(it) }
            )
        }
    }

    selectedScreenshotUrl?.let { screenshotUrl ->
        AlertDialog(
            onDismissRequest = { selectedScreenshotUrl = null },
            title = { Text(stringResource(R.string.screenshot)) },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 650.dp), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = screenshotUrl,
                        contentDescription = stringResource(R.string.enlarged_screenshot),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
            },
            confirmButton = { TextButton(onClick = { selectedScreenshotUrl = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}

@Composable
private fun AppListItem(
    app: StoreApp,
    action: AppAction,
    installedVersionName: String?,
    installing: Boolean,
    progress: Int,
    onOpenDetails: () -> Unit,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetails),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app = app, size = 54)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        app.summary.ifBlank { app.id },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (installedVersionName != null) {
                            stringResource(R.string.app_version_source_installed, app.version, app.sourceName, installedVersionName)
                        } else {
                            stringResource(R.string.app_version_source, app.version, app.sourceName)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (app.categories.isNotEmpty()) {
                        Text(
                            app.categories.take(3).joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FilledTonalButton(onClick = onAction, enabled = !installing, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        if (installing) stringResource(R.string.install_progress, progress)
                        else when (action) {
                            AppAction.INSTALL -> stringResource(R.string.install)
                            AppAction.UPDATE -> stringResource(R.string.update)
                            AppAction.OPEN -> stringResource(R.string.open)
                        }
                    )
                }
            }

            if (installing) {
                Spacer(Modifier.height(6.dp))
                if (progress > 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AppIcon(app: StoreApp, size: Int) {
    if (app.iconUrl != null) {
        AsyncImage(
            model = app.iconUrl,
            contentDescription = stringResource(R.string.icon_of, app.name),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(app.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun variantKey(app: StoreApp): String = "${app.id}\u0000${app.sourceName}"

private fun sourcePreferenceKey(packageName: String): String = SOURCE_KEY_PREFIX + packageName
