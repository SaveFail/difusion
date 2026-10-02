package com.difusion.app.service

import android.content.Context
import android.media.AudioManager

// Silencia el micrófono del dispositivo durante la llamada para que el cliente
// solo escuche el mensaje pregrabado (y nada del ambiente ni de la voz del
// operador). Al terminar la llamada se restaura el estado previo del micrófono.
object CallMicMute {

    private var previous: Boolean? = null

    private fun audio(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Silencia el micrófono (recordando su estado previo). */
    fun mute(context: Context) {
        if (previous != null) return
        val am = audio(context) ?: return
        runCatching {
            previous = am.isMicrophoneMute
            am.isMicrophoneMute = true
        }
    }

    /** Restaura el estado del micrófono que había antes de [mute]. */
    fun restore(context: Context) {
        val prev = previous ?: return
        previous = null
        val am = audio(context) ?: return
        runCatching { am.isMicrophoneMute = prev }
    }
}
