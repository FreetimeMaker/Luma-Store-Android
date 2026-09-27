package com.freetime.lumastore


import android.content.Intent
import android.content.Context

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.freetime.lumastore.data.AppRepository
import com.freetime.lumastore.data.AppRatingSummary
import com.freetime.lumastore.data.LumaStoreApi
import com.freetime.lumastore.data.StoreApp
import com.freetime.lumastore.install.ApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Composable
fun FdroidDiscoverScreen(
    repository: AppRepository,
    installedVersionCode: (String) -> Long?,
    installedVersionName: (String) -> String?,
    openInstalledApp: (String) -> Boolean,
    canInstallPackages: () -> Boolean,
    requestInstallPermission: () -> Unit,
    install: (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> ApkInstaller.DownloadHandle
) {
    var apps by remember(repository) { mutableStateOf(repository.currentApps()) }
    var loading by remember { mutableStateOf(apps.isEmpty()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedAppId by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var usingCachedData by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val recentPreferences = remember(context) { context.getSharedPreferences("luma_recent_apps", Context.MODE_PRIVATE) }
    var recentIds by remember { mutableStateOf(recentPreferences.getString("ids", "").orEmpty().split("\n").filter { it.isNotBlank() }) }
    var selectedDeveloper by remember { mutableStateOf<String?>(null) }
    var selectedLicense by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf("updated") }
    var ratingSummaries by remember { mutableStateOf<Map<String, AppRatingSummary>>(emptyMap()) }
    var openCollection by remember { mutableStateOf<Pair<String, List<StoreApp>>?>(null) }

    LaunchedEffect(refreshKey) {
        if (apps.isEmpty()) loading = true
        val result = withContext(Dispatchers.IO) {
            repository.loadApps(forceRefresh = refreshKey > 0)
        }
        result.onSuccess {
            apps = it
            // A normal initial load may intentionally come from the local cache without
            // contacting any repository. Do not label that as offline just because old
            // source-health entries are unsuccessful.
            usingCachedData = refreshKey > 0 &&
                repository.enabledSources().isNotEmpty() &&
                repository.enabledSources().none { repository.sourceHealth(it)?.successful == true }
        }.onFailure {
            apps = repository.currentApps()
            // Only show the offline/cache warning after an actual network refresh failed.
            usingCachedData = refreshKey > 0 && apps.isNotEmpty()
        }
        loading = false
    }

    val appVariants = remember(apps) { apps.groupBy { it.id } }
    val selectedApps = remember(appVariants) {
        appVariants.mapNotNull { (packageName, variants) ->
            repository.preferredVariant(packageName, variants)
        }.sortedBy { it.name.lowercase() }
    }
    LaunchedEffect(selectedApps) {
        val lumaApps = selectedApps.filter { it.sourceName.contains("Luma", ignoreCase = true) }.take(40)
        if (lumaApps.isNotEmpty()) {
            ratingSummaries = coroutineScope {
                lumaApps.map { app ->
                    async {
                        runCatching { LumaStoreApi.ratings(app.id) }.getOrNull()?.let { app.id to it }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
        }
    }

    val newestApps = remember(selectedApps) {
        selectedApps.filter { it.addedTimestamp != null }.sortedByDescending { it.addedTimestamp }.take(12)
    }
    val recentlyUpdatedApps = remember(selectedApps) {
        selectedApps.filter { it.lastUpdatedTimestamp != null }.sortedByDescending { it.lastUpdatedTimestamp }.take(12)
    }
    val privacyApps = remember(selectedApps) {
        selectedApps.filter { app ->
            app.antiFeatures.isEmpty() && !app.closedSource &&
                app.categories.none { it.contains("tracking", true) }
        }.take(12)
    }
    val gameApps = remember(selectedApps) {
        selectedApps.filter { app -> app.categories.any { it.contains("game", true) } }.take(12)
    }
    val trendingApps = remember(selectedApps) {
        selectedApps
            .filter { (it.downloadCount ?: 0L) > 0L }
            .sortedWith(
                compareByDescending<StoreApp> { it.downloadCount ?: 0L }
                    .thenByDescending { it.lastUpdatedTimestamp ?: 0L }
            )
            .take(12)
    }
    val recommendedApps = remember(selectedApps) {
        selectedApps
            .sortedWith(
                compareByDescending<StoreApp> {
                    var score = 0L
                    if (it.antiFeatures.isEmpty()) score += 1_000_000L
                    if (!it.closedSource) score += 500_000L
                    score + (it.downloadCount ?: 0L).coerceAtMost(250_000L) +
                        ((it.lastUpdatedTimestamp ?: 0L) / 100_000_000L)
                }
            )
            .take(12)
    }
    val categories = remember(selectedApps) {
        selectedApps.flatMap { it.categories }.distinct().sortedBy { it.lowercase() }
    }
    val developers = remember(selectedApps) { selectedApps.mapNotNull { it.authorName?.takeIf(String::isNotBlank) }.distinct().sorted() }
    val licenses = remember(selectedApps) { selectedApps.mapNotNull { it.license?.takeIf(String::isNotBlank) }.distinct().sorted() }
    val recentApps = remember(selectedApps, recentIds) {
        recentIds.mapNotNull { id -> selectedApps.firstOrNull { it.id == id } }.take(10)
    }
    val shownApps = remember(selectedApps, selectedCategory, selectedDeveloper, selectedLicense, sortMode) {
        selectedApps
            .asSequence()
            .filter { selectedCategory == null || selectedCategory in it.categories }
            .filter { selectedDeveloper == null || it.authorName == selectedDeveloper }
            .filter { selectedLicense == null || it.license == selectedLicense }
            .sortedWith(
                when (sortMode) {
                    "new" -> compareByDescending<StoreApp> { it.addedTimestamp ?: 0L }
                    "downloads" -> compareByDescending<StoreApp> { it.downloadCount ?: 0L }
                    "name" -> compareBy { it.name.lowercase() }
                    else -> compareByDescending<StoreApp> { it.lastUpdatedTimestamp ?: 0L }
                }
            )
            .toList()
    }

    fun openFromDiscover(app: StoreApp) {
        selectedAppId = app.id
        recentIds = (listOf(app.id) + recentIds.filterNot { it == app.id }).take(10)
        recentPreferences.edit().putString("ids", recentIds.joinToString("\n")).apply()
    }


    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), maxLines = 1) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loading && apps.isNotEmpty(),
            onRefresh = {
                loading = true
                refreshKey++
            },
            modifier = Modifier.fillMaxSize()
        ) {
        if (loading && apps.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(7) { SkeletonAppRow() }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                if (usingCachedData) {
                    item("offline_status") {
                        Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Text(
                                stringResource(R.string.offline_cached_data),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                if (recentApps.isNotEmpty()) {
                    item("recently_viewed") {
                        DiscoverCarousel(
                            title = stringResource(R.string.recently_viewed),
                            apps = recentApps,
                            onAppTap = { openFromDiscover(it) },
                            onShowAll = { openCollection = context.getString(R.string.recently_viewed) to recentApps }
                        )
                    }
                }

                item("discover_filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.discover_filters),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            item {
                                AssistChip(
                                    onClick = { sortMode = if (sortMode == "updated") "downloads" else "updated" },
                                    label = { Text(if (sortMode == "downloads") stringResource(R.string.sort_downloads) else stringResource(R.string.sort_updated)) }
                                )
                            }
                            if (developers.isNotEmpty()) {
                                item {
                                    AssistChip(
                                        onClick = {
                                            val index = developers.indexOf(selectedDeveloper)
                                            selectedDeveloper = if (index < 0) developers.first() else developers.getOrNull(index + 1)
                                        },
                                        label = { Text(selectedDeveloper ?: stringResource(R.string.all_developers)) }
                                    )
                                }
                            }
                            if (licenses.isNotEmpty()) {
                                item {
                                    AssistChip(
                                        onClick = {
                                            val index = licenses.indexOf(selectedLicense)
                                            selectedLicense = if (index < 0) licenses.first() else licenses.getOrNull(index + 1)
                                        },
                                        label = { Text(selectedLicense ?: stringResource(R.string.all_licenses)) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (shownApps.isNotEmpty()) {
                    item("discover_carousel") {
                        DiscoverCarousel(
                            title = stringResource(R.string.discover_apps),
                            apps = shownApps.take(12),
                            onAppTap = { openFromDiscover(it) }
                        )
                    }
                }

                if (recommendedApps.isNotEmpty()) {
                    item("recommended_apps") {
                        DiscoverCarousel(
                            title = stringResource(R.string.recommended_for_you),
                            apps = recommendedApps,
                            onAppTap = { openFromDiscover(it) },
                            onShowAll = { openCollection = context.getString(R.string.recommended_for_you) to recommendedApps }
                        )
                    }
                }

                if (trendingApps.isNotEmpty()) {
                    item("trending_apps") {
                        DiscoverCarousel(
                            title = stringResource(R.string.trending_apps),
                            apps = trendingApps,
                            onAppTap = { openFromDiscover(it) },
                            onShowAll = { openCollection = context.getString(R.string.trending_apps) to trendingApps }
                        )
                    }
                }

                if (repository.discoverSectionEnabled("new") && newestApps.isNotEmpty()) {
                    item("new_apps") {
                        DiscoverCarousel(
                            title = stringResource(R.string.new_apps),
                            apps = newestApps,
                            onAppTap = { openFromDiscover(it) },
                            onShowAll = { openCollection = context.getString(R.string.new_apps) to newestApps }
                        )
                    }
                }

                if (repository.discoverSectionEnabled("recent") && recentlyUpdatedApps.isNotEmpty()) {
                    item("recently_updated") {
                        DiscoverCarousel(
                            title = stringResource(R.string.recently_updated),
                            apps = recentlyUpdatedApps,
                            onAppTap = { openFromDiscover(it) },
                            onShowAll = { openCollection = context.getString(R.string.recently_updated) to recentlyUpdatedApps }
                        )
                    }
                }


                if (repository.discoverSectionEnabled("privacy") && privacyApps.isNotEmpty()) {
                    item("privacy_collection") {
                        DiscoverCarousel(
                            title = stringResource(R.string.privacy_collection),
                            apps = privacyApps,
                            onAppTap = { openFromDiscover(it) },
                            onShowAll = { openCollection = context.getString(R.string.privacy_collection) to privacyApps }
                        )
                    }
                }

                if (repository.discoverSectionEnabled("games") && gameApps.isNotEmpty()) {
                    item("games_collection") {
                        DiscoverCarousel(
                            title = stringResource(R.string.games_collection),
                            apps = gameApps,
                            onAppTap = { openFromDiscover(it) },
                            onShowAll = { openCollection = context.getString(R.string.games_collection) to gameApps }
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
                    BrowseAppRow(app = app, rating = ratingSummaries[app.id], onClick = { openFromDiscover(app) })
                    HorizontalDivider(modifier = Modifier.padding(start = 92.dp))
                }
            }
        }
        }
    }

    openCollection?.let { collection ->
        CollectionScreen(
            title = collection.first,
            apps = collection.second,
            ratings = ratingSummaries,
            onBack = { openCollection = null },
            onAppSelected = {
                openCollection = null
                openFromDiscover(it)
            }
        )
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
    initialAppId: String? = null,
    installedVersionCode: (String) -> Long?,
    installedVersionName: (String) -> String?,
    openInstalledApp: (String) -> Boolean,
    canInstallPackages: () -> Boolean,
    requestInstallPermission: () -> Unit,
    install: (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> ApkInstaller.DownloadHandle
) {
    var apps by remember(repository) { mutableStateOf(repository.currentApps()) }
    var query by remember { mutableStateOf("") }
    var selectedAppId by remember(initialAppId) { mutableStateOf(initialAppId) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedDeveloper by remember { mutableStateOf<String?>(null) }
    var selectedLicense by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf("name") }

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
    val searchDevelopers = remember(selectedApps) { selectedApps.mapNotNull { it.authorName?.takeIf(String::isNotBlank) }.distinct().sorted() }
    val searchLicenses = remember(selectedApps) { selectedApps.mapNotNull { it.license?.takeIf(String::isNotBlank) }.distinct().sorted() }
    val results = remember(selectedApps, query, selectedCategory, selectedDeveloper, selectedLicense, sortMode) {
        if (query.isBlank()) emptyList()
        else selectedApps.asSequence()
            .filter { app ->
                app.name.contains(query, true) ||
                    app.summary.contains(query, true) ||
                    app.description.contains(query, true) ||
                    app.id.contains(query, true) ||
                    app.categories.any { it.contains(query, true) } ||
                    app.authorName?.contains(query, true) == true
            }
            .filter { selectedCategory == null || selectedCategory in it.categories }
            .filter { selectedDeveloper == null || it.authorName == selectedDeveloper }
            .filter { selectedLicense == null || it.license == selectedLicense }
            .sortedWith(
                when (sortMode) {
                    "downloads" -> compareByDescending<StoreApp> { it.downloadCount ?: 0L }
                    "updated" -> compareByDescending<StoreApp> { it.lastUpdatedTimestamp ?: 0L }
                    "new" -> compareByDescending<StoreApp> { it.addedTimestamp ?: 0L }
                    else -> compareBy { it.name.lowercase() }
                }
            )
            .toList()
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
            } else {
                item("search_filters") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            AssistChip(
                                onClick = { sortMode = when (sortMode) { "name" -> "downloads"; "downloads" -> "updated"; "updated" -> "new"; else -> "name" } },
                                label = { Text(stringResource(when (sortMode) { "downloads" -> R.string.sort_downloads; "updated" -> R.string.sort_updated; "new" -> R.string.sort_new; else -> R.string.sort_name })) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    val values = categories
                                    val index = values.indexOf(selectedCategory)
                                    selectedCategory = if (index < 0) values.firstOrNull() else values.getOrNull(index + 1)
                                },
                                label = { Text(selectedCategory ?: stringResource(R.string.all_categories)) }
                            )
                        }
                        if (searchDevelopers.isNotEmpty()) item {
                            AssistChip(
                                onClick = {
                                    val index = searchDevelopers.indexOf(selectedDeveloper)
                                    selectedDeveloper = if (index < 0) searchDevelopers.first() else searchDevelopers.getOrNull(index + 1)
                                },
                                label = { Text(selectedDeveloper ?: stringResource(R.string.all_developers)) }
                            )
                        }
                        if (searchLicenses.isNotEmpty()) item {
                            AssistChip(
                                onClick = {
                                    val index = searchLicenses.indexOf(selectedLicense)
                                    selectedLicense = if (index < 0) searchLicenses.first() else searchLicenses.getOrNull(index + 1)
                                },
                                label = { Text(selectedLicense ?: stringResource(R.string.all_licenses)) }
                            )
                        }
                    }
                }
            }
            if (query.isNotBlank() && results.isEmpty()) {
                item("no_results") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_apps_found))
                    }
                }
            } else if (query.isNotBlank()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionScreen(
    title: String,
    apps: List<StoreApp>,
    ratings: Map<String, AppRatingSummary>,
    onBack: () -> Unit,
    onAppSelected: (StoreApp) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.symbol_back_chevron), style = MaterialTheme.typography.headlineSmall) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(apps, key = { it.id }) { app ->
                BrowseAppRow(app = app, rating = ratings[app.id], onClick = { onAppSelected(app) })
                HorizontalDivider(modifier = Modifier.padding(start = 92.dp))
            }
        }
    }
}

@Composable
private fun SkeletonAppRow() {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)))
            Box(Modifier.fillMaxWidth(0.85f).height(12.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)))
            Box(Modifier.fillMaxWidth(0.68f).height(12.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)))
        }
    }
}

@Composable
private fun DiscoverCarousel(
    title: String,
    apps: List<StoreApp>,
    onAppTap: (StoreApp) -> Unit,
    onShowAll: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            onShowAll?.let { TextButton(onClick = it) { Text(stringResource(R.string.show_all)) } }
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

private fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000L -> "${value / 1_000_000L}M+"
    value >= 1_000L -> "${value / 1_000L}K+"
    else -> value.toString()
}

@Composable
private fun BrowseAppRow(app: StoreApp, rating: AppRatingSummary? = null, onClick: () -> Unit) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.app_version_source, app.version, app.sourceName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    app.downloadCount?.takeIf { it > 0 }?.let { downloads ->
                        Text(
                            stringResource(R.string.download_count_compact, formatCompactCount(downloads)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    rating?.takeIf { it.count > 0 }?.let {
                        Text(
                            stringResource(R.string.rating_compact, it.average, it.count),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
    install: (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> ApkInstaller.DownloadHandle,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
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

    if (selectedDeveloper != null && developerSelectedAppId == null) {
        selectedDeveloper?.let { developer ->
            DeveloperAppsScreen(
                developerName = developer,
                apps = appVariants.values.flatten(),
                onBack = { selectedDeveloper = null },
                onAppSelected = { selected ->
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
                    if (developerSelectedAppId != null) {
                        developerSelectedAppId = null
                        selectedSource = null
                    } else {
                        onDismiss()
                    }
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
                dataSaver = repository.dataSaverEnabled(),
                onOpenUri = { uri -> runCatching { uriHandler.openUri(uri) } },
                onShare = { shared ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, shared.name)
                        putExtra(Intent.EXTRA_TEXT, "https://luma.free-time.me/" + android.net.Uri.encode(shared.id))
                    }
                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
                },
                onDeveloperSelected = { selectedDeveloper = it },
                isUpdateIgnored = repository.isUpdateIgnored(app),
                onIgnoreUpdateToggle = {
                    if (repository.isUpdateIgnored(app)) repository.clearIgnoredVersion(app.id) else repository.ignoreVersion(app)
                },
                signatureConflict = repository.signatureConflict(variants),
                verifiedMetadata = repository.verifiedMetadata(app),
                similarApps = repository.similarApps(app),
                onSimilarAppSelected = { developerSelectedAppId = it.id; selectedSource = it.sourceName }
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
