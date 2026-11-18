package com.freetime.lumastore.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

/*
 * Response contracts: All-API-Node/src/v2/lumastore/store.js, GET ratings,
 * reviews, rating/me, ratings/me, favorite/me and favorites/me routes.
 * Verified at e7010ad906104bd8e597634c6e0cf7718b88fd5e.
 * Keep account reads on the live client: explicit user filters supplement RLS,
 * and an account change during any suspended read must discard its result.
 */
internal suspend fun loadSupabaseRatingSummary(identifier: String): JSONObject {
    // Match store.js GET ratings and supabase.js getLumaStoreSupabaseClient():
    // publishable-key visibility, never the signed-in account's RLS visibility.
    val appId = storeReadAppId(identifier, client = publicStoreSupabase)
    return buildSupabaseRatingSummary(appId) { offset ->
        JSONArray(publicStoreSupabase.from("store_app_ratings").select(Columns.list("rating")) {
            filter { eq("app_id", appId) }
            // One rating per user/app; order by the key without selecting it.
            order("user_id", Order.ASCENDING)
            range(offset, offset + 99)
        }.data)
    }
}

internal suspend fun buildSupabaseRatingSummary(
    appId: String,
    loadPage: suspend (offset: Long) -> JSONArray
): JSONObject {
    var sum = 0.0
    var count = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val page = loadPage(count)
        currentCoroutineContext().ensureActive()
        if (page.length() == 0) break
        for (index in 0 until page.length()) sum += page.getJSONObject(index).getDouble("rating")
        count += page.length()
        // A short page may reflect a server cap, not the end of the ratings.
    }
    return JSONObject().put("app_id", appId)
        .put("average", if (count == 0L) 0.0 else sum / count)
        .put("count", count)
}

internal suspend fun loadSupabasePublicReviews(identifier: String, sort: String): JSONArray {
    val appId = storeReadAppId(identifier)
    val normalizedSort = sort.lowercase(java.util.Locale.ROOT)
    val rows = JSONArray(supabase.from("store_app_ratings").select(
        Columns.list("rating", "review_text", "updated_at")
    ) {
        filter {
            eq("app_id", appId)
            filterNot("review_text", FilterOperator.IS, null)
        }
        order(
            if (normalizedSort == "highest") "rating" else "updated_at",
            if (normalizedSort == "oldest") Order.ASCENDING else Order.DESCENDING
        )
        limit(100)
    }.data)
    return buildSupabasePublicReviews(rows)
}

internal suspend fun loadSupabaseMyRating(identifier: String): JSONObject = withStoreReadUser { userId ->
    val appId = storeReadAppId(identifier, userId)
    val rows = readForStoreUser(userId) {
        JSONArray(supabase.from("store_app_ratings").select(
            Columns.list("rating", "review_text", "updated_at")
        ) {
            filter { eq("app_id", appId); eq("user_id", userId) }
            limit(1)
        }.data)
    }
    buildSupabaseMyRating(appId, rows.optJSONObject(0))
}

internal suspend fun loadSupabaseMyRatings(): JSONArray = withStoreReadUser { userId ->
    readStoreAccountPages(
        userId,
        "store_app_ratings",
        "app_id,rating,review_text,updated_at,app:store_apps(id,package_name,name,icon_url)",
        "updated_at"
    )
}

internal suspend fun loadSupabaseFavorite(identifier: String): JSONObject = withStoreReadUser { userId ->
    val appId = storeReadAppId(identifier, userId)
    val rows = readForStoreUser(userId) {
        JSONArray(supabase.from("store_saved_apps").select(Columns.list("app_id")) {
            filter { eq("app_id", appId); eq("user_id", userId) }
            limit(1)
        }.data)
    }
    JSONObject().put("app_id", appId).put("favorite", rows.length() > 0)
}

internal suspend fun loadSupabaseFavorites(): JSONArray = withStoreReadUser { userId ->
    readStoreAccountPages(
        userId,
        "store_saved_apps",
        "app_id,created_at,app:store_apps(id,package_name,name,icon_url)",
        "created_at"
    )
}

private suspend fun storeReadAppId(
    identifier: String,
    userId: String? = null,
    client: SupabaseClient = supabase
): String {
    suspend fun read(): String {
        val rows = JSONArray(client.from("store_apps").select(Columns.list("id")) {
            filter { eq(storeAppIdentifierColumn(identifier), identifier) }
            limit(2)
        }.data)
        check(rows.length() == 1) { "App not found or identifier is ambiguous" }
        return rows.getJSONObject(0).getString("id")
    }
    return if (userId == null) read() else readForStoreUser(userId) { read() }
}

private suspend fun <T> withStoreReadUser(read: suspend (String) -> T): T {
    supabase.auth.awaitInitialization()
    val session = supabase.auth.currentSessionOrNull() ?: error("Authentication required")
    check(session.accessToken.isNotBlank()) { "Authentication required" }
    val userId = session.user?.id?.takeIf { it.isNotBlank() } ?: error("Authentication required")
    return readForStoreUser(userId) { read(userId) }
}

private suspend fun requireStoreReadUser(userId: String) {
    currentCoroutineContext().ensureActive()
    val session = supabase.auth.currentSessionOrNull()
    check(session?.user?.id == userId && session.accessToken.isNotBlank()) {
        "Authentication required: account changed during store read"
    }
}

private suspend fun <T> readForStoreUser(userId: String, read: suspend () -> T): T {
    requireStoreReadUser(userId)
    val result = read()
    requireStoreReadUser(userId)
    return result
}

private suspend fun readStoreAccountPages(
    userId: String,
    table: String,
    columns: String,
    timestampColumn: String
): JSONArray {
    val result = JSONArray()
    var offset = 0L
    while (true) {
        val page = readForStoreUser(userId) {
            JSONArray(supabase.from(table).select(Columns.raw(columns)) {
                filter { eq("user_id", userId) }
                order(timestampColumn, Order.DESCENDING)
                // Each account has at most one entry per app; break timestamp ties.
                order("app_id", Order.ASCENDING)
                range(offset, offset + 99)
            }.data)
        }
        // Continue even after a short page: a server row cap may be below 100.
        if (page.length() == 0) return result
        for (index in 0 until page.length()) result.put(page.getJSONObject(index))
        offset += page.length()
    }
}

internal fun buildSupabaseMyRating(appId: String, row: JSONObject?): JSONObject =
    JSONObject().put("app_id", appId).also { result ->
        for (field in listOf("rating", "review_text", "updated_at")) {
            result.put(field, row?.opt(field) ?: JSONObject.NULL)
        }
    }

internal fun buildSupabasePublicReviews(rows: JSONArray): JSONArray {
    val result = JSONArray()
    for (index in 0 until rows.length()) {
        val row = rows.getJSONObject(index)
        val text = (row.opt("review_text") as? String)
            ?.trim { it.isWhitespace() || it == '\uFEFF' }
            ?.takeIf { it.isNotEmpty() } ?: continue
        result.put(JSONObject()
            .put("rating", row.getInt("rating"))
            .put("review_text", text)
            .put("updated_at", row.opt("updated_at") ?: JSONObject.NULL))
    }
    return result
}
