package com.shiv.rally.data.remote.network

import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uses Android's resolver first, then a short-timeout encrypted lookup when a TV's DNS proxy is
 * unavailable. Successful fallback answers are cached so image-heavy screens do not repeat work.
 */
@Singleton
class ResilientDns @Inject constructor() : Dns {
    private data class CachedAnswer(val addresses: List<InetAddress>, val expiresAtMs: Long)

    private val answers = ConcurrentHashMap<String, CachedAnswer>()
    private val bootstrapDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return if (hostname.equals(DOH_HOST, ignoreCase = true)) {
                DOH_ADDRESSES.map { address -> InetAddress.getByAddress(hostname, address) }
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }
    private val dohClient by lazy {
        OkHttpClient.Builder()
            .dns(bootstrapDns)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (systemFailure: UnknownHostException) {
            val now = System.currentTimeMillis()
            answers[hostname]?.takeIf { it.expiresAtMs > now }?.let { return it.addresses }

            val fallback = runCatching { resolveIpv4(hostname) }
                .onFailure { Log.w(TAG, "Encrypted DNS fallback failed for $hostname", it) }
                .getOrDefault(emptyList())
            if (fallback.isEmpty()) throw systemFailure
            return fallback
        }
    }

    private fun resolveIpv4(hostname: String): List<InetAddress> {
        val url = "https://$DOH_HOST/resolve".toHttpUrl().newBuilder()
            .addQueryParameter("name", hostname)
            .addQueryParameter("type", "A")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .build()

        dohClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UnknownHostException("DoH returned ${response.code}")
            val body = response.body?.string() ?: throw UnknownHostException("Empty DoH response")
            val json = JSONObject(body)
            if (json.optInt("Status", -1) != 0) throw UnknownHostException("DoH lookup failed")
            val records = json.optJSONArray("Answer") ?: throw UnknownHostException("No DNS answers")
            val resolved = buildList {
                for (index in 0 until records.length()) {
                    val record = records.optJSONObject(index) ?: continue
                    if (record.optInt("type") != IPV4_RECORD) continue
                    parseIpv4(hostname, record.optString("data"))?.let(::add)
                }
            }.distinctBy { it.hostAddress }
            if (resolved.isEmpty()) throw UnknownHostException("No IPv4 address for $hostname")

            val ttlSeconds = (0 until records.length())
                .mapNotNull { records.optJSONObject(it)?.takeIf { item -> item.optInt("type") == IPV4_RECORD }?.optLong("TTL") }
                .minOrNull()
                ?.coerceIn(MIN_TTL_SECONDS, MAX_TTL_SECONDS)
                ?: MIN_TTL_SECONDS
            answers[hostname] = CachedAnswer(resolved, System.currentTimeMillis() + ttlSeconds * 1_000L)
            return resolved
        }
    }

    private fun parseIpv4(hostname: String, raw: String): InetAddress? {
        val parts = raw.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        parts.forEachIndexed { index, part ->
            val value = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            bytes[index] = value.toByte()
        }
        return InetAddress.getByAddress(hostname, bytes)
    }

    private companion object {
        const val TAG = "ResilientDns"
        const val DOH_HOST = "dns.google"
        const val IPV4_RECORD = 1
        const val MIN_TTL_SECONDS = 60L
        const val MAX_TTL_SECONDS = 3_600L
        val DOH_ADDRESSES = listOf(
            byteArrayOf(8, 8, 8, 8),
            byteArrayOf(8, 8, 4, 4)
        )
    }
}
