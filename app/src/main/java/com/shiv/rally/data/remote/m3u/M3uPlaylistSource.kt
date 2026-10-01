package com.shiv.rally.data.remote.m3u

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3uPlaylistSource @Inject constructor(@ApplicationContext private val context: Context) {
    // No Stalker credentials or request logging on user-supplied playlist hosts.
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()

    fun load(source: String, name: String): List<com.shiv.rally.domain.model.IptvChannel> {
        require(M3uPlaylistParser.isSupportedSource(source)) { "Enter a valid HTTP or HTTPS playlist URL, or choose a playlist file." }
        if (source.startsWith("content://")) {
            val input = context.contentResolver.openInputStream(Uri.parse(source))
                ?: error("The selected playlist file is unavailable. Choose it again.")
            return input.use { M3uPlaylistParser.parse(readBounded(it), source, name) }
        }
        return client.newCall(Request.Builder().url(source).build()).execute().use { response ->
            require(response.isSuccessful) { "The playlist server returned HTTP ${response.code}." }
            val body = response.body ?: error("The playlist server returned an empty response.")
            require(body.contentLength() <= MAX_BYTES) { "The playlist is larger than 16 MB." }
            val text = body.byteStream().use(::readBounded)
            M3uPlaylistParser.parse(text, response.request.url.toString(), name)
        }
    }

    private fun readBounded(input: InputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= MAX_BYTES) { "The playlist is larger than 16 MB." }
            bytes.write(buffer, 0, count)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    companion object { private const val MAX_BYTES = 16 * 1024 * 1024 }
}
