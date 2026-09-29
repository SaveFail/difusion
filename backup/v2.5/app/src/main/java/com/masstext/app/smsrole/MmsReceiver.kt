package com.masstext.app.smsrole

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.masstext.app.data.AppDatabase
import com.masstext.app.service.MmsInbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MmsReceiver : BroadcastReceiver() {
    companion object {
        // Tiempo que esperamos a que el sistema descargue el MMS del MMSC antes de sincronizar.
        private const val SYNC_DELAY_MS = 10_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.WAP_PUSH_DELIVER" &&
            intent.action != "android.provider.Telephony.WAP_PUSH_RECEIVED"
        ) return
        val pending = goAsync()
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            try {
                // Los MMS tardan en descargarse del centro del operador; damos
                // tiempo al componente SMS del sistema para dejar el mensaje.
                delay(SYNC_DELAY_MS)
                val db = AppDatabase.getInstance(context)
                MmsInbox.syncNow(context, db)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}