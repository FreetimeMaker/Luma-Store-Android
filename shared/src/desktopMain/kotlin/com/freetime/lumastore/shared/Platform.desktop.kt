package com.freetime.lumastore.shared

private val osName: String = System.getProperty("os.name").lowercase()

actual val currentPlatform: LumaPlatform = when {
    osName.contains("win") -> LumaPlatform.WINDOWS
    osName.contains("linux") -> LumaPlatform.LINUX
    else -> error("Unsupported desktop platform: ${System.getProperty("os.name")}")
}
