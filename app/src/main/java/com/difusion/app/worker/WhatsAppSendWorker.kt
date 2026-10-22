package com.difusion.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.difusion.app.service.WhatsAppBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Envía mensajes de WhatsApp (vía worker Baileys) en segundo plano con progreso. */
class WhatsAppSendWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recipients = parse(inputData.getString(KEY_RECIPIENTS) ?: "[]")
        val text = inputData.getString(KEY_TEXT) ?: ""
        if (recipients.isEmpty() || text.isBlank()) return@withContext Result.failure()

        runCatching { setForegroundAsync(foreground(0, recipients.size)) }

        var sent = 0
        var failed = 0
        var firstError = ""
        for (r in recipients) {
            val (ok, err) = WhatsAppBridge.send(applicationContext, r, text)
            if (ok) sent++ else {
                failed++
                if (firstError.isBlank()) firstError = err
            }
            val done = sent + failed
            runCatching {
                setProgressAsync(workDataOf(KEY_SENT to done, KEY_TOTAL to recipients.size))
                setForegroundAsync(foreground(done, recipients.size))
            }
        }
        finishNotification(sent, failed)
        Result.success(
            workDataOf(
                KEY_SENT to sent, KEY_TOTAL to recipients.size,
                "failed" to failed, "error" to firstError
            )
        )
    }

    private fun foreground(done: Int, total: Int): ForegroundInfo {
        ensureChannel()
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Enviando WhatsApp")
            .setContentText("$done de $total")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), done, false)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, n)
        }
    }

    private fun finishNotification(sent: Int, failed: Int) {
        ensureChannel()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NOTIF_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("WhatsApp terminado")
                .setContentText("Enviados: $sent · Fallidos: $failed")
                .setAutoCancel(true)
                .build()
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Envío de WhatsApp", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun parse(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { v -> v.isNotBlank() } }
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        const val KEY_RECIPIENTS = "recipients"
        const val KEY_TEXT = "text"
        const val KEY_SENT = "sent"
        const val KEY_TOTAL = "total"
        private const val CHANNEL_ID = "difusion_wa"
        private const val NOTIF_ID = 4712
    }
}
