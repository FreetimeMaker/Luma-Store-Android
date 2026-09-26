package com.freetime.lumastore.data

import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class AppRatingSummary(val average: Double, val count: Int)
data class MyAppRating(val rating: Int?, val reviewText: String?, val updatedAt: String?)
data class AccountRating(val appId: String, val packageName: String?, val appName: String, val rating: Int, val reviewText: String?, val updatedAt: String?)
data class FavoriteApp(val appId: String, val packageName: String?, val appName: String, val iconUrl: String?, val createdAt: String?)

object LumaStoreApi {
    private const val BASE_URL = "https://api.free-time.me/v2/lumastore"

    suspend fun appDetails(identifier: String): JSONObject = request("GET", "/apps/${encode(identifier)}")

    suspend fun ratings(identifier: String): AppRatingSummary {
        val json = request("GET", "/apps/${encode(identifier)}/ratings")
        return AppRatingSummary(json.optDouble("average", 0.0), json.optInt("count", 0))
    }

    suspend fun myRating(identifier: String): MyAppRating {
        val json = request("GET", "/apps/${encode(identifier)}/rating/me", authenticated = true)
        return MyAppRating(if (json.isNull("rating")) null else json.optInt("rating"), json.optString("review_text").takeIf { it.isNotBlank() }, json.optString("updated_at").takeIf { it.isNotBlank() })
    }

    suspend fun setMyRating(identifier: String, rating: Int, reviewText: String? = null): MyAppRating {
        require(rating in 1..5)
        val body = JSONObject().put("rating", rating).put("review_text", reviewText?.trim().orEmpty())
        val json = request("PUT", "/apps/${encode(identifier)}/rating/me", body.toString(), authenticated = true)
        return MyAppRating(json.optInt("rating"), json.optString("review_text").takeIf { it.isNotBlank() }, json.optString("updated_at").takeIf { it.isNotBlank() })
    }

    suspend fun myRatings(): List<AccountRating> {
        val array = requestArray("GET", "/ratings/me", authenticated = true)
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val app = row.optJSONObject("app")
                add(AccountRating(row.optString("app_id"), app?.optString("package_name")?.takeIf { it.isNotBlank() }, app?.optString("name")?.takeIf { it.isNotBlank() } ?: row.optString("app_id"), row.optInt("rating"), row.optString("review_text").takeIf { it.isNotBlank() }, row.optString("updated_at").takeIf { it.isNotBlank() }))
            }
        }
    }

    suspend fun isFavorite(identifier: String): Boolean =
        request("GET", "/apps/${encode(identifier)}/favorite/me", authenticated = true).optBoolean("favorite", false)

    suspend fun addFavorite(identifier: String) {
        request("PUT", "/apps/${encode(identifier)}/favorite/me", authenticated = true)
    }

    suspend fun removeFavorite(identifier: String) {
        request("DELETE", "/apps/${encode(identifier)}/favorite/me", authenticated = true, allowEmpty = true)
    }

    suspend fun myFavorites(): List<FavoriteApp> {
        val array = requestArray("GET", "/favorites/me", authenticated = true)
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val app = row.optJSONObject("app")
                add(FavoriteApp(
                    appId = row.optString("app_id"),
                    packageName = app?.optString("package_name")?.takeIf { it.isNotBlank() },
                    appName = app?.optString("name")?.takeIf { it.isNotBlank() } ?: row.optString("app_id"),
                    iconUrl = app?.optString("icon_url")?.takeIf { it.isNotBlank() },
                    createdAt = row.optString("created_at").takeIf { it.isNotBlank() }
                ))
            }
        }
    }

    suspend fun deleteMyRating(identifier: String) {
        request("DELETE", "/apps/${encode(identifier)}/rating/me", authenticated = true, allowEmpty = true)
    }

    private suspend fun requestArray(method: String, path: String, authenticated: Boolean = false): JSONArray = withContext(Dispatchers.IO) {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            if (authenticated) {
                supabase.auth.awaitInitialization()
                val session = supabase.auth.currentSessionOrNull() ?: error("Authentication required")
                connection.setRequestProperty("Authorization", "Bearer ${session.accessToken.trim()}")
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(runCatching { JSONObject(text).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Luma Store API request failed ($code)")
            JSONArray(text)
        } finally { connection.disconnect() }
    }

    private suspend fun request(method: String, path: String, body: String? = null, authenticated: Boolean = false, allowEmpty: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            if (authenticated) {
                supabase.auth.awaitInitialization()
                val session = supabase.auth.currentSessionOrNull() ?: error("Authentication required")
                val accessToken = session.accessToken.trim()
                check(accessToken.isNotBlank()) { "Authentication required" }
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter().use { it.write(body) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                error(message?.takeIf { it.isNotBlank() } ?: "Luma Store API request failed ($code)")
            }
            if (text.isBlank() && allowEmpty) JSONObject() else JSONObject(text)
        } finally { connection.disconnect() }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}
