package com.shiv.rally.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.shiv.rally.BuildConfig
import com.shiv.rally.data.local.RallyDiagnostics
import com.shiv.rally.data.remote.network.ResilientDns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class RallyRelease(
    val tag: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val assetName: String,
    val assetUrl: String,
    val assetSize: Long,
    val assetDigest: String?
)

sealed interface RallyUpdateState {
    data object Idle : RallyUpdateState
    data object Checking : RallyUpdateState
    data class UpToDate(val version: String) : RallyUpdateState
    data class Available(val release: RallyRelease) : RallyUpdateState
    data class Downloading(val release: RallyRelease, val progress: Int) : RallyUpdateState
    data class Ready(val release: RallyRelease, val file: File) : RallyUpdateState
    data class PermissionRequired(val release: RallyRelease, val file: File) : RallyUpdateState
    data class Error(val message: String) : RallyUpdateState
}

@Singleton
class RallyUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    resilientDns: ResilientDns,
    private val diagnostics: RallyDiagnostics
) {
    private val client = OkHttpClient.Builder()
        .dns(resilientDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.MINUTES)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun check(): RallyUpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Rally-AndroidTV/${BuildConfig.VERSION_NAME}")
                .build()
            val body = client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "GitHub returned ${response.code}." }
                response.body?.string() ?: error("GitHub returned an empty response.")
            }
            val releases = JSONArray(body)
            val candidates = buildList {
                for (index in 0 until releases.length()) {
                    val release = releases.getJSONObject(index)
                    if (release.optBoolean("draft")) continue
                    val assets = release.optJSONArray("assets") ?: continue
                    for (assetIndex in 0 until assets.length()) {
                        val asset = assets.getJSONObject(assetIndex)
                        val name = asset.optString("name")
                        val url = asset.optString("browser_download_url")
                        val size = asset.optLong("size")
                        if (!name.endsWith(".apk", true) || size !in 1..MAX_APK_BYTES) continue
                        if (!url.startsWith(TRUSTED_RELEASE_PREFIX)) continue
                        add(
                            RallyRelease(
                                tag = release.optString("tag_name"),
                                title = release.optString("name").ifBlank { release.optString("tag_name") },
                                notes = release.optString("body").take(2_000),
                                pageUrl = release.optString("html_url"),
                                assetName = name,
                                assetUrl = url,
                                assetSize = size,
                                assetDigest = asset.optString("digest").takeIf { it.startsWith("sha256:") }
                            )
                        )
                    }
                }
            }
            val latest = candidates.maxWithOrNull { first, second -> compareVersions(first.tag, second.tag) }
            if (latest != null && compareVersions(latest.tag, BuildConfig.VERSION_NAME) > 0) {
                diagnostics.record("Updater", "Update ${latest.tag} is available")
                RallyUpdateState.Available(latest)
            } else {
                RallyUpdateState.UpToDate(BuildConfig.VERSION_NAME)
            }
        }.getOrElse { error ->
            diagnostics.record("Updater", "Update check failed", error.message)
            RallyUpdateState.Error(error.message ?: "Unable to check GitHub Releases.")
        }
    }

    suspend fun download(release: RallyRelease, onProgress: (Int) -> Unit): RallyUpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(release.assetUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "Rally-AndroidTV/${BuildConfig.VERSION_NAME}")
                .build()
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            directory.listFiles()?.forEach { if (it.name != release.assetName) it.delete() }
            val destination = File(directory, release.assetName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            val temporary = File(directory, "${destination.name}.download")
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed with ${response.code}." }
                val responseBody = response.body ?: error("The release asset was empty.")
                val expected = release.assetSize.takeIf { it > 0 } ?: responseBody.contentLength()
                temporary.outputStream().buffered().use { output ->
                    responseBody.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var lastProgress = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            check(total <= MAX_APK_BYTES) { "The downloaded file exceeded Rally's safety limit." }
                            output.write(buffer, 0, read)
                            if (expected > 0) {
                                val progress = ((total * 100L) / expected).toInt().coerceIn(0, 100)
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
            }
            release.assetDigest?.removePrefix("sha256:")?.let { expected ->
                check(fileSha256(temporary).equals(expected, true)) { "The GitHub asset checksum did not match." }
            }
            verifyRallyPackage(temporary)
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            diagnostics.record("Updater", "Verified update ${release.tag}", "${release.assetName} · ${release.assetSize} bytes")
            RallyUpdateState.Ready(release, destination)
        }.getOrElse { error ->
            diagnostics.record("Updater", "Update download failed", error.message)
            RallyUpdateState.Error(error.message ?: "The update could not be downloaded.")
        }
    }

    fun install(release: RallyRelease, file: File): RallyUpdateState {
        if (!file.exists()) return RallyUpdateState.Error("The downloaded update is no longer available.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return RallyUpdateState.PermissionRequired(release, file)
        }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        return RallyUpdateState.Ready(release, file)
    }

    @Suppress("DEPRECATION")
    private fun verifyRallyPackage(file: File) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Android could not read the downloaded package.")
        check(packageInfo.packageName == BuildConfig.APPLICATION_ID) { "The downloaded package is not Rally." }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        check(versionCode > BuildConfig.VERSION_CODE) { "The downloaded package is not newer than this build." }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            packageInfo.signatures.orEmpty()
        }
        check(signatures.any { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex().equals(RELEASE_CERT_SHA256, true)
        }) { "The update was not signed by Rally's release key." }
    }

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/shivpatell25/rally/releases?per_page=10"
        private const val TRUSTED_RELEASE_PREFIX = "https://github.com/shivpatell25/rally/releases/download/"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val MAX_APK_BYTES = 200L * 1024L * 1024L
        private const val RELEASE_CERT_SHA256 = "79ffff57b611ec7fbbc690007196df16dfd658432dd452458c56108bae55df73"
    }
}

internal fun compareVersions(first: String, second: String): Int {
    fun parts(value: String): List<Int> {
        val normalized = value.trim().removePrefix("v").lowercase()
        val core = normalized.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }.toMutableList()
        while (core.size < 3) core += 0
        val prerelease = normalized.substringAfter('-', "")
        val channelRank = when {
            prerelease.isBlank() -> 3
            prerelease.startsWith("rc") -> 2
            prerelease.startsWith("beta") -> 1
            else -> 0
        }
        val prereleaseNumber = Regex("\\d+").find(prerelease)?.value?.toIntOrNull() ?: 0
        return core.take(3) + channelRank + prereleaseNumber
    }
    val left = parts(first)
    val right = parts(second)
    for (index in left.indices) {
        val compared = left[index].compareTo(right[index])
        if (compared != 0) return compared
    }
    return 0
}
