package com.freetime.lumastore.shared

enum class LumaPlatform(val apiValue: String) {
    ANDROID("android"),
    WINDOWS("windows"),
    LINUX("linux")
}

expect val currentPlatform: LumaPlatform

object LumaStoreCore {
    const val appName: String = "Luma Store"
    const val apiBaseUrl: String = "https://api.free-time.me/v2/lumastore"

    fun catalogUrl(platform: LumaPlatform = currentPlatform): String =
        "$apiBaseUrl/apps?platform=${platform.apiValue}"
}
