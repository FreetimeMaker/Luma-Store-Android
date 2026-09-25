package com.freetime.lumastore.data

import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class AppRatingSummary(val average: Double, val count: Int)
data class MyAppRating(val rating: Int?, val updatedAt: String?)

object LumaStoreApi {
    private const val BASE_URL = "https://api.free-time.me/v2/lumastore"

    suspend fun appDetails(identifier: String): JSONObject = request("GET", "/apps/${encode(identifier)}")

    suspend fun ratings(identifier: String): AppRatingSummary {
        val json = request("GET", "/apps/${encode(identifier)}/ratings")
        return AppRatingSummary(json.optDouble("average", 0.0), json.optInt("count", 0))
    }

    suspend fun myRating(identifier: String): MyAppRating {
        val json = request("GET", "/apps/${encode(identifier)}/rating/me", authenticated = true)
        return MyAppRating(if (json.isNull("rating")) null else json.optInt("rating"), json.optString("updated_at").takeIf { it.isNotBlank() })
    }

    suspend fun setMyRating(identifier: String, rating: Int): MyAppRating {
        require(rating in 1..5)
        val json = request("PUT", "/apps/${encode(identifier)}/rating/me", JSONObject().put("rating", rating).toString(), authenticated = true)
        return MyAppRating(json.optInt("rating"), json.optString("updated_at").takeIf { it.isNotBlank() })
    }

    suspend fun deleteMyRating(identifier: String) {
        request("DELETE", "/apps/${encode(identifier)}/rating/me", authenticated = true, allowEmpty = true)
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
