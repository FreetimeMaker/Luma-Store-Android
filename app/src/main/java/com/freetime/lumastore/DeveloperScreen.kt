package com.freetime.lumastore

import com.freetime.design.freetimeGlass

import android.Manifest
import android.content.pm.PackageManager
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
                    SubmissionCard(it, comments[it.id].orEmpty().map { c -> c.body })
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
        modifier = Modifier.fillMaxWidth().freetimeGlass(shape, interactive = false),
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
private fun SubmissionCard(submission: DeveloperSubmission, comments: List<String>) {
    val shape = RoundedCornerShape(18.dp)
    Card(
        modifier = Modifier.fillMaxWidth().freetimeGlass(shape, interactive = false),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(submission.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.status_value, localizedSubmissionStatus(submission.status)), color = statusColor(submission.status))
            submission.version?.let { Text(stringResource(R.string.version_value, it), style = MaterialTheme.typography.bodySmall) }
            submission.packageName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
