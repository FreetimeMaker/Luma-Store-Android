package com.freetime.lumastore

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.freetime.lumastore.data.StoreApp

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
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(
                        app.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "hero") {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DetailsAppIcon(app = app, size = 88)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                app.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                app.summary.ifBlank { app.id },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.app_version_source, app.version, app.sourceName),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            installedVersionName?.let {
                                Text(
                                    stringResource(R.string.installed_version, it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onAction,
                        enabled = !installing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (installing) stringResource(R.string.install_progress, progress) else actionLabel)
                    }
                    if (installing) {
                        Spacer(Modifier.height(8.dp))
                        if (progress > 0) {
                            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            if (variants.size > 1) {
                item(key = "sources") {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.choose_source), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(variants, key = { it.sourceName }) { variant ->
                                FilterChip(
                                    selected = variant.sourceName == app.sourceName,
                                    onClick = { onSourceSelected(variant) },
                                    label = { Text(stringResource(R.string.source_version, variant.sourceName, variant.version)) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "description") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (app.closedSource) stringResource(R.string.closed_source) else stringResource(R.string.open_source),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (app.closedSource) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        app.description.ifBlank { app.summary.ifBlank { stringResource(R.string.no_description_available) } },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (app.categories.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(app.categories, key = { it }) { category ->
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        category,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (app.screenshotUrls.isNotEmpty()) {
                item(key = "screenshots") {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(
                            stringResource(R.string.screenshots),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(app.screenshotUrls, key = { it }) { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = stringResource(R.string.screenshot_of, app.name),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(150.dp)
                                        .height(268.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onScreenshotSelected(url) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "links") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.app_information), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    app.authorName?.let { DetailsValue(stringResource(R.string.author), it) }
                    app.authorEmail?.let { DetailsValue(stringResource(R.string.email), it) }
                    app.authorWebsite?.let { DetailsLink(stringResource(R.string.author_website), it, onOpenUri) }
                    app.websiteUrl?.let { DetailsLink(stringResource(R.string.website), it, onOpenUri) }
                    app.sourceCodeUrl?.let { DetailsLink(stringResource(R.string.source_code), it, onOpenUri) }
                    app.issueTrackerUrl?.let { DetailsLink(stringResource(R.string.issue_tracker), it, onOpenUri) }
                    app.translationUrl?.let { DetailsLink(stringResource(R.string.translation), it, onOpenUri) }
                    app.changelogUrl?.let { DetailsLink(stringResource(R.string.changelog), it, onOpenUri) }
                    app.license?.let { DetailsValue(stringResource(R.string.license), it) }
                    if (app.antiFeatures.isNotEmpty()) {
                        DetailsValue(stringResource(R.string.anti_features), app.antiFeatures.joinToString(", "))
                    }
                    app.donationUrls.forEach { DetailsLink(stringResource(R.string.donation_link), it, onOpenUri) }
                    app.liberapay?.let { DetailsValue(stringResource(R.string.liberapay), it) }
                    app.openCollective?.let { DetailsValue(stringResource(R.string.open_collective), it) }
                    app.bitcoin?.let { DetailsValue(stringResource(R.string.bitcoin), it) }
                    app.litecoin?.let { DetailsValue(stringResource(R.string.litecoin), it) }
                }
            }

            item(key = "technical") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.package_label, app.id), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.version_with_code, app.version, app.versionCode), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.source_value, app.sourceName), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DetailsValue(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailsLink(label: String, value: String, onOpenUri: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUri(value) }
            .padding(vertical = 7.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DetailsAppIcon(app: StoreApp, size: Int) {
    if (app.iconUrl != null) {
        AsyncImage(
            model = app.iconUrl,
            contentDescription = stringResource(R.string.icon_of, app.name),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(18.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(app.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}
