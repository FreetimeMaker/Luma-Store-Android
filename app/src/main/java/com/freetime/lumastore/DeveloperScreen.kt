package com.freetime.lumastore

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import com.freetime.lumastore.data.*
import com.freetime.lumastore.notifications.SystemNotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun DeveloperScreen(
    repository: DeveloperRepository,
    onBack: () -> Unit,
    active: Boolean = true
) {
    val context = LocalContext.current
    val systemNotifications = remember(context) { SystemNotificationManager(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var session by remember { mutableStateOf<DeveloperSession?>(null) }
    var dashboard by remember(session?.userId) { mutableStateOf<DeveloperDashboard?>(null) }
    var loading by remember { mutableStateOf(false) }
    var authLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Int?>(null) }
    var loggingIn by remember { mutableStateOf(false) }
    var developerPage by rememberSaveable { mutableStateOf("overview") }
    var profile by remember { mutableStateOf<DeveloperProfileSettings?>(null) }
    var funding by remember { mutableStateOf<DeveloperFundingSettings?>(null) }
    var editorDataReady by remember(session?.userId) { mutableStateOf(false) }
    var editorDataError by remember(session?.userId) { mutableStateOf<Int?>(null) }
    var analyticsAppId by remember(session?.userId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val profileSaved = stringResource(R.string.profile_saved)
    val fundingSaved = stringResource(R.string.funding_saved)

    suspend fun loadEditorData(current: DeveloperSession) {
        val loadedProfile = repository.loadProfile(current)
        val loadedFunding = repository.loadFunding(current)
        profile = loadedProfile
        funding = loadedFunding
        editorDataReady = true
        editorDataError = null
    }

    suspend fun reload(current: DeveloperSession) {
        loading = true
        error = null
        try {
            runCatching { repository.loadDashboard(current) }
                .onSuccess {
                    dashboard = it
                    try {
                        loadEditorData(current)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        editorDataError = developerFailureMessage(failure, R.string.developer_data_load_failed)
                    }
                    systemNotifications.showNewNotifications(it.notifications)
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    error = developerFailureMessage(it, R.string.developer_data_load_failed)
                }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { repository.savedSession() }
            .onSuccess {
                session = it
                authLoading = false
            }
            .onFailure {
                error = developerFailureMessage(it, R.string.supabase_sign_in_load_failed)
                authLoading = false
            }
        repository.sessionFlow().collect {
            session = it

            loggingIn = false
            authLoading = false
        }
    }

    // A restored editor must load its baseline even while overview polling is paused.
    LaunchedEffect(session?.userId) {
        val current = session ?: return@LaunchedEffect
        try {
            loadEditorData(current)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            editorDataError = developerFailureMessage(failure, R.string.developer_data_load_failed)
        }
    }

    LaunchedEffect(session?.accessToken, active, developerPage) {
        if (!active || developerPage != "overview") return@LaunchedEffect
        val current = session ?: return@LaunchedEffect
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        reload(current)
        while (active && session?.accessToken == current.accessToken) {
            delay(30_000)
            reload(current)
        }
    }

    if (authLoading) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }

    if (session == null) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.developer_login), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.developer_login_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

            HorizontalDivider()
            Text(stringResource(R.string.developer_sign_in_options), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = {
                    scope.launch {
                        loggingIn = true
                        error = null
                        runCatching { repository.signInWithGitHub() }
                            .onFailure { error = developerFailureMessage(it, R.string.github_sign_in_failed); loggingIn = false }
                    }
                },
                enabled = !loggingIn,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sign_in_github)) }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        loggingIn = true
                        error = null
                        runCatching { repository.signInWithGitLab() }
                            .onFailure { error = developerFailureMessage(it, R.string.gitlab_sign_in_failed); loggingIn = false }
                    }
                },
                enabled = !loggingIn,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sign_in_gitlab)) }
        }
        return
    }

    val current = session ?: return
    if ((developerPage == "profile" || developerPage == "funding") && !editorDataReady) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { developerPage = "overview" }) { Text(stringResource(R.string.back)) }
            editorDataError?.let { message ->
                Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = {
                    editorDataError = null
                    scope.launch {
                        try { loadEditorData(current) }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (failure: Exception) { editorDataError = developerFailureMessage(failure, R.string.developer_data_load_failed) }
                    }
                }) { Text(stringResource(R.string.editor_retry)) }
            } ?: CircularProgressIndicator()
        }
        return
    }
    if (developerPage == "profile") {
        DeveloperProfileEditor(
            initial = profile,
            session = current,
            repository = repository,
            onBack = { developerPage = "overview" },
            active = active,
            onSaved = { savedProfile ->
                profile = savedProfile
                developerPage = "overview"
                Toast.makeText(context, profileSaved, Toast.LENGTH_SHORT).show()
            }
        )
        return
    }
    if (developerPage == "funding") {
        DeveloperFundingEditor(
            initial = funding,
            onBack = { developerPage = "overview" },
            repository = repository,
            session = current,
            active = active,
            onSaved = { savedFunding ->
                funding = savedFunding
                developerPage = "overview"
                Toast.makeText(context, fundingSaved, Toast.LENGTH_SHORT).show()
            }
        )
        return
    }
    val data = dashboard
    val analytics = data?.analytics
    val publishedApps = analytics?.apps
    val selectedApp = publishedApps?.firstOrNull { it.id == analyticsAppId }
    if (selectedApp != null && analytics != null) {
        DeveloperAnalyticsScreen(
            app = selectedApp,
            stats = analytics.stats?.get(selectedApp.id),
            statsFailed = analytics.stats == null,
            loading = loading,
            onRetry = { scope.launch { reload(current) } },
            onBack = { analyticsAppId = null }
        )
        return
    }
    val unread = data?.notifications?.count { it.readAt == null } ?: 0
    val comments = data?.comments?.groupBy { it.submissionId }.orEmpty()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                Arrangement.End,
                Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { repository.signOut() }
                            .onFailure { error = developerFailureMessage(it, R.string.sign_out_failed) }
                    }
                }) { Text(stringResource(R.string.sign_out)) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DeveloperAvatar(profile?.avatarUrl, profile?.displayName ?: current.email.orEmpty(), 72.dp)
                Column(Modifier.weight(1f)) {
                    Text(profile?.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.developer_area), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(current.email ?: current.userId, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { developerPage = "profile" }) { Text(stringResource(R.string.developer_profile)) }
                OutlinedButton(onClick = { developerPage = "funding" }) { Text(stringResource(R.string.developer_funding)) }
            }
            data?.let { dashboardData ->
                val totalDownloads = dashboardData.analytics.totalFor { it.total }
                val downloadsToday = dashboardData.analytics.totalFor { it.today }
                val downloadsMonth = dashboardData.analytics.totalFor { it.thisMonth }
                val pendingApps = dashboardData.submissions.count { it.status in setOf("Pending", "Changes Requested") }
                totalDownloads?.let {
                    Text(
                        stringResource(R.string.developer_total_downloads, it),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val unavailable = stringResource(R.string.developer_metric_unavailable)
                    DeveloperMetricCard(stringResource(R.string.metric_apps), publishedApps?.size?.toString() ?: unavailable, Modifier.weight(1f))
                    DeveloperMetricCard(stringResource(R.string.metric_today), downloadsToday?.toString() ?: unavailable, Modifier.weight(1f))
                    DeveloperMetricCard(stringResource(R.string.metric_month), downloadsMonth?.toString() ?: unavailable, Modifier.weight(1f))
                }
                if (pendingApps > 0) {
                    Text(
                        stringResource(R.string.developer_pending_apps, pendingApps),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { reload(current) } }, enabled = !loading) {
                    Text(stringResource(R.string.refresh))
                }
                if (unread > 0) Text(stringResource(R.string.unread_notifications, unread), color = MaterialTheme.colorScheme.primary)
            }
        }
        error?.let { message -> item { Text(stringResource(message), color = MaterialTheme.colorScheme.error) } }
        if (loading && data == null) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        }
        if (data != null) {
            item { Text(stringResource(R.string.developer_published_app_analytics), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            when {
                publishedApps == null -> item {
                    Text(stringResource(R.string.developer_published_apps_load_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { scope.launch { reload(current) } }, enabled = !loading) { Text(stringResource(R.string.editor_retry)) }
                }
                publishedApps.isEmpty() -> item { Text(stringResource(R.string.developer_no_published_apps)) }
                else -> {
                    if (data.analytics.stats == null) item {
                        Text(stringResource(R.string.developer_analytics_load_failed), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { scope.launch { reload(current) } }, enabled = !loading) { Text(stringResource(R.string.editor_retry)) }
                    }
                    items(publishedApps, key = { "analytics:${it.id}" }) { app ->
                        OutlinedButton(onClick = { analyticsAppId = app.id }, modifier = Modifier.fillMaxWidth()) {
                            Text(app.displayName)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            if (data.notifications.isEmpty()) {
                item { Text(stringResource(R.string.no_notifications), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(data.notifications, key = { it.id }) { n ->
                    NotificationCard(
                        n,
                        if (n.readAt == null) {
                            {
                                scope.launch {
                                    runCatching { repository.markNotificationRead(n.id) }
                                        .onSuccess { reload(current) }
                                        .onFailure { error = developerFailureMessage(it, R.string.notification_update_failed) }
                                }
                            }
                        } else null
                    )
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(stringResource(R.string.developer_submissions), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            if (data.submissions.isEmpty()) {
                item { Text(stringResource(R.string.developer_no_submissions), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(data.submissions, key = { it.id }) {
                    val storeId = it.storeAppId ?: data.submissionStoreIds[it.id]
                    val publishedApp = publishedApps?.firstOrNull { app -> app.id == storeId }
                    SubmissionCard(
                        submission = it,
                        comments = comments[it.id].orEmpty().map { c -> c.body },
                        artifacts = (it.storeAppId ?: data.submissionStoreIds[it.id])?.let { id -> data.artifacts[id] }.orEmpty(),
                        scan = data.scans[it.id],
                        downloadStats = publishedApp?.let { app -> data.analytics.stats?.get(app.id) },
                        onAnalytics = publishedApp?.let { app -> { analyticsAppId = app.id } },
                        onRemove = { submission ->
                            scope.launch {
                                runCatching { repository.removeSubmission(submission) }
                                    .onSuccess { reload(current) }
                                    .onFailure { error = developerFailureMessage(it, R.string.submission_remove_failed) }
                            }
                        }
                    )
                }
            }
        }
    }
}

internal val developerCryptoOptions = listOf(
    "bitcoin::Bitcoin", "ethereum::Ethereum", "tether::Ethereum (ERC-20)", "tether::TRON (TRC-20)", "tether::BNB Smart Chain (BEP-20)", "tether::Solana", "tether::Polygon", "tether::Avalanche C-Chain", "tether::Arbitrum", "tether::Optimism",
    "usdc::Ethereum (ERC-20)", "usdc::Solana", "usdc::Base", "usdc::Arbitrum", "usdc::Optimism", "usdc::Polygon", "usdc::Avalanche C-Chain", "bnb::BNB Smart Chain (BEP-20)", "solana::Solana", "cardano::Cardano", "dogecoin::Dogecoin", "tron::TRON", "polkadot::Polkadot", "avalanche::Avalanche C-Chain", "avalanche::Avalanche P-Chain", "chainlink::Ethereum (ERC-20)", "chainlink::BNB Smart Chain (BEP-20)", "chainlink::Polygon", "chainlink::Arbitrum", "chainlink::Optimism", "polygon::Polygon", "polygon::Ethereum (ERC-20)", "litecoin::Litecoin", "bitcoin_cash::Bitcoin Cash", "stellar::Stellar", "monero::Monero", "toncoin::TON", "shiba_inu::Ethereum (ERC-20)", "shiba_inu::Shibarium"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperAnalyticsScreen(
    app: DeveloperPublishedApp,
    stats: DeveloperDownloadStats?,
    statsFailed: Boolean,
    loading: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text(app.displayName) }, navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.download_statistics), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            when {
                statsFailed -> item {
                    Text(stringResource(R.string.developer_analytics_load_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, enabled = !loading) { Text(stringResource(R.string.editor_retry)) }
                }
                stats == null || listOf(stats.today, stats.thisMonth, stats.thisYear, stats.total).all { it == null } ->
                    item { Text(stringResource(R.string.no_download_analytics), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> {
                    stats.today?.let { item { DeveloperMetricCard(stringResource(R.string.metric_today), it.toString(), Modifier.fillMaxWidth()) } }
                    stats.thisMonth?.let { item { DeveloperMetricCard(stringResource(R.string.metric_month), it.toString(), Modifier.fillMaxWidth()) } }
                    stats.thisYear?.let { item { DeveloperMetricCard(stringResource(R.string.metric_year), it.toString(), Modifier.fillMaxWidth()) } }
                    stats.total?.let { item { DeveloperMetricCard(stringResource(R.string.metric_total), it.toString(), Modifier.fillMaxWidth()) } }
                }
            }
        }
    }
}

@Composable
private fun DeveloperMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NotificationCard(notification: DeveloperNotification, onMarkRead: (() -> Unit)?) {
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(notification.title, fontWeight = FontWeight.SemiBold)
                if (notification.readAt == null) Text(stringResource(R.string.new_label), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            notification.message?.let { Text(it) }
            notification.createdAt?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (onMarkRead != null) TextButton(onClick = onMarkRead) { Text(stringResource(R.string.mark_as_read)) }
        }
    }
}

@Composable
private fun SubmissionCard(
    submission: DeveloperSubmission,
    comments: List<String>,
    artifacts: List<DeveloperPlatformArtifact>,
    scan: DeveloperSecurityScan?,
    downloadStats: DeveloperDownloadStats?,
    onAnalytics: (() -> Unit)?,
    onRemove: (DeveloperSubmission) -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val canRemove = submission.status in setOf("Draft", "Rejected", "Approved", "Changes Requested")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(submission.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.status_value, localizedSubmissionStatus(submission.status)), color = statusColor(submission.status))
            submission.version?.let { Text(stringResource(R.string.version_value, it), style = MaterialTheme.typography.bodySmall) }
            submission.packageName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (downloadStats != null) {
                HorizontalDivider()
                Text(stringResource(R.string.download_statistics), fontWeight = FontWeight.SemiBold)
                downloadStats.total?.let { Text(stringResource(R.string.downloads_total, it)) }
                downloadStats.today?.let { Text(stringResource(R.string.downloads_today, it), style = MaterialTheme.typography.bodySmall) }
                downloadStats.thisMonth?.let { Text(stringResource(R.string.downloads_month, it), style = MaterialTheme.typography.bodySmall) }
                downloadStats.thisYear?.let { Text(stringResource(R.string.downloads_year, it), style = MaterialTheme.typography.bodySmall) }
            }
            if (onAnalytics != null) {
                TextButton(onClick = onAnalytics) { Text(stringResource(R.string.analytics)) }
            }

            if (scan != null) {
                HorizontalDivider()
                Text(stringResource(R.string.security_scan), fontWeight = FontWeight.SemiBold)
                Text(scan.status + " · " + stringResource(R.string.risk_level, scan.riskLevel))
                Text(stringResource(R.string.scan_detections, scan.maliciousCount ?: 0, scan.suspiciousCount ?: 0, scan.harmlessCount ?: 0, scan.undetectedCount ?: 0), style = MaterialTheme.typography.bodySmall)
                scan.errorMessage?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                val scanContext = LocalContext.current
                scan.virusTotalPermalink?.takeIf { it.isNotBlank() }?.let { url ->
                    TextButton(onClick = { scanContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(stringResource(R.string.open_virustotal)) }
                }
            }

            if (artifacts.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.platform_artifacts), fontWeight = FontWeight.SemiBold)
                artifacts.forEach { artifact ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(artifact.platform + (artifact.packageType?.let { " · $it" } ?: ""), fontWeight = FontWeight.SemiBold)
                            artifact.fileSizeMb?.let { Text(stringResource(R.string.artifact_size, stringResource(R.string.file_size_mb_precise, it)), style = MaterialTheme.typography.bodySmall) }
                            artifact.artifactVerifiedAt?.let { Text(stringResource(R.string.artifact_verified, it), style = MaterialTheme.typography.bodySmall) }
                            artifact.sha256?.let { Text(stringResource(R.string.sha256_value, it), style = MaterialTheme.typography.labelSmall) }
                            artifact.repoUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                            PlatformMetadata(artifact)
                        }
                    }
                }
            }

            if (canRemove) {
                TextButton(onClick = { onRemove(submission) }) {
                    Text(stringResource(if (submission.status == "Approved") R.string.archive_app else R.string.remove_submission))
                }
            }

            submission.reviewMessage?.let {
                HorizontalDivider()
                Text(stringResource(R.string.review_feedback), fontWeight = FontWeight.SemiBold)
                Text(it)
            }
            if (comments.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.comments_feedback), fontWeight = FontWeight.SemiBold)
                comments.forEach { Text(stringResource(R.string.comment_bullet, it)) }
            }
        }
    }
}

@Composable
private fun PlatformMetadata(artifact: DeveloperPlatformArtifact) {
    val metadata = artifact.listingMetadata as? JsonObject
    fun value(key: String) = metadata?.get(key)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
    val screenshotValues = (metadata?.get("screenshots") as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
    val title = value("title")
    val shortDescription = value("shortDescription").ifBlank { value("short_description") }
    val fullDescription = value("fullDescription").ifBlank { value("full_description") }
    val changelog = value("changelog")

    Text(stringResource(R.string.platform_metadata, artifact.platform), style = MaterialTheme.typography.labelMedium)
    Text(title.ifBlank { stringResource(R.string.no_platform_metadata) }, fontWeight = FontWeight.Medium)
    if (shortDescription.isNotBlank()) Text(shortDescription, style = MaterialTheme.typography.bodySmall)
    if (fullDescription.isNotBlank()) Text(fullDescription, style = MaterialTheme.typography.bodySmall, maxLines = 4)
    if (changelog.isNotBlank()) Text(changelog, style = MaterialTheme.typography.bodySmall, maxLines = 3)
    if (screenshotValues.isNotEmpty()) Text(stringResource(R.string.screenshot_urls_count, screenshotValues.size), style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun localizedSubmissionStatus(status: String): String = when (status) {
    "Approved" -> stringResource(R.string.status_approved)
    "Rejected" -> stringResource(R.string.status_rejected)
    "Changes Requested" -> stringResource(R.string.status_changes_requested)
    "Pending" -> stringResource(R.string.status_pending)
    "Draft" -> stringResource(R.string.status_draft)
    else -> status
}

@Composable
private fun statusColor(status: String) = when (status) {
    "Approved" -> MaterialTheme.colorScheme.primary
    "Rejected" -> MaterialTheme.colorScheme.error
    "Changes Requested" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
