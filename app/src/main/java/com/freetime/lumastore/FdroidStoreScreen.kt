package com.freetime.lumastore

import android.content.Intent

import me.free_time.design.freetimeGlass

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.freetime.lumastore.data.AppRepository
import com.freetime.lumastore.data.StoreApp
import com.freetime.lumastore.install.ApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class FdroidStoreView { APPS, UPDATES }
private enum class FdroidSourceFilter { ALL, OPEN_SOURCE, CLOSED_SOURCE }

private const val FDROID_SOURCE_PREFERENCES = "luma_store_source_preferences"
private const val FDROID_SOURCE_KEY_PREFIX = "source_"

@Composable
fun FdroidStoreScreen(
    repository: AppRepository,
    installedAppsRevision: Int,
    installedVersionCode: (String) -> Long?,
    installedVersionName: (String) -> String?,
    openInstalledApp: (String) -> Boolean,
    canInstallPackages: () -> Boolean,
    requestInstallPermission: () -> Unit,
    install: (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> ApkInstaller.DownloadHandle
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val sourcePreferences = remember(context) {
        context.applicationContext.getSharedPreferences(FDROID_SOURCE_PREFERENCES, Context.MODE_PRIVATE)
    }

    val initialCachedApps = remember(repository) { repository.loadCachedApps() }
    var apps by remember { mutableStateOf(initialCachedApps) }
    var loading by remember { mutableStateOf(initialCachedApps.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedSourceFilter by remember { mutableStateOf<String?>(null) }
    var sourceCodeFilter by remember { mutableStateOf(FdroidSourceFilter.ALL) }
    var storeView by remember { mutableStateOf(FdroidStoreView.APPS) }
    var selectedAppId by remember { mutableStateOf<String?>(null) }
    var selectedScreenshotUrl by remember { mutableStateOf<String?>(null) }
    var installingKey by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val selectedSources = remember { mutableStateMapOf<String, String>() }

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
    val availableSources = remember(apps) { apps.map { it.sourceName }.distinct().sortedBy { it.lowercase() } }

    LaunchedEffect(apps) {
        variantsById.forEach { (id, variants) ->
            val savedSource = sourcePreferences.getString(fdroidSourcePreferenceKey(id), null)
            if (savedSource != null && variants.any { it.sourceName == savedSource }) {
                selectedSources[id] = savedSource
            } else {
                selectedSources.remove(id)
            }
        }
        if (selectedSourceFilter != null && selectedSourceFilter !in availableSources) selectedSourceFilter = null
    }

    fun selectSource(appId: String, sourceName: String) {
        selectedSources[appId] = sourceName
        sourcePreferences.edit().putString(fdroidSourcePreferenceKey(appId), sourceName).apply()
        variantsById[appId]?.firstOrNull { it.sourceName == sourceName }?.let(repository::rememberPreferredSource)
    }

    val selectionSnapshot = selectedSources.toMap()
    val selectedApps = remember(variantsById, selectedSourceFilter, sourceCodeFilter, selectionSnapshot) {
        variantsById.mapNotNull { (id, variants) ->
            val matches = variants.filter { variant ->
                val sourceMatches = selectedSourceFilter == null || variant.sourceName == selectedSourceFilter
                val codeMatches = when (sourceCodeFilter) {
                    FdroidSourceFilter.ALL -> true
                    FdroidSourceFilter.OPEN_SOURCE -> !variant.closedSource
                    FdroidSourceFilter.CLOSED_SOURCE -> variant.closedSource
                }
                sourceMatches && codeMatches
            }
            if (matches.isEmpty()) null
            else matches.firstOrNull { it.sourceName == selectionSnapshot[id] } ?: matches.maxByOrNull { it.versionCode }
        }.sortedBy { it.name.lowercase() }
    }

    val installedCodes = remember(selectedApps, installedAppsRevision) {
        selectedApps.associate { it.id to installedVersionCode(it.id) }
    }
    val installedNames = remember(selectedApps, installedAppsRevision) {
        selectedApps.associate { it.id to installedVersionName(it.id) }
    }
    val updateApps = remember(selectedApps, installedCodes) {
        selectedApps.filter { app ->
            val code = installedCodes[app.id]
            code != null && app.versionCode > code
        }
    }
    val categories = remember(selectedApps) {
        selectedApps.flatMap { it.categories }.distinct().sortedBy { it.lowercase() }
    }
    val filteredApps = remember(selectedApps, query, selectedCategory, storeView, installedCodes) {
        selectedApps.filter { app ->
            val textMatches = query.isBlank() ||
                app.name.contains(query, true) ||
                app.summary.contains(query, true) ||
                app.id.contains(query, true) ||
                app.categories.any { it.contains(query, true) }
            val categoryMatches = selectedCategory == null || selectedCategory in app.categories
            val viewMatches = when (storeView) {
                FdroidStoreView.APPS -> true
                FdroidStoreView.UPDATES -> {
                    val code = installedCodes[app.id]
                    code != null && app.versionCode > code
                }
            }
            textMatches && categoryMatches && viewMatches
        }
    }

    fun runAppAction(app: StoreApp) {
        val installedCode = installedCodes[app.id] ?: installedVersionCode(app.id)
        if (installedCode != null && app.versionCode <= installedCode) {
            if (!openInstalledApp(app.id)) error = context.getString(R.string.app_not_launchable, app.name)
            return
        }
        if (!canInstallPackages()) {
            requestInstallPermission()
            return
        }
        repository.rememberPreferredSource(app)
        val key = fdroidVariantKey(app)
        installingKey = key
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

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), maxLines = 1) },
                actions = {
                    TextButton(onClick = { refreshKey++ }, enabled = !refreshing) {
                        Text(stringResource(R.string.refresh))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "search") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(if (storeView == FdroidStoreView.UPDATES) stringResource(R.string.search_updates) else stringResource(R.string.search_apps)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                    )
                    if (refreshing) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = storeView == FdroidStoreView.APPS,
                                onClick = { storeView = FdroidStoreView.APPS },
                                label = { Text(stringResource(R.string.apps)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = storeView == FdroidStoreView.UPDATES,
                                onClick = { storeView = FdroidStoreView.UPDATES },
                                label = { Text(stringResource(R.string.updates_count, updateApps.size)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = sourceCodeFilter == FdroidSourceFilter.ALL,
                                onClick = { sourceCodeFilter = FdroidSourceFilter.ALL },
                                label = { Text(stringResource(R.string.all_apps)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = sourceCodeFilter == FdroidSourceFilter.OPEN_SOURCE,
                                onClick = { sourceCodeFilter = FdroidSourceFilter.OPEN_SOURCE },
                                label = { Text(stringResource(R.string.open_source)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = sourceCodeFilter == FdroidSourceFilter.CLOSED_SOURCE,
                                onClick = { sourceCodeFilter = FdroidSourceFilter.CLOSED_SOURCE },
                                label = { Text(stringResource(R.string.closed_source)) }
                            )
                        }
                    }
                    if (availableSources.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedSourceFilter == null,
                                    onClick = { selectedSourceFilter = null },
                                    label = { Text(stringResource(R.string.all_sources)) }
                                )
                            }
                            items(availableSources, key = { it }) { source ->
                                FilterChip(
                                    selected = selectedSourceFilter == source,
                                    onClick = { selectedSourceFilter = if (selectedSourceFilter == source) null else source },
                                    label = { Text(source) }
                                )
                            }
                        }
                    }
                }
            }

            when {
                loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                error != null && apps.isEmpty() -> item(key = "error") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(error ?: stringResource(R.string.unknown_error))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { refreshKey++ }) { Text(stringResource(R.string.try_again)) }
                    }
                }

                else -> {
                    if (updateApps.isNotEmpty() && storeView == FdroidStoreView.APPS && query.isBlank() && selectedCategory == null) {
                        item(key = "updates_carousel") {
                            FdroidAppCarousel(
                                title = stringResource(R.string.updates_count, updateApps.size),
                                apps = updateApps.take(12),
                                onTitleTap = { storeView = FdroidStoreView.UPDATES },
                                onAppTap = { selectedAppId = it.id }
                            )
                        }
                    }

                    if (storeView == FdroidStoreView.APPS && query.isBlank() && selectedCategory == null && filteredApps.isNotEmpty()) {
                        item(key = "discover_carousel") {
                            FdroidAppCarousel(
                                title = stringResource(R.string.discover_apps),
                                apps = filteredApps.take(12),
                                onTitleTap = { },
                                onAppTap = { selectedAppId = it.id }
                            )
                        }
                    }

                    if (categories.isNotEmpty() && storeView == FdroidStoreView.APPS) {
                        item(key = "categories") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    stringResource(R.string.categories_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    item {
                                        FilterChip(
                                            selected = selectedCategory == null,
                                            onClick = { selectedCategory = null },
                                            label = { Text(stringResource(R.string.all_categories)) }
                                        )
                                    }
                                    items(categories, key = { it }) { category ->
                                        FilterChip(
                                            selected = selectedCategory == category,
                                            onClick = { selectedCategory = if (selectedCategory == category) null else category },
                                            label = { Text(category) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "all_apps_title") {
                        Text(
                            if (storeView == FdroidStoreView.UPDATES) stringResource(R.string.updates_count, filteredApps.size)
                            else stringResource(R.string.browse_all_apps),
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (filteredApps.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (storeView == FdroidStoreView.UPDATES) stringResource(R.string.no_updates_available)
                                    else stringResource(R.string.no_apps_found),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (storeView == FdroidStoreView.UPDATES) stringResource(R.string.apps_up_to_date)
                                    else stringResource(R.string.no_apps_for_filters),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredApps, key = { it.id }) { app ->
                            FdroidAppListRow(
                                app = app,
                                installedVersionName = installedNames[app.id],
                                installing = installingKey == fdroidVariantKey(app),
                                progress = installProgress,
                                onClick = { selectedAppId = app.id },
                                onAction = { runAppAction(app) },
                                actionLabel = when {
                                    installedCodes[app.id] == null -> stringResource(R.string.install)
                                    app.versionCode > (installedCodes[app.id] ?: Long.MAX_VALUE) -> stringResource(R.string.update)
                                    else -> stringResource(R.string.open)
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 92.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }

    selectedAppId?.let { appId ->
        val variants = variantsById[appId].orEmpty()
            .filter { variant ->
                val sourceMatches = selectedSourceFilter == null || variant.sourceName == selectedSourceFilter
                val codeMatches = when (sourceCodeFilter) {
                    FdroidSourceFilter.ALL -> true
                    FdroidSourceFilter.OPEN_SOURCE -> !variant.closedSource
                    FdroidSourceFilter.CLOSED_SOURCE -> variant.closedSource
                }
                sourceMatches && codeMatches
            }
            .sortedBy { it.sourceName.lowercase() }
        val selectedSource = selectedSources[appId]
        val app = variants.firstOrNull { it.sourceName == selectedSource } ?: variants.maxByOrNull { it.versionCode }
        if (app != null) {
            val installedCode = installedVersionCode(app.id)
            val installedName = installedVersionName(app.id)
            val label = when {
                installedCode == null -> stringResource(R.string.install)
                app.versionCode > installedCode -> stringResource(R.string.update)
                else -> stringResource(R.string.open)
            }
            AppDetailsScreen(
                app = app,
                variants = variants,
                installedVersionName = installedName,
                actionLabel = label,
                installing = installingKey == fdroidVariantKey(app),
                progress = installProgress,
                onBack = { selectedAppId = null },
                onAction = { runAppAction(app) },
                onSourceSelected = { variant -> selectSource(appId, variant.sourceName) },
                onScreenshotSelected = { selectedScreenshotUrl = it },
                onOpenUri = { uriHandler.openUri(it) },
                onShare = { shared ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, shared.name)
                        putExtra(Intent.EXTRA_TEXT, shared.websiteUrl ?: shared.sourceCodeUrl ?: shared.apkUrl)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
                },
                isUpdateIgnored = repository.isUpdateIgnored(app),
                onIgnoreUpdateToggle = {
                    if (repository.isUpdateIgnored(app)) repository.clearIgnoredVersion(app.id) else repository.ignoreVersion(app)
                },
                signatureConflict = repository.signatureConflict(variants),
                verifiedMetadata = repository.verifiedMetadata(app),
                similarApps = repository.similarApps(app),
                onSimilarAppSelected = { selectedAppId = it.id }
            )
        }
    }

    selectedScreenshotUrl?.let { screenshotUrl ->
        val dialogShape = RoundedCornerShape(28.dp)
        AlertDialog(
            onDismissRequest = { selectedScreenshotUrl = null },
            modifier = Modifier.freetimeGlass(dialogShape, interactive = false),
            shape = dialogShape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.screenshot)) },
            text = {
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 650.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = screenshotUrl,
                        contentDescription = stringResource(R.string.enlarged_screenshot),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedScreenshotUrl = null }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

@Composable
private fun FdroidAppCarousel(
    title: String,
    apps: List<StoreApp>,
    onTitleTap: () -> Unit,
    onAppTap: (StoreApp) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onTitleTap).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(36.dp).clickable(onClick = onTitleTap)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.symbol_forward_chevron), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(apps, key = { it.id }) { app ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.width(80.dp).clickable { onAppTap(app) }
                ) {
                    FdroidStoreIcon(app = app, size = 76)
                    Text(
                        app.name,
                        style = MaterialTheme.typography.bodySmall,
                        minLines = 2,
                        maxLines = 2,
                        lineHeight = 14.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FdroidAppListRow(
    app: StoreApp,
    installedVersionName: String?,
    installing: Boolean,
    progress: Int,
    actionLabel: String,
    onClick: () -> Unit,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FdroidStoreIcon(app = app, size = 60)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        app.summary.ifBlank { app.id },
                        style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun FdroidStoreIcon(app: StoreApp, size: Int) {
    if (app.iconUrl != null) {
        FallbackAppIcon(app = app, size = size)
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                app.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun fdroidVariantKey(app: StoreApp): String = "${app.id}\u0000${app.sourceName}"

private fun fdroidSourcePreferenceKey(packageName: String): String =
    FDROID_SOURCE_KEY_PREFIX + packageName


@Composable
private fun FallbackAppIcon(app: StoreApp, size: Int, index: Int = 0) {
    val candidates = app.iconUrls.ifEmpty { listOfNotNull(app.iconUrl) }
    if (index >= candidates.size) {
        Box(
            modifier = Modifier.size(size.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(app.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
        }
        return
    }
    SubcomposeAsyncImage(
        model = candidates[index],
        contentDescription = stringResource(R.string.icon_of, app.name),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(MaterialTheme.shapes.large),
        loading = { SubcomposeAsyncImageContent() },
        success = { SubcomposeAsyncImageContent() },
        error = { FallbackAppIcon(app, size, index + 1) }
    )
}
