package com.difusion.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.difusion.app.data.AppDatabase
import com.difusion.app.data.Contact
import com.difusion.app.service.EmailSyncService
import com.difusion.app.service.SmsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Ejecuta los envíos masivos programados que ya vencieron: SMS (canal 0) o
 * correo (canal 1). Usa "claim" para que un envío no se procese dos veces.
 */
class ScheduledSendWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val now = System.currentTimeMillis()
            val due = db.scheduledSendDao().getDue(now)
            if (due.isEmpty()) return@withContext Result.success()

            for (item in due) {
                if (db.scheduledSendDao().claim(item.id) != 1) continue
                try {
                    if (item.channel == 1) {
                        val emails = parseJson(item.emailsJson)
                        if (emails.isEmpty()) {
                            db.scheduledSendDao().updateStatus(item.id, 2, now, "Sin correos")
                            continue
                        }
                        val res = EmailSyncService.sendSmart(
                            applicationContext,
                            emails,
                            item.subject.ifBlank { "Mensaje" },
                            item.message
                        )
                        if (res.success) {
                            db.scheduledSendDao().updateStatus(
                                item.id, 1, now, "Enviado: ${res.sent} de ${res.total}"
                            )
                        } else {
                            db.scheduledSendDao().updateStatus(
                                item.id, 2, now, res.error
                            )
                        }
                    } else {
                        val phones = parseJson(item.phonesJson)
                        if (phones.isEmpty()) {
                            db.scheduledSendDao().updateStatus(item.id, 2, now, "Sin teléfonos")
                            continue
                        }
                        val contacts = phones.map { Contact(id = -1, name = it, phone = it) }
                        val result = SmsController.get(applicationContext)
                            .sendBatch(contacts, item.message, 4000L, safeMode = true, subId = null)
                        db.scheduledSendDao().updateStatus(
                            item.id, 1, now,
                            "Enviado: ${result.sent} · Fallidos: ${result.failed}"
                        )
                    }
                } catch (e: Exception) {
                    db.scheduledSendDao().updateStatus(item.id, 2, now, e.message ?: "Error")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("ScheduledSendWorker", "error", e)
            Result.retry()
        }
    }

    private fun parseJson(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
