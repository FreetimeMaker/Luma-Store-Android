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
    val iconUrls: List<String> = emptyList(),
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
    val antiFeatureReasons: Map<String, String> = emptyMap(),
    val closedSource: Boolean = false,
    val versionChangelog: String? = null,
    val expectedSha256: String? = null,
    val addedTimestamp: Long? = null,
    val lastUpdatedTimestamp: Long? = null,
    val downloadSize: Long? = null,
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val nativeCode: List<String> = emptyList(),
    val signerSha256: List<String> = emptyList(),
    val permissions: List<String> = emptyList()
)

enum class SourceType { FDROID_V1, LUMA_API }

data class SourceHealth(
    val successful: Boolean,
    val appCount: Int,
    val checkedAt: Long,
    val message: String? = null
)

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
    private val appSourcePreferences = appContext.getSharedPreferences(APP_SOURCE_PREFERENCES, Context.MODE_PRIVATE)

    @Volatile private var memoryApps: List<StoreApp>? = null

    private val defaultSources = listOf(
        AppSource(appContext.getString(R.string.source_freetime_fdroid), "https://fdroid.free-time.me/repo/index-v2.json", enabledByDefault = false),
        AppSource(appContext.getString(R.string.source_fdroid), "https://f-droid.org/repo/index-v2.json", enabledByDefault = false),
        AppSource(appContext.getString(R.string.source_izzyondroid), "https://apt.izzysoft.de/fdroid/repo/index-v2.json", enabledByDefault = false),
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

    fun preferredSourceName(packageName: String): String? =
        appSourcePreferences.getString(APP_SOURCE_KEY_PREFIX + packageName, null)

    fun isFavorite(packageName: String): Boolean = appSourcePreferences.getBoolean("favorite_" + packageName, false)
    fun setFavorite(packageName: String, favorite: Boolean) { appSourcePreferences.edit().putBoolean("favorite_" + packageName, favorite).apply() }
    fun ignoredVersion(packageName: String): Long = appSourcePreferences.getLong("ignored_" + packageName, Long.MIN_VALUE)
    fun ignoreVersion(app: StoreApp) { appSourcePreferences.edit().putLong("ignored_" + app.id, app.versionCode).apply() }
    fun clearIgnoredVersion(packageName: String) { appSourcePreferences.edit().remove("ignored_" + packageName).apply() }
    fun isUpdateIgnored(app: StoreApp): Boolean = ignoredVersion(app.id) == app.versionCode
    fun lockedSourceName(packageName: String): String? = appSourcePreferences.getString("locked_" + packageName, null)
    fun setSourceLock(packageName: String, sourceName: String?) {
        val editor = appSourcePreferences.edit()
        if (sourceName == null) editor.remove("locked_" + packageName) else editor.putString("locked_" + packageName, sourceName)
        editor.apply()
    }

    fun rememberPreferredSource(app: StoreApp) {
        appSourcePreferences.edit()
            .putString(APP_SOURCE_KEY_PREFIX + app.id, app.sourceName)
            .apply()
    }

    fun preferredVariant(packageName: String, variants: List<StoreApp>, installedVersionCode: Long? = null): StoreApp? {
        val lockedSource = lockedSourceName(packageName)
        variants.firstOrNull { it.sourceName == lockedSource }?.let { return it }
        val preferredSource = preferredSourceName(packageName)
        variants.firstOrNull { it.sourceName == preferredSource }?.let { return it }

        if (variants.size == 1) return variants.first()

        if (installedVersionCode != null) {
            variants.firstOrNull { it.versionCode == installedVersionCode }?.let {
                rememberPreferredSource(it)
                return it
            }
        }

        return variants.maxByOrNull { it.versionCode }
    }

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

    fun sourceHealth(source: AppSource): SourceHealth? {
        val raw = sourcePreferences.getString("health_" + sourcePreferenceKey(source), null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            SourceHealth(
                successful = obj.optBoolean("successful"),
                appCount = obj.optInt("appCount"),
                checkedAt = obj.optLong("checkedAt"),
                message = obj.optNullableString("message")
            )
        }.getOrNull()
    }

    private fun saveSourceHealth(source: AppSource, health: SourceHealth) {
        val raw = JSONObject()
            .put("successful", health.successful)
            .put("appCount", health.appCount)
            .put("checkedAt", health.checkedAt)
            .put("message", health.message)
            .toString()
        sourcePreferences.edit().putString("health_" + sourcePreferenceKey(source), raw).apply()
    }

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
            runCatching { loadSource(source) }
                .onSuccess { loaded ->
                    successfulSources++
                    variants += loaded
                    saveSourceHealth(source, SourceHealth(true, loaded.size, System.currentTimeMillis()))
                }
                .onFailure { error ->
                    saveSourceHealth(source, SourceHealth(false, 0, System.currentTimeMillis(), error.message))
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
        val raw = runCatching { httpGet(source.indexUrl) }.getOrElse { firstError ->
            if (source.indexUrl.endsWith("index-v2.json", true)) httpGet(source.indexUrl.substringBeforeLast('/') + "/index-v1.json")
            else throw firstError
        }
        val root = JSONObject(raw.trimStart('\uFEFF', ' ', '\n', '\r', '\t'))
        val packages = root.optJSONObject("packages") ?: return emptyList()
        val firstPackage = packages.keys().asSequence().firstOrNull()?.let(packages::optJSONObject)
        if (firstPackage?.optJSONObject("versions") != null) return parseFdroidV2(source, packages)
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
                iconUrl = resolveFdroidIconUrls(
                    source = source,
                    packageName = packageName,
                    locale = localizedLocale,
                    localizedIcon = firstText(localized?.opt("icon")),
                    legacyIcon = firstText(latest.opt("icon"), meta?.opt("icon"))
                ).firstOrNull(),
                iconUrls = resolveFdroidIconUrls(
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
                antiFeatures = parseAntiFeatures(meta?.opt("antiFeatures")),
                antiFeatureReasons = parseAntiFeatureReasons(meta?.opt("antiFeatures")),
                expectedSha256 = latest.optString("hash").takeIf {
                    it.matches(Regex("^[0-9a-fA-F]{64}$")) &&
                        latest.optString("hashType", "sha256").equals("sha256", true)
                },
                addedTimestamp = meta?.optLong("added", 0L)?.takeIf { it > 0 },
                lastUpdatedTimestamp = meta?.optLong("lastUpdated", 0L)?.takeIf { it > 0 }
            )
        }
        return results
    }

    private fun parseFdroidV2(source: AppSource, packages: JSONObject): List<StoreApp> {
        val base = source.indexUrl.substringBeforeLast('/')
        val results = mutableListOf<StoreApp>()
        val packageNames = packages.keys()
        while (packageNames.hasNext()) {
            val packageName = packageNames.next()
            val pkg = packages.optJSONObject(packageName) ?: continue
            val meta = pkg.optJSONObject("metadata") ?: JSONObject()
            val versions = pkg.optJSONObject("versions") ?: continue
            var best: JSONObject? = null
            var bestCode = Long.MIN_VALUE
            val versionKeys = versions.keys()
            while (versionKeys.hasNext()) {
                val version = versions.optJSONObject(versionKeys.next()) ?: continue
                val code = version.optJSONObject("manifest")?.optLong("versionCode", Long.MIN_VALUE) ?: Long.MIN_VALUE
                if (code > bestCode) { best = version; bestCode = code }
            }
            val latest = best ?: continue
            val manifest = latest.optJSONObject("manifest") ?: continue
            val file = latest.optJSONObject("file") ?: continue
            val fileName = file.optString("name").trim()
            if (fileName.isBlank()) continue
            val iconFile = fdroidV2FileName(meta.opt("icon"))
            val iconUrls = buildList {
                iconFile?.let { icon -> add(if (icon.startsWith("http")) icon else "$base/" + icon.trimStart('/')) }
            }
            results += StoreApp(
                id = packageName,
                name = localizedValue(meta.opt("name")) ?: packageName,
                summary = localizedValue(meta.opt("summary")).orEmpty(),
                description = localizedValue(meta.opt("description")).orEmpty(),
                version = manifest.optString("versionName").ifBlank { bestCode.toString() },
                versionCode = bestCode,
                iconUrl = iconUrls.firstOrNull(),
                iconUrls = iconUrls,
                screenshotUrls = emptyList(),
                categories = jsonStringList(meta.optJSONArray("categories")),
                apkUrl = "$base/" + fileName.trimStart('/'),
                sourceName = source.name,
                authorName = localizedValue(meta.opt("authorName")),
                authorEmail = stringValue(meta.opt("authorEmail")),
                authorWebsite = stringValue(meta.opt("authorWebSite")),
                websiteUrl = stringValue(meta.opt("webSite")),
                sourceCodeUrl = stringValue(meta.opt("sourceCode")),
                issueTrackerUrl = stringValue(meta.opt("issueTracker")),
                translationUrl = stringValue(meta.opt("translation")),
                changelogUrl = stringValue(meta.opt("changelog")),
                versionChangelog = localizedValue(latest.opt("whatsNew")),
                license = stringValue(meta.opt("license")),
                antiFeatures = parseAntiFeatures(latest.opt("antiFeatures")).ifEmpty { parseAntiFeatures(meta.opt("antiFeatures")) },
                antiFeatureReasons = parseAntiFeatureReasons(latest.opt("antiFeatures")) + parseAntiFeatureReasons(meta.opt("antiFeatures")),
                expectedSha256 = file.optString("sha256").takeIf { hash -> hash.matches(Regex("^[0-9a-fA-F]{64}$")) },
                addedTimestamp = meta.optLong("added", 0L).takeIf { timestamp -> timestamp > 0 },
                lastUpdatedTimestamp = meta.optLong("lastUpdated", 0L).takeIf { timestamp -> timestamp > 0 },
                downloadSize = file.optLong("size", 0L).takeIf { size -> size > 0 },
                minSdk = manifest.optInt("usesSdk", 0).takeIf { sdk -> sdk > 0 },
                targetSdk = manifest.optInt("targetSdkVersion", 0).takeIf { sdk -> sdk > 0 },
                nativeCode = jsonStringList(manifest.optJSONArray("nativecode")),
                signerSha256 = jsonStringList(manifest.optJSONObject("signer")?.optJSONArray("sha256")),
                permissions = jsonStringList(manifest.optJSONArray("usesPermission"))
            )
        }
        return results
    }

    private fun fdroidV2FileName(value: Any?): String? = when (value) {
        is JSONObject -> value.optString("name").takeIf { it.isNotBlank() }
            ?: value.keys().asSequence().mapNotNull { key ->
                value.optJSONObject(key)?.optString("name")?.takeIf { it.isNotBlank() }
            }.firstOrNull()
        is String -> value.takeIf { it.isNotBlank() }
        else -> null
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

    private fun resolveFdroidIconUrls(
        source: AppSource,
        packageName: String,
        locale: String?,
        localizedIcon: String?,
        legacyIcon: String?
    ): List<String> {
        val base = source.indexUrl.substringBeforeLast('/')
        val candidates = linkedSetOf<String>()

        fun add(value: String?) {
            val icon = value?.trim()?.takeIf { it.isNotBlank() } ?: return
            if (icon.startsWith("http://", true) || icon.startsWith("https://", true)) {
                candidates += icon
                return
            }
            val clean = icon.trimStart('/')
            if (clean.contains('/')) candidates += "$base/$clean"
        }

        add(localizedIcon)
        localizedIcon?.trim()?.trimStart('/')?.takeIf { it.isNotBlank() && !it.contains('/') }?.let { clean ->
            if (!locale.isNullOrBlank()) {
                candidates += "$base/$packageName/$locale/icon/$clean"
                candidates += "$base/$packageName/$locale/$clean"
            }
            candidates += "$base/icons-160/$clean"
            candidates += "$base/icons/$clean"
            candidates += "$base/$clean"
        }

        add(legacyIcon)
        legacyIcon?.trim()?.trimStart('/')?.takeIf { it.isNotBlank() && !it.contains('/') }?.let { clean ->
            candidates += "$base/icons-160/$clean"
            candidates += "$base/icons/$clean"
            candidates += "$base/$clean"
            if (!locale.isNullOrBlank()) candidates += "$base/$packageName/$locale/icon/$clean"
        }

        return candidates.toList()
    }

    private fun parseAntiFeatures(value: Any?): List<String> = when (value) {
        is JSONArray -> jsonStringList(value)
        is JSONObject -> value.keys().asSequence().toList()
        else -> emptyList()
    }

    private fun parseAntiFeatureReasons(value: Any?): Map<String, String> {
        if (value !is JSONObject) return emptyMap()
        return buildMap {
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val raw = value.opt(key)
                val reason = when (raw) {
                    is String -> raw.takeIf { it.isNotBlank() }
                    is JSONObject -> firstText(
                        raw.opt("reason"),
                        raw.opt("description"),
                        raw.opt("note"),
                        raw.opt("en-US"),
                        raw.opt("en")
                    )
                    is JSONArray -> (0 until raw.length()).asSequence()
                        .mapNotNull { index -> stringValue(raw.opt(index)) }
                        .firstOrNull()
                    else -> null
                }
                if (!reason.isNullOrBlank()) put(key, reason)
            }
        }
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
                    iconUrls = listOfNotNull(item.optNullableString("icon_url")),
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
        return when {
            clean.endsWith("index-v2.json", true) || clean.endsWith("index-v1.json", true) -> clean
            else -> clean.trimEnd('/') + "/index-v2.json"
        }
    }

    private fun sourcePreferenceKey(source: AppSource): String =
        "source_enabled_${source.name}_${source.indexUrl}".hashCode().toString()

    private fun saveCache(apps: List<StoreApp>) {
        val array = JSONArray()
        apps.forEach { app -> array.put(JSONObject().apply {
            put("id", app.id); put("name", app.name); put("summary", app.summary); put("description", app.description)
            put("version", app.version); put("versionCode", app.versionCode); put("iconUrl", app.iconUrl); put("iconUrls", JSONArray(app.iconUrls))
            put("screenshotUrls", JSONArray(app.screenshotUrls)); put("categories", JSONArray(app.categories))
            put("apkUrl", app.apkUrl); put("sourceName", app.sourceName); put("authorName", app.authorName)
            put("authorEmail", app.authorEmail); put("authorWebsite", app.authorWebsite); put("websiteUrl", app.websiteUrl)
            put("sourceCodeUrl", app.sourceCodeUrl); put("issueTrackerUrl", app.issueTrackerUrl); put("translationUrl", app.translationUrl)
            put("changelogUrl", app.changelogUrl); put("donationUrls", JSONArray(app.donationUrls)); put("liberapay", app.liberapay)
            put("openCollective", app.openCollective); put("bitcoin", app.bitcoin); put("litecoin", app.litecoin)
            put("license", app.license); put("antiFeatures", JSONArray(app.antiFeatures)); put("antiFeatureReasons", JSONObject(app.antiFeatureReasons)); put("closedSource", app.closedSource)
            put("versionChangelog", app.versionChangelog); put("expectedSha256", app.expectedSha256)
            app.addedTimestamp?.let { put("addedTimestamp", it) }; app.lastUpdatedTimestamp?.let { put("lastUpdatedTimestamp", it) }
            app.downloadSize?.let { put("downloadSize", it) }; app.minSdk?.let { put("minSdk", it) }; app.targetSdk?.let { put("targetSdk", it) }
            put("nativeCode", JSONArray(app.nativeCode)); put("signerSha256", JSONArray(app.signerSha256)); put("permissions", JSONArray(app.permissions))
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
                    item.optLong("versionCode"), item.optNullableString("iconUrl"),
                    jsonStringList(item.optJSONArray("iconUrls")).ifEmpty { listOfNotNull(item.optNullableString("iconUrl")) },
                    jsonStringList(item.optJSONArray("screenshotUrls")),
                    jsonStringList(item.optJSONArray("categories")), item.optString("apkUrl"), item.optString("sourceName"),
                    item.optNullableString("authorName"), item.optNullableString("authorEmail"), item.optNullableString("authorWebsite"),
                    item.optNullableString("websiteUrl"), item.optNullableString("sourceCodeUrl"), item.optNullableString("issueTrackerUrl"),
                    item.optNullableString("translationUrl"), item.optNullableString("changelogUrl"), jsonStringList(item.optJSONArray("donationUrls")),
                    item.optNullableString("liberapay"), item.optNullableString("openCollective"), item.optNullableString("bitcoin"),
                    item.optNullableString("litecoin"), item.optNullableString("license"), jsonStringList(item.optJSONArray("antiFeatures")),
                    jsonStringMap(item.optJSONObject("antiFeatureReasons")), item.optBoolean("closedSource", false), item.optNullableString("versionChangelog"),
                    item.optNullableString("expectedSha256"), item.optLong("addedTimestamp", 0L).takeIf { it > 0 },
                    item.optLong("lastUpdatedTimestamp", 0L).takeIf { it > 0 },
                    item.optLong("downloadSize", 0L).takeIf { it > 0 }, item.optInt("minSdk", 0).takeIf { it > 0 },
                    item.optInt("targetSdk", 0).takeIf { it > 0 }, jsonStringList(item.optJSONArray("nativeCode")),
                    jsonStringList(item.optJSONArray("signerSha256")), jsonStringList(item.optJSONArray("permissions"))
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
        private const val APP_SOURCE_PREFERENCES = "luma_store_source_preferences"
        private const val APP_SOURCE_KEY_PREFIX = "source_"
        private const val CACHE_KEY_APPS = "apps_validated_download_urls_v7"
        private const val CACHE_KEY_TIMESTAMP = "timestamp"
        private const val CUSTOM_SOURCES_KEY = "custom_sources"
    }
}

private fun jsonStringMap(obj: JSONObject?): Map<String, String> {
    if (obj == null) return emptyMap()
    return buildMap {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            obj.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { put(key, it) }
        }
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
