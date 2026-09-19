package com.shiv.rally.data.remote.stalker

import com.shiv.rally.data.local.PreferencesManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val preferencesManager: PreferencesManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        val portalHttpUrl = preferencesManager.portalUrl.toHttpUrlOrNull()
            ?: throw IOException("A valid Stalker portal URL is required")
        val newUrlBuilder = portalHttpUrl.newBuilder().query(null)

        for (segment in request.url.pathSegments.filter { it.isNotEmpty() }) {
            newUrlBuilder.addPathSegment(segment)
        }

        for (i in 0 until request.url.querySize) {
            val name = request.url.queryParameterName(i)
            request.url.queryParameterValue(i)?.let { newUrlBuilder.addQueryParameter(name, it) }
        }

        fun addQueryDefault(name: String, value: String) {
            if (newUrlBuilder.build().queryParameter(name) == null) {
                newUrlBuilder.addQueryParameter(name, value)
            }
        }

        addQueryDefault("JsHttpRequest", "1-xml")
        when (newUrlBuilder.build().queryParameter("action")) {
            "handshake" -> {
                addQueryDefault("token", "")
                addQueryDefault("prehash", "0")
            }
            "get_profile" -> {
                addQueryDefault("hd", "1")
                addQueryDefault(
                    "ver",
                    "ImageDescription: 0.2.18-r23-250; ImageDate: Wed Sep 18 12:40:14 EEST 2013; PORTAL version: 5.6.0; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c"
                )
                addQueryDefault("num_banks", "2")
                addQueryDefault("stb_type", "MAG250")
                addQueryDefault("client_type", "STB")
                addQueryDefault("image_version", "218")
                addQueryDefault("video_out", "hdmi")
                addQueryDefault("auth_second_step", "1")
                addQueryDefault("hw_version", "1.7-BD-00")
                addQueryDefault("not_valid_token", "0")
            }
            "get_ordered_list" -> {
                addQueryDefault("fav", "0")
                addQueryDefault("sortby", "number")
            }
        }

        val querySn = preferencesManager.serialNumber.trim()
        val queryDevId = preferencesManager.deviceId.trim()
        if (querySn.isNotEmpty() && newUrlBuilder.build().queryParameter("sn") == null) {
            newUrlBuilder.addQueryParameter("sn", querySn)
        }
        if (queryDevId.isNotEmpty() && newUrlBuilder.build().queryParameter("device_id") == null) {
            newUrlBuilder.addQueryParameter("device_id", queryDevId)
            newUrlBuilder.addQueryParameter("device_id2", queryDevId)
        }
        request = request.newBuilder().url(newUrlBuilder.build()).build()

        // Add standard MAG250 User-Agent, X-User-Agent, and Cookie for full Stalker compatibility
        val mac = preferencesManager.macAddress.trim()
        val sn = preferencesManager.serialNumber.trim()
        val devId = preferencesManager.deviceId.trim()
        val xUserAgent = buildString {
            append("Model: MAG250; Link: Ethernet")
            if (sn.isNotEmpty()) append("; SerialNumber: $sn")
            if (devId.isNotEmpty()) append("; DeviceId: $devId")
            if (devId.isNotEmpty()) append("; DeviceId2: $devId")
            if (mac.isNotEmpty()) append("; Mac: $mac")
        }
        val portalPath = portalHttpUrl.encodedPath.trimEnd('/')
        val referer = buildString {
            append(portalHttpUrl.scheme)
            append("://")
            append(portalHttpUrl.host)
            if (portalHttpUrl.port != if (portalHttpUrl.isHttps) 443 else 80) append(":${portalHttpUrl.port}")
            if (portalPath.isNotEmpty()) append(portalPath)
            if (!portalPath.endsWith("/c")) append("/c")
            append('/')
        }

        val requestBuilder = request.newBuilder()
            .header("X-User-Agent", xUserAgent)
            .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
            .header("Accept", "*/*")
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")

        if (mac.isNotEmpty()) {
            val existingCookie = request.header("Cookie")
            val macCookieStr = "mac=$mac; stb_lang=en; timezone=GMT"
            if (existingCookie.isNullOrEmpty()) {
                requestBuilder.header("Cookie", macCookieStr)
            } else if (!existingCookie.contains("mac=")) {
                requestBuilder.header("Cookie", "$existingCookie; $macCookieStr")
            }
        }

        val response = chain.proceed(requestBuilder.build())
        val preview = runCatching { response.peekBody(96).string().trimStart() }.getOrDefault("")
        val first = preview.firstOrNull()
        if (preview.isNotEmpty() && first != '{' && first != '[') {
            val action = request.url.queryParameter("action") ?: "unknown"
            val firstCodePoint = "U+${first!!.code.toString(16).uppercase().padStart(4, '0')}"
            Log.w(
                "StalkerResponse",
                "Unexpected response for $action: status=${response.code}, type=${response.body?.contentType()}, first=$firstCodePoint"
            )
        }
        return response
    }
}
