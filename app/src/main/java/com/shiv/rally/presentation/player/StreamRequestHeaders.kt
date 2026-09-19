package com.shiv.rally.presentation.player

private val allowedStreamHeaderNames = setOf(
    "accept",
    "accept-language",
    "authorization",
    "cookie",
    "origin",
    "referer",
    "user-agent"
)

internal fun sanitizedStreamHeaders(headers: Map<String, String>?): Map<String, String> =
    headers.orEmpty().filter { (name, value) ->
        name.lowercase() in allowedStreamHeaderNames &&
                name.length <= 64 && value.length <= 4096 &&
                !name.contains('\n') && !name.contains('\r') &&
                !value.contains('\n') && !value.contains('\r')
    }

internal fun normalizedBearerToken(token: String): String =
    if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"

