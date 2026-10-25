package com.moorty.voice

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.concurrent.LinkedBlockingDeque

class VoiceService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val queue = LinkedBlockingDeque<SpeechItem>()
    @Volatile private var running = false
    private var worker: Thread? = null
    data class SpeechItem(val text: String, val rate: Float, val volume: Float, val sound: String? = null)

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createChannel()
        startForeground(31, notification("Servicio de voz activo"))
        running = true
        worker = Thread {
            while (running) {
                try {
                    val item = queue.takeFirst()
                    item.sound?.let { playSound(it) }
                    while (!ready && running) Thread.sleep(100)
                    if (ready && running) {
                        tts?.setSpeechRate(item.rate.coerceIn(0.5f, 2f))
                        val params = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, item.volume.coerceIn(0f, 1f)) }
                        tts?.speak(item.text, TextToSpeech.QUEUE_FLUSH, params, "moorty-${System.nanoTime()}")
                        Thread.sleep((item.text.length * 65L).coerceIn(700L, 9000L))
                    }
                } catch (_: InterruptedException) { break } catch (_: Exception) { }
            }
        }.apply { start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { running = false; worker?.interrupt(); tts?.stop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            ACTION_SPEAK -> {
                val phrase = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                if (phrase.isNotBlank()) {
                    val rate = intent.getFloatExtra(EXTRA_RATE, 1f)
                    val volume = intent.getFloatExtra(EXTRA_VOLUME, 1f)
                    val sound = intent.getStringExtra(EXTRA_SOUND)
                    // Prefer newer alerts over stale pending ones, as in the desktop version.
                    if (queue.size >= 20) queue.pollLast()
                    queue.offerFirst(SpeechItem(phrase, rate, volume, sound))
                }
            }
        }
        return START_STICKY
    }

    private fun playSound(uriString: String) {
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            mp.setDataSource(this, Uri.parse(uriString)); mp.setOnCompletionListener { it.release() }; mp.setOnErrorListener { p, _, _ -> p.release(); true }; mp.prepare(); mp.start()
        } catch (_: Exception) { }
    }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) { ready = true; tts?.language = Locale("es", "ES") } }
    override fun onDestroy() { running = false; worker?.interrupt(); tts?.stop(); tts?.shutdown(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) { val c = NotificationChannel(CHANNEL, "Moorty Voice", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(c) } }
    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL).setContentTitle("Moorty Voice").setContentText(text).setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setOngoing(true).build()
    companion object {
        const val ACTION_SPEAK = "com.moorty.voice.SPEAK"; const val ACTION_STOP = "com.moorty.voice.STOP"
        const val EXTRA_TEXT = "text"; const val EXTRA_RATE = "rate"; const val EXTRA_VOLUME = "volume"; const val EXTRA_SOUND = "sound"
        const val CHANNEL = "moorty_voice_channel"
        fun speak(context: Context, text: String, rate: Float = 1f, volume: Float = 1f, sound: String? = null) {
            val i = Intent(context, VoiceService::class.java).setAction(ACTION_SPEAK).putExtra(EXTRA_TEXT, text).putExtra(EXTRA_RATE, rate).putExtra(EXTRA_VOLUME, volume)
            if (sound != null) i.putExtra(EXTRA_SOUND, sound)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
    }
}
