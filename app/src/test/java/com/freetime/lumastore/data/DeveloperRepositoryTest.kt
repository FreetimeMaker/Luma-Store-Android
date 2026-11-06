package com.freetime.lumastore.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.util.UUID

class DeveloperRepositoryTest {
    private val base = "https://project.supabase.co/storage/v1/object/public/developer-avatars/"

    @Test fun avatarVersionsChangeWithoutChangingTheObjectPath() {
        val first = versionedAvatarUrl(base, "owner")
        val second = versionedAvatarUrl(base.trimEnd('/'), "owner")
        assertNotEquals(first, second)
        for (url in listOf(first, second)) {
            val uri = URI(url)
            assertEquals("${base}owner/avatar.jpg", url.substringBefore('?'))
            assertEquals("owner/avatar.jpg", ownedAvatarPath(base, "owner", url))
            val version = uri.rawQuery.removePrefix("v=")
            assertEquals(version, UUID.fromString(version).toString())
        }
    }

    @Test fun onlyJpegExtensionsAreAccepted() {
        for (extension in listOf("jpg", "jpeg", "JPG", "JPEG")) requireJpegAvatar(extension)
        for (extension in listOf("png", "webp", "", ".jpg", "jpg/extra")) {
            assertThrows(IllegalArgumentException::class.java) { requireJpegAvatar(extension) }
        }
    }

    @Test fun malformedOwnersCannotGenerateObjectUrls() {
        for (owner in listOf("", "../other", "owner/other", "owner%2fother", "owner?query", "owner#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { versionedAvatarUrl(base, owner) }
        }
    }

    @Test fun identityMustMatchAndCannotBeMissing() {
        assertTrue(developerIdentityMatches("owner", "owner"))
        assertFalse(developerIdentityMatches("owner", "other"))
        assertFalse(developerIdentityMatches("owner", null))
        assertFalse(developerIdentityMatches("", ""))
        assertFalse(developerIdentityMatches(" ", " "))
        assertFalse(developerIdentityMatches("owner", "OWNER"))
    }

    @Test fun onlyInitialCreateExplicitlySetsUnverified() {
        val update = DeveloperProfileUpdate(developerId = "owner", displayName = "Developer")
        val created = developerProfilePayload(update, creating = true)
        val updated = developerProfilePayload(update, creating = false)
        assertEquals(JsonPrimitive(false), created["verified"])
        assertFalse(updated.containsKey("verified"))
        assertEquals(updated, kotlinx.serialization.json.JsonObject(created - "verified"))
        assertEquals(JsonPrimitive("owner"), updated["developer_id"])
        assertEquals(JsonPrimitive("Developer"), updated["display_name"])
    }

    @Test fun updatesExplicitlyClearOptionalFields() {
        val payload = developerProfilePayload(
            DeveloperProfileUpdate(developerId = "owner", displayName = "Developer"), creating = false
        )
        for (field in listOf("bio", "website_url", "github_url", "gitlab_url", "avatar_url")) {
            assertEquals(field, JsonNull, payload[field])
        }
        assertFalse(payload.containsKey("verified"))
    }
}
