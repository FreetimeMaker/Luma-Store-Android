package com.freetime.lumastore.shared

import java.util.prefs.Preferences

class DesktopSourceStateStore : SourceStateStore {
    private val preferences = Preferences.userRoot().node("com/freetime/lumastore")

    override fun load(): String? = preferences.get(SOURCE_STATE_KEY, null)

    override fun save(value: String) {
        preferences.put(SOURCE_STATE_KEY, value)
        runCatching { preferences.flush() }
    }

    private companion object {
        const val SOURCE_STATE_KEY = "source_state"
    }
}
