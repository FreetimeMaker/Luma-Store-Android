package com.freetime.lumastore


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.compose.AsyncImage
import com.freetime.lumastore.data.LumaStoreApi
import com.freetime.lumastore.data.PublicDeveloperProfile
import com.freetime.lumastore.data.StoreApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperAppsScreen(developerName: String, apps: List<StoreApp>, onBack: () -> Unit, onAppSelected: (StoreApp) -> Unit) {
    val developerApps = apps.filter { it.authorName?.trim()?.equals(developerName.trim(), true) == true }
        .groupBy { it.id }.mapNotNull { (_, variants) -> variants.maxByOrNull { it.versionCode } }.sortedBy { it.name.lowercase() }
    val developerId = developerApps.firstOrNull()?.developerId
    var publicProfile by remember(developerId) { mutableStateOf<PublicDeveloperProfile?>(null) }
    LaunchedEffect(developerId) {
        publicProfile = developerId?.let { runCatching { LumaStoreApi.developerProfile(it) }.getOrNull() }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground, topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.apps_by_developer, developerName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
        )
    }) { padding ->
        if (developerApps.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_developer_apps)) }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            publicProfile?.let { profile ->
                item("developer_profile") {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = profile.avatarUrl,
                                contentDescription = profile.displayName,
                                modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.extraLarge),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                if (profile.verified) Text(stringResource(R.string.verified_developer), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                profile.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text(stringResource(R.string.developer_app_count, profile.apps.size), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            items(developerApps, key = { it.id }) { app ->
                Row(
                    Modifier.fillMaxWidth().clickable { onAppSelected(app) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeveloperAppIcon(app, 60)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.summary.ifBlank { app.id }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.app_version_source, app.version, app.sourceName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperAppIcon(app: StoreApp, size: Int, index: Int = 0) {
    val candidates = app.iconUrls.ifEmpty { listOfNotNull(app.iconUrl) }
    if (index >= candidates.size) {
        Box(Modifier.size(size.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(app.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
        }
        return
    }
    SubcomposeAsyncImage(
        model = candidates[index], contentDescription = stringResource(R.string.icon_of, app.name), contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(16.dp)),
        loading = { SubcomposeAsyncImageContent() }, success = { SubcomposeAsyncImageContent() }, error = { DeveloperAppIcon(app, size, index + 1) }
    )
}
