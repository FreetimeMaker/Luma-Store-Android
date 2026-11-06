package com.freetime.lumastore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.freetime.lumastore.data.DeveloperFundingSettings
import com.freetime.lumastore.data.DeveloperRepository
import com.freetime.lumastore.data.DeveloperSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal class DeveloperFundingDraft(initial: DeveloperFundingSettings?) {
    var donate by mutableStateOf(initial?.donateUrl.orEmpty())
    var liberapay by mutableStateOf(initial?.liberapay.orEmpty())
    var collective by mutableStateOf(initial?.opencollective.orEmpty())
    var crypto by mutableStateOf(initial?.cryptoAddresses.orEmpty().mapValues {
        it.value.jsonPrimitive.contentOrNull.orEmpty()
    })
    var saving by mutableStateOf(false)
        internal set
    var error by mutableStateOf<Int?>(null)
        internal set
    var saved by mutableStateOf(false)
        internal set

    internal fun normalizedFields() = listOf(donate, liberapay, collective).map(String::trim)
    internal fun normalizedCrypto() = crypto.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
    private var baselineFields = normalizedFields()
    private var baselineCrypto = normalizedCrypto()
    val dirty: Boolean get() = normalizedFields() != baselineFields || normalizedCrypto() != baselineCrypto

    internal fun committed() {
        baselineFields = normalizedFields()
        baselineCrypto = normalizedCrypto()
        saved = true
    }

    internal fun discard() {
        if (saving) return
        donate = baselineFields[0]
        liberapay = baselineFields[1]
        collective = baselineFields[2]
        crypto = baselineCrypto.toMap()
        error = null
    }
}

/** Activity lifetime: rotation must not cancel a write or retain a composition callback. */
internal class DeveloperFundingEditorState : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val drafts = mutableMapOf<String, DeveloperFundingDraft>()

    fun draft(userId: String, initial: DeveloperFundingSettings?): DeveloperFundingDraft =
        drafts.getOrPut(userId) { DeveloperFundingDraft(initial) }

    fun save(userId: String, repository: DeveloperRepository, session: DeveloperSession) {
        require(session.userId == userId)
        val draft = drafts[userId] ?: return
        if (draft.saving || draft.saved || !draft.dirty) return
        val fields = draft.normalizedFields()
        val crypto = draft.normalizedCrypto()
        draft.saving = true
        draft.error = null
        scope.launch {
            try {
                repository.saveFunding(session, fields[0], fields[1], fields[2], crypto)
                draft.committed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                draft.error = developerFailureMessage(failure, R.string.funding_save_failed)
            } finally {
                draft.saving = false
            }
        }
    }

    fun consumeSaved(userId: String, draft: DeveloperFundingDraft): Boolean {
        if (drafts[userId] !== draft || !draft.saved) return false
        draft.saved = false
        drafts.remove(userId)
        return true
    }

    fun leave(userId: String, draft: DeveloperFundingDraft) {
        if (drafts[userId] === draft && !draft.saving && !draft.saved) {
            draft.discard()
            drafts.remove(userId)
        }
    }

    override fun onCleared() {
        scope.cancel()
    }
}
