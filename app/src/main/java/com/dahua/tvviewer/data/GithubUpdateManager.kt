package com.dahua.tvviewer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.dahua.tvviewer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class GithubReleaseInfo(
    val tagName: String,
    val versionClean: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long
)

sealed class AppUpdateState {
    data object Idle : AppUpdateState()
    data object Checking : AppUpdateState()
    data class UpToDate(val currentVersion: String) : AppUpdateState()
    data class UpdateAvailable(
        val release: GithubReleaseInfo,
        val currentVersion: String
    ) : AppUpdateState()
    data class Downloading(
        val release: GithubReleaseInfo,
        val progressPercent: Int,
        val downloadedMb: Float,
        val totalMb: Float
    ) : AppUpdateState()
    data class ReadyToInstall(
        val release: GithubReleaseInfo,
        val apkFile: File
    ) : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

object GithubUpdateManager {

    const val GITHUB_OWNER = "iamblanquet"
    const val GITHUB_REPO = "VisionTVip"
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val ALL_RELEASES_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases"

    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    suspend fun checkForUpdates(): AppUpdateState = withContext(Dispatchers.IO) {
        try {
            val releaseJson = fetchLatestReleaseJson()
                ?: return@withContext AppUpdateState.UpToDate(currentVersionName)

            val tagName = releaseJson.optString("tag_name", "").trim()
            if (tagName.isEmpty()) {
                return@withContext AppUpdateState.UpToDate(currentVersionName)
            }

            val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
            val releaseTitle = releaseJson.optString("name", "Versión $tagName").ifBlank { "Versión $tagName" }
            val releaseNotes = releaseJson.optString("body", "Mejoras de rendimiento y estabilidad para Android TV.").ifBlank {
                "Mejoras de rendimiento y estabilidad para Android TV."
            }

            val assets = releaseJson.optJSONArray("assets") ?: JSONArray()
            var selectedAsset: JSONObject? = null

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    if (selectedAsset == null || name.startsWith("VisionTV", ignoreCase = true)) {
                        selectedAsset = asset
                    }
                }
            }

            if (selectedAsset == null) {
                return@withContext AppUpdateState.UpToDate(currentVersionName)
            }

            val downloadUrl = selectedAsset.optString("browser_download_url", "")
            val fileName = selectedAsset.optString("name", "VisionTV_update.apk")
            val sizeBytes = selectedAsset.optLong("size", 0L)

            if (downloadUrl.isBlank()) {
                return@withContext AppUpdateState.UpToDate(currentVersionName)
            }

            val releaseInfo = GithubReleaseInfo(
                tagName = tagName,
                versionClean = cleanRemoteVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                apkDownloadUrl = downloadUrl,
                apkFileName = fileName,
                apkSizeBytes = sizeBytes
            )

            if (isNewerVersion(cleanRemoteVersion, currentVersionName)) {
                AppUpdateState.UpdateAvailable(
                    release = releaseInfo,
                    currentVersion = currentVersionName
                )
            } else {
                AppUpdateState.UpToDate(currentVersionName)
            }
        } catch (e: Exception) {
            AppUpdateState.Error(
                "No se pudo verificar actualizaciones en GitHub: ${e.localizedMessage ?: "Error de red"}"
            )
        }
    }

    private fun fetchLatestReleaseJson(): JSONObject? {
        // 1. Try /releases/latest first
        runCatching {
            val conn = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "VisionTV-AndroidTV-Updater")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                return JSONObject(body)
            }
        }

        // 2. Fallback to /releases list (in case pre-release is used)
        runCatching {
            val conn = (URL(ALL_RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "VisionTV-AndroidTV-Updater")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    return arr.optJSONObject(0)
                }
            }
        }

        return null
    }

    suspend fun downloadUpdateApk(
        context: Context,
        release: GithubReleaseInfo,
        onProgress: (AppUpdateState.Downloading) -> Unit
    ): AppUpdateState = withContext(Dispatchers.IO) {
        try {
            val outputFile = File(context.cacheDir, "VisionTV_update.apk")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            var currentUrl = release.apkDownloadUrl
            var redirects = 0
            var connection: HttpURLConnection

            while (true) {
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "VisionTV-AndroidTV-Updater")
                }
                val status = connection.responseCode
                if (status in listOf(
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307,
                        308
                    )
                ) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank() || redirects >= 5) {
                        throw IllegalStateException("Demasiadas redirecciones al descargar el APK")
                    }
                    currentUrl = location
                    redirects++
                } else {
                    break
                }
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext AppUpdateState.Error(
                    "Error al descargar el APK desde GitHub (HTTP ${connection.responseCode})"
                )
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSizeBytes
            val totalMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 0f

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var downloadedBytes = 0L
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            val downloadedMb = downloadedBytes / (1024f * 1024f)
                            onProgress(
                                AppUpdateState.Downloading(
                                    release = release,
                                    progressPercent = percent,
                                    downloadedMb = downloadedMb,
                                    totalMb = totalMb
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }

            AppUpdateState.ReadyToInstall(release = release, apkFile = outputFile)
        } catch (e: Exception) {
            AppUpdateState.Error(
                "Error durante la descarga del APK: ${e.localizedMessage ?: "Fallo de conexión"}"
            )
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val permissionIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permissionIntent)
                return false
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
