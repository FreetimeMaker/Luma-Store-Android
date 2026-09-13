package com.freetime.lumastore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freetime.lumastore.data.DeveloperDashboard
import com.freetime.lumastore.data.DeveloperNotification
import com.freetime.lumastore.data.DeveloperRepository
import com.freetime.lumastore.data.DeveloperSession
import com.freetime.lumastore.data.DeveloperSubmission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeveloperScreen(
    repository: DeveloperRepository,
    onBack: () -> Unit,
    onOpenAuth: (String) -> Unit
) {
    var session by remember { mutableStateOf(repository.savedSession()) }
    var dashboard by remember { mutableStateOf<DeveloperDashboard?>(null) }
    var loading by remember { mutableStateOf(session != null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload(currentSession: DeveloperSession) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) { repository.loadDashboard(currentSession) }
        }.onSuccess { dashboard = it }
            .onFailure {
                error = it.message ?: "Developer-Daten konnten nicht geladen werden."
                if ((it.message ?: "").contains("JWT", ignoreCase = true) ||
                    (it.message ?: "").contains("401")) {
                    withContext(Dispatchers.IO) { repository.signOut(currentSession) }
                    session = null
                    dashboard = null
                }
            }
        loading = false
    }

    LaunchedEffect(session?.accessToken) {
        val current = session ?: return@LaunchedEffect
        reload(current)
        while (session?.accessToken == current.accessToken) {
            delay(30_000)
            reload(current)
        }
    }

    LaunchedEffect(Unit) {
        repository.consumePendingSession()?.let { session = it }
    }

    if (session == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Zurück zum Store") }
            Text("Developer Login", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Melde dich mit GitHub oder GitLab an.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { onOpenAuth(repository.oauthUrl("github")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mit GitHub anmelden")
            }
            OutlinedButton(
                onClick = { onOpenAuth(repository.oauthUrl("gitlab")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mit GitLab anmelden")
            }
        }
        return
    }

    val currentSession = session ?: return
    val data = dashboard
    val unreadCount = data?.notifications?.count { it.readAt == null } ?: 0
    val commentsBySubmission = data?.comments?.groupBy { it.submissionId }.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← Store") }
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.signOut(currentSession) }
                        session = null
                        dashboard = null
                        error = null
                    }
                }) { Text("Ausloggen") }
            }
        }

        item {
            Text("Developer Bereich", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(currentSession.email ?: currentSession.userId, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { reload(currentSession) } }, enabled = !loading) {
                    Text("Aktualisieren")
                }
                if (unreadCount > 0) {
                    Text("$unreadCount ungelesene Benachrichtigungen", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }

        if (loading && data == null) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        if (data != null) {
            item {
                Text("Benachrichtigungen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            if (data.notifications.isEmpty()) {
                item { Text("Noch keine Benachrichtigungen.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(data.notifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onMarkRead = if (notification.readAt == null) {
                            {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            repository.markNotificationRead(currentSession, notification.id)
                                        }
                                    }
                                    reload(currentSession)
                                }
                            }
                        } else null
                    )
                }
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Meine Apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            if (data.submissions.isEmpty()) {
                item { Text("Für dieses Konto wurden keine Apps gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(data.submissions, key = { it.id }) { submission ->
                    SubmissionCard(
                        submission = submission,
                        comments = commentsBySubmission[submission.id].orEmpty().map { it.body }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun NotificationCard(
    notification: DeveloperNotification,
    onMarkRead: (() -> Unit)?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(notification.title, fontWeight = FontWeight.SemiBold)
                if (notification.readAt == null) {
                    Text("Neu", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            notification.message?.let { Text(it) }
            notification.createdAt?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (onMarkRead != null) {
                TextButton(onClick = onMarkRead) { Text("Als gelesen markieren") }
            }
        }
    }
}

@Composable
private fun SubmissionCard(
    submission: DeveloperSubmission,
    comments: List<String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(submission.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Status: ${submission.status}", color = statusColor(submission.status))
            submission.version?.let { Text("Version: $it", style = MaterialTheme.typography.bodySmall) }
            submission.packageName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            submission.reviewMessage?.let {
                Divider()
                Text("Review-Feedback", fontWeight = FontWeight.SemiBold)
                Text(it)
            }
            if (comments.isNotEmpty()) {
                Divider()
                Text("Kommentare / Feedback", fontWeight = FontWeight.SemiBold)
                comments.forEach { body -> Text("• $body") }
            }
        }
    }
}

@Composable
private fun statusColor(status: String) = when (status) {
    "Approved" -> MaterialTheme.colorScheme.primary
    "Rejected" -> MaterialTheme.colorScheme.error
    "Changes Requested" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
