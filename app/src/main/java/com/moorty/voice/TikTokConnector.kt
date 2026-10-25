package com.moorty.voice

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Android-compatible client for Moorty Relay. TikTok connection runs on the server, not in Android. */
class TikTokConnector(
    private val context: Context,
    private val username: String,
    private val relayUrl: String,
    private val normalSound: () -> String?,
    private val subscriberSound: () -> String?,
    private val onStatus: (String) -> Unit
) {
    private val stopped = AtomicBoolean(false)
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).connectTimeout(15, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    @Volatile private var socket: WebSocket? = null

    fun start() {
        if (stopped.getAndSet(false)) { /* allow restart */ }
        val url = relayUrl.trim().trimEnd('/')
        if (!(url.startsWith("wss://") || url.startsWith("ws://"))) {
            onStatus("TikTok: introduce la dirección del servidor WebSocket (wss://...)")
            return
        }
        onStatus("TikTok: conectando al servidor de eventos…")
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus("Servidor conectado. Solicitando eventos de @$username…")
                webSocket.send(JSONObject().put("action", "subscribe").put("username", username.removePrefix("@")).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val e = JSONObject(text)
                    if (e.optString("type") == "status") { onStatus("TikTok: " + e.optString("message", "estado actualizado")); return }
                    handleEvent(e)
                } catch (_: Exception) { onStatus("TikTok: llegó un evento que no se pudo interpretar") }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!stopped.get()) onStatus("TikTok relay: ${t.message ?: "falló la conexión"}. Revisa la URL y que el servidor esté activo.")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!stopped.get()) onStatus("TikTok relay desconectado: $reason")
            }
        })
    }

    private fun handleEvent(e: JSONObject) {
        val type = e.optString("type").lowercase()
        val user = e.optString("user", "alguien").ifBlank { "alguien" }.take(80)
        when (type) {
            "comment" -> { val message=e.optString("text").take(220); if(message.isNotBlank()) speak("$user dice: $message") }
            "gift" -> speak("¡$user envió ${e.optString("gift", "un regalo")}! ¡Gracias por el apoyo!")
            "follow" -> speak("¡$user empezó a seguir el canal! Muchas gracias.")
            "share" -> speak("¡Gracias $user por compartir el directo!")
            "join" -> {
                val isSub = if (e.has("subscriber") && !e.isNull("subscriber")) e.optBoolean("subscriber") else false
                val sound = if (isSub) subscriberSound() else normalSound()
                VoiceService.speak(context, "¡$user se unió al directo!", 1f, 1f, sound)
            }
            "like" -> { val count=e.optLong("count", 0); speak(if(count > 0) "¡Gracias $user por enviar $count likes!" else "¡Gracias $user por los likes!") }
            "subscribe" -> VoiceService.speak(context, "¡$user se suscribió al canal! Muchas gracias.", 1f, 1f, subscriberSound())
            "connected" -> onStatus("TikTok conectado: @$username")
            "error" -> onStatus("TikTok relay: ${e.optString("message", "error del conector")}")
        }
    }

    fun stop() {
        stopped.set(true)
        socket?.close(1000, "Moorty disconnected")
        socket = null
        onStatus("TikTok desconectado")
    }

    private fun speak(text: String) = VoiceService.speak(context, text, 1f, 1f, null)
}
