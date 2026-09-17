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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
    onOpenUri: (String) -> Unit
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    val scrollState = rememberScrollState()

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
                        Text("‹", style = MaterialTheme.typography.headlineSmall)
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.donations),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    app.donationUrls.forEach { url ->
                        DetailActionRow(stringResource(R.string.donation_link), url) { onOpenUri(url) }
                    }
                    app.liberapay?.let { value ->
                        val url = fundingUrl("https://liberapay.com/", value)
                        DetailActionRow(stringResource(R.string.liberapay), value) { onOpenUri(url) }
                    }
                    app.openCollective?.let { value ->
                        val url = fundingUrl("https://opencollective.com/", value)
                        DetailActionRow(stringResource(R.string.open_collective), value) { onOpenUri(url) }
                    }
                    app.bitcoin?.let { value ->
                        DetailActionRow(stringResource(R.string.bitcoin), value) {
                            onOpenUri(cryptoUri("bitcoin", value))
                        }
                    }
                    app.litecoin?.let { value ->
                        DetailActionRow(stringResource(R.string.litecoin), value) {
                            onOpenUri(cryptoUri("litecoin", value))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            DetailsExpandableSection(stringResource(R.string.links)) {
                app.websiteUrl?.let { DetailActionRow(stringResource(R.string.website), it) { onOpenUri(it) } }
                app.issueTrackerUrl?.let { DetailActionRow(stringResource(R.string.issue_tracker), it) { onOpenUri(it) } }
                app.changelogUrl?.let { DetailActionRow(stringResource(R.string.changelog), it) { onOpenUri(it) } }
                app.translationUrl?.let { DetailActionRow(stringResource(R.string.translation), it) { onOpenUri(it) } }
                app.sourceCodeUrl?.let { DetailActionRow(stringResource(R.string.source_code), it) { onOpenUri(it) } }
                app.license?.let { DetailValueRow(stringResource(R.string.license), it) }
            }

            if (!app.authorName.isNullOrBlank() || !app.authorEmail.isNullOrBlank() || !app.authorWebsite.isNullOrBlank()) {
                DetailsExpandableSection(stringResource(R.string.developer_contact)) {
                    app.authorName?.let { DetailValueRow(stringResource(R.string.author), it) }
                    app.authorWebsite?.let { DetailActionRow(stringResource(R.string.author_website), it) { onOpenUri(it) } }
                    app.authorEmail?.let { DetailActionRow(stringResource(R.string.email), it) { onOpenUri("mailto:$it") } }
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

            Spacer(Modifier.height(16.dp))
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
                Text(if (expanded) "⌃" else "⌄")
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
private fun DetailActionRow(label: String, value: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
