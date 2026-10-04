package com.shiv.rally.presentation.player

import android.os.SystemClock
import com.google.gson.Gson
import com.shiv.rally.domain.model.SportEvent
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Real HTTP/HLS, with rotating addon/portal links and repeatable failure injection.
 * Lives entirely in the instrumentation APK; never contacts a user's providers. */
internal class MultiViewFaultServer(private val games: List<SportEvent>, private val segment: ByteArray) : AutoCloseable {
    private val listener = ServerSocket(0)
    private val workers = Executors.newCachedThreadPool()
    private val gson = Gson()
    private val started = SystemClock.elapsedRealtime()
    val base = "http://127.0.0.1:${listener.localPort}"
    val badHeaders = AtomicInteger()
    val denied = AtomicInteger()
    val links = ConcurrentHashMap<String, AtomicInteger>()
    val blockedTokens = ConcurrentHashMap.newKeySet<String>()
    val frozenGames = ConcurrentHashMap.newKeySet<String>()
    val offlineGames = ConcurrentHashMap.newKeySet<String>()
    private val frozenSequence = ConcurrentHashMap<String, Long>()
    @Volatile private var closed = false

    init {
        workers.execute {
            while (!closed) {
                val socket = try { listener.accept() } catch (_: Exception) { break }
                try { workers.execute { socket.use { serve(it) } } }
                catch (_: java.util.concurrent.RejectedExecutionException) { socket.close(); break }
            }
        }
    }
    private fun freshUrl(id: String, kind: String): String {
        val n = links.getOrPut(id) { AtomicInteger() }.incrementAndGet()
        return "$base/play/$kind/$id/$n/playlist.m3u8"
    }
    fun revoke(url: String) { blockedTokens.add(URI(url).path.substringBeforeLast('/')) }
    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 5_000
            val reader = socket.getInputStream().bufferedReader()
            val request = reader.readLine() ?: return
            val target = request.split(' ').getOrNull(1) ?: return
            val uri = URI(target)
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
            }
            fun respond(code: Int, type: String, body: ByteArray) {
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 $code Result\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-cache\r\n\r\n".toByteArray())
                out.write(body)
                out.flush()
            }
            fun json(value: Any) = respond(200, "application/json", gson.toJson(value).toByteArray())
            val path = uri.path
            when {
                path.endsWith("manifest.json") -> json(mapOf("id" to "rally.qa", "name" to "Sports Streams QA", "version" to "1.0.0",
                    "types" to listOf("sport"), "catalogs" to listOf(mapOf("type" to "sport", "id" to "live", "name" to "Live Sports"))))
                path.startsWith("/addon/catalog/") -> json(mapOf("metas" to games.map { mapOf("id" to it.id, "type" to "sport", "name" to it.name) }))
                path.startsWith("/addon/stream/") -> {
                    val id = path.substringAfterLast('/').removeSuffix(".json")
                    json(mapOf("streams" to listOf(mapOf("name" to "QA", "title" to "Sports Streams $id 720p",
                        "url" to freshUrl(id, "addon"), "behaviorHints" to mapOf("proxyHeaders" to mapOf("request" to
                            mapOf("Referer" to "$base/watch", "User-Agent" to "RallyMultiviewQA")))))))
                }
                path == "/server/load.php" -> {
                    val query = uri.rawQuery.orEmpty().split('&').associate {
                        it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
                    }
                    when (query["action"]) {
                        "handshake" -> json(mapOf("js" to mapOf("token" to "qa-session")))
                        "get_profile" -> json(mapOf("js" to mapOf("id" to 1, "status" to 0)))
                        "create_link" -> {
                            val id = query["cmd"].orEmpty().substringAfterLast('/')
                            json(mapOf("js" to mapOf("cmd" to "ffmpeg ${freshUrl(id, "portal")}")))
                        }
                        "get_all_channels" -> json(mapOf("js" to mapOf("data" to games.mapIndexed { i, event ->
                            mapOf("id" to event.id, "number" to "${i + 1}", "name" to event.name, "cmd" to "ffrt http://localhost/ch/${event.id}")
                        })))
                        else -> json(mapOf("js" to emptyList<Any>()))
                    }
                }
                path.startsWith("/play/") -> {
                    val parts = path.split('/')
                    val kind = parts[2]
                    val id = parts[3]
                    val signedPath = parts.take(5).joinToString("/")
                    val validHeaders = if (kind == "addon") headers["referer"] == "$base/watch" && headers["user-agent"] == "RallyMultiviewQA"
                        else headers["cookie"]?.contains("mac=00:1A:79:11:22:33") == true && headers["authorization"] == "Bearer qa-session"
                    if (!validHeaders) { badHeaders.incrementAndGet(); respond(403, "text/plain", "headers".toByteArray()); return }
                    if (signedPath in blockedTokens || id in offlineGames) {
                        denied.incrementAndGet(); respond(403, "text/plain", "expired".toByteArray()); return
                    }
                    if (path.endsWith(".m3u8")) {
                        val liveSequence = 6 + (SystemClock.elapsedRealtime() - started) / 2_000
                        val sequence = if (id in frozenGames) frozenSequence.getOrPut(id) { liveSequence } else {
                            frozenSequence.remove(id); liveSequence
                        }
                        val playlist = buildString {
                            append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:${sequence - 5}\n#EXT-X-DISCONTINUITY-SEQUENCE:${sequence - 5}\n")
                            for (n in sequence - 5..sequence) append("#EXT-X-DISCONTINUITY\n#EXTINF:2.0,\n$n.ts\n")
                        }
                        respond(200, "application/vnd.apple.mpegurl", playlist.toByteArray())
                    } else respond(200, "video/mp2t", segment)
                }
                else -> respond(404, "text/plain", ByteArray(0))
            }
        } catch (_: Exception) { /* Closing the fixture interrupts any outstanding socket. */ }
    }
    override fun close() { closed = true; listener.close(); workers.shutdownNow() }
}
