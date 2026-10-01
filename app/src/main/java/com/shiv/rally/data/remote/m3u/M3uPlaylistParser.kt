package com.shiv.rally.data.remote.m3u

import com.shiv.rally.domain.model.IptvChannel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder
import java.security.MessageDigest

/** Extended M3U catalogs and HLS manifests share an extension, but have different semantics. */
object M3uPlaylistParser {
    fun isSupportedSource(value: String): Boolean =
        value.trim().toHttpUrlOrNull() != null || runCatching {
            val uri = java.net.URI(value.trim())
            uri.scheme == "content" && !uri.authority.isNullOrBlank()
        }.getOrDefault(false)

    fun parse(text: String, source: String, name: String = ""): List<IptvChannel> {
        val lines = text.removePrefix("\uFEFF").lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val bareUrls = lines.filterNot { it.startsWith('#') }
        val basicPlaylist = bareUrls.isNotEmpty() && bareUrls.all { it.substringBefore('|').toHttpUrlOrNull() != null }
        require(lines.firstOrNull()?.startsWith("#EXTM3U", true) == true || lines.any { it.startsWith("#EXTINF:", true) } || basicPlaylist) {
            "This response is not an M3U/M3U8 playlist. Check the URL or choose another file."
        }
        // Pass the manifest itself to ExoPlayer, preserving variants, segments, audio and subtitles.
        if (lines.any { it.startsWith("#EXT-X-", true) }) {
            require(source.toHttpUrlOrNull() != null) {
                "For a single HLS stream, enter its HTTP or HTTPS URL. Local files must contain a channel playlist."
            }
            return listOf(channel(source, name.ifBlank { "Live Stream" }, "Live TV", null, "1", emptyMap())
                .copy(streamMimeType = "application/x-mpegURL"))
        }
        val channels = LinkedHashMap<String, IptvChannel>()
        var attributes = emptyMap<String, String>()
        var title = ""
        var group = ""
        var headers = mutableMapOf<String, String>()
        lines.forEach { line ->
            when {
                line.startsWith("#EXTINF:", true) -> {
                    val comma = titleSeparator(line)
                    attributes = attributes(line.substring(0, if (comma < 0) line.length else comma))
                    title = if (comma < 0) "" else line.substring(comma + 1).trim()
                    group = attributes["group-title"].orEmpty()
                    headers = mutableMapOf()
                    attributes["user-agent"]?.let { putHeader(headers, "User-Agent", it) }
                    attributes["referrer"]?.let { putHeader(headers, "Referer", it) }
                }
                line.startsWith("#EXTGRP:", true) -> group = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:", true) -> {
                    val option = line.substringAfter(':').substringBefore('=').lowercase()
                    val value = line.substringAfter('=', "").trim()
                    when (option) {
                        "http-user-agent" -> putHeader(headers, "User-Agent", value)
                        "http-referrer", "http-referer" -> putHeader(headers, "Referer", value)
                        "http-origin" -> putHeader(headers, "Origin", value)
                    }
                }
                line.startsWith('#') -> Unit
                else -> {
                    val url = resolveUrl(source, line.substringBefore('|'))
                    if (url != null) {
                        line.substringAfter('|', "").split('&').forEach { part ->
                            val key = part.substringBefore('=').lowercase()
                            val value = runCatching { URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrDefault("")
                            when (key) {
                                "user-agent" -> putHeader(headers, "User-Agent", value)
                                "referer", "referrer" -> putHeader(headers, "Referer", value)
                                "origin" -> putHeader(headers, "Origin", value)
                            }
                        }
                        val channel = channel(url, title.ifBlank { attributes["tvg-name"].orEmpty() }.ifBlank { "Channel ${channels.size + 1}" },
                            group.ifBlank { "Live TV" }, resolveUrl(source, attributes["tvg-logo"].orEmpty()),
                            attributes["tvg-chno"]?.takeIf { it.isNotBlank() } ?: "${channels.size + 1}", headers.toMap())
                        channels.putIfAbsent(channel.id, channel)
                        require(channels.size <= 20_000) { "This playlist exceeds the 20,000 channel limit." }
                    }
                    attributes = emptyMap(); title = ""; group = ""; headers = mutableMapOf()
                }
            }
        }
        require(channels.isNotEmpty()) { "No playable channels found. Choose an M3U/M3U8 playlist containing HTTP or HTTPS stream URLs." }
        return channels.values.toList()
    }

    private fun channel(url: String, title: String, group: String, logo: String?, number: String, headers: Map<String, String>): IptvChannel {
        val identity = url + headers.toSortedMap().entries.joinToString { "${it.key}=${it.value}" }
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        return IptvChannel("m3u:$hash", number, title, group, logoUrl = logo, streamUrl = url, streamHeaders = headers)
    }

    private fun resolveUrl(source: String, value: String): String? {
        if (value.isBlank()) return null
        return value.trim().toHttpUrlOrNull()?.toString() ?: source.toHttpUrlOrNull()?.resolve(value.trim())?.toString()
    }

    private fun attributes(value: String): Map<String, String> =
        Regex("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s]+))").findAll(value).associate {
            it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
        }

    private fun titleSeparator(value: String): Int {
        var quote: Char? = null
        value.forEachIndexed { index, char ->
            if (char == quote) quote = null
            else if (quote == null && (char == '\'' || char == '"')) quote = char
            else if (quote == null && char == ',') return index
        }
        return -1
    }

    private fun putHeader(headers: MutableMap<String, String>, key: String, value: String) {
        if (value.isNotBlank() && value.none { it == '\r' || it == '\n' || it.code < 32 }) headers[key] = value
    }
}
