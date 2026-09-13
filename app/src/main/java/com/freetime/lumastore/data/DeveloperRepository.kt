package com.freetime.lumastore.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Gitlab
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable data class DeveloperSession(val accessToken: String, val refreshToken: String?, val userId: String, val email: String?)
@Serializable data class DeveloperSubmission(val id: String, val name: String, val status: String, @SerialName("review_message") val reviewMessage: String? = null, val version: String? = null, @SerialName("package_name") val packageName: String? = null, @SerialName("submitted_at") val submittedAt: String? = null, @SerialName("status_updated_at") val statusUpdatedAt: String? = null)
@Serializable data class DeveloperComment(val id: String, @SerialName("submission_id") val submissionId: String, val body: String, @SerialName("created_at") val createdAt: String? = null, @SerialName("user_id") val userId: String? = null)
@Serializable data class DeveloperNotification(val id: String, @SerialName("submission_id") val submissionId: String, val type: String, val title: String, val message: String? = null, @SerialName("created_at") val createdAt: String? = null, @SerialName("read_at") val readAt: String? = null)
data class DeveloperDashboard(val submissions: List<DeveloperSubmission>, val comments: List<DeveloperComment>, val notifications: List<DeveloperNotification>)

class DeveloperRepository {
    fun sessionFlow(): Flow<DeveloperSession?> = supabase.auth.sessionStatus.map { supabase.auth.currentSessionOrNull()?.toDeveloperSession() }
    suspend fun savedSession(): DeveloperSession? { supabase.auth.awaitInitialization(); return supabase.auth.currentSessionOrNull()?.toDeveloperSession() }
    suspend fun signInWithGitHub() { supabase.auth.signInWith(Github) }
    suspend fun signInWithGitLab() { supabase.auth.signInWith(Gitlab) }
    suspend fun currentSession(): DeveloperSession? = supabase.auth.currentSessionOrNull()?.toDeveloperSession()
    suspend fun signOut() { supabase.auth.signOut() }

    suspend fun loadDashboard(session: DeveloperSession): DeveloperDashboard {
        val submissions = loadSubmissions(session)
        if (submissions.isEmpty()) return DeveloperDashboard(emptyList(), emptyList(), loadNotifications(session))
        return DeveloperDashboard(submissions, loadComments(submissions.map { it.id }), loadNotifications(session))
    }

    suspend fun markNotificationRead(notificationId: String) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        supabase.from("luma_developer_notifications").update({ set("read_at", now) }) { filter { eq("id", notificationId) } }
    }

    private suspend fun loadSubmissions(session: DeveloperSession): List<DeveloperSubmission> = supabase.from("luma_submissions").select(columns = Columns.list("id", "name", "status", "review_message", "version", "package_name", "submitted_at", "status_updated_at")) { filter { eq("user_id", session.userId) }; order("submitted_at", Order.DESCENDING) }.decodeList()
    private suspend fun loadComments(submissionIds: List<String>): List<DeveloperComment> = supabase.from("luma_review_comments").select(columns = Columns.list("id", "submission_id", "body", "created_at", "user_id")) { filter { isIn("submission_id", submissionIds) }; order("created_at", Order.DESCENDING) }.decodeList()
    private suspend fun loadNotifications(session: DeveloperSession): List<DeveloperNotification> = supabase.from("luma_developer_notifications").select(columns = Columns.list("id", "submission_id", "type", "title", "message", "created_at", "read_at")) { filter { eq("user_id", session.userId) }; order("created_at", Order.DESCENDING); limit(100) }.decodeList()

    private fun io.github.jan.supabase.auth.user.UserSession.toDeveloperSession(): DeveloperSession {
        val currentUser = user ?: error("Supabase session does not contain a user.")
        return DeveloperSession(accessToken, refreshToken, currentUser.id, currentUser.email)
    }
}
