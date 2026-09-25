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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import com.freetime.lumastore.data.*
import com.freetime.lumastore.notifications.SystemNotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
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
    var dashboard by remember { mutableStateOf<DeveloperDashboard?>(null) }
    var loading by remember { mutableStateOf(false) }
    var authLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var loggingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val developerLoadError = stringResource(R.string.developer_data_load_failed)
    val authLoadError = stringResource(R.string.supabase_sign_in_load_failed)
    val googleError = stringResource(R.string.google_sign_in_failed)
    val githubError = stringResource(R.string.github_sign_in_failed)
    val gitlabError = stringResource(R.string.gitlab_sign_in_failed)
    val signOutError = stringResource(R.string.sign_out_failed)
    val notificationError = stringResource(R.string.notification_update_failed)
    val submissionUpdateError = stringResource(R.string.submission_update_failed)
    val submissionRemoveError = stringResource(R.string.submission_remove_failed)
    val platformUpdateError = stringResource(R.string.platform_update_failed)
    val artifactAddError = stringResource(R.string.artifact_add_failed)
    val artifactRemoveError = stringResource(R.string.artifact_remove_failed)

    suspend fun reload(current: DeveloperSession) {
        loading = true
        error = null
        runCatching { repository.loadDashboard(current) }
            .onSuccess {
                dashboard = it
                systemNotifications.showNewNotifications(it.notifications)
            }
            .onFailure { error = it.message ?: developerLoadError }
        loading = false
    }

    LaunchedEffect(Unit) {
        runCatching { repository.savedSession() }
            .onSuccess {
                session = it
                authLoading = false
            }
            .onFailure {
                error = it.message ?: authLoadError
                authLoading = false
            }
        repository.sessionFlow().collect {
            session = it
            if (it == null) dashboard = null
            loggingIn = false
            authLoading = false
        }
    }

    LaunchedEffect(session?.accessToken, active) {
        if (!active) return@LaunchedEffect
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
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Text(stringResource(R.string.normal_account), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = {
                    scope.launch {
                        loggingIn = true
                        error = null
                        runCatching { repository.signInWithGoogle() }
                            .onFailure { error = it.message ?: googleError; loggingIn = false }
                    }
                },
                enabled = !loggingIn,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sign_in_google)) }

            HorizontalDivider()
            Text(stringResource(R.string.developer_sign_in_options), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = {
                    scope.launch {
                        loggingIn = true
                        error = null
                        runCatching { repository.signInWithGitHub() }
                            .onFailure { error = it.message ?: githubError; loggingIn = false }
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
                            .onFailure { error = it.message ?: gitlabError; loggingIn = false }
                    }
                },
                enabled = !loggingIn,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sign_in_gitlab)) }
        }
        return
    }

    val current = session ?: return
    val data = dashboard
    val unread = data?.notifications?.count { it.readAt == null } ?: 0
    val comments = data?.comments?.groupBy { it.submissionId }.orEmpty()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
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
                            .onFailure { error = it.message ?: signOutError }
                    }
                }) { Text(stringResource(R.string.sign_out)) }
            }
        }
        item {
            Text(stringResource(R.string.developer_area), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(current.email ?: current.userId, color = MaterialTheme.colorScheme.onSurfaceVariant)
            data?.let { dashboardData ->
                val totalDownloads = dashboardData.downloadStats.values.sumOf { stats -> stats.total }
                Text(
                    stringResource(R.string.developer_total_downloads, totalDownloads),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { reload(current) } }, enabled = !loading) {
                    Text(stringResource(R.string.refresh))
                }
                if (unread > 0) Text(stringResource(R.string.unread_notifications, unread), color = MaterialTheme.colorScheme.primary)
            }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (loading && data == null) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        }
        if (data != null) {
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
                                        .onFailure { error = it.message ?: notificationError }
                                    reload(current)
                                }
                            }
                        } else null
                    )
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(stringResource(R.string.my_apps), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            if (data.submissions.isEmpty()) {
                item { Text(stringResource(R.string.no_account_apps), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(data.submissions, key = { it.id }) {
                    SubmissionCard(
                        submission = it,
                        comments = comments[it.id].orEmpty().map { c -> c.body },
                        artifacts = (it.storeAppId ?: data.submissionStoreIds[it.id])?.let { id -> data.artifacts[id] }.orEmpty(),
                        scan = data.scans[it.id],
                        downloadStats = (it.storeAppId ?: data.submissionStoreIds[it.id])?.let { id -> data.downloadStats[id] },
                        onSave = { submission, name, shortDescription, description, version, versionCode, changelog, repoUrl ->
                            scope.launch {
                                runCatching { repository.updateSubmission(submission, name, shortDescription, description, version, versionCode, changelog, repoUrl) }
                                    .onFailure { error = it.message ?: submissionUpdateError }
                                reload(current)
                            }
                        },
                        onArtifactAdd = { submission, platform, packageType, downloadUrl, repoUrl ->
                            scope.launch {
                                runCatching { repository.addPlatformArtifact(submission, platform, packageType, downloadUrl, repoUrl) }
                                    .onFailure { error = it.message ?: artifactAddError }
                                reload(current)
                            }
                        },
                        onArtifactRemove = { submission, platform, packageType ->
                            scope.launch {
                                runCatching { repository.removePlatformArtifact(submission, platform, packageType) }
                                    .onFailure { error = it.message ?: artifactRemoveError }
                                reload(current)
                            }
                        },
                        onPlatformSave = { submission, platform, title, shortDescription, fullDescription, changelog, repoUrl, downloadUrl, screenshots ->
                            scope.launch {
                                runCatching { repository.updatePlatformMetadata(submission, platform, title, shortDescription, fullDescription, changelog, repoUrl, downloadUrl, screenshots) }
                                    .onFailure { error = it.message ?: platformUpdateError }
                                reload(current)
                            }
                        },
                        onRemove = { submission ->
                            scope.launch {
                                runCatching { repository.removeSubmission(submission) }
                                    .onFailure { error = it.message ?: submissionRemoveError }
                                reload(current)
                            }
                        }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun NotificationCard(notification: DeveloperNotification, onMarkRead: (() -> Unit)?) {
    val shape = RoundedCornerShape(18.dp)
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
    onSave: (DeveloperSubmission, String, String, String, String, Long?, String, String) -> Unit,
    onArtifactAdd: (DeveloperSubmission, String, String, String, String) -> Unit,
    onArtifactRemove: (DeveloperSubmission, String, String) -> Unit,
    onPlatformSave: (DeveloperSubmission, String, String, String, String, String, String, String, List<String>) -> Unit,
    onRemove: (DeveloperSubmission) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    var editing by remember(submission.id, submission.status) { mutableStateOf(false) }
    var name by remember(submission.id) { mutableStateOf(submission.name) }
    var shortDescription by remember(submission.id) { mutableStateOf(submission.shortDescription.orEmpty()) }
    var description by remember(submission.id) { mutableStateOf(submission.description.orEmpty()) }
    var version by remember(submission.id) { mutableStateOf(submission.version.orEmpty()) }
    var versionCode by remember(submission.id) { mutableStateOf(submission.versionCode?.toString().orEmpty()) }
    var changelog by remember(submission.id) { mutableStateOf(submission.changelog.orEmpty()) }
    var repoUrl by remember(submission.id) { mutableStateOf(submission.repoUrl ?: submission.link.orEmpty()) }
    val editable = submission.status in setOf("Draft", "Rejected", "Approved", "Changes Requested")

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
                Text(stringResource(R.string.downloads_total, downloadStats.total))
                Text(stringResource(R.string.downloads_today, downloadStats.today), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.downloads_month, downloadStats.thisMonth), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.downloads_year, downloadStats.thisYear), style = MaterialTheme.typography.bodySmall)
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

            if (editable) {
                AddPlatformArtifactEditor(submission, onArtifactAdd)
            }

            if (artifacts.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.platform_artifacts), fontWeight = FontWeight.SemiBold)
                artifacts.forEach { artifact ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(artifact.platform + (artifact.packageType?.let { " · $it" } ?: ""), fontWeight = FontWeight.SemiBold)
                            artifact.fileSizeMb?.let { Text(stringResource(R.string.artifact_size, "%.2f MB".format(it)), style = MaterialTheme.typography.bodySmall) }
                            artifact.artifactVerifiedAt?.let { Text(stringResource(R.string.artifact_verified, it), style = MaterialTheme.typography.bodySmall) }
                            artifact.sha256?.let { Text(stringResource(R.string.sha256_value, it), style = MaterialTheme.typography.labelSmall) }
                            artifact.repoUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                            PlatformMetadataEditor(submission, artifact, onPlatformSave)
                            if (editable && artifact.packageType != null) {
                                TextButton(onClick = { onArtifactRemove(submission, artifact.platform, artifact.packageType) }) {
                                    Text(stringResource(R.string.remove_artifact), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            if (editing) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.app_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(shortDescription, { shortDescription = it }, label = { Text(stringResource(R.string.short_description)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(version, { version = it }, label = { Text(stringResource(R.string.version)) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(versionCode, { versionCode = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.version_code)) }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(changelog, { changelog = it }, label = { Text(stringResource(R.string.changelog)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(repoUrl, { repoUrl = it }, label = { Text(stringResource(R.string.repository_url)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onSave(submission, name, shortDescription, description, version, versionCode.toLongOrNull(), changelog, repoUrl)
                        editing = false
                    }, enabled = name.isNotBlank() && version.isNotBlank() && repoUrl.isNotBlank()) {
                        Text(stringResource(R.string.save_and_resubmit))
                    }
                    TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel)) }
                }
            } else if (editable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = true }) { Text(stringResource(R.string.edit_submission)) }
                    TextButton(onClick = { onRemove(submission) }) {
                        Text(stringResource(if (submission.status == "Approved") R.string.archive_app else R.string.remove_submission))
                    }
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
private fun AddPlatformArtifactEditor(
    submission: DeveloperSubmission,
    onAdd: (DeveloperSubmission, String, String, String, String) -> Unit
) {
    var expanded by remember(submission.id) { mutableStateOf(false) }
    var platform by remember(submission.id) { mutableStateOf("Android") }
    var packageType by remember(submission.id, platform) { mutableStateOf(if (platform == "Android") "apk" else if (platform == "Windows") "exe" else "deb") }
    var downloadUrl by remember(submission.id) { mutableStateOf("") }
    var repoUrl by remember(submission.id) { mutableStateOf("") }
    val types = when (platform) { "Android" -> listOf("apk"); "Windows" -> listOf("exe", "msi"); else -> listOf("deb", "rpm", "appimage") }

    if (!expanded) {
        OutlinedButton(onClick = { expanded = true }) { Text(stringResource(R.string.add_platform_artifact)) }
        return
    }
    Text(stringResource(R.string.add_platform_artifact), fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Android", "Windows", "Linux").forEach { item ->
            FilterChip(selected = platform == item, onClick = {
                platform = item
                packageType = when (item) { "Android" -> "apk"; "Windows" -> "exe"; else -> "deb" }
            }, label = { Text(item) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        types.forEach { item -> FilterChip(selected = packageType == item, onClick = { packageType = item }, label = { Text(item.uppercase()) }) }
    }
    OutlinedTextField(downloadUrl, { downloadUrl = it }, label = { Text(stringResource(R.string.download_url)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(repoUrl, { repoUrl = it }, label = { Text(stringResource(R.string.repository_url)) }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            onAdd(submission, platform, packageType, downloadUrl, repoUrl)
            expanded = false
            downloadUrl = ""
            repoUrl = ""
        }, enabled = downloadUrl.startsWith("https://") || downloadUrl.startsWith("http://")) { Text(stringResource(R.string.add_artifact)) }
        TextButton(onClick = { expanded = false }) { Text(stringResource(android.R.string.cancel)) }
    }
}

@Composable
private fun PlatformMetadataEditor(
    submission: DeveloperSubmission,
    artifact: DeveloperPlatformArtifact,
    onSave: (DeveloperSubmission, String, String, String, String, String, String, String, List<String>) -> Unit
) {
    val metadata = artifact.listingMetadata as? JsonObject
    fun value(key: String) = metadata?.get(key)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
    val screenshotValues = (metadata?.get("screenshots") as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
    var editing by remember(submission.id, artifact.id) { mutableStateOf(false) }
    var title by remember(submission.id, artifact.id) { mutableStateOf(value("title")) }
    var shortDescription by remember(submission.id, artifact.id) { mutableStateOf(value("shortDescription").ifBlank { value("short_description") }) }
    var fullDescription by remember(submission.id, artifact.id) { mutableStateOf(value("fullDescription").ifBlank { value("full_description") }) }
    var changelog by remember(submission.id, artifact.id) { mutableStateOf(value("changelog")) }
    var repoUrl by remember(submission.id, artifact.id) { mutableStateOf(artifact.repoUrl.orEmpty()) }
    var downloadUrl by remember(submission.id, artifact.id) { mutableStateOf(artifact.downloadUrl.orEmpty()) }
    var screenshots by remember(submission.id, artifact.id) { mutableStateOf(screenshotValues.joinToString("\n")) }
    val editable = submission.status in setOf("Draft", "Rejected", "Approved", "Changes Requested")

    Text(stringResource(R.string.platform_metadata, artifact.platform), style = MaterialTheme.typography.labelMedium)
    if (!editing) {
        Text(title.ifBlank { stringResource(R.string.no_platform_metadata) }, fontWeight = FontWeight.Medium)
        if (shortDescription.isNotBlank()) Text(shortDescription, style = MaterialTheme.typography.bodySmall)
        if (fullDescription.isNotBlank()) Text(fullDescription, style = MaterialTheme.typography.bodySmall, maxLines = 4)
        if (changelog.isNotBlank()) Text(changelog, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        if (screenshotValues.isNotEmpty()) Text(stringResource(R.string.screenshots_urls) + ": " + screenshotValues.size, style = MaterialTheme.typography.labelSmall)
        if (editable) TextButton(onClick = { editing = true }) { Text(stringResource(R.string.edit_platform_metadata, artifact.platform)) }
    } else {
        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.app_name)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(shortDescription, { shortDescription = it }, label = { Text(stringResource(R.string.short_description)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fullDescription, { fullDescription = it }, label = { Text(stringResource(R.string.full_description)) }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        OutlinedTextField(changelog, { changelog = it }, label = { Text(stringResource(R.string.changelog)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(repoUrl, { repoUrl = it }, label = { Text(stringResource(R.string.repository_url)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(downloadUrl, { downloadUrl = it }, label = { Text(stringResource(R.string.download_url)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(screenshots, { screenshots = it }, label = { Text(stringResource(R.string.screenshots_urls)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onSave(submission, artifact.platform, title, shortDescription, fullDescription, changelog, repoUrl, downloadUrl, screenshots.lines().map(String::trim).filter(String::isNotBlank))
                editing = false
            }, enabled = title.isNotBlank() && shortDescription.isNotBlank() && fullDescription.isNotBlank() && changelog.isNotBlank() && downloadUrl.isNotBlank()) {
                Text(stringResource(R.string.save_platform_metadata))
            }
            TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel)) }
        }
    }
}

@Composable
private fun localizedSubmissionStatus(status: String): String = when (status) {
    "Approved" -> stringResource(R.string.status_approved)
    "Rejected" -> stringResource(R.string.status_rejected)
    "Changes Requested" -> stringResource(R.string.status_changes_requested)
    else -> status
}

@Composable
private fun statusColor(status: String) = when (status) {
    "Approved" -> MaterialTheme.colorScheme.primary
    "Rejected" -> MaterialTheme.colorScheme.error
    "Changes Requested" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
