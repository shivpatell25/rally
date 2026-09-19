package com.shiv.rally.data.remote.xtream

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Builds Xtream URLs without string concatenation or credential leakage in logs. */
object XtreamUrlBuilder {
    fun playerApi(server: String, username: String, password: String, action: String? = null): String? {
        val base = server.toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder().addPathSegment("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
        if (!action.isNullOrBlank()) builder.addQueryParameter("action", action)
        return builder.build().toString()
    }

    fun liveStream(server: String, username: String, password: String, streamId: String, extension: String = "m3u8"): String? {
        val base = server.toHttpUrlOrNull() ?: return null
        return base.newBuilder()
            .addPathSegment("live")
            .addPathSegment(username)
            .addPathSegment(password)
            .addPathSegment("${streamId.trim()}.$extension")
            .build()
            .toString()
    }
}
