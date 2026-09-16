package com.freetime.lumastore.shared

data class StoreApp(
    val id: String,
    val name: String,
    val summary: String = "",
    val description: String = "",
    val version: String = "",
    val versionCode: Long = 0,
    val iconUrl: String? = null,
    val screenshotUrls: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val downloadUrl: String = "",
    val sourceName: String = "Luma Store",
    val authorName: String? = null,
    val websiteUrl: String? = null,
    val sourceCodeUrl: String? = null,
    val issueTrackerUrl: String? = null,
    val license: String? = null,
    val antiFeatures: List<String> = emptyList(),
    val closedSource: Boolean = false,
)

enum class SourceType {
    LUMA_API,
    FDROID_V1,
}

data class AppSource(
    val name: String,
    val url: String,
    val type: SourceType,
    val enabled: Boolean = true,
)

val defaultStoreSources: List<AppSource>
    get() = listOf(
        AppSource(
            name = "Luma Store",
            url = LumaStoreCore.catalogUrl(),
            type = SourceType.LUMA_API,
            enabled = true,
        ),
        AppSource(
            name = "F-Droid",
            url = "https://f-droid.org/repo/index-v1.json",
            type = SourceType.FDROID_V1,
            enabled = false,
        ),
        AppSource(
            name = "IzzyOnDroid",
            url = "https://apt.izzysoft.de/fdroid/repo/index-v1.json",
            type = SourceType.FDROID_V1,
            enabled = false,
        ),
        AppSource(
            name = "Freetime F-Droid",
            url = "https://fdroid.free-time.me/repo/index-v1.json",
            type = SourceType.FDROID_V1,
            enabled = false,
        ),
    )
