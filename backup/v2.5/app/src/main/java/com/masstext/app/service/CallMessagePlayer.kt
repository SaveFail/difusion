package com.masstext.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

// Reproduce el mensaje pregrabado cuando una llamada se conecta. Usa
// USAGE_VOICE_COMMUNICATION para salir SIEMPRE por la ruta real de la llamada
// (auricular, audífonos con cable/USB, Bluetooth, o altavoz solo si el usuario
// lo activó con el botón). Se reproduce una sola vez y se detiene al terminar.
object CallMessagePlayer {

    private var player: MediaPlayer? = null

    @Volatile
    private var generation = 0L

    fun play(context: Context, onFinished: (() -> Unit)? = null) {
        try {
            if (!VoiceMessageStore.isEnabled(context)) return
            val f = VoiceMessageStore.file(context)
            if (!f.exists() || f.length() <= 0) return
            stop()
            android.util.Log.i(
                "CallMessagePlayer",
                "Reproduciendo mensaje por la ruta actual (USAGE_VOICE_COMMUNICATION)"
            )
            val gen = ++generation
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(f.absolutePath)
                setVolume(1.0f, 1.0f)
                setOnPreparedListener { p ->
                    if (generation != gen) {
                        runCatching { p.release() }
                    } else {
                        runCatching { p.start() }
                    }
                }
                setOnCompletionListener { mp ->
                    if (generation == gen) {
                        player = null
                        runCatching { mp.release() }
                        android.util.Log.i("CallMessagePlayer", "Mensaje terminado")
                        onFinished?.invoke()
                    }
                }
            }
            player = mp
            // prepare() bloqueaba el hilo principal justo al conectar la llamada.
            mp.prepareAsync()
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        val p = player
        player = null
        generation++
        runCatching { p?.stop() }
        runCatching { p?.release() }
    }
}