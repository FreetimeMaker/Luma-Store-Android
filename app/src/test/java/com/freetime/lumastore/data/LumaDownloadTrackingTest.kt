package com.freetime.lumastore.data

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class LumaDownloadTrackingTest {
    private fun app(lumaAppId: String? = null) = StoreApp(
        id = "com.freetime.lumastore", name = "Luma Store", summary = "", description = "",
        version = "4.4.0", versionCode = 30, iconUrl = null, screenshotUrls = emptyList(),
        categories = emptyList(), apkUrl = "https://example.test/releases/4.4.0.apk",
        sourceName = "Luma Store", lumaAppId = lumaAppId
    )

    @Test fun selfUpdatePostsCompletionWithoutDownloadingOrRedirecting() {
        val method = AtomicReference<String>()
        val query = AtomicReference<String>()
        val body = AtomicReference<String>()
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/download-app") { exchange ->
            calls.incrementAndGet()
            method.set(exchange.requestMethod)
            query.set(exchange.requestURI.rawQuery)
            body.set(exchange.requestBody.bufferedReader().use { it.readText() })
            assertEquals("application/json", exchange.requestHeaders.getFirst("Content-Type"))
            val response = "{}".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val selected = app("12345678-abcd-4abc-8abc-123456789abc")
            LumaDownloadTracking.record(selected, "http://127.0.0.1:${server.address.port}/download-app")
            assertEquals(1, calls.get())
            assertEquals("POST", method.get())
            assertEquals("app_id=12345678-abcd-4abc-8abc-123456789abc&platform=Android&package_type=apk", query.get())
            val json = Json.parseToJsonElement(body.get()).jsonObject
            assertEquals(setOf("app_id", "platform", "package_type"), json.keys)
            assertEquals(selected.lumaAppId, json.getValue("app_id").jsonPrimitive.content)
            assertEquals("Android", json.getValue("platform").jsonPrimitive.content)
            assertEquals("apk", json.getValue("package_type").jsonPrimitive.content)
            assertEquals("https://example.test/releases/4.4.0.apk", selected.apkUrl)
        } finally {
            server.stop(0)
        }
    }

    @Test fun nonLumaVariantsAndUnidentifiedLegacyEntriesDoNotSendMetrics() {
        // An invalid endpoint proves no network operation occurs, even for the same package.
        LumaDownloadTracking.record(app().copy(sourceName = "F-Droid"), "not a URL")
        LumaDownloadTracking.record(app(), "not a URL")
        LumaDownloadTracking.record(app(" "), "not a URL")
    }

    @Test fun selectingAnotherSourceDoesNotKeepLumaIdentity() {
        val luma = app("store-id")
        val fdroid = app().copy(sourceName = "F-Droid")
        assertNotNull(luma.lumaAppId)
        assertNull(fdroid.lumaAppId)
        assertEquals(luma.id, fdroid.id)
        assertEquals("store-id", luma.copy(version = "4.4.1").lumaAppId)
    }

    @Test fun serverErrorsAndRedirectsAreNotReportedAsSuccessfulTracking() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/failure") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.createContext("/redirect") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            exchange.responseHeaders.add("Location", "/failure")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.start()
        try {
            for ((path, status) in listOf("failure" to 503, "redirect" to 302)) {
                val failure = assertThrows(IOException::class.java) {
                    LumaDownloadTracking.record(app("store-id"), "http://127.0.0.1:${server.address.port}/$path")
                }
                assertTrue(failure.message.orEmpty().contains("HTTP $status"))
            }
        } finally {
            server.stop(0)
        }
    }
}
