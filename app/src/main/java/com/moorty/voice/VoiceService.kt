package com.moorty.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class VoiceService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val EXTRA_SPEAK = "com.moorty.voice.SPEAK"
        const val ACTION_TEST = "com.moorty.voice.TEST"
        const val ACTION_START_YOUTUBE = "com.moorty.voice.START_YOUTUBE"
        const val ACTION_STOP = "com.moorty.voice.STOP"
        private const val CHANNEL_ID = "moorty_voice_channel"
        private const val NOTIFICATION_ID = 801
        private const val TAG = "MoortyVoice"
    }

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var pendingText: String? = null
    private val running = AtomicBoolean(false)
    @Volatile private var monitorThread: Thread? = null
    @Volatile private var statusText = "Servicio de voz activo"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tts = TextToSpeech(applicationContext, this)
        startForeground(NOTIFICATION_ID, buildNotification("Moorty Voice preparado"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                running.set(false)
                monitorThread?.interrupt()
                speak("Moorty Voice detenido.")
                updateNotification("Lector detenido")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_TEST -> {
                intent.getStringExtra(EXTRA_SPEAK)?.let { speak(it) }
            }
            ACTION_START_YOUTUBE -> {
                if (running.compareAndSet(false, true)) {
                    statusText = "Conectando con YouTube LIVE…"
                    updateNotification(statusText)
                    monitorThread = Thread({ youtubeLoop() }, "MoortyYouTubeMonitor").apply { isDaemon = true; start() }
                } else {
                    speak("Moorty Voice ya está conectado o intentando conectar.")
                }
            }
            else -> intent?.getStringExtra(EXTRA_SPEAK)?.let { speak(it) }
        }
        return START_STICKY
    }

    private fun youtubeLoop() {
        val prefs = getSharedPreferences("moorty_settings", MODE_PRIVATE)
        val key = prefs.getString("api_key", "").orEmpty()
        val channel = prefs.getString("channel_id", "").orEmpty()
        if (key.isBlank() || channel.isBlank()) {
            report("Falta API key o ID del canal.")
            running.set(false)
            return
        }
        var activeVideo = ""
        var activeChat = ""
        var pageToken: String? = null
        var firstChatPage = true
        var lastViewerCount: Int? = null
        var nextViewerCheck = 0L

        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                if (activeChat.isBlank()) {
                    report("Buscando un directo público activo…")
                    val live = findLive(key, channel)
                    if (live == null) {
                        report("No hay un directo público activo con chat disponible. Reintento en 30 segundos.")
                        sleepInterruptibly(120_000)
                        continue
                    }
                    activeVideo = live.first
                    activeChat = live.second
                    pageToken = null
                    firstChatPage = true
                    lastViewerCount = null
                    report("Conectado al directo: ${live.third}")
                    speak("Moorty Voice está conectado al directo de YouTube.")
                }

                val now = System.currentTimeMillis()
                if (now >= nextViewerCheck) {
                    val count = getViewerCount(key, activeVideo)
                    if (count != null) {
                        val previous = lastViewerCount
                        if (previous != null && count > previous) {
                            val delta = count - previous
                            speak(if (delta == 1) "El contador de espectadores subió de $previous a $count." else
                                "El contador de espectadores subió de $previous a $count, un aumento de $delta.")
                        }
                        lastViewerCount = count
                    }
                    nextViewerCheck = now + 60_000
                }

                val params = linkedMapOf("part" to "id,snippet,authorDetails", "liveChatId" to activeChat, "maxResults" to "200")
                if (!pageToken.isNullOrBlank()) params["pageToken"] = pageToken!!
                val response = getJson("https://www.googleapis.com/youtube/v3/liveChat/messages", params, key)
                pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
                val items = response.optJSONArray("items")
                if (items != null && !firstChatPage) {
                    for (i in 0 until items.length()) {
                        if (!running.get()) break
                        val item = items.optJSONObject(i) ?: continue
                        val snippet = item.optJSONObject("snippet") ?: continue
                        val authorDetails = item.optJSONObject("authorDetails") ?: JSONObject()
                        val author = authorDetails.optString("displayName", "Alguien")
                        val phrase = formatEvent(author, snippet)
                        if (!phrase.isNullOrBlank()) {
                            report(phrase)
                            speak(phrase)
                        }
                    }
                }
                firstChatPage = false
                val wait = response.optLong("pollingIntervalMillis", 5000L).coerceIn(1000L, 15000L)
                sleepInterruptibly(wait)
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                val message = e.message.orEmpty()
                Log.e(TAG, "YouTube monitor error", e)
                if (message.contains("liveChatEnded", true) || message.contains("liveChatNotFound", true) ||
                    message.contains("liveChatDisabled", true) || message.contains("not found", true)) {
                    activeVideo = ""
                    activeChat = ""
                    pageToken = null
                    report("El chat terminó o ya no está disponible. Buscando otro directo…")
                } else {
                    report("Error de YouTube: ${message.take(180)}. Reintento en 15 segundos.")
                    sleepInterruptibly(60_000)
                }
            }
        }
        running.set(false)
        report("Lector de YouTube detenido.")
    }

    private fun findLive(key: String, channel: String): Triple<String, String, String>? {
        val search = getJson("https://www.googleapis.com/youtube/v3/search", mapOf(
            "part" to "id,snippet", "channelId" to channel, "eventType" to "live",
            "type" to "video", "maxResults" to "5"
        ), key)
        val items = search.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val videoId = items.optJSONObject(i)?.optJSONObject("id")?.optString("videoId").orEmpty()
            if (videoId.isBlank()) continue
            val videoResponse = getJson("https://www.googleapis.com/youtube/v3/videos", mapOf(
                "part" to "snippet,liveStreamingDetails", "id" to videoId
            ), key)
            val videos = videoResponse.optJSONArray("items") ?: continue
            if (videos.length() == 0) continue
            val video = videos.optJSONObject(0) ?: continue
            val chatId = video.optJSONObject("liveStreamingDetails")?.optString("activeLiveChatId").orEmpty()
            if (chatId.isNotBlank()) {
                val title = video.optJSONObject("snippet")?.optString("title", "Directo de YouTube") ?: "Directo de YouTube"
                return Triple(videoId, chatId, title)
            }
        }
        return null
    }

    private fun getViewerCount(key: String, videoId: String): Int? {
        return try {
            val response = getJson("https://www.googleapis.com/youtube/v3/videos",
                mapOf("part" to "liveStreamingDetails", "id" to videoId), key)
            val items = response.optJSONArray("items")
            if (items == null || items.length() == 0) null
            else items.optJSONObject(0)?.optJSONObject("liveStreamingDetails")?.optString("concurrentViewers")?.toIntOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Viewer count unavailable", e)
            null
        }
    }

    private fun formatEvent(author: String, snippet: JSONObject): String? {
        val prefs = getSharedPreferences("moorty_settings", MODE_PRIVATE)
        return when (snippet.optString("type")) {
            "textMessageEvent" -> {
                if (!prefs.getBoolean("comments", true)) return null
                val text = snippet.optString("displayMessage").replace(Regex("https?://\\S+"), " enlace ")
                    .replace(Regex("[\\r\\n\\t]+"), " ").take(280).trim()
                if (text.isBlank()) null else "$author dice: $text"
            }
            "newSponsorEvent" -> if (prefs.getBoolean("gifts", true)) {
                val level = snippet.optJSONObject("newSponsorDetails")?.optString("memberLevelName").orEmpty()
                if (level.isNotBlank()) "¡$author se unió como miembro del canal, nivel $level! ¡Gracias por apoyar!"
                else "¡$author se unió como miembro del canal! ¡Gracias por apoyar!"
            } else null
            "memberMilestoneChatEvent" -> if (prefs.getBoolean("gifts", true)) {
                val details = snippet.optJSONObject("memberMilestoneChatDetails") ?: JSONObject()
                val months = details.optString("memberMonth").takeIf { it.isNotBlank() && it != "null" }
                val level = details.optString("memberLevelName").takeIf { it.isNotBlank() && it != "null" }
                val comment = details.optString("userComment").take(160)
                "¡$author celebra su membresía${months?.let { " por $it meses" } ?: ""}${level?.let { ", nivel $it" } ?: ""}!${if (comment.isNotBlank()) " Dice: $comment" else ""}"
            } else null
            "membershipGiftingEvent" -> if (prefs.getBoolean("gifts", true)) {
                val d = snippet.optJSONObject("membershipGiftingDetails") ?: JSONObject()
                "¡$author regaló ${d.optInt("giftMembershipsCount", 1)} membresía(s)! ¡Muchas gracias!"
            } else null
            "giftMembershipReceivedEvent" -> if (prefs.getBoolean("gifts", true)) {
                "¡$author recibió una membresía regalada! ¡Felicidades!"
            } else null
            "superChatEvent" -> if (prefs.getBoolean("gifts", true)) {
                val d = snippet.optJSONObject("superChatDetails") ?: JSONObject()
                val amount = d.optString("amountDisplayString")
                val comment = d.optString("userComment").take(160)
                "¡Super Chat de $author${if (amount.isNotBlank()) " por $amount" else ""}!${if (comment.isNotBlank()) " Mensaje: $comment" else ""}"
            } else null
            "superStickerEvent" -> if (prefs.getBoolean("gifts", true)) {
                val amount = snippet.optJSONObject("superStickerDetails")?.optString("amountDisplayString").orEmpty()
                "¡$author envió un Super Sticker${if (amount.isNotBlank()) " de $amount" else ""}!"
            } else null
            else -> null
        }
    }

    private fun getJson(endpoint: String, params: Map<String, String>, apiKey: String): JSONObject {
        val query = (params + ("key" to apiKey)).entries.joinToString("&") {
            enc(it.key) + "=" + enc(it.value)
        }
        val connection = (URL("$endpoint?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (body.isNotBlank()) JSONObject(body) else JSONObject()
            if (code !in 200..299) {
                val error = json.optJSONObject("error")
                val details = error?.optString("message").orEmpty().ifBlank { "HTTP $code" }
                throw IllegalStateException(details)
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
    private fun sleepInterruptibly(ms: Long) { Thread.sleep(ms) }

    private fun speak(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "moorty-${System.currentTimeMillis()}")
        else pendingText = text
    }

    private fun report(text: String) {
        statusText = text
        updateNotification(text)
        val intent = Intent("com.moorty.voice.STATUS").putExtra("status", text)
        sendBroadcast(intent)
        Log.i(TAG, text)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text.take(100)))
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale("es", "MX")
            pendingText?.let { speak(it); pendingText = null }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Moorty Voice LIVE", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID).setContentTitle("Moorty Voice").setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setOngoing(running.get()).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("Moorty Voice").setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setOngoing(running.get()).build()
        }
    }

    override fun onDestroy() {
        running.set(false)
        monitorThread?.interrupt()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
