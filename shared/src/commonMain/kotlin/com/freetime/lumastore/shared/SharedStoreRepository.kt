package com.freetime.lumastore.shared

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

class SharedStoreRepository(
    private val sourceStateStore: SourceStateStore = InMemorySourceStateStore(),
    private val client: HttpClient = HttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var sourceState: List<AppSource> = restoreSources(sourceStateStore.load())

    fun sources(): List<AppSource> = sourceState

    fun setSourceEnabled(name: String, enabled: Boolean) {
        sourceState = sourceState.map { source ->
            if (source.name == name) source.copy(enabled = enabled) else source
        }
        persistSources()
    }

    fun addFdroidSource(name: String, repositoryUrl: String): Result<AppSource> = runCatching {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Source name is required." }
        require(sourceState.none { it.name.equals(cleanName, ignoreCase = true) }) {
            "A source with this name already exists."
        }

        val normalizedUrl = normalizeFdroidUrl(repositoryUrl)
        require(sourceState.none { it.url.equals(normalizedUrl, ignoreCase = true) }) {
            "This repository is already added."
        }

        val source = AppSource(
            name = cleanName,
            url = normalizedUrl,
            type = SourceType.FDROID_V1,
            enabled = true,
            custom = true,
        )
        sourceState = sourceState + source
        persistSources()
        source
    }

    fun removeSource(name: String): Boolean {
        val source = sourceState.firstOrNull { it.name == name } ?: return false
        if (!source.custom) return false
        sourceState = sourceState.filterNot { it.name == name }
        persistSources()
        return true
    }

    suspend fun loadApps(): List<StoreApp> {
        val enabledSources = sourceState.filter { it.enabled }
        if (enabledSources.isEmpty()) return emptyList()

        val collected = buildList {
            for (source in enabledSources) {
                runCatching { loadSource(source) }
                    .onSuccess { addAll(it) }
            }
        }

        return collected
            .distinctBy { "${it.id}\u0000${it.sourceName}" }
            .sortedBy { it.name.lowercase() }
    }

    private fun restoreSources(raw: String?): List<AppSource> {
        if (raw.isNullOrBlank()) return defaultStoreSources

        val saved = runCatching {
            (json.parseToJsonElement(raw) as? JsonArray)
                ?.mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val name = item.string("name") ?: return@mapNotNull null
                    val url = item.string("url", "indexUrl") ?: return@mapNotNull null
                    val type = item.string("type")
                        ?.let { runCatching { SourceType.valueOf(it) }.getOrNull() }
                        ?: SourceType.FDROID_V1
                    AppSource(
                        name = name,
                        url = url,
                        type = type,
                        enabled = item.boolean("enabled") ?: true,
                        custom = item.boolean("custom") ?: false,
                    )
                }
                .orEmpty()
        }.getOrDefault(emptyList())

        if (saved.isEmpty()) return defaultStoreSources

        val restoredDefaults = defaultStoreSources.map { defaultSource ->
            val previous = saved.firstOrNull {
                !it.custom && it.name.equals(defaultSource.name, ignoreCase = true)
            }
            if (previous == null) defaultSource else defaultSource.copy(enabled = previous.enabled)
        }
        val customSources = saved
            .filter { it.custom }
            .filter { custom ->
                restoredDefaults.none { default ->
                    default.name.equals(custom.name, ignoreCase = true) ||
                        default.url.equals(custom.url, ignoreCase = true)
                }
            }
            .distinctBy { it.url.lowercase() }

        return restoredDefaults + customSources
    }

    private fun persistSources() {
        val value = JsonArray(
            sourceState.map { source ->
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(source.name),
                        "url" to JsonPrimitive(source.url),
                        "type" to JsonPrimitive(source.type.name),
                        "enabled" to JsonPrimitive(source.enabled),
                        "custom" to JsonPrimitive(source.custom),
                    )
                )
            }
        ).toString()
        sourceStateStore.save(value)
    }

    private fun normalizeFdroidUrl(rawUrl: String): String {
        val clean = rawUrl.trim()
        require(clean.isNotBlank()) { "Repository URL is required." }
        require(clean.startsWith("https://") || clean.startsWith("http://")) {
            "Repository URL must start with https:// or http://."
        }
        return when {
            clean.endsWith("/index-v1.json", ignoreCase = true) -> clean
            clean.endsWith("index-v1.json", ignoreCase = true) -> clean
            else -> clean.trimEnd('/') + "/index-v1.json"
        }
    }

    private suspend fun loadSource(source: AppSource): List<StoreApp> {
        val body = client.get(source.url).bodyAsText()
        return when (source.type) {
            SourceType.LUMA_API -> parseLumaApps(body, source)
            SourceType.FDROID_V1 -> parseFdroidApps(body, source)
        }
    }

    private fun parseLumaApps(raw: String, source: AppSource): List<StoreApp> {
        val root = json.parseToJsonElement(raw)
        val apps = when (root) {
            is JsonArray -> root
            is JsonObject -> root.array("apps", "data", "results", "items") ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

        return apps.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("package_name", "packageName", "package", "id", "app_id") ?: return@mapNotNull null
            val name = item.string("name", "app_name", "title") ?: id

            StoreApp(
                id = id,
                name = name,
                summary = item.string("summary", "short_description", "subtitle").orEmpty(),
                description = item.string("description", "full_description").orEmpty(),
                version = item.string("version", "version_name", "versionName").orEmpty(),
                versionCode = item.long("version_code", "versionCode") ?: 0,
                iconUrl = item.string("icon_url", "iconUrl", "icon"),
                screenshotUrls = item.stringList("screenshots", "screenshot_urls", "screenshotUrls"),
                categories = item.stringList("categories", "category"),
                downloadUrl = item.string("download_url", "downloadUrl", "apk_url", "apkUrl", "url").orEmpty(),
                sourceName = source.name,
                authorName = item.string("author_name", "authorName", "author", "developer"),
                websiteUrl = item.string("website_url", "websiteUrl", "website"),
                sourceCodeUrl = item.string("repo_url", "source_code_url", "sourceCodeUrl", "source_code"),
                issueTrackerUrl = item.string("issue_tracker_url", "issueTrackerUrl", "issues_url"),
                license = item.string("license"),
                antiFeatures = item.stringList("anti_features", "antiFeatures"),
                closedSource = item.boolean("closed_source", "closedSource") ?: false,
            )
        }
    }

    private fun parseFdroidApps(raw: String, source: AppSource): List<StoreApp> {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
        val packages = root["packages"] as? JsonObject ?: return emptyList()
        val metadata = root["apps"] as? JsonObject ?: JsonObject(emptyMap())
        val repoBase = source.url.substringBeforeLast("/index-v1.json")

        return packages.mapNotNull { (packageName, versionsElement) ->
            val versions = versionsElement as? JsonArray ?: return@mapNotNull null
            val latest = versions
                .mapNotNull { it as? JsonObject }
                .maxByOrNull { it.long("versionCode", "version_code") ?: 0 }
                ?: return@mapNotNull null
            val meta = metadata[packageName] as? JsonObject
            val apkName = latest.string("apkName", "apk_name").orEmpty()
            val icon = latest.string("icon") ?: meta?.string("icon")

            StoreApp(
                id = packageName,
                name = meta?.string("name") ?: packageName,
                summary = meta?.string("summary").orEmpty(),
                description = meta?.string("description").orEmpty(),
                version = latest.string("versionName", "version_name")
                    ?: (latest.long("versionCode", "version_code")?.toString().orEmpty()),
                versionCode = latest.long("versionCode", "version_code") ?: 0,
                iconUrl = icon?.let { assetUrl(repoBase, it) },
                screenshotUrls = meta?.stringList("screenshots")?.map { assetUrl(repoBase, it) }.orEmpty(),
                categories = meta?.stringList("categories").orEmpty(),
                downloadUrl = if (apkName.isBlank()) "" else assetUrl(repoBase, apkName),
                sourceName = source.name,
                authorName = meta?.string("authorName", "author_name"),
                websiteUrl = meta?.string("webSite", "website"),
                sourceCodeUrl = meta?.string("sourceCode", "source_code"),
                issueTrackerUrl = meta?.string("issueTracker", "issue_tracker"),
                license = meta?.string("license"),
                antiFeatures = meta?.stringList("antiFeatures", "anti_features").orEmpty(),
            )
        }
    }

    private fun assetUrl(base: String, value: String): String = when {
        value.startsWith("https://") || value.startsWith("http://") -> value
        value.startsWith("/") -> base + value
        else -> "$base/$value"
    }

    private fun JsonObject.array(vararg keys: String): JsonArray? =
        keys.firstNotNullOfOrNull { key -> this[key] as? JsonArray }

    private fun JsonObject.string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> textValue(this[key]) }

    private fun JsonObject.long(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        when (val value = this[key]) {
            is JsonPrimitive -> value.longOrNull ?: value.contentOrNull?.toLongOrNull()
            else -> null
        }
    }

    private fun JsonObject.boolean(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
        when (val value = this[key]) {
            is JsonPrimitive -> value.booleanOrNull ?: value.contentOrNull?.toBooleanStrictOrNull()
            else -> null
        }
    }

    private fun JsonObject.stringList(vararg keys: String): List<String> {
        val value = keys.firstNotNullOfOrNull { key -> this[key] } ?: return emptyList()
        return when (value) {
            is JsonArray -> value.mapNotNull(::textValue)
            is JsonObject -> value.values.mapNotNull(::textValue)
            else -> textValue(value)?.let(::listOf).orEmpty()
        }
    }

    private fun textValue(value: JsonElement?): String? = when (value) {
        null, JsonNull -> null
        is JsonPrimitive -> value.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        is JsonObject -> {
            val preferred = listOf("en-US", "en_US", "en", "default")
            preferred.firstNotNullOfOrNull { locale -> textValue(value[locale]) }
                ?: value.values.firstNotNullOfOrNull(::textValue)
        }
        is JsonArray -> value.firstNotNullOfOrNull(::textValue)
    }
}
