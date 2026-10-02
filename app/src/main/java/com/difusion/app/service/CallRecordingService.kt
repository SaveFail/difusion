package com.difusion.app.service

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
import androidx.core.app.NotificationCompat
import com.difusion.app.R
import java.util.concurrent.Executors

// Servicio en primer plano de tipo "microphone": es quien legalmente posee el
// micrófono mientras se graba una llamada en segundo plano (requisito desde
// Android 9+). De él depende CallRecorder para iniciar/detener la grabación.
class CallRecordingService : Service() {

    companion object {
        const val ACTION_START = "com.difusion.app.RECORDING_START"
        const val ACTION_STOP = "com.difusion.app.RECORDING_STOP"
        const val EXTRA_NUMBER = "number"
        const val NOTIF_ID = 9001
        const val CHANNEL_ID = "call_recording"
        const val TAG = "CallRecordingService"
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val number = intent.getStringExtra(EXTRA_NUMBER) ?: "Desconocido"
                // startForeground() SIEMPRE primero y garantizado: si el tipo
                // "microphone" o la notificación fallan, se usa respaldo mínimo
                // para NUNCA disparar ForegroundServiceDidNotStartInTimeException
                // (que el sistema resuelve matando todo el proceso).
                if (!startForegroundWithMic(number)) {
                    startForegroundGuaranteed(number)
                }
                executor.execute {
                    try {
                        CallRecorder.start(applicationContext, number)
                    } catch (t: Throwable) {
                        android.util.Log.e(TAG, "start() falló; el FGS ya está arriba", t)
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                try {
                    executor.execute { CallRecorder.stop() }
                } catch (_: Throwable) {
                }
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        executor.execute { CallRecorder.stop() }
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithMic(number: String): Boolean {
        return try {
            ensureChannel(this)
            val notif = buildNotification(number)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
            true
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "startForeground con micrófono falló", t)
            false
        }
    }

    // Respaldo mínimo garantizado: se llama con el startForeground(true) clásico
    // (sin tipo), que nunca lanza, para cumplir el contrato "startForeground()
    // dentro de N segundos" incluso si la notificación completa falló.
    private fun startForegroundGuaranteed(number: String) {
        try {
            val notif = buildNotification(number)
            startForeground(NOTIF_ID, notif)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Notificación falló; usando notificación mínima", t)
            val channelId = CHANNEL_ID
            val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(this, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Grabando llamada")
                    .setContentText(number)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Grabando llamada")
                    .setContentText(number)
                    .build()
            }
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(number: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CallRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Grabando llamada… N.º $number")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Detener grabación", stopIntent)
            .build()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Grabación de llamadas",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Informa mientras se graba una llamada"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}