
package com.moorty.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter

class MainActivity : Activity() {

    private lateinit var platform: Spinner
    private lateinit var account: Spinner
    private lateinit var apiKey: EditText
    private lateinit var channelId: EditText
    private lateinit var status: TextView
    private lateinit var comments: CheckBox
    private lateinit var gifts: CheckBox
    private lateinit var likes: CheckBox
    private lateinit var follows: CheckBox
    private lateinit var shares: CheckBox
    private lateinit var joins: CheckBox

    private var statusReceiverRegistered = false

    private val prefs by lazy {
        getSharedPreferences("moorty_settings", Context.MODE_PRIVATE)
    }

    // Recibe las actualizaciones de estado enviadas por VoiceService.
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "com.moorty.voice.STATUS") return

            val message =
                intent.getStringExtra("status")
                    ?: intent.getStringExtra("message")
                    ?: intent.getStringExtra("status_message")

            if (!message.isNullOrBlank()) {
                status.text = message
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        platform = findViewById(R.id.platformSpinner)
        account = findViewById(R.id.accountSpinner)
        apiKey = findViewById(R.id.apiKeyInput)
        channelId = findViewById(R.id.channelIdInput)
        status = findViewById(R.id.statusText)
        comments = findViewById(R.id.commentsCheck)
        gifts = findViewById(R.id.giftsCheck)
        likes = findViewById(R.id.likesCheck)
        follows = findViewById(R.id.followsCheck)
        shares = findViewById(R.id.sharesCheck)
        joins = findViewById(R.id.joinsCheck)

        // Registrar el receptor después de inicializar la interfaz.
        val filter = IntentFilter("com.moorty.voice.STATUS")

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                statusReceiver,
                filter,
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(statusReceiver, filter)
        }

        statusReceiverRegistered = true

        platform.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("YouTube LIVE", "TikTok LIVE")
        )

        account.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("@moorty_yt", "@tocolita")
        )

        apiKey.setText(prefs.getString("api_key", ""))
        channelId.setText(prefs.getString("channel_id", ""))

        comments.isChecked = prefs.getBoolean("comments", true)
        gifts.isChecked = prefs.getBoolean("gifts", true)
        likes.isChecked = prefs.getBoolean("likes", true)
        follows.isChecked = prefs.getBoolean("follows", true)
        shares.isChecked = prefs.getBoolean("shares", true)
        joins.isChecked = prefs.getBoolean("joins", false)

        platform.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(parent: AdapterView<*>?) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val youtube = position == 0

                    account.isEnabled = !youtube
                    apiKey.isEnabled = youtube
                    channelId.isEnabled = youtube

                    status.text = if (youtube) {
                        "YouTube seleccionado. Introduce API key e ID del canal."
                    } else {
                        "TikTok seleccionado. La conexión directa Android aún requiere un conector compatible; no se simularán eventos."
                    }
                }
            }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        findViewById<Button>(R.id.testVoiceButton).setOnClickListener {
            startServiceAction(
                VoiceService.ACTION_TEST,
                "Prueba de voz. Moorty Voice está listo."
            )
            status.text = "Prueba de voz enviada."
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            saveSettings()

            if (platform.selectedItemPosition == 0) {
                val key = apiKey.text.toString().trim()
                val channel = channelId.text.toString().trim()

                if (key.isBlank() || channel.isBlank()) {
                    status.text = "Falta la API key o el ID del canal."
                    return@setOnClickListener
                }

                startServiceAction(
                    VoiceService.ACTION_START_YOUTUBE,
                    null
                )

                status.text = "Iniciando conexión con YouTube LIVE…"
            } else {
                status.text =
                    "TikTok LIVE todavía no está conectado en esta versión Android. No se iniciará una conexión falsa."

                Toast.makeText(
                    this,
                    "La conexión TikTok requiere un conector compatible.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            startServiceAction(VoiceService.ACTION_STOP, null)
            status.text = "Deteniendo lector LIVE…"
        }
    }

    override fun onDestroy() {
        if (statusReceiverRegistered) {
            try {
                unregisterReceiver(statusReceiver)
            } catch (_: Exception) {
                // Evita errores si el receptor ya fue desvinculado.
            }

            statusReceiverRegistered = false
        }

        super.onDestroy()
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("api_key", apiKey.text.toString().trim())
            .putString("channel_id", channelId.text.toString().trim())
            .putString(
                "tiktok_account",
                account.selectedItem.toString().removePrefix("@")
            )
            .putBoolean("comments", comments.isChecked)
            .putBoolean("gifts", gifts.isChecked)
            .putBoolean("likes", likes.isChecked)
            .putBoolean("follows", follows.isChecked)
            .putBoolean("shares", shares.isChecked)
            .putBoolean("joins", joins.isChecked)
            .apply()
    }

    private fun startServiceAction(action: String, speech: String?) {
        val intent = Intent(this, VoiceService::class.java)
            .setAction(action)

        if (speech != null) {
            intent.putExtra(VoiceService.EXTRA_SPEAK, speech)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
