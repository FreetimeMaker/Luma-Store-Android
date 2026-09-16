package com.freetime.lumastore.shared

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AndroidSourceStateStore(context: Context) : SourceStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        SOURCE_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun load(): String? {
        preferences.getString(SHARED_SOURCE_STATE, null)?.let { return it }

        val result = JSONArray()
        defaultStoreSources.forEach { source ->
            result.put(
                source.toJson(
                    enabled = preferences.getBoolean(
                        legacyEnabledKey(source.name, source.url),
                        source.enabled,
                    )
                )
            )
        }

        val legacyCustom = preferences.getString(LEGACY_CUSTOM_SOURCES, null)
        if (!legacyCustom.isNullOrBlank()) {
            runCatching { JSONArray(legacyCustom) }.getOrNull()?.let { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    val url = item.optString("indexUrl").trim()
                    if (name.isBlank() || url.isBlank()) continue
                    result.put(
                        AppSource(
                            name = name,
                            url = url,
                            type = SourceType.FDROID_V1,
                            enabled = preferences.getBoolean(
                                legacyEnabledKey(name, url),
                                true,
                            ),
                            custom = true,
                        ).toJson()
                    )
                }
            }
        }

        return result.toString()
    }

    override fun save(value: String) {
        preferences.edit().putString(SHARED_SOURCE_STATE, value).apply()
    }

    private fun AppSource.toJson(enabled: Boolean = this.enabled): JSONObject =
        JSONObject()
            .put("name", name)
            .put("url", url)
            .put("type", type.name)
            .put("enabled", enabled)
            .put("custom", custom)

    private fun legacyEnabledKey(name: String, url: String): String =
        "source_enabled_${name}_${url}".hashCode().toString()

    private companion object {
        const val SOURCE_PREFERENCES = "app_sources"
        const val SHARED_SOURCE_STATE = "shared_source_state"
        const val LEGACY_CUSTOM_SOURCES = "custom_sources"
    }
}
