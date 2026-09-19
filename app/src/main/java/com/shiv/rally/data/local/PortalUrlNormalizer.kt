package com.shiv.rally.data.local

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One source of truth for user-entered portal and addon URLs. */
object PortalUrlNormalizer {
    fun normalizePortal(rawValue: String): String {
        var value = rawValue.trim()
        if (value.isEmpty()) return ""
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            value = "http://$value"
        }

        value = repairRemoteColonTypo(value)
            .removeSuffix("/")
            .removeSuffix("/server/load.php")
            .removeSuffix("/load.php")
            .removeSuffix("/")

        return value.toHttpUrlOrNull()?.newBuilder()?.build()?.toString()?.removeSuffix("/") ?: ""
    }

    fun normalizeAddon(rawValue: String): String? {
        var value = rawValue.trim()
        if (value.isEmpty()) return null
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            value = "https://$value"
        }
        val parsed = value.toHttpUrlOrNull() ?: return null
        return if (parsed.encodedPath.endsWith("manifest.json")) {
            parsed.toString()
        } else {
            parsed.newBuilder().addPathSegment("manifest.json").build().toString()
        }
    }

    private fun repairRemoteColonTypo(value: String): String {
        val schemeEnd = value.indexOf("://")
        if (schemeEnd < 0) return value
        val authorityStart = schemeEnd + 3
        val pathStart = value.indexOf('/', authorityStart).let { if (it < 0) value.length else it }
        val authority = value.substring(authorityStart, pathStart)
        if (authority.startsWith("[") || authority.contains('@')) return value
        val colon = authority.lastIndexOf(':')
        if (colon <= 0) return value
        val suffix = authority.substring(colon + 1)
        if (suffix.isEmpty() || suffix.all(Char::isDigit)) return value
        val repairedAuthority = authority.substring(0, colon) + "." + suffix
        return value.substring(0, authorityStart) + repairedAuthority + value.substring(pathStart)
    }
}
