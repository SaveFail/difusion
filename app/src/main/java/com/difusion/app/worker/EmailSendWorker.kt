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
import com.difusion.app.service.EmailSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Envía correos masivos en SEGUNDO PLANO con una notificación de progreso,
 * igual que el envío de SMS. Muestra "N de M".
 */
class EmailSendWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recipients = parseRecipients(inputData.getString(KEY_RECIPIENTS) ?: "[]")
        val subject = inputData.getString(KEY_SUBJECT) ?: ""
        val body = inputData.getString(KEY_BODY) ?: ""
        if (recipients.isEmpty()) return@withContext Result.failure()

        runCatching { setForegroundAsync(foreground(0, recipients.size)) }

        val result = EmailSyncService.sendSmart(applicationContext, recipients, subject, body) { sent, total ->
            runCatching {
                setProgressAsync(workDataOf(KEY_SENT to sent, KEY_TOTAL to total))
                setForegroundAsync(foreground(sent, total))
            }
        }

        finishNotification(result.sent, result.failed)
        Result.success(
            workDataOf(
                KEY_SENT to result.sent,
                KEY_TOTAL to result.total,
                "failed" to result.failed,
                "error" to result.error
            )
        )
    }

    private fun foreground(sent: Int, total: Int): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Enviando correos")
            .setContentText("$sent de $total")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), sent, false)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun finishNotification(sent: Int, failed: Int) {
        ensureChannel()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Envío de correos terminado")
            .setContentText("Enviados: $sent · Fallidos: $failed")
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Envío de correos", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun parseRecipients(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { v -> v.isNotBlank() } }
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        const val KEY_RECIPIENTS = "recipients"
        const val KEY_SUBJECT = "subject"
        const val KEY_BODY = "body"
        const val KEY_SENT = "sent"
        const val KEY_TOTAL = "total"
        private const val CHANNEL_ID = "difusion_email"
        private const val NOTIF_ID = 4711
    }
}
