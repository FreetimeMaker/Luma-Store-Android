package com.freetime.lumastore

import com.freetime.lumastore.data.buildSupabaseDeveloperProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperProfileFallbackTest {
    @Test fun missingProfileUsesNewestPublishedAppDeveloperName() {
        val apps = JSONArray().put(JSONObject().put("developer_name", "App author"))
        val result = buildSupabaseDeveloperProfile("developer-id", null, apps, null)
        assertEquals("developer-id", result.getString("developer_id"))
        assertEquals("App author", result.getString("display_name"))
        assertFalse(result.getBoolean("verified"))
        assertTrue(result.isNull("bio"))
        assertTrue(result.isNull("funding"))
        assertEquals(apps, result.getJSONArray("apps"))
    }

    @Test fun profileNameAndModernCryptoAddressesTakePrecedence() {
        val profile = JSONObject().put("display_name", "Profile author").put("verified", true)
        val funding = JSONObject().put("bitcoin", "legacy-btc").put("litecoin", "legacy-ltc")
            .put("crypto_addresses", JSONObject().put("bitcoin::Bitcoin", "modern-btc"))
        val result = buildSupabaseDeveloperProfile("developer-id", profile,
            JSONArray().put(JSONObject().put("developer_name", "App author")), funding)
        assertEquals("Profile author", result.getString("display_name"))
        assertTrue(result.getBoolean("verified"))
        val crypto = result.getJSONObject("funding").getJSONObject("crypto_addresses")
        assertEquals("modern-btc", crypto.getString("bitcoin::Bitcoin"))
        assertEquals("legacy-ltc", crypto.getString("litecoin::Litecoin"))
        assertFalse(funding.getJSONObject("crypto_addresses").has("litecoin::Litecoin"))
    }

    @Test fun profileWithoutAppsUsesDefaultDisplayName() {
        val result = buildSupabaseDeveloperProfile("developer-id", JSONObject(), JSONArray(), null)
        assertEquals("Developer", result.getString("display_name"))
        assertEquals(0, result.getJSONArray("apps").length())
    }
}
