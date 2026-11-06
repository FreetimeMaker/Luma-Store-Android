package com.freetime.lumastore.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object LumaDownloadTracking {
    private const val ENDPOINT = "https://ndlaevedujqxhygbyxfh.supabase.co/functions/v1/download-app"

    // Contract: Luma-Store-Website app/[id]/page.tsx, recordSuccessfulDownload/downloadHref.
    // POST records completion without resolving/replacing the exact downloaded artifact.
    fun record(app: StoreApp, endpoint: String = ENDPOINT) {
        val appId = app.lumaAppId?.takeIf { it.isNotBlank() } ?: return
        val query = "app_id=${URLEncoder.encode(appId, "UTF-8")}&platform=Android&package_type=apk"
        val body = buildJsonObject {
            put("app_id", appId)
            put("platform", "Android")
            put("package_type", "apk")
        }.toString().toByteArray(Charsets.UTF_8)
        val connection = URL("$endpoint?$query").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Luma-Store-Android")
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Download tracking failed (HTTP $status)")
            connection.inputStream.close()
        } finally {
            connection.disconnect()
        }
    }
}
