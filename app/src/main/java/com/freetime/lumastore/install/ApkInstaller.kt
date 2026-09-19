package com.freetime.lumastore.install

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.content.FileProvider
import com.freetime.lumastore.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object ApkInstaller {
    fun downloadAndInstall(
        context: Context,
        packageName: String,
        apkUrl: String,
        expectedSha256: String? = null,
        onProgress: (Int) -> Unit,
        onReady: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        Thread {
            runCatching {
                val downloadUrl = runCatching { URL(apkUrl.trim()) }.getOrNull()
                require(downloadUrl != null &&
                    (downloadUrl.protocol == "https" || downloadUrl.protocol == "http") &&
                    downloadUrl.host.isNotBlank()) {
                    context.getString(R.string.invalid_apk_download_url)
                }
                val dir = File(context.cacheDir, "apks").apply { mkdirs() }
                val target = File(dir, "${packageName.replace('.', '_')}.apk")
                val connection = downloadUrl.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Luma-Store")
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        var downloaded = 0L
                        var lastProgress = -1
                        while (input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val progress = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }

                expectedSha256?.takeIf { it.isNotBlank() }?.let { expected ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    target.inputStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } > 0) digest.update(buffer, 0, read)
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    require(actual.equals(expected, true)) { "APK SHA-256 verification failed." }
                }

                verifyUpdateSignature(context, packageName, target)

                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    target
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                onReady()
                context.startActivity(intent)
            }.onFailure(onError)
        }.start()
    }

    private fun verifyUpdateSignature(context: Context, packageName: String, apk: File) {
        val pm = context.packageManager
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            else @Suppress("DEPRECATION") pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }.getOrNull() ?: return

        val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: error("Downloaded APK could not be inspected.")

        require(archive.packageName == packageName) { "Downloaded APK package name does not match." }

        fun fingerprints(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signing = info.signingInfo ?: return emptySet()
                if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            return signatures.orEmpty().map {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
            }.toSet()
        }

        val installedFingerprints = fingerprints(installed)
        val archiveFingerprints = fingerprints(archive)
        require(installedFingerprints.isNotEmpty() && archiveFingerprints.isNotEmpty() &&
            installedFingerprints.intersect(archiveFingerprints).isNotEmpty()) {
            "APK signature does not match the installed app."
        }
    }
}
