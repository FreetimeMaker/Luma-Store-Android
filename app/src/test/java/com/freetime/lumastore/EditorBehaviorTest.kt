package com.freetime.lumastore

import com.freetime.lumastore.data.DeveloperFundingSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class EditorBehaviorTest {
    @Test fun cleanNavigationRunsExactlyOnce() {
        var calls = 0
        EditorNavigationGuard().request { calls++ }
        assertEquals(1, calls)
    }

    @Test fun guardedNavigationWaitsForExplicitConfirmation() {
        val guard = EditorNavigationGuard()
        var calls = 0
        var pending: (() -> Unit)? = null
        guard.interceptor = { destination -> pending = destination }
        guard.request { calls++ }
        assertEquals(0, calls)
        pending!!.invoke()
        assertEquals(1, calls)
    }

    @Test fun cancelledNavigationDoesNotLeave() {
        val guard = EditorNavigationGuard()
        var calls = 0
        guard.interceptor = { /* Keep editing: do not invoke the destination. */ }
        guard.request { calls++ }
        assertEquals(0, calls)
        guard.interceptor = null
        guard.request { calls++ }
        assertEquals(1, calls)
    }

    @Test fun fundingDirtyComparisonMatchesStorageNormalization() {
        val draft = DeveloperFundingDraft(DeveloperFundingSettings(
            developerId = "owner", donateUrl = "https://example.test/donate", opencollective = "collective",
            cryptoAddresses = JsonObject(mapOf("custom::Network" to JsonPrimitive("address")))
        ))
        assertFalse(draft.dirty)
        draft.donate = "  https://example.test/donate  "
        draft.crypto = draft.crypto + ("empty::Network" to "   ")
        assertFalse(draft.dirty)
        draft.collective = "different"
        assertTrue(draft.dirty)
        draft.discard()
        assertFalse(draft.dirty)
        assertEquals("collective", draft.collective)
        assertEquals("address", draft.crypto["custom::Network"])
    }

    @Test fun fundingCommitUpdatesBaselineAndRetainsUnknownWallets() {
        val draft = DeveloperFundingDraft(null)
        draft.crypto = mapOf("new-token::New Network" to " wallet ")
        assertTrue(draft.dirty)
        draft.committed()
        assertTrue(draft.saved)
        assertFalse(draft.dirty)
        assertEquals(mapOf("new-token::New Network" to "wallet"), draft.normalizedCrypto())
    }
}
