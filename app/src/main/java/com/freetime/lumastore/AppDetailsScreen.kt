package com.freetime.lumastore

import me.free_time.design.freetimeGlass

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.freetime.lumastore.data.StoreApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    app: StoreApp,
    variants: List<StoreApp>,
    installedVersionName: String?,
    actionLabel: String,
    installing: Boolean,
    progress: Int,
    onBack: () -> Unit,
    onAction: () -> Unit,
    onSourceSelected: (StoreApp) -> Unit,
    onScreenshotSelected: (String) -> Unit,
    onOpenUri: (String) -> Unit,
    onDeveloperSelected: (String) -> Unit = {},
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    isUpdateIgnored: Boolean = false,
    onIgnoreUpdateToggle: () -> Unit = {},
    lockedSourceName: String? = null,
    onSourceLockToggle: () -> Unit = {},
    signatureConflict: Boolean = false,
    verifiedMetadata: Boolean = false,
    similarApps: List<StoreApp> = emptyList(),
    onSimilarAppSelected: (StoreApp) -> Unit = {}
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    val scrollState = rememberScrollState()
    val glassShape = RoundedCornerShape(20.dp)
    val actionShape = RoundedCornerShape(50)

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        app.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.symbol_back_chevron), style = MaterialTheme.typography.headlineSmall)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = 0.dp,
                    end = 0.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                )
        ) {
            AppDetailsHeader(
                app = app,
                variants = variants,
                installedVersionName = installedVersionName,
                actionLabel = actionLabel,
                showAction = !(app.id == "com.freetime.lumastore" && installedVersionName != null && actionLabel == stringResource(R.string.open)),
                installing = installing,
                progress = progress,
                onAction = onAction,
                onSourceSelected = onSourceSelected,
                onDeveloperSelected = onDeveloperSelected,
                actionShape = actionShape
            )

            if (signatureConflict) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).freetimeGlass(glassShape, interactive = false),
                    shape = glassShape,
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Text(stringResource(R.string.signature_conflict), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp))
                    Text(stringResource(R.string.signature_conflict_description), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
                }
            } else if (verifiedMetadata) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).freetimeGlass(glassShape, interactive = false),
                    shape = glassShape,
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Text(stringResource(R.string.verified_metadata), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp))
                    Text(stringResource(R.string.verified_metadata_description), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
                }
            }

            DetailsExpandableSection(stringResource(R.string.app_information)) {
                TextButton(onClick = onFavoriteToggle, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isFavorite) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite))
                }
                if (installedVersionName != null && actionLabel == stringResource(R.string.update)) {
                    TextButton(onClick = onIgnoreUpdateToggle, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isUpdateIgnored) stringResource(R.string.stop_ignoring_update) else stringResource(R.string.ignore_update))
                    }
                }
                TextButton(onClick = onSourceLockToggle, modifier = Modifier.fillMaxWidth()) {
                    Text(if (lockedSourceName == app.sourceName) stringResource(R.string.unlock_source) else stringResource(R.string.lock_to_source))
                }
                lockedSourceName?.let { DetailValueRow(stringResource(R.string.source_lock_value, it), it) }
            }

            DetailsExpandableSection(stringResource(R.string.version_history)) {
                Text(stringResource(R.string.version_history_read_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f), modifier = Modifier.padding(bottom = 8.dp))
                if (app.versions.isEmpty()) {
                    Text(stringResource(R.string.no_version_history), style = MaterialTheme.typography.bodyMedium)
                } else {
                    app.versions.forEachIndexed { index, version ->
                        DetailValueRow(
                            if (index == 0) stringResource(R.string.current_version) else stringResource(R.string.older_version),
                            version.versionName + " (" + version.versionCode + ") • " + version.sourceName
                        )
                        version.changelog?.takeIf { it.isNotBlank() }?.let { changelogText ->
                            Text(changelogText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f), modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                }
            }
            app.versionChangelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .freetimeGlass(glassShape, interactive = false),
                    shape = glassShape,
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        stringResource(R.string.whats_new_version, app.version),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
                    )
                    Text(
                        changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    )
                }
            }

            if (similarApps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.similar_apps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(similarApps, key = { it.id }) { similar ->
                            ElevatedCard(
                                onClick = { onSimilarAppSelected(similar) },
                                modifier = Modifier.width(180.dp).freetimeGlass(glassShape, interactive = true),
                                colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(similar.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(similar.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            if (app.screenshotUrls.isNotEmpty()) {
                DetailsScreenshots(app, onScreenshotSelected)
            }

            if (app.antiFeatures.isNotEmpty()) {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .freetimeGlass(glassShape, interactive = false)
                ) {
                    Text(
                        stringResource(R.string.anti_features),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        app.antiFeatures.forEach { antiFeature ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    antiFeature,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                app.antiFeatureReasons[antiFeature]?.takeIf { it.isNotBlank() }?.let { reason ->
                                    Text(
                                        reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ExpandableDescription(app)

            if (
                app.donationUrls.isNotEmpty() ||
                !app.liberapay.isNullOrBlank() ||
                !app.openCollective.isNullOrBlank() ||
                !app.bitcoin.isNullOrBlank() ||
                !app.litecoin.isNullOrBlank()
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .freetimeGlass(glassShape, interactive = false),
                    shape = glassShape,
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        stringResource(R.string.donations),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Top
                    ) {
                        items(app.donationUrls, key = { it }) { url -> DetailActionItem(stringResource(R.string.donation_link), Icons.Filled.AttachMoney) { onOpenUri(url) } }
                        app.liberapay?.let { value -> item("liberapay") {
                            val url = fundingUrl("https://liberapay.com/", value)
                            DetailActionItem(stringResource(R.string.liberapay), Icons.Filled.AttachMoney) { onOpenUri(url) } } }
                        app.openCollective?.let { value -> item("opencollective") {
                            val url = fundingUrl("https://opencollective.com/", value)
                            DetailActionItem(stringResource(R.string.open_collective), Icons.Filled.AttachMoney) { onOpenUri(url) } } }
                        app.bitcoin?.let { value -> item("bitcoin") { DetailActionItem(stringResource(R.string.bitcoin), Icons.Filled.AttachMoney) { onOpenUri(cryptoUri("bitcoin", value)) } } }
                        app.litecoin?.let { value -> item("litecoin") { DetailActionItem(stringResource(R.string.litecoin), Icons.Filled.AttachMoney) { onOpenUri(cryptoUri("litecoin", value)) } } }
                    
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            DetailsExpandableSection(stringResource(R.string.links)) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Top
                ) {
                    app.websiteUrl?.let { url -> item("website") { DetailActionItem(stringResource(R.string.website), Icons.Filled.Language) { onOpenUri(url) } } }
                    app.issueTrackerUrl?.let { url -> item("issues") { DetailActionItem(stringResource(R.string.issue_tracker), Icons.Filled.BugReport) { onOpenUri(url) } } }
                    app.changelogUrl?.let { url -> item("changelog") { DetailActionItem(stringResource(R.string.changelog), Icons.Filled.History) { onOpenUri(url) } } }
                    app.translationUrl?.let { url -> item("translation") { DetailActionItem(stringResource(R.string.translation), Icons.Filled.Translate) { onOpenUri(url) } } }
                    app.sourceCodeUrl?.let { url -> item("source") { DetailActionItem(stringResource(R.string.source_code), Icons.Filled.Code) { onOpenUri(url) } } }
                }
                app.license?.let { DetailValueRow(stringResource(R.string.license), it) }
            }

            if (!app.authorName.isNullOrBlank() || !app.authorEmail.isNullOrBlank() || !app.authorWebsite.isNullOrBlank()) {
                DetailsExpandableSection(stringResource(R.string.developer_contact)) {
                    app.authorName?.let { DetailValueRow(stringResource(R.string.author), it) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Top
                    ) {
                        app.authorWebsite?.let { DetailActionItem(stringResource(R.string.author_website), Icons.Filled.Language) { onOpenUri(it) } }
                        app.authorEmail?.let { DetailActionItem(stringResource(R.string.email), Icons.Filled.Email) { onOpenUri("mailto:$it") } }
                    }
                }
            }

            DetailsExpandableSection(stringResource(R.string.technical_info)) {
                DetailValueRow(stringResource(R.string.package_label, app.id), app.id)
                DetailValueRow(stringResource(R.string.version_with_code, app.version, app.versionCode), app.version)
                DetailValueRow(stringResource(R.string.source), app.sourceName)
                DetailValueRow(
                    stringResource(R.string.app_information),
                    if (app.closedSource) stringResource(R.string.closed_source) else stringResource(R.string.open_source)
                )
                app.downloadSize?.let { DetailValueRow(stringResource(R.string.download_size), formatBytes(it)) }
                app.minSdk?.let { DetailValueRow(stringResource(R.string.minimum_android_sdk), it.toString()) }
                app.targetSdk?.let { DetailValueRow(stringResource(R.string.target_android_sdk), it.toString()) }
                if (app.nativeCode.isNotEmpty()) DetailValueRow(stringResource(R.string.supported_architectures), app.nativeCode.joinToString(", "))
                if (app.signerSha256.isNotEmpty()) DetailValueRow(stringResource(R.string.signing_certificate), app.signerSha256.joinToString("\n"))
                if (app.expectedSha256 != null) DetailValueRow(stringResource(R.string.apk_sha256), app.expectedSha256)
                if (app.permissions.isNotEmpty()) DetailValueRow(stringResource(R.string.permissions), app.permissions.joinToString("\n"))
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return "%.1f %s".format(value, units[unit.coerceAtLeast(0)])
}

private fun fundingUrl(base: String, value: String): String {
    val trimmed = value.trim()
    if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
        return trimmed
    }
    return base + trimmed.trim('/')
}

private fun cryptoUri(scheme: String, value: String): String {
    val trimmed = value.trim()
    if (trimmed.startsWith("$scheme:", ignoreCase = true)) return trimmed
    if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) return trimmed
    return "$scheme:$trimmed"
}

@Composable
private fun AppDetailsHeader(
    app: StoreApp,
    variants: List<StoreApp>,
    installedVersionName: String?,
    actionLabel: String,
    showAction: Boolean,
    installing: Boolean,
    progress: Int,
    onAction: () -> Unit,
    onSourceSelected: (StoreApp) -> Unit,
    onDeveloperSelected: (String) -> Unit,
    actionShape: androidx.compose.ui.graphics.Shape
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .freetimeGlass(RoundedCornerShape(28.dp), interactive = false)
            .padding(16.dp)
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailsAppIcon(app = app, size = 82)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            app.authorName?.takeIf { it.isNotBlank() }?.let { author ->
                Text(
                    stringResource(R.string.by_author, author),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onDeveloperSelected(author) }
                )
            }
            Text(stringResource(R.string.app_version_source, app.version, app.sourceName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f))
            installedVersionName?.let { Text(stringResource(R.string.installed_version, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)) }
        }
    }
    if (app.summary.isNotBlank()) Text(app.summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    if (app.categories.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(app.categories, key = { it }) { category -> AssistChip(onClick = {}, label = { Text(category) }) }
        }
    }
    if (variants.size > 1) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.choose_source), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f))
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(variants, key = { it.sourceName }) { variant ->
                    FilterChip(selected = variant.sourceName == app.sourceName, onClick = { onSourceSelected(variant) }, label = { Text(variant.sourceName) })
                }
            }
        }
    }
    if (installing) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.install_progress, progress), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f))
            Spacer(Modifier.height(6.dp))
            if (progress > 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    } else if (showAction) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .freetimeGlass(actionShape, interactive = true)
                .clickable(onClick = onAction)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(actionLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DetailsScreenshots(app: StoreApp, onScreenshotSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.screenshots), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(app.screenshotUrls, key = { it }) { url ->
                AsyncImage(model = url, contentDescription = stringResource(R.string.screenshot_of, app.name), contentScale = ContentScale.Crop, modifier = Modifier.width(150.dp).height(268.dp).clip(RoundedCornerShape(14.dp)).clickable { onScreenshotSelected(url) })
            }
        }
    }
}

@Composable
private fun ExpandableDescription(app: StoreApp) {
    val text = app.description.ifBlank { app.summary.ifBlank { stringResource(R.string.no_description_available) } }
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) stringResource(R.string.less) else stringResource(R.string.more)) }
    }
}

@Composable
private fun DetailsExpandableSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val shape = RoundedCornerShape(20.dp)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().freetimeGlass(shape, interactive = false),
            shape = shape,
            colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (expanded) stringResource(R.string.symbol_collapse) else stringResource(R.string.symbol_expand))
            }
            if (expanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { content() }
            }
        }
    }
}

@Composable
private fun DetailValueRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailActionItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 56.dp, max = 76.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun DetailActionRow(label: String, value: String, icon: ImageVector = Icons.Filled.OpenInNew, onClick: () -> Unit) {
    // Keep URLs, wallet addresses and e-mail addresses out of the visible UI.
    // The complete target is still used by the click action.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DetailsAppIcon(app: StoreApp, size: Int) {
    if (app.iconUrl != null) {
        FallbackAppIcon(app = app, size = size)
    } else {
        Box(modifier = Modifier.size(size.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(app.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
