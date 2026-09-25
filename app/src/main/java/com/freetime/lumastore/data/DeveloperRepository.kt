package com.freetime.lumastore.data

import android.content.Context
import com.freetime.lumastore.R
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Gitlab
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable data class DeveloperSession(val accessToken: String, val refreshToken: String?, val userId: String, val email: String?)
@Serializable data class DeveloperSubmission(val id: String, val name: String, val status: String, @SerialName("review_message") val reviewMessage: String? = null, val version: String? = null, @SerialName("version_code") val versionCode: Long? = null, @SerialName("package_name") val packageName: String? = null, @SerialName("short_description") val shortDescription: String? = null, val description: String? = null, val changelog: String? = null, @SerialName("repo_url") val repoUrl: String? = null, val link: String? = null, @SerialName("store_app_id") val storeAppId: String? = null, val platforms: JsonElement? = null, @SerialName("separate_platform_repos") val separatePlatformRepos: Boolean? = null, @SerialName("submitted_at") val submittedAt: String? = null, @SerialName("status_updated_at") val statusUpdatedAt: String? = null)
@Serializable data class DeveloperComment(val id: String, @SerialName("submission_id") val submissionId: String, val body: String, @SerialName("created_at") val createdAt: String? = null, @SerialName("user_id") val userId: String? = null)
@Serializable data class DeveloperNotification(val id: String, @SerialName("submission_id") val submissionId: String, val type: String, val title: String, val message: String? = null, @SerialName("created_at") val createdAt: String? = null, @SerialName("read_at") val readAt: String? = null)
@Serializable data class DeveloperPlatformArtifact(val id: String, @SerialName("app_id") val appId: String, val platform: String, @SerialName("package_type") val packageType: String? = null, @SerialName("download_url") val downloadUrl: String? = null, @SerialName("file_size_mb") val fileSizeMb: Double? = null, @SerialName("linux_package_base") val linuxPackageBase: String? = null, val sha256: String? = null, @SerialName("artifact_verified_at") val artifactVerifiedAt: String? = null, @SerialName("artifact_size_bytes") val artifactSizeBytes: Long? = null, @SerialName("repo_url") val repoUrl: String? = null, @SerialName("listing_metadata") val listingMetadata: JsonElement? = null)
@Serializable data class DeveloperSecurityScan(val id: String, @SerialName("submission_id") val submissionId: String, val status: String, @SerialName("risk_level") val riskLevel: String, val provider: String? = null, @SerialName("malicious_count") val maliciousCount: Int? = null, @SerialName("suspicious_count") val suspiciousCount: Int? = null, @SerialName("harmless_count") val harmlessCount: Int? = null, @SerialName("undetected_count") val undetectedCount: Int? = null, @SerialName("virus_total_permalink") val virusTotalPermalink: String? = null, @SerialName("error_message") val errorMessage: String? = null, @SerialName("scanned_at") val scannedAt: String? = null)
@Serializable data class DeveloperDownloadStats(@SerialName("app_id") val appId: String, val total: Long = 0, val today: Long = 0, @SerialName("this_month") val thisMonth: Long = 0, @SerialName("this_year") val thisYear: Long = 0)
@Serializable private data class DeveloperProfileRef(@SerialName("developer_id") val developerId: String)
@Serializable private data class StoreAppSubmissionRef(val id: String, @SerialName("luma_submission_id") val lumaSubmissionId: String? = null)
data class DeveloperDashboard(val submissions: List<DeveloperSubmission>, val comments: List<DeveloperComment>, val notifications: List<DeveloperNotification>, val artifacts: Map<String, List<DeveloperPlatformArtifact>> = emptyMap(), val scans: Map<String, DeveloperSecurityScan> = emptyMap(), val downloadStats: Map<String, DeveloperDownloadStats> = emptyMap(), val submissionStoreIds: Map<String, String> = emptyMap())

class DeveloperRepository(context: Context) {
    private val appContext = context.applicationContext
    fun sessionFlow(): Flow<DeveloperSession?> = supabase.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> status.session.toDeveloperSession()
            is SessionStatus.NotAuthenticated -> null
            is SessionStatus.RefreshFailure -> supabase.auth.currentSessionOrNull()?.toDeveloperSession()
            SessionStatus.Initializing -> supabase.auth.currentSessionOrNull()?.toDeveloperSession()
        }
    }
    suspend fun savedSession(): DeveloperSession? { supabase.auth.awaitInitialization(); return supabase.auth.currentSessionOrNull()?.toDeveloperSession() }
    suspend fun signInWithGoogle() { supabase.auth.signInWith(Google, redirectUrl = OAUTH_REDIRECT_URL) }
    suspend fun signInWithGitHub() { supabase.auth.signInWith(Github, redirectUrl = OAUTH_REDIRECT_URL) }
    suspend fun signInWithGitLab() { supabase.auth.signInWith(Gitlab, redirectUrl = OAUTH_REDIRECT_URL) }
    suspend fun currentSession(): DeveloperSession? = supabase.auth.currentSessionOrNull()?.toDeveloperSession()
    suspend fun signOut() { supabase.auth.signOut() }

    suspend fun isDeveloper(session: DeveloperSession): Boolean =
        runCatching {
            supabase.from("luma_developer_profiles")
                .select(columns = Columns.list("developer_id")) { filter { eq("developer_id", session.userId) }; limit(1) }
                .decodeList<DeveloperProfileRef>()
                .isNotEmpty()
        }.getOrDefault(false)

    suspend fun loadDashboard(session: DeveloperSession): DeveloperDashboard {
        val submissions = loadSubmissions(session)
        if (submissions.isEmpty()) return DeveloperDashboard(emptyList(), emptyList(), loadNotifications(session))
        val storeRefs = supabase.from("store_apps")
            .select(columns = Columns.list("id", "luma_submission_id")) {
                filter { isIn("luma_submission_id", submissions.map { it.id }) }
            }
            .decodeList<StoreAppSubmissionRef>()
        val submissionStoreIds = storeRefs.mapNotNull { ref -> ref.lumaSubmissionId?.let { it to ref.id } }.toMap()
        val appIds = (submissions.mapNotNull { it.storeAppId } + storeRefs.map { it.id }).distinct()
        val artifacts: Map<String, List<DeveloperPlatformArtifact>> = if (appIds.isEmpty()) emptyMap() else supabase.from("store_app_platforms")
            .select(columns = Columns.list("id", "app_id", "platform", "package_type", "download_url", "file_size_mb", "linux_package_base", "sha256", "artifact_verified_at", "artifact_size_bytes", "repo_url", "listing_metadata")) { filter { isIn("app_id", appIds) } }
            .decodeList<DeveloperPlatformArtifact>()
            .groupBy { it.appId }
        val scans = supabase.from("luma_security_scans")
            .select(columns = Columns.list("id", "submission_id", "status", "risk_level", "provider", "malicious_count", "suspicious_count", "harmless_count", "undetected_count", "virus_total_permalink", "error_message", "scanned_at")) { filter { isIn("submission_id", submissions.map { it.id }) }; order("created_at", Order.DESCENDING) }
            .decodeList<DeveloperSecurityScan>().groupBy { it.submissionId }.mapValues { it.value.first() }
        val stats: Map<String, DeveloperDownloadStats> = runCatching {
            supabase.postgrest.rpc("get_my_luma_download_stats").decodeList<DeveloperDownloadStats>().associateBy { stat -> stat.appId }
        }.getOrElse { emptyMap() }
        return DeveloperDashboard(submissions, loadComments(submissions.map { it.id }), loadNotifications(session), artifacts, scans, stats, submissionStoreIds)
    }

    suspend fun updateSubmission(submission: DeveloperSubmission, name: String, shortDescription: String, description: String, version: String, versionCode: Long?, changelog: String, repoUrl: String) {
        require(submission.status in setOf("Draft", "Rejected", "Approved", "Changes Requested")) { "This submission cannot be edited in its current state." }
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        supabase.from("luma_submissions").update({
            set("name", name.trim())
            set("short_description", shortDescription.trim())
            set("description", description.trim())
            set("version", version.trim())
            set("version_code", versionCode)
            set("changelog", changelog.trim())
            set("repo_url", repoUrl.trim())
            set("link", repoUrl.trim())
            set("status", "Pending")
            set("status_updated_at", now)
        }) { filter { eq("id", submission.id); eq("user_id", supabase.auth.currentUserOrNull()?.id ?: error("Authentication required")); eq("status", submission.status) } }
    }

    suspend fun updatePlatformMetadata(submission: DeveloperSubmission, platform: String, title: String, shortDescription: String, fullDescription: String, changelog: String, repoUrl: String, downloadUrl: String, screenshots: List<String>) {
        require(submission.status in setOf("Draft", "Rejected", "Approved", "Changes Requested")) { "This submission cannot be edited in its current state." }
        val entries = (submission.platforms as? JsonArray)?.toMutableList() ?: mutableListOf()
        val index = entries.indexOfFirst { runCatching { it.jsonObject["platform"]?.jsonPrimitive?.contentOrNull == platform }.getOrDefault(false) }
        val existing = if (index >= 0) entries[index].jsonObject else JsonObject(emptyMap())
        val metadata = JsonObject(mapOf(
            "title" to JsonPrimitive(title.trim()),
            "shortDescription" to JsonPrimitive(shortDescription.trim()),
            "fullDescription" to JsonPrimitive(fullDescription.trim()),
            "changelog" to JsonPrimitive(changelog.trim()),
            "screenshots" to JsonArray(screenshots.filter { it.isNotBlank() }.map { JsonPrimitive(it.trim()) })
        ))
        val updated = JsonObject(existing.toMutableMap().apply {
            put("platform", JsonPrimitive(platform))
            put("repoUrl", JsonPrimitive(repoUrl.trim()))
            put("downloadUrl", JsonPrimitive(downloadUrl.trim()))
            put("metadata", metadata)
        })
        if (index >= 0) entries[index] = updated else entries.add(updated)
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Authentication required")
        supabase.from("luma_submissions").update({
            set("platforms", JsonArray(entries))
            set("separate_platform_repos", true)
            set("status", "Pending")
            set("status_updated_at", now)
        }) { filter { eq("id", submission.id); eq("user_id", userId); eq("status", submission.status) } }
    }

    suspend fun addPlatformArtifact(submission: DeveloperSubmission, platform: String, packageType: String, downloadUrl: String, repoUrl: String) {
        require(submission.status in setOf("Draft", "Rejected", "Approved", "Changes Requested")) { "This submission cannot be edited in its current state." }
        val allowed = mapOf("Android" to setOf("apk"), "Windows" to setOf("exe", "msi"), "Linux" to setOf("deb", "rpm", "appimage"))
        require(packageType in allowed[platform].orEmpty()) { "Unsupported package type for $platform." }
        require(downloadUrl.startsWith("https://") || downloadUrl.startsWith("http://")) { "A valid download URL is required." }
        val entries = (submission.platforms as? JsonArray)?.toMutableList() ?: mutableListOf()
        val duplicate = entries.any { entry ->
            runCatching {
                val obj = entry.jsonObject
                obj["platform"]?.jsonPrimitive?.contentOrNull == platform &&
                    obj["packageType"]?.jsonPrimitive?.contentOrNull == packageType
            }.getOrDefault(false)
        }
        require(!duplicate) { "$platform $packageType already exists." }
        entries.add(JsonObject(buildMap {
            put("platform", JsonPrimitive(platform))
            put("packageType", JsonPrimitive(packageType))
            put("downloadUrl", JsonPrimitive(downloadUrl.trim()))
            if (repoUrl.isNotBlank()) put("repoUrl", JsonPrimitive(repoUrl.trim()))
        }))
        savePlatforms(submission, entries)
    }

    suspend fun removePlatformArtifact(submission: DeveloperSubmission, platform: String, packageType: String) {
        require(submission.status in setOf("Draft", "Rejected", "Approved", "Changes Requested")) { "This submission cannot be edited in its current state." }
        val entries = (submission.platforms as? JsonArray)?.toMutableList() ?: mutableListOf()
        val removed = entries.removeAll { entry ->
            runCatching {
                val obj = entry.jsonObject
                obj["platform"]?.jsonPrimitive?.contentOrNull == platform &&
                    obj["packageType"]?.jsonPrimitive?.contentOrNull == packageType
            }.getOrDefault(false)
        }
        require(removed) { "Platform artifact was not found." }
        require(entries.isNotEmpty()) { "At least one platform artifact is required." }
        savePlatforms(submission, entries)
    }

    private suspend fun savePlatforms(submission: DeveloperSubmission, entries: List<JsonElement>) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Authentication required")
        supabase.from("luma_submissions").update({
            set("platforms", JsonArray(entries))
            set("separate_platform_repos", true)
            set("status", "Pending")
            set("status_updated_at", now)
        }) { filter { eq("id", submission.id); eq("user_id", userId); eq("status", submission.status) } }
    }

    suspend fun removeSubmission(submission: DeveloperSubmission) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Authentication required")
        if (submission.status == "Approved") {
            val appId = submission.storeAppId ?: error("Published app could not be found.")
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
            supabase.from("store_apps").update({ set("archived_at", now); set("updated_at", now) }) { filter { eq("id", appId); eq("developer_id", userId) } }
            supabase.from("luma_submissions").update({ set("status", "Archived"); set("status_updated_at", now) }) { filter { eq("id", submission.id); eq("user_id", userId) } }
        } else {
            require(submission.status in setOf("Draft", "Pending", "In Review", "Changes Requested", "Rejected")) { "This submission cannot be removed." }
            supabase.from("luma_submissions").delete { filter { eq("id", submission.id); eq("user_id", userId) } }
        }
    }

    suspend fun markNotificationRead(notificationId: String) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        supabase.from("luma_developer_notifications").update({ set("read_at", now) }) { filter { eq("id", notificationId) } }
    }

    private suspend fun loadSubmissions(session: DeveloperSession): List<DeveloperSubmission> {
        val submissions: List<DeveloperSubmission> = supabase.from("luma_submissions")
            .select(columns = Columns.list("id", "name", "status", "review_message", "version", "version_code", "package_name", "short_description", "description", "changelog", "repo_url", "link", "store_app_id", "platforms", "separate_platform_repos", "submitted_at", "status_updated_at")) {
                filter { eq("user_id", session.userId) }
                order("submitted_at", Order.DESCENDING)
            }
            .decodeList()

        val canonicalSubmissionIds = supabase.from("store_apps")
            .select(columns = Columns.list("luma_submission_id"))
            .decodeList<StoreAppSubmissionRef>()
            .mapNotNull { it.lumaSubmissionId }
            .toSet()

        val canonicalSubmissions = submissions.filter { submission ->
            submission.status != "Approved" || submission.id in canonicalSubmissionIds
        }

        val seenApprovedApps = mutableSetOf<String>()
        return canonicalSubmissions.filter { submission ->
            if (submission.status != "Approved") return@filter true

            val appKey = submission.packageName
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?: submission.name.trim().lowercase()

            seenApprovedApps.add(appKey)
        }
    }
    private suspend fun loadComments(submissionIds: List<String>): List<DeveloperComment> = supabase.from("luma_review_comments").select(columns = Columns.list("id", "submission_id", "body", "created_at", "user_id")) { filter { isIn("submission_id", submissionIds) }; order("created_at", Order.DESCENDING) }.decodeList()
    private suspend fun loadNotifications(session: DeveloperSession): List<DeveloperNotification> = supabase.from("luma_developer_notifications").select(columns = Columns.list("id", "submission_id", "type", "title", "message", "created_at", "read_at")) { filter { eq("user_id", session.userId); exact("read_at", null) }; order("created_at", Order.DESCENDING); limit(100) }.decodeList()

    private fun io.github.jan.supabase.auth.user.UserSession.toDeveloperSession(): DeveloperSession {
        val currentUser = user ?: error(appContext.getString(R.string.supabase_session_missing_user))
        return DeveloperSession(accessToken, refreshToken, currentUser.id, currentUser.email)
    }

    companion object {
        private const val OAUTH_REDIRECT_URL = "lumastore://auth"
    }
}
