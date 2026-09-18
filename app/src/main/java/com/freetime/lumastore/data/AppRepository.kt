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
    val closedSource: Boolean = false,
    val versionChangelog: String? = null
)

enum class SourceType { FDROID_V1, LUMA_API }

data class AppSource(
    val name: String,
    val indexUrl: String,
    val type: SourceType = SourceType.FDROID_V1,
    val custom: Boolean = false,
    val enabledByDefault: Boolean = true
)

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cachePreferences = appContext.getSharedPreferences(CACHE_PREFERENCES, Context.MODE_PRIVATE)
    private val sourcePreferences = appContext.getSharedPreferences(SOURCE_PREFERENCES, Context.MODE_PRIVATE)

    @Volatile private var memoryApps: List<StoreApp>? = null

    private val defaultSources = listOf(
        AppSource(appContext.getString(R.string.source_freetime_fdroid), "https://fdroid.free-time.me/repo/index-v1.json", enabledByDefault = false),
        AppSource(appContext.getString(R.string.source_fdroid), "https://f-droid.org/repo/index-v1.json", enabledByDefault = false),
        AppSource(appContext.getString(R.string.source_izzyondroid), "https://apt.izzysoft.de/fdroid/repo/index-v1.json", enabledByDefault = false),
        AppSource(appContext.getString(R.string.source_luma_store), "https://api.free-time.me/v2/lumastore/apps?platform=android", SourceType.LUMA_API)
    )

    val sources: List<AppSource> get() = defaultSources + loadCustomSources()

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
        require(sources.none { it.name.equals(cleanName, true) }) { appContext.getString(R.string.source_name_exists) }
        require(sources.none { it.indexUrl.equals(indexUrl, true) }) { appContext.getString(R.string.repository_already_added) }
        AppSource(cleanName, indexUrl, SourceType.FDROID_V1, custom = true).also {
            saveCustomSources(loadCustomSources() + it)
            setSourceEnabled(it, true)
        }
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
        val others = sources.filterNot { it.name == source.name && it.indexUrl == source.indexUrl }
        require(others.none { it.name.equals(cleanName, true) }) { appContext.getString(R.string.source_name_exists) }
        require(others.none { it.indexUrl.equals(indexUrl, true) }) { appContext.getString(R.string.repository_already_added) }
        val updated = source.copy(name = cleanName, indexUrl = indexUrl)
        val wasEnabled = isSourceEnabled(source)
        saveCustomSources(loadCustomSources().map { if (it == source) updated else it })
        sourcePreferences.edit().remove(sourcePreferenceKey(source)).apply()
        setSourceEnabled(updated, wasEnabled)
        updated
    }

    fun removeCustomSource(source: AppSource): Boolean {
        if (!source.custom) return false
        saveCustomSources(loadCustomSources().filterNot { it == source })
        sourcePreferences.edit().remove(sourcePreferenceKey(source)).apply()
        memoryApps = null
        return true
    }

    fun currentApps(): List<StoreApp> {
        memoryApps?.let { return it }
        return loadCachedApps().also { if (it.isNotEmpty()) memoryApps = it }
    }

    fun loadCachedApps(): List<StoreApp> {
        val raw = cachePreferences.getString(CACHE_KEY_APPS, null) ?: return emptyList()
        val enabled = enabledSources().mapTo(mutableSetOf()) { it.name }
        return runCatching { parseCachedApps(raw) }.getOrDefault(emptyList()).filter { it.sourceName in enabled }
    }

    fun cacheTimestamp(): Long = cachePreferences.getLong(CACHE_KEY_TIMESTAMP, 0L)

    fun loadApps(forceRefresh: Boolean = false): Result<List<StoreApp>> = runCatching {
        if (!forceRefresh) {
            memoryApps?.let { return@runCatching it }
            val cached = loadCachedApps()
            if (cached.isNotEmpty()) return@runCatching cached.also { memoryApps = it }
        }

        val activeSources = enabledSources()
        if (activeSources.isEmpty()) return@runCatching emptyList<StoreApp>().also { memoryApps = it }

        val variants = mutableListOf<StoreApp>()
        var successfulSources = 0
        activeSources.forEach { source ->
            runCatching { loadSource(source) }.onSuccess {
                successfulSources++
                variants += it
            }
        }

        if (successfulSources == 0) {
            val cached = loadCachedApps()
            check(cached.isNotEmpty()) { appContext.getString(R.string.no_source_cache_available) }
            return@runCatching cached.also { memoryApps = it }
        }

        variants
            .groupBy { it.id to it.sourceName }
            .mapNotNull { (_, entries) -> entries.maxByOrNull { it.versionCode } }
            .sortedWith(compareBy<StoreApp> { it.name.lowercase() }.thenBy { it.sourceName.lowercase() })
            .also {
                memoryApps = it
                saveCache(it)
            }
    }

    private fun loadSource(source: AppSource): List<StoreApp> = when (source.type) {
        SourceType.FDROID_V1 -> loadFdroidSource(source)
        SourceType.LUMA_API -> loadLumaApiSource(source)
    }

    private fun loadFdroidSource(source: AppSource): List<StoreApp> {
        val root = JSONObject(httpGet(source.indexUrl).trimStart('\uFEFF', ' ', '\n', '\r', '\t'))
        val packages = root.optJSONObject("packages") ?: return emptyList()
        val metadataByPackage = parseFdroidMetadata(root.opt("apps"))
        val results = ArrayList<StoreApp>(packages.length())

        val packageNames = packages.keys()
        while (packageNames.hasNext()) {
            val packageName = packageNames.next()
            val versions = packages.optJSONArray(packageName) ?: continue
            val latest = newestVersion(versions) ?: continue
            val apkName = latest.optString("apkName").trim()
            if (apkName.isBlank()) continue

            val meta = metadataByPackage[packageName]
            val localizedEntry = preferredLocalizedMetadata(meta)
            val localizedLocale = localizedEntry?.first
            val localized = localizedEntry?.second
            val versionCode = latest.optLong("versionCode", 0L)
            val versionName = latest.optString("versionName").ifBlank { versionCode.toString() }

            val name = firstText(
                localized?.opt("title"),
                localized?.opt("name"),
                meta?.opt("name")
            ) ?: packageName

            val summary = firstText(
                localized?.opt("short_description"),
                localized?.opt("summary"),
                meta?.opt("summary")
            ).orEmpty()

            val description = firstText(
                localized?.opt("full_description"),
                localized?.opt("description"),
                meta?.opt("description")
            ).orEmpty()

            val iconName = firstText(
                localized?.opt("icon"),
                latest.opt("icon"),
                meta?.opt("icon")
            ).orEmpty()

            results += StoreApp(
                id = packageName,
                name = name,
                summary = summary,
                description = description,
                version = versionName,
                versionCode = versionCode,
                iconUrl = resolveFdroidIconUrl(
                    source = source,
                    packageName = packageName,
                    locale = localizedLocale,
                    localizedIcon = firstText(localized?.opt("icon")),
                    legacyIcon = firstText(latest.opt("icon"), meta?.opt("icon"))
                ),
                screenshotUrls = parseFdroidScreenshots(
                    meta = meta,
                    localized = localized,
                    source = source,
                    packageName = packageName,
                    locale = localizedLocale
                ),
                categories = jsonStringList(meta?.optJSONArray("categories")),
                apkUrl = resolveFdroidAssetUrl(source, apkName) ?: continue,
                sourceName = source.name,
                authorName = stringValue(meta?.opt("authorName")),
                authorEmail = stringValue(meta?.opt("authorEmail")),
                authorWebsite = stringValue(meta?.opt("authorWebSite")),
                websiteUrl = stringValue(meta?.opt("webSite")),
                sourceCodeUrl = stringValue(meta?.opt("sourceCode")),
                issueTrackerUrl = stringValue(meta?.opt("issueTracker")),
                translationUrl = stringValue(meta?.opt("translation")),
                changelogUrl = stringValue(meta?.opt("changelog")),
                versionChangelog = firstText(
                    localized?.opt("whatsNew"),
                    localized?.opt("whats_new"),
                    meta?.opt("whatsNew")
                ),
                donationUrls = buildList {
                    stringValue(meta?.opt("donate"))?.let(::add)
                    addAll(jsonStringList(meta?.optJSONArray("donationLinks")))
                },
                liberapay = stringValue(meta?.opt("liberapay")),
                openCollective = stringValue(meta?.opt("openCollective")),
                bitcoin = stringValue(meta?.opt("bitcoin")),
                litecoin = stringValue(meta?.opt("litecoin")),
                license = stringValue(meta?.opt("license")),
                antiFeatures = parseAntiFeatures(meta?.opt("antiFeatures"))
            )
        }
        return results
    }

    private fun preferredLocalizedMetadata(meta: JSONObject?): Pair<String, JSONObject>? {
        val localized = meta?.optJSONObject("localized") ?: return null
        val locale = Locale.getDefault()
        val preferred = listOf(
            locale.toLanguageTag(),
            locale.language + "-" + locale.country,
            locale.language,
            "en-US",
            "en"
        ).filter { it.isNotBlank() }.distinct()

        preferred.forEach { key ->
            localized.optJSONObject(key)?.let { return key to it }
        }

        val keys = localized.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            localized.optJSONObject(key)?.let { return key to it }
        }
        return null
    }

    private fun parseFdroidMetadata(value: Any?): Map<String, JSONObject> {
        val result = linkedMapOf<String, JSONObject>()
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    value.optJSONObject(key)?.let { result[key] = it }
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val item = value.optJSONObject(i) ?: continue
                    val packageName = item.optString("packageName").ifBlank { item.optString("package_name") }
                    if (packageName.isNotBlank()) result[packageName] = item
                }
            }
        }
        return result
    }

    private fun newestVersion(versions: JSONArray): JSONObject? {
        var best: JSONObject? = null
        var bestCode = Long.MIN_VALUE
        for (i in 0 until versions.length()) {
            val candidate = versions.optJSONObject(i) ?: continue
            val code = candidate.optLong("versionCode", Long.MIN_VALUE)
            if (best == null || code > bestCode) {
                best = candidate
                bestCode = code
            }
        }
        return best
    }

    private fun firstText(vararg values: Any?): String? {
        values.forEach { localizedValue(it)?.takeIf(String::isNotBlank)?.let { value -> return value } }
        return null
    }

    private fun localizedValue(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        is String -> value.takeIf { it.isNotBlank() }
        is JSONObject -> {
            val locale = Locale.getDefault()
            listOf(locale.toLanguageTag(), locale.language, "en-US", "en")
                .firstNotNullOfOrNull { value.optString(it).takeIf(String::isNotBlank) }
                ?: value.keys().asSequence().mapNotNull { value.optString(it).takeIf(String::isNotBlank) }.firstOrNull()
        }
        else -> value.toString().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun stringValue(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        is String -> value.takeIf { it.isNotBlank() }
        else -> value.toString().takeIf { it.isNotBlank() && it != "null" && !it.startsWith("{") && !it.startsWith("[") }
    }

    private fun parseFdroidScreenshots(
        meta: JSONObject?,
        localized: JSONObject?,
        source: AppSource,
        packageName: String,
        locale: String?
    ): List<String> {
        val localizedScreenshots = buildList {
            addAll(resolveLocalizedScreenshotList(source, packageName, locale, "phoneScreenshots", localized?.optJSONArray("phoneScreenshots")))
            addAll(resolveLocalizedScreenshotList(source, packageName, locale, "sevenInchScreenshots", localized?.optJSONArray("sevenInchScreenshots")))
            addAll(resolveLocalizedScreenshotList(source, packageName, locale, "tenInchScreenshots", localized?.optJSONArray("tenInchScreenshots")))
            addAll(resolveLocalizedScreenshotList(source, packageName, locale, "tvScreenshots", localized?.optJSONArray("tvScreenshots")))
            addAll(resolveLocalizedScreenshotList(source, packageName, locale, "wearScreenshots", localized?.optJSONArray("wearScreenshots")))
        }
        if (localizedScreenshots.isNotEmpty()) return localizedScreenshots.distinct()

        return jsonStringList(meta?.optJSONArray("screenshots"))
            .mapNotNull { resolveFdroidAssetUrl(source, it) }
            .distinct()
    }

    private fun resolveLocalizedScreenshotList(
        source: AppSource,
        packageName: String,
        locale: String?,
        directory: String,
        screenshots: JSONArray?
    ): List<String> {
        if (screenshots == null || locale.isNullOrBlank()) return emptyList()
        return jsonStringList(screenshots).mapNotNull { fileName ->
            if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
                fileName
            } else {
                val base = source.indexUrl.substringBeforeLast('/')
                val cleanName = fileName.trimStart('/')
                if (cleanName.contains('/')) "$base/$cleanName"
                else "$base/$packageName/$locale/$directory/$cleanName"
            }
        }
    }

    private fun resolveFdroidIconUrl(
        source: AppSource,
        packageName: String,
        locale: String?,
        localizedIcon: String?,
        legacyIcon: String?
    ): String? {
        val base = source.indexUrl.substringBeforeLast('/')

        localizedIcon?.takeIf { it.isNotBlank() }?.let { icon ->
            if (icon.startsWith("http://") || icon.startsWith("https://")) return icon
            val clean = icon.trimStart('/')
            if (clean.contains('/')) return "$base/$clean"
            if (!locale.isNullOrBlank()) return "$base/$packageName/$locale/$clean"
        }

        legacyIcon?.takeIf { it.isNotBlank() }?.let { icon ->
            if (icon.startsWith("http://") || icon.startsWith("https://")) return icon
            val clean = icon.trimStart('/')
            if (clean.contains('/')) return "$base/$clean"
            return "$base/icons-160/$clean"
        }

        return null
    }

    private fun parseAntiFeatures(value: Any?): List<String> = when (value) {
        is JSONArray -> jsonStringList(value)
        is JSONObject -> value.keys().asSequence().toList()
        else -> emptyList()
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
                    for (p in 0 until platforms.length()) {
                        val platform = platforms.optJSONObject(p) ?: continue
                        if (platform.optString("platform").equals("Android", true)) {
                            androidDownloadUrl = platform.optDownloadUrl("download_url")
                            if (androidDownloadUrl != null) break
                        }
                    }
                }

                val apkUrl = androidDownloadUrl
                    ?: item.optDownloadUrl("download_url")
                    ?: item.optDownloadUrl("apk_url")
                    ?: continue

                add(StoreApp(
                    id = id,
                    name = name,
                    summary = item.optString("short_description").ifBlank { item.optString("summary") },
                    description = item.optString("description"),
                    version = item.optString("version_name").ifBlank { item.optString("version") },
                    versionCode = item.optLong("version_code"),
                    iconUrl = item.optNullableString("icon_url"),
                    screenshotUrls = jsonStringList(item.optJSONArray("screenshots")).ifEmpty { jsonStringList(item.optJSONArray("screenshot_urls")) },
                    categories = jsonStringList(item.optJSONArray("categories")),
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
                    versionChangelog = item.optNullableString("changelog")
                        ?: item.optNullableString("release_notes")
                        ?: item.optNullableString("whats_new"),
                    donationUrls = buildList {
                        item.optNullableString("donate_url")?.let(::add)
                        addAll(jsonStringList(item.optJSONArray("donation_urls")))
                    },
                    liberapay = item.optNullableString("liberapay"),
                    openCollective = item.optNullableString("opencollective") ?: item.optNullableString("open_collective"),
                    bitcoin = item.optNullableString("bitcoin"),
                    litecoin = item.optNullableString("litecoin"),
                    license = item.optNullableString("license_type") ?: item.optNullableString("license"),
                    antiFeatures = jsonStringList(item.optJSONArray("ant_features")).ifEmpty { jsonStringList(item.optJSONArray("anti_features")) },
                    closedSource = item.optBoolean("closed_source", false)
                ))
            }
        }
    }

    private fun resolveFdroidAssetUrl(source: AppSource, asset: String): String? {
        if (asset.isBlank()) return null
        if (asset.startsWith("http://") || asset.startsWith("https://")) return asset
        val base = source.indexUrl.substringBeforeLast('/')
        return "$base/${asset.trimStart('/')}"
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
                    if (name.isNotBlank() && indexUrl.isNotBlank()) add(AppSource(name, indexUrl, SourceType.FDROID_V1, custom = true))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCustomSources(sources: List<AppSource>) {
        val array = JSONArray()
        sources.forEach { array.put(JSONObject().put("name", it.name).put("indexUrl", it.indexUrl)) }
        sourcePreferences.edit().putString(CUSTOM_SOURCES_KEY, array.toString()).apply()
    }

    private fun normalizeFdroidUrl(rawUrl: String): String {
        val clean = rawUrl.trim()
        require(clean.isNotBlank()) { appContext.getString(R.string.repository_url_required) }
        return if (clean.endsWith("index-v1.json", true)) clean else clean.trimEnd('/') + "/index-v1.json"
    }

    private fun sourcePreferenceKey(source: AppSource): String =
        "source_enabled_${source.name}_${source.indexUrl}".hashCode().toString()

    private fun saveCache(apps: List<StoreApp>) {
        val array = JSONArray()
        apps.forEach { app -> array.put(JSONObject().apply {
            put("id", app.id); put("name", app.name); put("summary", app.summary); put("description", app.description)
            put("version", app.version); put("versionCode", app.versionCode); put("iconUrl", app.iconUrl)
            put("screenshotUrls", JSONArray(app.screenshotUrls)); put("categories", JSONArray(app.categories))
            put("apkUrl", app.apkUrl); put("sourceName", app.sourceName); put("authorName", app.authorName)
            put("authorEmail", app.authorEmail); put("authorWebsite", app.authorWebsite); put("websiteUrl", app.websiteUrl)
            put("sourceCodeUrl", app.sourceCodeUrl); put("issueTrackerUrl", app.issueTrackerUrl); put("translationUrl", app.translationUrl)
            put("changelogUrl", app.changelogUrl); put("donationUrls", JSONArray(app.donationUrls)); put("liberapay", app.liberapay)
            put("openCollective", app.openCollective); put("bitcoin", app.bitcoin); put("litecoin", app.litecoin)
            put("license", app.license); put("antiFeatures", JSONArray(app.antiFeatures)); put("closedSource", app.closedSource)
            put("versionChangelog", app.versionChangelog)
        }) }
        cachePreferences.edit().putString(CACHE_KEY_APPS, array.toString()).putLong(CACHE_KEY_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    private fun parseCachedApps(raw: String): List<StoreApp> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(StoreApp(
                    id, name, item.optString("summary"), item.optString("description"), item.optString("version"),
                    item.optLong("versionCode"), item.optNullableString("iconUrl"), jsonStringList(item.optJSONArray("screenshotUrls")),
                    jsonStringList(item.optJSONArray("categories")), item.optString("apkUrl"), item.optString("sourceName"),
                    item.optNullableString("authorName"), item.optNullableString("authorEmail"), item.optNullableString("authorWebsite"),
                    item.optNullableString("websiteUrl"), item.optNullableString("sourceCodeUrl"), item.optNullableString("issueTrackerUrl"),
                    item.optNullableString("translationUrl"), item.optNullableString("changelogUrl"), jsonStringList(item.optJSONArray("donationUrls")),
                    item.optNullableString("liberapay"), item.optNullableString("openCollective"), item.optNullableString("bitcoin"),
                    item.optNullableString("litecoin"), item.optNullableString("license"), jsonStringList(item.optJSONArray("antiFeatures")),
                    item.optBoolean("closedSource", false), item.optNullableString("versionChangelog")
                ))
            }
        }
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Luma-Store-Android")
        return try {
            val code = connection.responseCode
            check(code in 200..299) { appContext.getString(R.string.http_request_failed, code, url) }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    companion object {
        private const val CACHE_PREFERENCES = "app_cache"
        private const val SOURCE_PREFERENCES = "app_sources"
        private const val CACHE_KEY_APPS = "apps_validated_download_urls_v3"
        private const val CACHE_KEY_TIMESTAMP = "timestamp"
        private const val CUSTOM_SOURCES_KEY = "custom_sources"
    }
}

private fun jsonStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList { for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() && it != "null" }?.let(::add) }
}

private fun JSONObject.optNullableString(name: String): String? = optString(name).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optDownloadUrl(name: String): String? {
    val value = optNullableString(name)?.trim() ?: return null
    val url = runCatching { URL(value) }.getOrNull() ?: return null
    return value.takeIf { (url.protocol == "http" || url.protocol == "https") && url.host.isNotBlank() }
}
