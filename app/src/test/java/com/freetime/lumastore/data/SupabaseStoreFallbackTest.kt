package com.freetime.lumastore.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SupabaseStoreFallbackTest {
    @Test fun ratingValidationMatchesApi() {
        assertEquals("review", normalizedStoreReview(5, "  review  "))
        assertEquals("", normalizedStoreReview(1, null))
        assertEquals(2000, normalizedStoreReview(3, " " + "x".repeat(2000) + " ").length)
        for (rating in listOf(0, 6)) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { normalizedStoreReview(rating, null) }
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { normalizedStoreReview(3, "x".repeat(2001)) }
    }

    @Test fun appResolutionUsesApiUuidPattern() {
        assertEquals("id", storeAppIdentifierColumn("550E8400-E29B-41D4-A716-446655440000"))
        for (identifier in listOf("com.example.app", "550e8400-e29b-71d4-a716-446655440000", "550e8400-e29b-41d4-0716-446655440000")) {
            assertEquals("package_name", storeAppIdentifierColumn(identifier))
        }
    }

    @Test fun developersCannotRateOwnApps() {
        requireNotOwnStoreRating(null, "user")
        requireNotOwnStoreRating("other", "user")
        org.junit.Assert.assertThrows(IllegalStateException::class.java) { requireNotOwnStoreRating("user", "user") }
    }

    @Test fun ambiguousIdempotentWriteUsesFallback() = runBlocking {
        val writes = mutableListOf<String>()
        withStoreReadFallback(
            primary = { writes.add("same payload"); throw IOException("Response lost") },
            fallback = { writes.add("same payload"); Unit }
        )
        assertEquals(listOf("same payload", "same payload"), writes)
    }
    @Test fun successfulReadDoesNotUseFallback() = runBlocking {
        assertEquals("api", withStoreReadFallback({ "api" }, { error("Unexpected fallback") }))
    }

    @Test fun networkAndServerFailuresUseFallback() = runBlocking {
        for (failure in listOf(IOException("offline"), LumaApiHttpException(500, "server"), LumaApiHttpException(599, "server"))) {
            assertEquals("database", withStoreReadFallback({ throw failure }, { "database" }))
        }
    }

    @Test fun disabledHostingDeploymentUsesFallback() = runBlocking {
        val body = "Payment required\n\nDEPLOYMENT_DISABLED\n\nfra1::request-id"
        val failure = lumaApiHttpFailure(402, body, "API unavailable")
        assertEquals(true, failure.deploymentDisabled)
        assertEquals("database", withStoreReadFallback({ throw failure }, { "database" }))
    }

    @Test fun disabledHostingHeaderUsesFallbackWithoutResponseBody() = runBlocking {
        val failure = lumaApiHttpFailure(402, "", "API unavailable", hostingError = "DEPLOYMENT_DISABLED")
        assertEquals("database", withStoreReadFallback({ throw failure }, { "database" }))
    }

    @Test fun paymentAndAuthorizationFailuresAreNotReclassifiedAsHostingOutages() = runBlocking {
        for (failure in listOf(
            lumaApiHttpFailure(402, "Payment required for this feature", "Payment required"),
            lumaApiHttpFailure(403, "DEPLOYMENT_DISABLED", "Forbidden"),
            lumaApiHttpFailure(402, "{\"message\":\"DEPLOYMENT_DISABLED\"}", "Unknown response")
        )) {
            try {
                withStoreReadFallback({ throw failure }, { error("Unexpected fallback") })
                fail("Expected original failure")
            } catch (actual: Exception) {
                assertSame(failure, actual)
            }
        }
    }

    @Test fun clientErrorsParsingErrorsAndCancellationPropagate() = runBlocking {
        for (failure in listOf(LumaApiHttpException(401, "auth"), LumaApiHttpException(403, "forbidden"), LumaApiHttpException(404, "missing"), LumaApiHttpException(429, "limited"), IllegalArgumentException("bad response"), CancellationException("cancelled"))) {
            try {
                withStoreReadFallback({ throw failure }, { error("Unexpected fallback") })
                fail("Expected original failure")
            } catch (actual: Exception) {
                assertSame(failure, actual)
            }
        }
    }

    @Test fun databaseFailurePropagates() = runBlocking {
        val failure = IllegalStateException("RLS denied")
        try {
            withStoreReadFallback({ throw IOException("offline") }, { throw failure })
            fail("Expected database failure")
        } catch (actual: Exception) {
            assertSame(failure, actual)
        }
    }
}
