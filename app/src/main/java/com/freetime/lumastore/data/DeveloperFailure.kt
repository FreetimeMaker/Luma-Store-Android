package com.freetime.lumastore.data

import io.github.jan.supabase.exceptions.RestException

import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException

enum class DeveloperFailureKind {
    SESSION_EXPIRED, ACCESS_DENIED, BUCKET_MISSING, IMAGE_FORMAT, IMAGE_TOO_LARGE, NETWORK, UNKNOWN
}

/** No logging or I/O; cancellation remains control flow, never a user-facing error. */
fun classifyDeveloperFailure(failure: Throwable): DeveloperFailureKind {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val causes = generateSequence(failure) { it.cause }.takeWhile { seen.add(it) }.toList()
    causes.filterIsInstance<CancellationException>().firstOrNull()?.let { throw it }
    for (cause in causes) {
        val rest = cause as? RestException ?: continue
        val code = rest.error.lowercase()
        when (code) {
            "invalidjwt", "jwt_expired", "refresh_token_not_found", "refresh_token_already_used", "session_not_found" -> return DeveloperFailureKind.SESSION_EXPIRED
            "accessdenied", "42501", "insufficient_privilege" -> return DeveloperFailureKind.ACCESS_DENIED
            "nosuchbucket" -> return DeveloperFailureKind.BUCKET_MISSING
            "invalidmimetype" -> return DeveloperFailureKind.IMAGE_FORMAT
            "entitytoolarge" -> return DeveloperFailureKind.IMAGE_TOO_LARGE
        }
        when (rest.statusCode) {
            401 -> return DeveloperFailureKind.SESSION_EXPIRED
            403 -> return DeveloperFailureKind.ACCESS_DENIED
            413 -> return DeveloperFailureKind.IMAGE_TOO_LARGE
            415 -> return DeveloperFailureKind.IMAGE_FORMAT
        }
    }
    for (cause in causes) {
        // RestException.message includes request metadata; do not classify URLs/headers.
        val message = (if (cause is RestException) "${cause.error} ${cause.description.orEmpty()}" else cause.message.orEmpty()).lowercase()
        when {
            "jwt expired" in message || "token has expired" in message -> return DeveloperFailureKind.SESSION_EXPIRED
            "row-level security" in message || "permission denied" in message || "avatar access denied" in message -> return DeveloperFailureKind.ACCESS_DENIED
            "bucket not found" in message || "bucket does not exist" in message -> return DeveloperFailureKind.BUCKET_MISSING
            "unsupported profile image format" in message ||
                Regex("\\bmime type(?:\\s+[^\\s]+)?\\s+is not supported\\b").containsMatchIn(message) -> return DeveloperFailureKind.IMAGE_FORMAT
            "profile image must be between 1 byte and 5 mb" in message || "payload too large" in message || "maximum allowed size" in message -> return DeveloperFailureKind.IMAGE_TOO_LARGE
        }
    }
    return if (causes.any { it is IOException }) DeveloperFailureKind.NETWORK else DeveloperFailureKind.UNKNOWN
}
