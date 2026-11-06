package com.freetime.lumastore

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.freetime.lumastore.data.*
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import java.io.File

/** Activity-owned, per-account draft. Never owns composition callbacks or an Activity. */
internal class DeveloperProfileEditorState(
    context: Context,
    private val repository: DeveloperRepository,
    private val session: DeveloperSession
) : ViewModel() {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("profile_state_cleanup", Context.MODE_PRIVATE)
    private val key = "avatars:${session.userId}"
    private var baseline = emptyList<String>()
    var values by mutableStateOf(List(6) { "" }); private set
    var draftFile by mutableStateOf<String?>(null); private set
    var cropUri by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<Int?>(null); private set
    var saved by mutableStateOf(false); private set
    var cleanupPending by mutableStateOf(false); private set
    var cleanupFailure by mutableStateOf<Int?>(null); private set
    var cleanupRunning by mutableStateOf(false); private set
    private var initialized = false
    val dirty get() = values.map(String::trim) != baseline || draftFile != null || cropUri != null

    fun open(initial: DeveloperProfileSettings?) {
        if (initialized) return
        initialized = true
        baseline = fields(initial)
        values = baseline
        refreshCleanupPending()
        retryCleanup()
    }

    fun field(index: Int) = object : kotlin.properties.ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = values[index]
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String) = edit(index, value)
    }

    fun retryCleanup() {
        if (busy || cleanupRunning) return
        cleanupRunning = true
        scope.launch {
            try { runCleanup() } finally { cleanupRunning = false }
        }
    }

    fun edit(index: Int, value: String) {
        if (!busy) {
            saved = false
            values = values.toMutableList().also { it[index] = value }
        }
    }

    fun selectImage(uri: String) {
        if (busy) return
        saved = false
        cropUri = uri
    }

    fun stage(bytes: ByteArray) {
        if (busy) return
        busy = true
        cropUri = null
        saved = false
        scope.launch {
            var staged: File? = null
            try {
                // Assign ownership inside the IO block: cancellation can discard its result.
                val file = withContext(Dispatchers.IO + NonCancellable) {
                    File.createTempFile("profile-draft-", ".jpg", app.cacheDir).also {
                        staged = it
                        it.writeBytes(bytes)
                    }
                }
                if (!scope.isActive) {
                    file.delete()
                    return@launch
                }
                draftFile?.let { File(it).delete() }
                draftFile = file.absolutePath
                staged = null
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = developerFailureMessage(failure, R.string.profile_crop_save_error)
            } finally {
                staged?.delete()
                busy = false
            }
        }
    }

    fun remove() {
        if (busy) return
        draftFile?.let { File(it).delete() }
        draftFile = null
        edit(5, "")
    }

    fun discard() {
        if (busy) return
        draftFile?.let { File(it).delete() }
        draftFile = null
        cropUri = null
        error = null
        saved = false
        initialized = false
        // Remote cleanup is deliberately independent of the discardable draft.
        // Leave its durable records intact; the next open retries them.
    }

    private fun managedPath(url: String): String? = try {
        ownedAvatarPath(supabase.storage["developer-avatars"].publicUrl(""), session.userId, url)
    } catch (_: IllegalArgumentException) { null }

    private fun managed(url: String) = managedPath(url) != null

    private fun sameAvatar(first: String, second: String): Boolean {
        val path = managedPath(first)
        return if (path != null) path == managedPath(second) else first == second
    }

    private fun matchesProfile(profile: DeveloperProfileSettings, target: List<String>): Boolean {
        val current = fields(profile)
        return current.take(5) == target.take(5) && sameAvatar(current[5], target[5])
    }

    private fun refreshCleanupPending() {
        cleanupPending = queued().isNotEmpty() ||
            prefs.getStringSet("$key:replacements", emptySet()).orEmpty().isNotEmpty()
    }

    private suspend fun runCleanup() {
        cleanupFailure = null
        try {
            withTimeout(20_000) { cleanup() }
        } catch (_: TimeoutCancellationException) {
            cleanupFailure = R.string.developer_error_network
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            cleanupFailure = developerFailureMessage(failure, R.string.profile_saved_cleanup_failed)
        } finally {
            refreshCleanupPending()
        }
    }

    private fun queued() = prefs.getStringSet(key, emptySet()).orEmpty().toSet()
    private fun persist(urls: Set<String>) {
        check(prefs.edit().putStringSet(key, urls).commit()) { "Unable to persist avatar cleanup" }
    }
    private fun enqueue(url: String) {
        if (url.isNotBlank() && managed(url)) persist(queued() + url)
    }

    private suspend fun cleanup() {
        // Journal replacements before the write so a process death after commit does not
        // lose the old-object cleanup. Unrelated profile edits must not block recovery.
        val transitionsKey = "$key:replacements"
        for (entry in prefs.getStringSet(transitionsKey, emptySet()).orEmpty().toSet()) {
            try {
                val record = org.json.JSONArray(entry)
                val target = (1..6).map { record.getString(it) }
                val fresh = repository.loadProfile(session)
                if (fresh != null && sameAvatar(fresh.avatarUrl.orEmpty().trim(), target[5])) {
                    enqueue(record.getString(0))
                }
                // A successful non-matching read retires the journal without authorizing
                // deletion of the old image; failed reads leave the journal untouched.
                val remaining = prefs.getStringSet(transitionsKey, emptySet()).orEmpty() - entry
                check(prefs.edit().putStringSet(transitionsKey, remaining).commit())
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                cleanupFailure = developerFailureMessage(failure, R.string.profile_saved_cleanup_failed)
            }
        }
        for (url in queued()) {
            try {
                val path = managedPath(url)
                if (path == null) { persist(queued() - url); continue }
                // A failed read is NOT evidence that an image is unreferenced.
                val current = repository.loadProfile(session)
                // Query strings do not change object identity. Referenced uploads are
                // published, not deletion candidates, so retire them from the queue.
                if (managedPath(current?.avatarUrl.orEmpty().trim()) != path) {
                    repository.removeAvatar(session, url)
                }
                persist(queued() - url)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                cleanupFailure = developerFailureMessage(failure, R.string.profile_saved_cleanup_failed)
            }
        }
    }

    fun save() {
        if (busy || saved || cleanupRunning) return
        busy = true
        error = null
        val submitted = values.map(String::trim)
        scope.launch {
            var attempted: List<String>? = null
            try {
                val url = draftFile?.let { path ->
                    val bytes = withContext(Dispatchers.IO) { File(path).readBytes() }
                    // Persist the intended object before sending bytes: a lost response can
                    // still mean Storage accepted the upload. Recovery must know its key.
                    val pendingUrl = repository.newAvatarUrl(session, "jpg")
                    enqueue(pendingUrl)
                    repository.uploadAvatar(session, bytes, "jpg", pendingUrl)
                } ?: submitted[5]
                val target = submitted.toMutableList().also { it[5] = url }
                val old = baseline[5]
                if (!sameAvatar(old, target[5]) && old.isNotBlank() && managed(old)) {
                    val transitionsKey = "$key:replacements"
                    val entry = org.json.JSONArray(listOf(old) + target).toString()
                    val records = prefs.getStringSet(transitionsKey, emptySet()).orEmpty() + entry
                    check(prefs.edit().putStringSet(transitionsKey, records).commit())
                }
                attempted = target
                repository.saveProfile(session, target[0], target[1], target[2], target[3], target[4], target[5])
                complete(target)
            } catch (failure: Exception) {
                // Even cancellation or a lost response may follow a committed upsert.
                withContext(NonCancellable) {
                    val target = attempted
                    val fresh = try { repository.loadProfile(session) } catch (_: Exception) { null }
                    if (target != null && fresh != null && matchesProfile(fresh, target)) {
                        complete(target)
                    } else {
                        error = if (failure is CancellationException) R.string.profile_save_failed
                            else developerFailureMessage(failure, R.string.profile_save_failed)
                    }
                }
                if (failure is CancellationException) throw failure
            } finally {
                try {
                    withContext(NonCancellable) {
                        // Unknown outcomes stay queued; cleanup requires a successful fresh read.
                        runCleanup()
                    }
                } finally {
                    busy = false
                }
            }
        }
    }

    private fun complete(target: List<String>) {
        val old = baseline.getOrElse(5) { "" }
        // Only a confirmed DB write/reconciliation makes the old object eligible.
        if (!sameAvatar(old, target[5])) enqueue(old)
        baseline = target
        values = target
        draftFile?.let { File(it).delete() }
        draftFile = null
        cropUri = null
        saved = true
    }

    override fun onCleared() {
        scope.cancel()
        draftFile?.let { File(it).delete() }
    }
}

private fun fields(profile: DeveloperProfileSettings?) = listOf(
    profile?.displayName.orEmpty(), profile?.bio.orEmpty(), profile?.websiteUrl.orEmpty(),
    profile?.githubUrl.orEmpty(), profile?.gitlabUrl.orEmpty(), profile?.avatarUrl.orEmpty()
).map(String::trim)
