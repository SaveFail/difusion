package com.difusion.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.difusion.app.service.BotAutomation
import com.difusion.app.service.WhatsAppBridge
import com.difusion.app.storage.WaPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sondea los mensajes entrantes del worker de WhatsApp y aplica las
 * automatizaciones de la app (responder, cambiar gestión/estado/medio,
 * etiquetar, programar mensajes).
 */
class BotPollWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!WaPrefs.isConfigured(applicationContext) ||
                !WaPrefs.isBotEnabled(applicationContext)
            ) {
                return@withContext Result.success()
            }
            val since = WaPrefs.getLastMessageTs(applicationContext)
            val messages = WhatsAppBridge.fetchMessages(applicationContext, since)
            if (messages.isEmpty()) return@withContext Result.success()
            BotAutomation.process(applicationContext, messages)
            WaPrefs.setLastMessageTs(applicationContext, messages.maxOf { it.ts })
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
