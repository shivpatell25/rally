package com.shiv.rally.data.remote.network

import com.shiv.rally.domain.model.StreamCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class StreamPreflightResult(
    val passed: Boolean,
    val latencyMs: Long,
    val contentType: String? = null,
    val statusCode: Int? = null,
    val detail: String
)

/** Lightweight availability check that never downloads a video body. */
@Singleton
class StreamPreflightProbe @Inject constructor(resilientDns: ResilientDns) {
    private val client = OkHttpClient.Builder()
        .dns(resilientDns)
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun probe(candidate: StreamCandidate): StreamPreflightResult = probe(
        url = candidate.playbackTarget,
        headers = candidate.headers.orEmpty()
    )

    suspend fun probe(url: String, headers: Map<String, String> = emptyMap()): StreamPreflightResult = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext StreamPreflightResult(false, 0L, detail = "Provider link must be resolved first")
        }
        val started = System.nanoTime()
        val head = buildRequest(url, headers, head = true)
        val headResult = runCatching { execute(head, started) }.getOrNull()
        if (headResult?.passed == true || (headResult != null && headResult.statusCode !in listOf(403, 405, 501))) {
            return@withContext headResult
        }

        val range = buildRequest(url, headers, head = false)
        runCatching { execute(range, started) }.getOrElse { error ->
            StreamPreflightResult(false, elapsedMs(started), detail = error.javaClass.simpleName.ifBlank { "Connection failed" })
        }
    }

    private fun buildRequest(url: String, headers: Map<String, String>, head: Boolean): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", headers["User-Agent"] ?: "Rally/1.0 Android TV")
            .header("Accept", "application/vnd.apple.mpegurl, application/x-mpegURL, video/*, */*")
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (head) builder.head() else builder.header("Range", "bytes=0-1023").get()
        return builder.build()
    }

    private fun execute(request: Request, started: Long): StreamPreflightResult {
        client.newCall(request).execute().use { response ->
            val type = response.header("Content-Type")?.substringBefore(';')?.trim()
            val html = type?.contains("text/html", ignoreCase = true) == true
            val passed = response.isSuccessful && !html
            return StreamPreflightResult(
                passed = passed,
                latencyMs = elapsedMs(started),
                contentType = type,
                statusCode = response.code,
                detail = when {
                    html -> "Received a web page instead of video"
                    passed -> "Verified before playback"
                    else -> "Server returned ${response.code}"
                }
            )
        }
    }

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / 1_000_000L
}
