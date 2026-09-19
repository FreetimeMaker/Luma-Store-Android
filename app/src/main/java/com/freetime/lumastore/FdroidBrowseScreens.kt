package com.freetime.lumastore

import android.os.Build

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FdroidDiscoverScreen(
    repository: AppRepository,
    installedVersionCode: (String) -> Long?,
    installedVersionName: (String) -> String?,
    openInstalledApp: (String) -> Boolean,
    canInstallPackages: () -> Boolean,
    requestInstallPermission: () -> Unit,
    install: (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> Unit
) {
    var apps by remember(repository) { mutableStateOf(repository.currentApps()) }
    var loading by remember { mutableStateOf(apps.isEmpty()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedAppId by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        if (apps.isEmpty()) loading = true
        val result = withContext(Dispatchers.IO) {
            repository.loadApps(forceRefresh = refreshKey > 0)
        }
        result.onSuccess { apps = it }
        loading = false
    }

    val appVariants = remember(apps) { apps.groupBy { it.id } }
    val selectedApps = remember(appVariants) {
        appVariants.mapNotNull { (packageName, variants) ->
            repository.preferredVariant(packageName, variants)
        }.sortedBy { it.name.lowercase() }
    }
    val newestApps = remember(selectedApps) {
        selectedApps.filter { it.addedTimestamp != null }.sortedByDescending { it.addedTimestamp }.take(12)
    }
    val recentlyUpdatedApps = remember(selectedApps) {
        selectedApps.filter { it.lastUpdatedTimestamp != null }.sortedByDescending { it.lastUpdatedTimestamp }.take(12)
    }
    val categories = remember(selectedApps) {
        selectedApps.flatMap { it.categories }.distinct().sortedBy { it.lowercase() }
    }
    val shownApps = remember(selectedApps, selectedCategory) {
        val category = selectedCategory
        if (category == null) selectedApps else selectedApps.filter { category in it.categories }
    }


    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), maxLines = 1) },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (loading && apps.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                if (shownApps.isNotEmpty()) {
                    item("discover_carousel") {
                        DiscoverCarousel(
                            title = stringResource(R.string.discover_apps),
                            apps = shownApps.take(12),
                            onAppTap = { selectedAppId = it.id }
                        )
                    }
                }

                if (newestApps.isNotEmpty()) {
                    item("new_apps") {
                        DiscoverCarousel(
                            title = stringResource(R.string.new_apps),
                            apps = newestApps,
                            onAppTap = { selectedAppId = it.id }
                        )
                    }
                }

                if (recentlyUpdatedApps.isNotEmpty()) {
                    item("recently_updated") {
                        DiscoverCarousel(
                            title = stringResource(R.string.recently_updated),
                            apps = recentlyUpdatedApps,
                            onAppTap = { selectedAppId = it.id }
                        )
                    }
                }

                if (categories.isNotEmpty()) {
                    item("categories") {
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
                                    AssistChip(
                                        onClick = { selectedCategory = null },
                                        label = { Text(stringResource(R.string.all_categories)) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent)
                                    )
                                }
                                items(categories, key = { it }) { category ->
                                    AssistChip(
                                        onClick = {
                                            selectedCategory = if (selectedCategory == category) null else category
                                        },
                                        label = { Text(category) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }

                item("all_apps_title") {
                    Text(
                        selectedCategory ?: stringResource(R.string.browse_all_apps),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                items(shownApps, key = { it.id }) { app ->
                    BrowseAppRow(app = app, onClick = { selectedAppId = app.id })
                    HorizontalDivider(modifier = Modifier.padding(start = 92.dp))
                }
            }
        }
    }

    FdroidDetailsHost(
        repository = repository,
        selectedAppId = selectedAppId,
        appVariants = appVariants,
        installedVersionCode = installedVersionCode,
        installedVersionName = installedVersionName,
        openInstalledApp = openInstalledApp,
        canInstallPackages = canInstallPackages,
        requestInstallPermission = requestInstallPermission,
        install = install,
        onDismiss = { selectedAppId = null }
    )
}

@Composable
fun FdroidSearchScreen(
    repository: AppRepository,
    installedVersionCode: (String) -> Long?,
    installedVersionName: (String) -> String?,
    openInstalledApp: (String) -> Boolean,
    canInstallPackages: () -> Boolean,
    requestInstallPermission: () -> Unit,
    install: (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> Unit
) {
    var apps by remember(repository) { mutableStateOf(repository.currentApps()) }
    var query by remember { mutableStateOf("") }
    var selectedAppId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            withContext(Dispatchers.IO) { repository.loadApps() }.onSuccess { apps = it }
        }
    }

    val appVariants = remember(apps) { apps.groupBy { it.id } }
    val selectedApps = remember(appVariants) {
        appVariants.mapNotNull { (packageName, variants) ->
            repository.preferredVariant(packageName, variants)
        }.sortedBy { it.name.lowercase() }
    }
    val categories = remember(selectedApps) {
        selectedApps.flatMap { it.categories }.distinct().sortedBy { it.lowercase() }
    }
    val results = remember(selectedApps, query) {
        if (query.isBlank()) emptyList()
        else selectedApps.filter { app ->
            app.name.contains(query, true) ||
                app.summary.contains(query, true) ||
                app.description.contains(query, true) ||
                app.id.contains(query, true) ||
                app.categories.any { it.contains(query, true) }
        }
    }
    val matchingCategories = remember(categories, query) {
        if (query.isBlank()) categories else categories.filter { it.contains(query, true) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (query.isBlank()) {
                if (matchingCategories.isNotEmpty()) {
                    item("search_categories_title") {
                        Text(
                            stringResource(R.string.categories_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    items(matchingCategories, key = { it }) { category ->
                        Text(
                            category,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { query = category }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    }
                }
            } else if (results.isEmpty()) {
                item("no_results") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_apps_found))
                    }
                }
            } else {
                items(results, key = { it.id }) { app ->
                    BrowseAppRow(app = app, onClick = { selectedAppId = app.id })
                    HorizontalDivider(modifier = Modifier.padding(start = 92.dp))
                }
            }
        }
    }

    FdroidDetailsHost(
        repository = repository,
        selectedAppId = selectedAppId,
        appVariants = appVariants,
        installedVersionCode = installedVersionCode,
        installedVersionName = installedVersionName,
        openInstalledApp = openInstalledApp,
        canInstallPackages = canInstallPackages,
        requestInstallPermission = requestInstallPermission,
        install = install,
        onDismiss = { selectedAppId = null }
    )
}

@Composable
private fun DiscoverCarousel(
    title: String,
    apps: List<StoreApp>,
    onAppTap: (StoreApp) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(apps, key = { it.id }) { app ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.width(80.dp).clickable { onAppTap(app) }
                ) {
                    BrowseAppIcon(app, 76)
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
private fun BrowseAppRow(app: StoreApp, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowseAppIcon(app, 60)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
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
                    stringResource(R.string.app_version_source, app.version, app.sourceName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BrowseAppIcon(app: StoreApp, size: Int) {
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
            Text(app.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FdroidDetailsHost(
    repository: AppRepository,
    selectedAppId: String?,
    appVariants: Map<String, List<StoreApp>>,
    installedVersionCode: (String) -> Long?,
    installedVersionName: (String) -> String?,
    openInstalledApp: (String) -> Boolean,
    canInstallPackages: () -> Boolean,
    requestInstallPermission: () -> Unit,
    install: (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var selectedSource by remember(selectedAppId) { mutableStateOf<String?>(null) }
    var installingKey by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var selectedDeveloper by remember(selectedAppId) { mutableStateOf<String?>(null) }
    var developerSelectedAppId by remember(selectedAppId) { mutableStateOf<String?>(null) }

    val effectiveSelectedAppId = developerSelectedAppId ?: selectedAppId
    val variants = effectiveSelectedAppId?.let { appVariants[it] }.orEmpty().sortedBy { it.sourceName }
    val installedCodeForSelection = effectiveSelectedAppId?.let(installedVersionCode)
    val app = variants.firstOrNull { it.sourceName == selectedSource }
        ?: effectiveSelectedAppId?.let { repository.preferredVariant(it, variants, installedCodeForSelection) }

    if (selectedDeveloper != null) {
        selectedDeveloper?.let { developer ->
            DeveloperAppsScreen(
                developerName = developer,
                apps = appVariants.values.flatten(),
                onBack = { selectedDeveloper = null },
                onAppSelected = { selected ->
                    selectedDeveloper = null
                    developerSelectedAppId = selected.id
                    selectedSource = selected.sourceName
                }
            )
        }
    } else {
        if (app != null) {
            val installedCode = installedVersionCode(app.id)
            val sdkCompatible = app.minSdk == null || Build.VERSION.SDK_INT >= app.minSdk
            val abiCompatible = app.nativeCode.isEmpty() || android.os.Build.SUPPORTED_ABIS.any { abi -> app.nativeCode.any { it.equals(abi, true) } }
            val actionLabel = when {
                !sdkCompatible -> stringResource(R.string.incompatible_android, app.minSdk ?: 0)
                !abiCompatible -> stringResource(R.string.incompatible_architecture)
                installedCode == null -> stringResource(R.string.install)
                app.versionCode > installedCode -> stringResource(R.string.update)
                else -> stringResource(R.string.open)
            }
            val key = "${app.id}\u0000${app.sourceName}"

            AppDetailsScreen(
                app = app,
                variants = variants,
                installedVersionName = installedVersionName(app.id),
                actionLabel = actionLabel,
                installing = installingKey == key,
                progress = progress,
                onBack = {
                    if (developerSelectedAppId != null) developerSelectedAppId = null else onDismiss()
                },
                onAction = {
                    if (!sdkCompatible || !abiCompatible) {
                        Unit
                    } else if (installedCode != null && app.versionCode <= installedCode) {
                        openInstalledApp(app.id)
                    } else if (!canInstallPackages()) {
                        requestInstallPermission()
                    } else {
                        repository.rememberPreferredSource(app)
                        installingKey = key
                        progress = 0
                        install(
                            app,
                            { progress = it },
                            { progress = 100; installingKey = null },
                            { installingKey = null }
                        )
                    }
                },
                onSourceSelected = {
                    selectedSource = it.sourceName
                    repository.rememberPreferredSource(it)
                },
                onScreenshotSelected = {},
                onOpenUri = { uri -> runCatching { uriHandler.openUri(uri) } },
                onDeveloperSelected = { selectedDeveloper = it }
            )
        }
    }

}


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
