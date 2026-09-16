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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class SharedStoreRepository(
    private val client: HttpClient = HttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var sourceState: List<AppSource> = defaultStoreSources

    fun sources(): List<AppSource> = sourceState

    fun setSourceEnabled(name: String, enabled: Boolean) {
        sourceState = sourceState.map { source ->
            if (source.name == name) source.copy(enabled = enabled) else source
        }
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
