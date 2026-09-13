package com.freetime.lumastore.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val SUPABASE_URL = "https://ndlaevedujqxhygbyxfh.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_HlppI4ILiXV7DZkpyrDEhQ_ytb2vV6g"
private const val OAUTH_REDIRECT = "lumastore://auth/callback"

data class DeveloperSession(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
    val email: String?
)

data class DeveloperSubmission(
    val id: String,
    val name: String,
    val status: String,
    val reviewMessage: String?,
    val version: String?,
    val packageName: String?,
    val submittedAt: String?,
    val statusUpdatedAt: String?
)

data class DeveloperComment(
    val id: String,
    val submissionId: String,
    val body: String,
    val createdAt: String?,
    val userId: String?
)

data class DeveloperNotification(
    val id: String,
    val submissionId: String,
    val type: String,
    val title: String,
    val message: String?,
    val createdAt: String?,
    val readAt: String?
)

data class DeveloperDashboard(
    val submissions: List<DeveloperSubmission>,
    val comments: List<DeveloperComment>,
    val notifications: List<DeveloperNotification>
)

class DeveloperRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "luma_store_developer_auth",
        Context.MODE_PRIVATE
    )

    fun savedSession(): DeveloperSession? {
        val accessToken = preferences.getString("access_token", null) ?: return null
        val userId = preferences.getString("user_id", null) ?: return null
        return DeveloperSession(
            accessToken = accessToken,
            refreshToken = preferences.getString("refresh_token", null),
            userId = userId,
            email = preferences.getString("email", null)
        )
    }

    fun oauthUrl(provider: String): String {
        val normalizedProvider = provider.lowercase(Locale.US)
        require(normalizedProvider == "github" || normalizedProvider == "gitlab") {
            "Nicht unterstützter Login-Anbieter."
        }
        return "$SUPABASE_URL/auth/v1/authorize?provider=${encode(normalizedProvider)}&redirect_to=${encode(OAUTH_REDIRECT)}"
    }

    fun handleOAuthCallback(uri: Uri): DeveloperSession? {
        if (uri.scheme != "lumastore" || uri.host != "auth" || uri.path != "/callback") return null

        val values = mutableMapOf<String, String>()
        uri.fragment?.split("&")?.forEach { part ->
            val index = part.indexOf('=')
            if (index > 0) {
                val key = Uri.decode(part.substring(0, index))
                val value = Uri.decode(part.substring(index + 1))
                values[key] = value
            }
        }
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let { values[key] = it }
        }

        val errorDescription = values["error_description"] ?: values["error"]
        if (!errorDescription.isNullOrBlank()) error(errorDescription)

        val accessToken = values["access_token"] ?: return null
        val refreshToken = values["refresh_token"]
        val userJson = requestJson(
            method = "GET",
            url = "$SUPABASE_URL/auth/v1/user",
            body = null,
            accessToken = accessToken
        )
        val session = DeveloperSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userJson.getString("id"),
            email = userJson.optString("email").takeIf { it.isNotBlank() && it != "null" }
        )
        saveSession(session)
        return session
    }

    fun consumePendingSession(): DeveloperSession? = savedSession()

    fun signOut(session: DeveloperSession?) {
        if (session != null) {
            runCatching {
                requestRaw(
                    method = "POST",
                    url = "$SUPABASE_URL/auth/v1/logout",
                    body = null,
                    accessToken = session.accessToken
                )
            }
        }
        preferences.edit().clear().apply()
    }

    fun loadDashboard(session: DeveloperSession): DeveloperDashboard {
        val submissions = loadSubmissions(session)
        if (submissions.isEmpty()) {
            return DeveloperDashboard(emptyList(), emptyList(), loadNotifications(session))
        }
        val ids = submissions.map { it.id }
        return DeveloperDashboard(
            submissions = submissions,
            comments = loadComments(session, ids),
            notifications = loadNotifications(session)
        )
    }

    fun markNotificationRead(session: DeveloperSession, notificationId: String) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        requestRaw(
            method = "PATCH",
            url = "$SUPABASE_URL/rest/v1/luma_developer_notifications?id=eq.${encode(notificationId)}",
            body = JSONObject().put("read_at", now).toString(),
            accessToken = session.accessToken,
            prefer = "return=minimal"
        )
    }

    private fun saveSession(session: DeveloperSession) {
        preferences.edit()
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putString("user_id", session.userId)
            .putString("email", session.email)
            .apply()
    }

    private fun loadSubmissions(session: DeveloperSession): List<DeveloperSubmission> {
        val select = "id,name,status,review_message,version,package_name,submitted_at,status_updated_at"
        val text = requestRaw(
            method = "GET",
            url = "$SUPABASE_URL/rest/v1/luma_submissions?select=${encode(select)}&user_id=eq.${encode(session.userId)}&order=submitted_at.desc",
            body = null,
            accessToken = session.accessToken
        )
        val array = JSONArray(text)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    DeveloperSubmission(
                        id = item.getString("id"),
                        name = item.optString("name").ifBlank { "Unbenannte App" },
                        status = item.optString("status"),
                        reviewMessage = nullableString(item, "review_message"),
                        version = nullableString(item, "version"),
                        packageName = nullableString(item, "package_name"),
                        submittedAt = nullableString(item, "submitted_at"),
                        statusUpdatedAt = nullableString(item, "status_updated_at")
                    )
                )
            }
        }
    }

    private fun loadComments(session: DeveloperSession, submissionIds: List<String>): List<DeveloperComment> {
        val inFilter = submissionIds.joinToString(",")
        val select = "id,submission_id,body,created_at,user_id"
        val text = requestRaw(
            method = "GET",
            url = "$SUPABASE_URL/rest/v1/luma_review_comments?select=${encode(select)}&submission_id=in.(${encode(inFilter)})&order=created_at.desc",
            body = null,
            accessToken = session.accessToken
        )
        val array = JSONArray(text)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    DeveloperComment(
                        id = item.getString("id"),
                        submissionId = item.getString("submission_id"),
                        body = item.optString("body"),
                        createdAt = nullableString(item, "created_at"),
                        userId = nullableString(item, "user_id")
                    )
                )
            }
        }
    }

    private fun loadNotifications(session: DeveloperSession): List<DeveloperNotification> {
        val select = "id,submission_id,type,title,message,created_at,read_at"
        val text = requestRaw(
            method = "GET",
            url = "$SUPABASE_URL/rest/v1/luma_developer_notifications?select=${encode(select)}&user_id=eq.${encode(session.userId)}&order=created_at.desc&limit=100",
            body = null,
            accessToken = session.accessToken
        )
        val array = JSONArray(text)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    DeveloperNotification(
                        id = item.getString("id"),
                        submissionId = item.getString("submission_id"),
                        type = item.optString("type"),
                        title = item.optString("title"),
                        message = nullableString(item, "message"),
                        createdAt = nullableString(item, "created_at"),
                        readAt = nullableString(item, "read_at")
                    )
                )
            }
        }
    }

    private fun requestJson(
        method: String,
        url: String,
        body: JSONObject?,
        accessToken: String?
    ): JSONObject = JSONObject(
        requestRaw(method, url, body?.toString(), accessToken)
    )

    private fun requestRaw(
        method: String,
        url: String,
        body: String?,
        accessToken: String?,
        prefer: String? = null
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "Luma-Store/1.0")
            if (!accessToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (!prefer.isNullOrBlank()) connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter().use { it.write(body) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    val json = JSONObject(text)
                    json.optString("msg").ifBlank { json.optString("message") }
                }.getOrNull().orEmpty().ifBlank { "HTTP $code" }
                error(message)
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun nullableString(obj: JSONObject, key: String): String? =
        obj.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}
