package com.freetime.lumastore.data

import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonNull
import java.time.Instant
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal class LumaApiHttpException(
    val statusCode: Int,
    message: String,
    val deploymentDisabled: Boolean = false
) : IllegalStateException(message)

internal fun lumaApiHttpFailure(status: Int, responseBody: String, message: String): LumaApiHttpException =
    LumaApiHttpException(status, message, deploymentDisabled = status == 402 &&
        responseBody.lineSequence().any { it.trim() == "DEPLOYMENT_DISABLED" })

internal suspend fun <T> withStoreReadFallback(primary: suspend () -> T, fallback: suspend () -> T): T {
    try {
        return primary()
    } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        if (failure !is IOException && !(failure is LumaApiHttpException &&
                (failure.statusCode in 500..599 || failure.deploymentDisabled))) throw failure
        return fallback()
    }
}

internal fun normalizedStoreReview(rating: Int, review: String?): String {
    require(rating in 1..5) { "rating must be an integer from 1 to 5" }
    return review.orEmpty().trim { it.isWhitespace() || it == '\uFEFF' }.also {
        require(it.length <= 2000) { "review_text must be 2000 characters or fewer" }
    }
}

internal fun storeAppIdentifierColumn(identifier: String): String =
    if (Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE).matches(identifier)) "id" else "package_name"

internal fun requireNotOwnStoreRating(developerId: String?, userId: String) {
    check(developerId != userId) { "Developers cannot rate their own app" }
}

// Use the initialized live session, never a service key or a captured account token.
private suspend fun storeWriteTarget(identifier: String): Pair<String, JSONObject> {
    supabase.auth.awaitInitialization()
    val session = supabase.auth.currentSessionOrNull() ?: error("Authentication required")
    check(session.accessToken.isNotBlank()) { "Authentication required" }
    val userId = session.user?.id?.takeIf { it.isNotBlank() } ?: error("Authentication required")
    val app = JSONArray(supabase.from("store_apps").select(Columns.list("id", "developer_id")) {
        filter { eq(storeAppIdentifierColumn(identifier), identifier) }
        limit(1)
    }.data).optJSONObject(0) ?: error("App not found")
    check(supabase.auth.currentSessionOrNull()?.user?.id == userId) { "Authentication required" }
    return userId to app
}

internal suspend fun saveSupabaseStoreRating(identifier: String, rating: Int, review: String): JSONObject {
    val normalized = normalizedStoreReview(rating, review)
    val (userId, app) = storeWriteTarget(identifier)
    requireNotOwnStoreRating(app.optNullableString("developer_id"), userId)
    val payload = buildJsonObject {
        put("app_id", app.getString("id"))
        put("user_id", userId)
        put("rating", rating)
        put("review_text", if (normalized.isEmpty()) JsonNull else kotlinx.serialization.json.JsonPrimitive(normalized))
        put("updated_at", Instant.now().toString())
    }
    return JSONArray(supabase.from("store_app_ratings").upsert(payload) {
        onConflict = "app_id,user_id"
        select(Columns.list("rating", "review_text", "updated_at"))
    }.data).getJSONObject(0)
}

internal suspend fun writeSupabaseStoreFavorite(identifier: String, saved: Boolean) {
    if (!saved) return deleteSupabaseStoreEntry(identifier, "store_saved_apps")
    val (userId, app) = storeWriteTarget(identifier)
    supabase.from("store_saved_apps").upsert(buildJsonObject {
        put("app_id", app.getString("id"))
        put("user_id", userId)
    }) { onConflict = "app_id,user_id" }
}

internal suspend fun deleteSupabaseStoreEntry(identifier: String, table: String) {
    val (userId, app) = storeWriteTarget(identifier)
    supabase.from(table).delete {
        filter { eq("app_id", app.getString("id")); eq("user_id", userId) }
    }
}

/** Only verified tables are read here; RLS remains authoritative for visibility. */
internal suspend fun loadSupabaseStoreApps(): JSONArray {
    val apps = readStorePages("store_apps", publicApps = true)
    val platforms = readStorePages("store_app_platforms")
    val byApp = mutableMapOf<String, JSONArray>()
    for (index in 0 until platforms.length()) {
        val row = platforms.getJSONObject(index)
        byApp.getOrPut(row.getString("app_id")) { JSONArray() }.put(row)
    }
    for (index in 0 until apps.length()) {
        val app = apps.getJSONObject(index)
        app.put("platforms", byApp[app.getString("id")] ?: JSONArray())
    }
    return apps
}

private suspend fun readStorePages(table: String, publicApps: Boolean = false): JSONArray {
    val result = JSONArray()
    var offset = 0L
    // Small pages also work with the usual PostgREST server-side row cap.
    val pageSize = 100L
    while (true) {
        currentCoroutineContext().ensureActive()
        val page = JSONArray(supabase.from(table).select {
            if (publicApps) filter { exact("archived_at", null) }
            order("id", Order.ASCENDING)
            range(offset, offset + pageSize - 1)
        }.data)
        for (index in 0 until page.length()) result.put(page.getJSONObject(index))
        if (page.length() == 0) return result
        offset += page.length()
    }
}

internal suspend fun loadSupabaseAppDetails(identifier: String): JSONObject {
    val apps = loadSupabaseStoreApps()
    for (index in 0 until apps.length()) {
        val app = apps.getJSONObject(index)
        if (app.optString("id") == identifier || app.optString("package_name") == identifier) return app
    }
    error("Public store app not found")
}

internal suspend fun loadSupabaseDeveloperProfile(identifier: String): JSONObject {
    val profiles = JSONArray(supabase.from("luma_developer_profiles").select(
        columns = Columns.list("developer_id", "display_name", "bio", "website_url", "github_url", "gitlab_url", "avatar_url", "verified")
    ) {
        filter { eq("developer_id", identifier) }; limit(1)
    }.data)
    val developerApps = JSONArray()
    var offset = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val page = JSONArray(supabase.from("store_apps").select(
            columns = Columns.list("id", "package_name", "name", "icon_url", "short_description", "developer_name", "version", "updated_at")
        ) {
            filter { eq("developer_id", identifier); exact("archived_at", null) }
            order("updated_at", Order.DESCENDING)
            order("id", Order.ASCENDING)
            range(offset, offset + 99)
        }.data)
        for (index in 0 until page.length()) developerApps.put(page.getJSONObject(index))
        if (page.length() == 0) break
        offset += page.length()
    }
    val profile = profiles.optJSONObject(0)
    check(profile != null || developerApps.length() > 0) { "Public developer profile not found" }
    val funding = JSONArray(supabase.from("luma_developer_funding").select(
        columns = Columns.list("donate_url", "liberapay", "opencollective", "bitcoin", "litecoin", "crypto_addresses")
    ) {
        filter { eq("developer_id", identifier) }; limit(1)
    }.data).optJSONObject(0)
    return buildSupabaseDeveloperProfile(identifier, profile, developerApps, funding)
}

internal fun buildSupabaseDeveloperProfile(
    identifier: String,
    profile: JSONObject?,
    apps: JSONArray,
    funding: JSONObject?
): JSONObject {
    // Match /developers/:id: published apps can identify a developer without a profile row.
    val result = JSONObject().put("developer_id", identifier)
        .put("display_name", profile?.optNullableString("display_name")
            ?: apps.optJSONObject(0)?.optNullableString("developer_name") ?: "Developer")
        .put("verified", profile?.optBoolean("verified", false) ?: false)
        .put("apps", apps)
    for (field in listOf("bio", "website_url", "github_url", "gitlab_url", "avatar_url")) {
        result.put(field, profile?.optNullableString(field) ?: JSONObject.NULL)
    }
    val normalizedFunding = funding?.let {
        val crypto = JSONObject((it.optJSONObject("crypto_addresses") ?: JSONObject()).toString())
        for ((field, key) in listOf("bitcoin" to "bitcoin::Bitcoin", "litecoin" to "litecoin::Litecoin")) {
            if (crypto.optNullableString(key).isNullOrEmpty()) {
                it.optNullableString(field)?.takeIf { value -> value.isNotEmpty() }?.let { value -> crypto.put(key, value) }
            }
        }
        JSONObject().put("crypto_addresses", crypto).also { normalized ->
            for (field in listOf("donate_url", "liberapay", "opencollective")) {
                normalized.put(field, it.optNullableString(field) ?: JSONObject.NULL)
            }
        }
    }
    return result.put("funding", normalizedFunding ?: JSONObject.NULL)
}
