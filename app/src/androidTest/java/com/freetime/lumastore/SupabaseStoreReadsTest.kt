package com.freetime.lumastore

import com.freetime.lumastore.data.buildSupabaseMyRating
import com.freetime.lumastore.data.buildSupabasePublicReviews
import com.freetime.lumastore.data.buildSupabaseRatingSummary
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

import org.junit.Test

class SupabaseStoreReadsTest {
    @Test fun missingRatingHasExplicitNullFields() {
        val result = buildSupabaseMyRating("app-id", null)
        assertEquals("app-id", result.getString("app_id"))
        assertEquals(4, result.length())
        for (field in listOf("rating", "review_text", "updated_at")) {
            assertTrue(result.has(field))
            assertTrue(result.isNull(field))
        }
    }

    @Test fun ownRatingPreservesValuesWithoutExposingUserId() {
        val row = JSONObject().put("rating", 4).put("review_text", "  My review  ")
            .put("updated_at", "2026-11-18T12:00:00Z").put("user_id", "private")
        val result = buildSupabaseMyRating("app-id", row)
        assertEquals(4, result.getInt("rating"))
        assertEquals("  My review  ", result.getString("review_text"))
        assertEquals("2026-11-18T12:00:00Z", result.getString("updated_at"))
        assertFalse(result.has("user_id"))
        assertFalse(row.has("app_id"))
    }

    @Test fun publicReviewsTrimTextRejectNonTextAndKeepQueryOrder() {
        val rows = JSONArray()
            .put(JSONObject().put("rating", 2).put("review_text", JSONObject.NULL))
            .put(JSONObject().put("rating", 3).put("review_text", " \n\uFEFF "))
            .put(JSONObject().put("rating", 4).put("review_text", 123))
            .put(JSONObject().put("rating", 5).put("review_text", "\uFEFF Great! \n")
                .put("updated_at", "newer").put("user_id", "private"))
            .put(JSONObject().put("rating", 1).put("review_text", "Older review")
                .put("updated_at", JSONObject.NULL))
        val result = buildSupabasePublicReviews(rows)
        assertEquals(2, result.length())
        val first = result.getJSONObject(0)
        assertEquals(3, first.length())
        assertEquals(5, first.getInt("rating"))
        assertEquals("Great!", first.getString("review_text"))
        assertEquals("newer", first.getString("updated_at"))
        assertFalse(first.has("user_id"))
        assertEquals("Older review", result.getJSONObject(1).getString("review_text"))
        assertTrue(result.getJSONObject(1).isNull("updated_at"))
        assertEquals("\uFEFF Great! \n", rows.getJSONObject(3).getString("review_text"))
    }

    @Test fun emptyRatingSummaryHasZeroAverageAndCount() = runBlocking {
        val offsets = mutableListOf<Long>()
        val result = buildSupabaseRatingSummary("app-id") { offset ->
            offsets.add(offset)
            JSONArray()
        }
        assertEquals(listOf(0L), offsets)
        assertEquals("app-id", result.getString("app_id"))
        assertEquals(0.0, result.getDouble("average"), 0.0)
        assertEquals(0L, result.getLong("count"))
        assertEquals(3, result.length())
    }

    @Test fun ratingSummaryAccumulatesShortPagesWithoutAveragingPageAverages() = runBlocking {
        val offsets = mutableListOf<Long>()
        val result = buildSupabaseRatingSummary("app-id") { offset ->
            offsets.add(offset)
            when (offset) {
                0L -> JSONArray().put(JSONObject().put("rating", 1))
                    .put(JSONObject().put("rating", 2))
                2L -> JSONArray().put(JSONObject().put("rating", 5))
                3L -> JSONArray()
                else -> error("Unexpected offset: $offset")
            }
        }
        assertEquals(listOf(0L, 2L, 3L), offsets)
        assertEquals("app-id", result.getString("app_id"))
        assertEquals(8.0 / 3.0, result.getDouble("average"), 0.0000001)
        assertEquals(3L, result.getLong("count"))
        assertEquals(3, result.length())
        assertFalse(result.has("rating"))
        assertFalse(result.has("user_id"))
    }

    @Test fun ratingSummaryReadsBeyondAFullPage() = runBlocking {
        val offsets = mutableListOf<Long>()
        val result = buildSupabaseRatingSummary("app-id") { offset ->
            offsets.add(offset)
            when (offset) {
                0L -> JSONArray().also { page ->
                    repeat(100) { page.put(JSONObject().put("rating", 4)) }
                }
                100L -> JSONArray().put(JSONObject().put("rating", 1))
                101L -> JSONArray()
                else -> error("Unexpected offset: $offset")
            }
        }
        assertEquals(listOf(0L, 100L, 101L), offsets)
        assertEquals(101L, result.getLong("count"))
        assertEquals(401.0 / 101.0, result.getDouble("average"), 0.0000001)
    }
}
