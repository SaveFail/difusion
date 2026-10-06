package com.difusion.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reproduce el mensaje de voz seleccionado durante una llamada:
 *  - Se dispara al CONTESTAR (estado activo).
 *  - Al TERMINAR el audio, avisa con [onFinished] para COLGAR la llamada.
 *
 * Detalles:
 *  - Se reproduce una sola vez por llamada (se identifica la llamada por el
 *    teléfono normalizado), así da igual si lo dispara el teléfono (Telecom) o
 *    la secuencia de llamadas: no se duplica.
 *  - Fuerza el altavoz para que el mensaje salga hacia la llamada; se puede
 *    oír al interlocutor (en muchos equipos el micrófono capta el altavoz).
 *  - Usa USAGE_VOICE_COMMUNICATION para enrutar por el audio de la llamada.
 */
object CallMessagePlayer {
    private var player: MediaPlayer? = null
    private var currentKey: String? = null
    private var audioManager: AudioManager? = null
    private var forcedSpeaker = false

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private fun normalize(s: String): String = s.filter { it.isDigit() }.takeLast(10)

    /**
     * Devuelve true si empezó a reproducir. [onFinished] se llama al terminar
     * el audio (o si falla la reproducción).
     */
    fun play(context: Context, key: String, onFinished: () -> Unit): Boolean {
        if (!VoiceMessageStore.isEnabled(context)) return false
        val file = VoiceMessageStore.selected(context) ?: return false
        val k = normalize(key).ifBlank { "call_${System.currentTimeMillis()}" }
        if (k == currentKey) return false
        stopInternal()
        currentKey = k
        forceSpeaker(context)
        android.util.Log.i("CallMessagePlayer", "Reproduciendo ${file.name} (llamada $k)")
        return try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setVolume(1.0f, 1.0f)
                setOnPreparedListener { runCatching { it.start() } }
                setOnCompletionListener { m ->
                    runCatching { m.release() }
                    if (player === m) player = null
                    _playing.value = false
                    restoreSpeaker()
                    android.util.Log.i("CallMessagePlayer", "Mensaje terminado -> colgar")
                    onFinished()
                }
                setOnErrorListener { m, _, _ ->
                    runCatching { m.release() }
                    if (player === m) player = null
                    _playing.value = false
                    restoreSpeaker()
                    onFinished()
                    true
                }
            }
            player = mp
            _playing.value = true
            mp.prepareAsync()
            true
        } catch (e: Exception) {
            android.util.Log.e("CallMessagePlayer", "Error: ${e.message}")
            _playing.value = false
            restoreSpeaker()
            false
        }
    }

    /** Detiene y olvida la llamada actual (para que la siguiente vuelva a sonar). */
    fun stop() {
        stopInternal()
        currentKey = null
    }

    private fun stopInternal() {
        val p = player
        player = null
        _playing.value = false
        runCatching { p?.stop() }
        runCatching { p?.release() }
        restoreSpeaker()
    }

    private fun forceSpeaker(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am
        runCatching {
            // El micrófono debe estar ABIERTO: si está silenciado, el receptor no
            // oiría nada. El audio del parlante lo capta el micrófono y lo transmite.
            am.isMicrophoneMute = false
        }
        runCatching {
            if (!am.isSpeakerphoneOn) {
                am.isSpeakerphoneOn = true
                forcedSpeaker = true
            }
        }
        runCatching {
            // Volumen al máximo de la voz de llamada para que se oiga del otro lado.
            val max = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, max, 0)
        }
    }

    private fun restoreSpeaker() {
        val am = audioManager ?: return
        if (forcedSpeaker) {
            runCatching { am.isSpeakerphoneOn = false }
            forcedSpeaker = false
        }
    }
}
