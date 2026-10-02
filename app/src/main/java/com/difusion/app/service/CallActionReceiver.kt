package com.difusion.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recibe las acciones de la notificación "Llamada en curso" (colgar / altavoz)
 * y las reenvía a [CallMonitor], que tiene la referencia a la llamada real.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotification.ACTION_END_CALL -> CallMonitor.endCall()
            CallNotification.ACTION_TOGGLE_SPEAKER -> CallMonitor.toggleSpeaker()
            CallNotification.ACTION_TOGGLE_MUTE -> CallMonitor.toggleMute()
        }
    }
}