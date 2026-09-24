package com.masstext.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.masstext.app.R
import com.masstext.app.data.AppDatabase
import com.masstext.app.data.SendRecord
import com.masstext.app.storage.RecordStore
import com.masstext.app.storage.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Envía el lote de mensajes masivos en segundo plano con notificación de progreso,
// de modo que el usuario pueda seguir usando la app (llamadas, contactos, etc.)
// sin que el envío se corte.
class SmsSendService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false
    private var notifierJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            val task = SmsBatchTask.take()
            val notification = buildNotification("Iniciando envío de mensajes")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            if (task == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            acquireWakeLock()
            val sender = SmsController.get(this)
            // Actualiza la notificación cada segundo con el conteo en vivo y la
            // cuenta regresiva del bloque de 5 minutos. Sigue funcionando aunque
            // la app esté en segundo plano (servicio en primer plano).
            notifierJob = scope.launch {
                while (true) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(buildStatusText(sender, task.contacts.size)))
                    delay(1000)
                }
            }
            scope.launch {
                try {
                    val result = sender.sendBatch(task.contacts, task.template, task.delayMs, task.safeMode)
                    runCatching {
                        val db = AppDatabase.getInstance(this@SmsSendService)
                        val breakdown = if (result.firstRowId > 0L && result.lastRowId >= result.firstRowId) {
                            db.smsMessageDao()
                                .failureBreakdown(result.firstRowId, result.lastRowId, com.masstext.app.data.SmsStatus.FAILED)
                                .joinToString("; ") { "${it.n}× ${it.label}" }
                        } else {
                            ""
                        }
                        val rec = SendRecord(
                            date = System.currentTimeMillis(),
                            total = task.contacts.size,
                            sent = result.sent,
                            failed = result.failed,
                            message = task.template,
                            user = UserStore.getUser(this@SmsSendService),
                            failureBreakdown = breakdown
                        )
                        db.sendDao().insert(rec)
                        RecordStore.appendSend(this@SmsSendService, rec)
                    }
                } finally {
                    notifierJob?.cancel()
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.masstext.app.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("LEX RECOVER")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }

    // Texto en vivo: mensajes enviados, avance del bloque y cuenta regresiva de
    // los 5 minutos. Se recalcula cada segundo desde el servicio.
    private fun buildStatusText(sender: SmsSender, total: Int): String {
        val n = sender.sentCount.value + sender.failedCount.value
        val target = sender.windowTarget.value
        val inWindow = sender.windowSent.value
        val nextAt = sender.nextWindowAtMs.value
        return buildString {
            append("Enviados: $n / $total")
            if (target > 0) append("  ·  Bloque: $inWindow / $target")
            if (nextAt > 0L) {
                val rem = (nextAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                append("  ·  Próxima ventana en ${formatClock(rem)}")
            }
        }
    }

    private fun formatClock(ms: Long): String {
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    // Mantiene la CPU despierta durante los bloques de espera de 5 minutos para
    // que la cuenta regresiva y el reanudado sean exactos aunque la pantalla
    // esté apagada.
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "masstext:sms_send").apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Envío de mensajes",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "sms_send_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, SmsSendService::class.java)
            context.startForegroundService(intent)
        }
    }
}