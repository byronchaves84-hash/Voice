package com.moorty.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private val bg = Color.rgb(16,17,22); private val panel = Color.rgb(29,30,38); private val pink = Color.rgb(255,77,103)
    private lateinit var status: TextView
    private lateinit var account: Spinner
    private lateinit var rate: SeekBar
    private lateinit var volume: SeekBar
    private lateinit var relayUrl: EditText
    private lateinit var apiKey: EditText
    private lateinit var channelId: EditText
    private lateinit var normalSound: TextView
    private lateinit var subscriberSound: TextView
    private var normalUri: Uri? = null; private var subscriberUri: Uri? = null
    private var tiktokConnector: TikTokConnector? = null
    private var youtubeConnector: YouTubeConnector? = null
    private lateinit var tiktokStatus: TextView
    private lateinit var youtubeStatus: TextView
    private val prefs by lazy { getSharedPreferences("moorty", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1); buildUi() }
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(20,24,20,24) }
        val scroll = ScrollView(this); val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,0,0,18) }; scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        content.addView(label("MOORTY VOICE", 28, pink)); content.addView(label("TikTok LIVE + YouTube LIVE · Android", 14, Color.LTGRAY)); content.addView(space(18))
        status = label("Listo para configurar", 15, Color.WHITE); content.addView(card(status))
        content.addView(label("TIKTOK LIVE",20,Color.WHITE)); relayUrl = EditText(this).apply { hint = "Servidor Moorty (wss://...)"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setSingleLine(true); setText(prefs.getString("tt_relay", "")) }; content.addView(card(relayUrl)); account = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("@moorty_yt", "@tocolita")) }; content.addView(card(account))
        val toggles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf("Comentarios", "Regalos", "Likes", "Seguidores", "Compartidos", "Entradas al directo").forEach { name -> val cb = CheckBox(this).apply { text = name; setTextColor(Color.WHITE); isChecked = true; buttonTintList = android.content.res.ColorStateList.valueOf(pink) }; toggles.addView(cb) }
        content.addView(card(toggles))
        content.addView(label("VOZ EN ESPAÑOL",20,Color.WHITE)); rate = SeekBar(this).apply { max = 15; progress = 5 }; volume = SeekBar(this).apply { max = 100; progress = 100 }; content.addView(label("Velocidad",14,Color.LTGRAY)); content.addView(card(rate)); content.addView(label("Volumen de voz",14,Color.LTGRAY)); content.addView(card(volume))
        content.addView(label("SONIDOS DE ENTRADA · TIKTOK",20,Color.WHITE)); normalSound = label("Sonido entrada normal: no seleccionado",14,Color.WHITE); subscriberSound = label("Sonido suscriptor: no seleccionado",14,Color.WHITE)
        content.addView(card(normalSound)); content.addView(button("Elegir sonido entrada normal") { chooseSound(false) }); content.addView(card(subscriberSound)); content.addView(button("Elegir sonido suscriptor") { chooseSound(true) })
        content.addView(label("YOUTUBE LIVE",20,Color.WHITE)); apiKey = EditText(this).apply { hint = "YouTube Data API key"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setSingleLine(true); setText(prefs.getString("yt_key", "")) }; channelId = EditText(this).apply { hint = "ID del canal (UC...)"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setSingleLine(true); setText(prefs.getString("yt_channel", "")) }; content.addView(card(apiKey)); content.addView(card(channelId))
        content.addView(label("CONECTORES EN VIVO",20,Color.WHITE))
        tiktokStatus = label("TikTok: desconectado",13,Color.LTGRAY); content.addView(card(tiktokStatus))
        content.addView(button("CONECTAR TIKTOK LIVE · CUENTA SELECCIONADA") { connectTikTok() })
        content.addView(button("DESCONECTAR TIKTOK LIVE") { tiktokConnector?.stop(); tiktokConnector = null })
        youtubeStatus = label("YouTube: desconectado",13,Color.LTGRAY); content.addView(card(youtubeStatus))
        content.addView(button("CONECTAR YOUTUBE LIVE") { connectYouTube() })
        content.addView(button("DESCONECTAR YOUTUBE LIVE") { youtubeConnector?.stop(); youtubeConnector = null })
        content.addView(card(label("YouTube por API key: lee chat público, Super Chats/Stickers, nuevos miembros visibles y contador público de espectadores. Las alertas de nuevos suscriptores requieren además autorización OAuth del canal; una API key sola no permite leer la lista de suscriptores recientes.",12,Color.LTGRAY)))
        root.addView(button("GUARDAR CONFIGURACIÓN") { prefs.edit().putString("yt_key",apiKey.text.toString()).putString("yt_channel",channelId.text.toString()).putString("tt_relay", relayUrl.text.toString()).apply(); status.text = "Configuración guardada en este teléfono" })
        root.addView(button("PROBAR VOZ") { VoiceService.speak(this, "Prueba de voz. Moorty Voice está listo.", 0.5f + rate.progress / 10f, volume.progress / 100f); status.text = "Prueba de voz enviada" })
        root.addView(button("DETENER VOZ EN SEGUNDO PLANO") { startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_STOP)); status.text = "Servicio detenido" })
        setContentView(root)
    }
    private fun connectTikTok() {
        val handle = account.selectedItem?.toString().orEmpty().removePrefix("@").trim()
        if (handle.isBlank()) { tiktokStatus.text = "Selecciona una cuenta de TikTok."; return }
        tiktokConnector?.stop()
        val relay = relayUrl.text.toString().trim()
        if (relay.isBlank()) { tiktokStatus.text = "Introduce primero la dirección del servidor Moorty Relay."; return }
        prefs.edit().putString("tt_relay", relay).apply()
        tiktokConnector = TikTokConnector(this, handle, relay,
            { prefs.getString("normal_sound", null) },
            { prefs.getString("subscriber_sound", null) }) { message -> runOnUiThread { tiktokStatus.text = message } }
        tiktokConnector?.start()
        status.text = "Conector TikTok iniciado para @$handle"
    }
    private fun connectYouTube() {
        val key = apiKey.text.toString().trim(); val channel = channelId.text.toString().trim()
        if (key.isBlank() || channel.isBlank()) { youtubeStatus.text = "Introduce la API key y el ID del canal de YouTube."; return }
        prefs.edit().putString("yt_key", key).putString("yt_channel", channel).apply()
        youtubeConnector?.stop()
        youtubeConnector = YouTubeConnector(this, key, channel) { message -> runOnUiThread { youtubeStatus.text = message } }
        youtubeConnector?.start()
        status.text = "Conector YouTube iniciado"
    }
    override fun onDestroy() { tiktokConnector?.stop(); youtubeConnector?.stop(); super.onDestroy() }
    private fun chooseSound(subscriber: Boolean) { val i = Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, if (subscriber) 42 else 41) }
    @Deprecated("Deprecated in Android API, kept for broad compatibility") override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode,resultCode,data); if(resultCode==RESULT_OK && data?.data!=null) { val uri=data.data!!; try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {}; if(requestCode==41) { normalUri=uri; normalSound.text="Sonido entrada normal: ${uri.lastPathSegment}"; prefs.edit().putString("normal_sound",uri.toString()).apply() } else if(requestCode==42) { subscriberUri=uri; subscriberSound.text="Sonido suscriptor: ${uri.lastPathSegment}"; prefs.edit().putString("subscriber_sound",uri.toString()).apply() } } }
    private fun label(s:String,size:Int,color:Int)=TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(color); setPadding(10,12,10,12) }
    private fun card(v: android.view.View): LinearLayout { return LinearLayout(this).apply { setBackgroundColor(panel); setPadding(8,4,8,4); addView(v, ViewGroup.LayoutParams(-1,-2)); val p=LinearLayout.LayoutParams(-1,-2); p.setMargins(0,5,0,9); layoutParams=p } }
    private fun button(s:String, action:()->Unit)=Button(this).apply { text=s; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(pink)); setOnClickListener { action() }; val p=LinearLayout.LayoutParams(-1,-2); p.setMargins(0,5,0,5); layoutParams=p }
    private fun space(h:Int)=android.view.View(this).apply { layoutParams=LinearLayout.LayoutParams(1,h) }
}
