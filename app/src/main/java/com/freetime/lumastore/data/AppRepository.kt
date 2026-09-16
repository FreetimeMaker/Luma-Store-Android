package com.freetime.lumastore.data

import android.content.Context
import com.freetime.lumastore.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class StoreApp(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val version: String,
    val versionCode: Long,
    val iconUrl: String?,
    val screenshotUrls: List<String>,
    val categories: List<String>,
    val apkUrl: String,
    val sourceName: String,
    val authorName: String? = null,
    val authorEmail: String? = null,
    val authorWebsite: String? = null,
    val websiteUrl: String? = null,
    val sourceCodeUrl: String? = null,
    val issueTrackerUrl: String? = null,
    val translationUrl: String? = null,
    val changelogUrl: String? = null,
    val donationUrls: List<String> = emptyList(),
    val liberapay: String? = null,
    val openCollective: String? = null,
    val bitcoin: String? = null,
    val litecoin: String? = null,
    val license: String? = null,
    val antiFeatures: List<String> = emptyList(),
    val closedSource: Boolean = false
)

enum class SourceType {
    FDROID_V1,
    LUMA_API,
    GOOGLE_PLAY
}

data class AppSource(
    val name: String,
    val indexUrl: String,
    val type: SourceType = SourceType.FDROID_V1,
    val custom: Boolean = false,
    val enabledByDefault: Boolean = true,
    val requiresAcknowledgement: Boolean = false
)

private data class ClosedSourceMetadata(
    val values: Map<String, String>,
    val summary: String,
    val description: String,
    val changelog: String,
    val screenshots: List<String>
)

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cachePreferences = appContext.getSharedPreferences(CACHE_PREFERENCES, Context.MODE_PRIVATE)
    private val sourcePreferences = appContext.getSharedPreferences(SOURCE_PREFERENCES, Context.MODE_PRIVATE)

    @Volatile
    private var memoryApps: List<StoreApp>? = null

    private val defaultSources = listOf(
        AppSource("Freetime F-Droid", "https://fdroid.free-time.me/repo/index-v1.json", enabledByDefault = false),
        AppSource("F-Droid", "https://f-droid.org/repo/index-v1.json", enabledByDefault = false),
        AppSource("IzzyOnDroid", "https://apt.izzysoft.de/fdroid/repo/index-v1.json", enabledByDefault = false),
        AppSource("Luma Store", "https://api.free-time.me/v2/lumastore/apps?platform=android", SourceType.LUMA_API),
    )

    val sources: List<AppSource>
        get() = defaultSources + loadCustomSources()

    fun isSourceEnabled(source: AppSource): Boolean =
        sourcePreferences.getBoolean(sourcePreferenceKey(source), source.enabledByDefault)

    fun setSourceEnabled(source: AppSource, enabled: Boolean) {
        sourcePreferences.edit().putBoolean(sourcePreferenceKey(source), enabled).apply()
        memoryApps = null
    }

    fun enabledSources(): List<AppSource> = sources.filter(::isSourceEnabled)

    fun isCustomSource(source: AppSource): Boolean = source.custom

    fun addCustomSource(name: String, repositoryUrl: String): Result<AppSource> = runCatching {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { appContext.getString(R.string.source_name_required) }

        val indexUrl = normalizeFdroidUrl(repositoryUrl)
        val parsed = URL(indexUrl)
        require(parsed.protocol == "https" || parsed.protocol == "http") {
            appContext.getString(R.string.repository_url_invalid_scheme)
        }

        val existingSources = sources
        require(existingSources.none { it.name.equals(cleanName, ignoreCase = true) }) {
            appContext.getString(R.string.source_name_exists)
        }
        require(existingSources.none { it.indexUrl.equals(indexUrl, ignoreCase = true) }) {
            appContext.getString(R.string.repository_already_added)
        }

        val source = AppSource(cleanName, indexUrl, SourceType.FDROID_V1, true)
        saveCustomSources(loadCustomSources() + source)
        setSourceEnabled(source, true)
        memoryApps = null
        source
    }

    fun updateCustomSource(source: AppSource, name: String, repositoryUrl: String): Result<AppSource> = runCatching {
        require(source.custom) { appContext.getString(R.string.only_custom_sources_editable) }

        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { appContext.getString(R.string.source_name_required) }

        val indexUrl = normalizeFdroidUrl(repositoryUrl)
        val parsed = URL(indexUrl)
        require(parsed.protocol == "https" || parsed.protocol == "http") {
            appContext.getString(R.string.repository_url_invalid_scheme)
        }

        val currentCustomSources = loadCustomSources()
        val existingSources = sources.filterNot { it.name == source.name && it.indexUrl == source.indexUrl }
        require(existingSources.none { it.name.equals(cleanName, ignoreCase = true) }) {
            appContext.getString(R.string.source_name_exists)
        }
        require(existingSources.none { it.indexUrl.equals(indexUrl, ignoreCase = true) }) {
            appContext.getString(R.string.repository_already_added)
        }

        val updated = source.copy(name = cleanName, indexUrl = indexUrl)
        saveCustomSources(currentCustomSources.map {
            if (it.name == source.name && it.indexUrl == source.indexUrl) updated else it
        })

        val wasEnabled = isSourceEnabled(source)
        sourcePreferences.edit().remove(sourcePreferenceKey(source)).apply()
        setSourceEnabled(updated, wasEnabled)
        memoryApps = null
        updated
    }

    fun removeCustomSource(source: AppSource): Boolean {
        if (!source.custom) return false
        val updated = loadCustomSources().filterNot { it.name == source.name && it.indexUrl == source.indexUrl }
        saveCustomSources(updated)
        sourcePreferences.edit().remove(sourcePreferenceKey(source)).apply()
        memoryApps = null
        return true
    }

    fun currentApps(): List<StoreApp> {
        memoryApps?.let { return it }
        return loadCachedApps().also { cached ->
            if (cached.isNotEmpty()) memoryApps = cached
        }
    }

    fun loadCachedApps(): List<StoreApp> {
        val raw = cachePreferences.getString(CACHE_KEY_APPS, null) ?: return emptyList()
        val enabledSourceNames = enabledSources().mapTo(mutableSetOf()) { it.name }
        return runCatching { parseCachedApps(raw) }
            .getOrDefault(emptyList())
            .filter { it.sourceName in enabledSourceNames }
    }

    fun cacheTimestamp(): Long = cachePreferences.getLong(CACHE_KEY_TIMESTAMP, 0L)

    fun loadApps(forceRefresh: Boolean = false): Result<List<StoreApp>> = runCatching {
        if (!forceRefresh) {
            memoryApps?.let { return@runCatching it }
            val cached = loadCachedApps()
            if (cached.isNotEmpty()) {
                memoryApps = cached
                return@runCatching cached
            }
        }

        val activeSources = enabledSources()
        if (activeSources.isEmpty()) {
            memoryApps = emptyList()
            return@runCatching emptyList()
        }

        val variants = mutableListOf<StoreApp>()
        var successfulSources = 0
        activeSources.forEach { source ->
            runCatching { loadSource(source) }
                .onSuccess { loaded ->
                    successfulSources++
                    variants += loaded
                }
        }

        if (successfulSources == 0) {
            val cached = loadCachedApps()
            check(cached.isNotEmpty()) { appContext.getString(R.string.no_source_cache_available) }
            memoryApps = cached
            return@runCatching cached
        }

        val normalized = variants
            .distinctBy { "${it.id}\u0000${it.sourceName}" }
            .sortedWith(compareBy<StoreApp> { it.name.lowercase() }.thenBy { it.sourceName.lowercase() })

        memoryApps = normalized
        saveCache(normalized)
        normalized
    }

    private fun loadCustomSources(): List<AppSource> {
        val raw = sourcePreferences.getString(CUSTOM_SOURCES_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    val indexUrl = item.optString("indexUrl").trim()
                    if (name.isBlank() || indexUrl.isBlank()) continue
                    add(AppSource(name, indexUrl, SourceType.FDROID_V1, true))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCustomSources(sources: List<AppSource>) {
        val array = JSONArray()
        sources.forEach { source ->
            array.put(JSONObject().put("name", source.name).put("indexUrl", source.indexUrl))
        }
        sourcePreferences.edit().putString(CUSTOM_SOURCES_KEY, array.toString()).apply()
    }

    private fun normalizeFdroidUrl(rawUrl: String): String {
        val clean = rawUrl.trim()
        require(clean.isNotBlank()) { appContext.getString(R.string.repository_url_required) }
        return when {
            clean.endsWith("/index-v1.json", ignoreCase = true) -> clean
            clean.endsWith("index-v1.json", ignoreCase = true) -> clean
            else -> clean.trimEnd('/') + "/index-v1.json"
        }
    }

    private fun saveCache(apps: List<StoreApp>) {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(JSONObject().apply {
                put("id", app.id)
                put("name", app.name)
                put("summary", app.summary)
                put("description", app.description)
                put("version", app.version)
                put("versionCode", app.versionCode)
                put("iconUrl", app.iconUrl)
                put("screenshotUrls", JSONArray(app.screenshotUrls))
                put("categories", JSONArray(app.categories))
                put("apkUrl", app.apkUrl)
                put("sourceName", app.sourceName)
                put("authorName", app.authorName)
                put("authorEmail", app.authorEmail)
                put("authorWebsite", app.authorWebsite)
                put("websiteUrl", app.websiteUrl)
                put("sourceCodeUrl", app.sourceCodeUrl)
                put("issueTrackerUrl", app.issueTrackerUrl)
                put("translationUrl", app.translationUrl)
                put("changelogUrl", app.changelogUrl)
                put("donationUrls", JSONArray(app.donationUrls))
                put("liberapay", app.liberapay)
                put("openCollective", app.openCollective)
                put("bitcoin", app.bitcoin)
                put("litecoin", app.litecoin)
                put("license", app.license)
                put("antiFeatures", JSONArray(app.antiFeatures))
                put("closedSource", app.closedSource)
            })
        }
        cachePreferences.edit()
            .putString(CACHE_KEY_APPS, array.toString())
            .putLong(CACHE_KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun parseCachedApps(raw: String): List<StoreApp> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    StoreApp(
                        id = id,
                        name = name,
                        summary = item.optString("summary"),
                        description = item.optString("description"),
                        version = item.optString("version"),
                        versionCode = item.optLong("versionCode"),
                        iconUrl = item.optString("iconUrl").takeIf { it.isNotBlank() && it != "null" },
                        screenshotUrls = item.optJSONArray("screenshotUrls").toStringList(),
                        categories = item.optJSONArray("categories").toStringList(),
                        apkUrl = item.optString("apkUrl"),
                        sourceName = item.optString("sourceName"),
                        authorName = item.optNullableString("authorName"),
                        authorEmail = item.optNullableString("authorEmail"),
                        authorWebsite = item.optNullableString("authorWebsite"),
                        websiteUrl = item.optNullableString("websiteUrl"),
                        sourceCodeUrl = item.optNullableString("sourceCodeUrl"),
                        issueTrackerUrl = item.optNullableString("issueTrackerUrl"),
                        translationUrl = item.optNullableString("translationUrl"),
                        changelogUrl = item.optNullableString("changelogUrl"),
                        donationUrls = item.optJSONArray("donationUrls").toStringList(),
                        liberapay = item.optNullableString("liberapay"),
                        openCollective = item.optNullableString("openCollective"),
                        bitcoin = item.optNullableString("bitcoin"),
                        litecoin = item.optNullableString("litecoin"),
                        license = item.optNullableString("license"),
                        antiFeatures = item.optJSONArray("antiFeatures").toStringList(),
                        closedSource = item.optBoolean("closedSource", false)
                    )
                )
            }
        }
    }

    private fun sourcePreferenceKey(source: AppSource): String =
        "source_enabled_${source.name}_${source.indexUrl}".hashCode().toString()

    private fun loadSource(source: AppSource): List<StoreApp> = when (source.type) {
        SourceType.FDROID_V1 -> loadFdroidSource(source)
        SourceType.LUMA_API -> loadLumaApiSource(source)
        SourceType.GOOGLE_PLAY -> emptyList()
    }

    private fun loadFdroidSource(source: AppSource): List<StoreApp> {
        val root = JSONObject(httpGet(source.indexUrl))
        val packages = root.optJSONObject("packages") ?: return emptyList()
        val metadata = root.optJSONObject("apps") ?: JSONObject()
        val results = mutableListOf<StoreApp>()

        packages.keys().forEach { packageName ->
            val versions = packages.optJSONArray(packageName) ?: return@forEach
            val appMetadata = metadata.optJSONObject(packageName)
            val appName = localizedString(appMetadata?.optJSONObject("name"))
                ?: appMetadata?.optString("name")?.takeIf { it.isNotBlank() }
                ?: packageName
            val summary = localizedString(appMetadata?.optJSONObject("summary"))
                ?: appMetadata?.optString("summary").orEmpty()
            val description = localizedString(appMetadata?.optJSONObject("description"))
                ?: appMetadata?.optString("description").orEmpty()
            val categories = appMetadata?.optJSONArray("categories").toStringList()
            val license = appMetadata?.optString("license")?.takeIf { it.isNotBlank() }
            val sourceCode = appMetadata?.optString("sourceCode")?.takeIf { it.isNotBlank() }
            val issueTracker = appMetadata?.optString("issueTracker")?.takeIf { it.isNotBlank() }
            val translation = appMetadata?.optString("translation")?.takeIf { it.isNotBlank() }
            val changelog = appMetadata?.optString("changelog")?.takeIf { it.isNotBlank() }
            val webSite = appMetadata?.optString("webSite")?.takeIf { it.isNotBlank() }
            val authorName = appMetadata?.optString("authorName")?.takeIf { it.isNotBlank() }
            val authorEmail = appMetadata?.optString("authorEmail")?.takeIf { it.isNotBlank() }
            val authorWebSite = appMetadata?.optString("authorWebSite")?.takeIf { it.isNotBlank() }
            val donationUrls = buildList {
                appMetadata?.optString("donate")?.takeIf { it.isNotBlank() }?.let(::add)
                addAll(appMetadata?.optJSONArray("donationLinks").toStringList())
            }
            val antiFeatures = appMetadata?.optJSONArray("antiFeatures").toStringList()
            val screenshots = parseFdroidScreenshots(appMetadata, source)

            for (i in 0 until versions.length()) {
                val version = versions.optJSONObject(i) ?: continue
                val apkName = version.optString("apkName")
                if (apkName.isBlank()) continue
                val versionName = version.optString("versionName").ifBlank { version.optLong("versionCode").toString() }
                val versionCode = version.optLong("versionCode")
                val iconUrl = resolveFdroidAssetUrl(source, version.optString("icon").ifBlank { appMetadata?.optString("icon").orEmpty() })
                results += StoreApp(
                    id = packageName,
                    name = appName,
                    summary = summary,
                    description = description,
                    version = versionName,
                    versionCode = versionCode,
                    iconUrl = iconUrl,
                    screenshotUrls = screenshots,
                    categories = categories,
                    apkUrl = resolveFdroidAssetUrl(source, apkName) ?: continue,
                    sourceName = source.name,
                    authorName = authorName,
                    authorEmail = authorEmail,
                    authorWebsite = authorWebSite,
                    websiteUrl = webSite,
                    sourceCodeUrl = sourceCode,
                    issueTrackerUrl = issueTracker,
                    translationUrl = translation,
                    changelogUrl = changelog,
                    donationUrls = donationUrls,
                    license = license,
                    antiFeatures = antiFeatures
                )
            }
        }
        return results
    }

    private fun loadLumaApiSource(source: AppSource): List<StoreApp> {
        val raw = httpGet(source.indexUrl).trim()
        val apps = when {
            raw.startsWith("[") -> JSONArray(raw)
            raw.startsWith("{") -> JSONObject(raw).optJSONArray("apps") ?: JSONArray()
            else -> JSONArray()
        }

        return buildList {
            for (i in 0 until apps.length()) {
                val item = apps.optJSONObject(i) ?: continue
                val id = item.optString("package_name").ifBlank { item.optString("id") }
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue

                val platforms = item.optJSONArray("platforms")
                var androidDownloadUrl: String? = null
                if (platforms != null) {
                    for (platformIndex in 0 until platforms.length()) {
                        val platform = platforms.optJSONObject(platformIndex) ?: continue
                        if (platform.optString("platform").equals("Android", ignoreCase = true)) {
                            androidDownloadUrl = platform.optDownloadUrl("download_url")
                            if (androidDownloadUrl != null) break
                        }
                    }
                }

                val apkUrl = androidDownloadUrl
                    ?: item.optDownloadUrl("download_url")
                    ?: item.optDownloadUrl("apk_url")
                    ?: continue

                add(
                    StoreApp(
                        id = id,
                        name = name,
                        summary = item.optString("short_description").ifBlank { item.optString("summary") },
                        description = item.optString("description"),
                        version = item.optString("version_name").ifBlank { item.optString("version") },
                        versionCode = item.optLong("version_code"),
                        iconUrl = item.optNullableString("icon_url"),
                        screenshotUrls = item.optJSONArray("screenshots").toStringList().ifEmpty {
                            item.optJSONArray("screenshot_urls").toStringList()
                        },
                        categories = item.optJSONArray("categories").toStringList().ifEmpty {
                            item.optJSONObject("category")?.optString("name")
                                ?.takeIf { it.isNotBlank() }
                                ?.let(::listOf)
                                ?: emptyList()
                        },
                        apkUrl = apkUrl,
                        sourceName = source.name,
                        authorName = item.optNullableString("author_name") ?: item.optNullableString("developer_name"),
                        authorEmail = item.optNullableString("author_email"),
                        authorWebsite = item.optNullableString("author_website"),
                        websiteUrl = item.optNullableString("website_url"),
                        sourceCodeUrl = item.optNullableString("source_code_url") ?: item.optNullableString("repo_url"),
                        issueTrackerUrl = item.optNullableString("issue_tracker_url"),
                        translationUrl = item.optNullableString("translation_url"),
                        changelogUrl = item.optNullableString("changelog_url"),
                        donationUrls = buildList {
                            item.optNullableString("donate_url")?.let(::add)
                            addAll(item.optJSONArray("donation_urls").toStringList())
                        },
                        liberapay = item.optNullableString("liberapay"),
                        openCollective = item.optNullableString("opencollective") ?: item.optNullableString("open_collective"),
                        bitcoin = item.optNullableString("bitcoin"),
                        litecoin = item.optNullableString("litecoin"),
                        license = item.optNullableString("license_type") ?: item.optNullableString("license"),
                        antiFeatures = item.optJSONArray("ant_features").toStringList().ifEmpty {
                            item.optJSONArray("anti_features").toStringList()
                        },
                        closedSource = item.optBoolean("closed_source", false)
                    )
                )
            }
        }
    }

    private fun parseFdroidScreenshots(metadata: JSONObject?, source: AppSource): List<String> = emptyList()

    private fun resolveFdroidAssetUrl(source: AppSource, asset: String): String? {
        if (asset.isBlank()) return null
        if (asset.startsWith("http://") || asset.startsWith("https://")) return asset
        val base = source.indexUrl.substringBeforeLast('/')
        return "$base/${asset.trimStart('/')}"
    }

    private fun localizedString(value: JSONObject?): String? {
        if (value == null) return null
        val locale = Locale.getDefault()
        val preferred = listOf(locale.toLanguageTag(), locale.language, "en-US", "en")
        preferred.forEach { key ->
            value.optString(key).takeIf { it.isNotBlank() }?.let { return it }
        }
        val keys = value.keys()
        while (keys.hasNext()) {
            value.optString(keys.next()).takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    companion object {
        private const val CACHE_PREFERENCES = "app_cache"
        private const val SOURCE_PREFERENCES = "app_sources"
        // Reload snapshots written before download URL validation was introduced.
        private const val CACHE_KEY_APPS = "apps_validated_download_urls"
        private const val CACHE_KEY_TIMESTAMP = "timestamp"
        private const val CUSTOM_SOURCES_KEY = "custom_sources"
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    optString(name).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optDownloadUrl(name: String): String? {
    val value = optNullableString(name)?.trim() ?: return null
    val url = runCatching { URL(value) }.getOrNull() ?: return null
    return value.takeIf {
        (url.protocol == "http" || url.protocol == "https") && url.host.isNotBlank()
    }
}
