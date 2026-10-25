package com.moorty.voice

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** YouTube Data API v3 connector. API-key access can read public live chat and public concurrent-viewer count. */
class YouTubeConnector(
    private val context: Context,
    private val apiKey: String,
    private val channelId: String,
    private val onStatus: (String) -> Unit
) {
    private val stopped = AtomicBoolean(false)
    private var worker: Thread? = null
    private var lastViewerCount: Int? = null

    fun start() {
        if (worker?.isAlive == true) return
        stopped.set(false)
        worker = thread(name = "Moorty-YouTube", isDaemon = true) {
            try {
                onStatus("YouTube: buscando directo público…")
                while (!stopped.get()) {
                    try {
                        val live = findLive()
                        if (live == null) {
                            onStatus("YouTube: no se encontró un directo público activo; reintento en 30 s.")
                            sleep(900_000); continue
                        }
                        val (videoId, chatId, title) = live
                        onStatus("YouTube conectado: $title")
                        readChat(videoId, chatId)
                    } catch (e: Exception) {
                        onStatus("YouTube: ${e.message ?: "error de conexión"}. Reintentando en 15 s.")
                        sleep(15_000)
                    }
                }
            } finally { onStatus("YouTube desconectado") }
        }
    }

    fun stop() { stopped.set(true); worker?.interrupt(); worker = null }

    private data class Live(val videoId: String, val chatId: String, val title: String)

    private fun findLive(): Live? {
        val search = get("search", mapOf("part" to "id,snippet", "channelId" to channelId, "eventType" to "live", "type" to "video", "maxResults" to "5"))
        val items = search.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val videoId = items.optJSONObject(i)?.optJSONObject("id")?.optString("videoId").orEmpty()
            if (videoId.isBlank()) continue
            val details = get("videos", mapOf("part" to "snippet,liveStreamingDetails", "id" to videoId)).optJSONArray("items")
            if (details == null || details.length() == 0) continue
            val video = details.optJSONObject(0) ?: continue
            val stream = video.optJSONObject("liveStreamingDetails") ?: continue
            val chatId = stream.optString("activeLiveChatId")
            if (chatId.isNotBlank()) return Live(videoId, chatId, video.optJSONObject("snippet")?.optString("title") ?: "YouTube LIVE")
        }
        return null
    }

    private fun readChat(videoId: String, chatId: String) {
        var pageToken: String? = null
        var baseline = true
        var lastViewerCheck = 0L
        while (!stopped.get()) {
            val now = System.currentTimeMillis()
            if (now - lastViewerCheck >= 60_000) {
                try {
                    val video = get("videos", mapOf("part" to "liveStreamingDetails", "id" to videoId)).optJSONArray("items")?.optJSONObject(0)
                    val count = video?.optJSONObject("liveStreamingDetails")?.optString("concurrentViewers")?.toIntOrNull()
                    if (count != null) {
                        val old = lastViewerCount
                        if (old != null && count > old) {
                            val diff = count - old
                            VoiceService.speak(context, if (diff == 1) "¡Hay un nuevo espectador! Ahora hay $count espectadores." else "¡Aumentaron los espectadores en $diff! Ahora hay $count espectadores.", 1f, 1f)
                        }
                        lastViewerCount = count
                    }
                } catch (_: Exception) { }
                lastViewerCheck = now
            }
            val params = mutableMapOf("part" to "snippet,authorDetails", "liveChatId" to chatId, "maxResults" to "200")
            if (pageToken != null) params["pageToken"] = pageToken!!
            val response = get("liveChat/messages", params)
            pageToken = response.optString("nextPageToken", pageToken ?: "").ifBlank { pageToken }
            val items = response.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val snippet = item.optJSONObject("snippet") ?: continue
                    val author = item.optJSONObject("authorDetails")?.optString("displayName") ?: "Alguien"
                    when (snippet.optString("type")) {
                        "textMessageEvent" -> {
                            val message = snippet.optJSONObject("textMessageDetails")?.optString("messageText").orEmpty()
                            if (!baseline && message.isNotBlank()) VoiceService.speak(context, "$author dice: ${message.take(220)}", 1f, 1f)
                        }
                        "newSponsorEvent" -> if (!baseline) VoiceService.speak(context, "¡$author se acaba de unir como miembro del canal!", 1f, 1f)
                        "superChatEvent" -> if (!baseline) VoiceService.speak(context, "¡Gracias $author por su Super Chat!", 1f, 1f)
                        "superStickerEvent" -> if (!baseline) VoiceService.speak(context, "¡Gracias $author por su Super Sticker!", 1f, 1f)
                        "membershipGiftingEvent" -> if (!baseline) VoiceService.speak(context, "¡$author regaló membresías!", 1f, 1f)
                    }
                }
            }
            baseline = false
            val waitMs = response.optLong("pollingIntervalMillis", 5000L).coerceIn(1000L, 30_000L)
            sleep(waitMs)
        }
    }

    private fun get(path: String, params: Map<String, String>): JSONObject {
        val query = (params + mapOf("key" to apiKey)).entries.joinToString("&") {
            "${enc(it.key)}=${enc(it.value)}"
        }
        val conn = (URL("https://www.googleapis.com/youtube/v3/$path?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000; readTimeout = 15_000; requestMethod = "GET"
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val reason = try { JSONObject(body).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                throw IllegalStateException("API HTTP $code${reason?.let { ": $it" } ?: ""}")
            }
            return JSONObject(body)
        } finally { conn.disconnect() }
    }
    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    private fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) { if (stopped.get()) return } }
}
