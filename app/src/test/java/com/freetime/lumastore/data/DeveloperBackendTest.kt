package com.freetime.lumastore.data

import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

class DeveloperBackendTest {
    private val base = "https://project.supabase.co/storage/v1/object/public/developer-avatars/"

    @Test fun canonicalAvatarsAndCacheSuffix() {
        for (ext in listOf("jpg", "png", "webp")) {
            assertEquals("owner/avatar.$ext", ownedAvatarPath(base, "owner", "${base}owner/avatar.$ext?v=123"))
        }
    }

    @Test fun generatedUuidAvatarsAndCacheSuffix() {
        val uuid = java.util.UUID.randomUUID().toString()
        for (ext in listOf("jpg", "png", "webp")) {
            val path = "owner/avatar-$uuid.$ext"
            assertEquals(path, ownedAvatarPath(base, "owner", base + path))
            assertEquals(path, ownedAvatarPath(base, "owner", "$base$path?v=123"))
        }
    }

    @Test fun malformedUuidAndArbitraryNamesAreRejected() {
        val uuid = "12345678-abcd-4abc-8abc-123456789abc"
        for (filename in listOf(
            "avatar-.jpg", "avatar-random.jpg", "avatar-${uuid.uppercase()}.jpg",
            "avatar-${uuid.replace("-", "")}.jpg", "avatar-${uuid.dropLast(1)}.jpg",
            "avatar-${uuid}0.jpg", "avatar-$uuid.jpeg", "avatar-$uuid.JPG",
            "photo-$uuid.png", "avatar-$uuid.jpg/extra", "avatar-$uuid%2ejpg",
            "avatar-%31${uuid.drop(1)}.jpg", "../avatar-$uuid.jpg"
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                ownedAvatarPath(base, "owner", "${base}owner/$filename")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ownedAvatarPath(base, "owner", "${base}other/avatar-$uuid.jpg")
        }
    }

    @Test fun externalUrlsAreIgnored() {
        for (url in listOf(
            "https://foreign.supabase.co/storage/v1/object/public/developer-avatars/owner/avatar.jpg",
            "https://project.supabase.co.evil.test/storage/v1/object/public/developer-avatars/owner/avatar.jpg",
            "https://project.supabase.co/storage/v1/object/public/another-bucket/owner/avatar.jpg",
            "not a URL", "//project.supabase.co/storage/v1/object/public/developer-avatars/owner/avatar.jpg"
        )) assertNull(ownedAvatarPath(base, "owner", url))
    }

    @Test fun wrongOwnersAndAmbiguousPathsAreRejected() {
        for (path in listOf(
            "other/avatar.jpg", "owner-extra/avatar.jpg", "owner/../other/avatar.jpg",
            "owner/%2e%2e/avatar.jpg", "owner%2fother/avatar.jpg", "owner/%252e%252e/avatar.jpg",
            "owner/avatar.jpg/extra", "owner/unrelated.png", "owner//avatar.png", "owner/avatar%2ejpg"
        )) {
            assertThrows(IllegalArgumentException::class.java) { ownedAvatarPath(base, "owner", base + path) }
        }
    }

    @Test fun conservativeMessagesAndWrappedFailures() {
        val cases = mapOf(
            "JWT expired" to DeveloperFailureKind.SESSION_EXPIRED,
            "new row violates row-level security policy" to DeveloperFailureKind.ACCESS_DENIED,
            "Avatar access denied: different owner." to DeveloperFailureKind.ACCESS_DENIED,
            "Bucket not found" to DeveloperFailureKind.BUCKET_MISSING,
            "Unsupported profile image format. Choose JPEG, PNG or WebP." to DeveloperFailureKind.IMAGE_FORMAT,
            "Profile image must be between 1 byte and 5 MB." to DeveloperFailureKind.IMAGE_TOO_LARGE,
            "404 Not Found" to DeveloperFailureKind.UNKNOWN,
            "403 Forbidden" to DeveloperFailureKind.UNKNOWN,
            "Object not found" to DeveloperFailureKind.UNKNOWN,
            "Unexpected failure" to DeveloperFailureKind.UNKNOWN
        )
        for ((message, expected) in cases) {
            assertEquals(message, expected, classifyDeveloperFailure(RuntimeException("wrapper", IllegalStateException(message))))
        }
        assertEquals(DeveloperFailureKind.NETWORK, classifyDeveloperFailure(RuntimeException(UnknownHostException("offline"))))
    }

    @Test fun mimeRejectionsWithAndWithoutAnExplicitTypeAreClassified() {
        for (message in listOf(
            "mime type is not supported",
            "mime type image/heic is not supported",
            "mime type image/jpeg is not supported",
            "MIME type \"image/avif\" is not supported"
        )) {
            assertEquals(message, DeveloperFailureKind.IMAGE_FORMAT,
                classifyDeveloperFailure(RuntimeException("wrapper", IllegalStateException(message))))
        }
        assertEquals(DeveloperFailureKind.UNKNOWN,
            classifyDeveloperFailure(IllegalStateException("MIME type image/heic was detected; operation is not supported")))
    }

    @Test fun cancellationIsNeverClassifiedEvenWhenWrapped() {
        val cancellation = CancellationException("cancelled")
        val thrown = assertThrows(CancellationException::class.java) {
            classifyDeveloperFailure(RuntimeException("Bucket not found", cancellation))
        }
        assertSame(cancellation, thrown)
    }
}
