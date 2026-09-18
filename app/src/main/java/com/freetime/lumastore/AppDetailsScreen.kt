package com.freetime.lumastore

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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.freetime.lumastore.data.StoreApp
import com.freetime.lumastore.ui.glass.lumaLiquidGlass
import com.freetime.lumastore.ui.glass.rememberLumaBackdrop

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
    onOpenUri: (String) -> Unit
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    val scrollState = rememberScrollState()
    val backdrop = rememberLumaBackdrop()
    val glassShape = RoundedCornerShape(20.dp)

    Scaffold(
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
                scrollBehavior = scrollBehavior
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
                installing = installing,
                progress = progress,
                onAction = onAction,
                onSourceSelected = onSourceSelected
            )

            app.versionChangelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .lumaLiquidGlass(backdrop, glassShape, interactive = false),
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

            if (app.screenshotUrls.isNotEmpty()) {
                DetailsScreenshots(app, onScreenshotSelected)
            }

            if (app.antiFeatures.isNotEmpty()) {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.anti_features),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        app.antiFeatures.joinToString(", "),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                        .lumaLiquidGlass(backdrop, glassShape, interactive = false),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Top
                    ) {
                        app.donationUrls.forEach { url ->
                            DetailActionItem(stringResource(R.string.donation_link), Icons.Filled.AttachMoney) { onOpenUri(url) }
                        }
                        app.liberapay?.let { value ->
                            val url = fundingUrl("https://liberapay.com/", value)
                            DetailActionItem(stringResource(R.string.liberapay), Icons.Filled.AttachMoney) { onOpenUri(url) }
                        }
                        app.openCollective?.let { value ->
                            val url = fundingUrl("https://opencollective.com/", value)
                            DetailActionItem(stringResource(R.string.open_collective), Icons.Filled.AttachMoney) { onOpenUri(url) }
                        }
                        app.bitcoin?.let { value ->
                            DetailActionItem(stringResource(R.string.bitcoin), Icons.Filled.AttachMoney) {
                                onOpenUri(cryptoUri("bitcoin", value))
                            }
                        }
                        app.litecoin?.let { value ->
                            DetailActionItem(stringResource(R.string.litecoin), Icons.Filled.AttachMoney) {
                                onOpenUri(cryptoUri("litecoin", value))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            DetailsExpandableSection(stringResource(R.string.links)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    app.websiteUrl?.let { DetailActionItem(stringResource(R.string.website), Icons.Filled.Language) { onOpenUri(it) } }
                    app.issueTrackerUrl?.let { DetailActionItem(stringResource(R.string.issue_tracker), Icons.Filled.BugReport) { onOpenUri(it) } }
                    app.changelogUrl?.let { DetailActionItem(stringResource(R.string.changelog), Icons.Filled.History) { onOpenUri(it) } }
                    app.translationUrl?.let { DetailActionItem(stringResource(R.string.translation), Icons.Filled.Translate) { onOpenUri(it) } }
                    app.sourceCodeUrl?.let { DetailActionItem(stringResource(R.string.source_code), Icons.Filled.Code) { onOpenUri(it) } }
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
            }

            Spacer(Modifier.height(96.dp))
        }
    }
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
    installing: Boolean,
    progress: Int,
    onAction: () -> Unit,
    onSourceSelected: (StoreApp) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailsAppIcon(app = app, size = 64)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            app.authorName?.let { Text(stringResource(R.string.by_author, it), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.app_version_source, app.version, app.sourceName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            installedVersionName?.let { Text(stringResource(R.string.installed_version, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (app.summary.isNotBlank()) Text(app.summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    if (app.categories.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(app.categories, key = { it }) { category -> AssistChip(onClick = {}, label = { Text(category) }) }
        }
    }
    if (variants.size > 1) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.choose_source), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(stringResource(R.string.install_progress, progress), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            if (progress > 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            if (actionLabel == stringResource(R.string.open)) OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            else Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
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
        ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        AsyncImage(model = app.iconUrl, contentDescription = stringResource(R.string.icon_of, app.name), contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp).clip(MaterialTheme.shapes.large))
    } else {
        Box(modifier = Modifier.size(size.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(app.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}
