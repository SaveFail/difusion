package com.masstext.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.masstext.app.MainActivity
import com.masstext.app.R

// Notificación permanente con el avance de la secuencia de llamadas
// consecutivas. Tocar el cuerpo reabre la app en la lista de la secuencia;
// las acciones Pausar/Reanudar y Detener se envían a SequenceActionReceiver.
object SequenceNotification {

    const val NOTIFICATION_ID = 1002
    const val CHANNEL_ID = "sequence"
    const val ACTION_PAUSE = "com.masstext.app.SEQ_PAUSE"
    const val ACTION_RESUME = "com.masstext.app.SEQ_RESUME"
    const val ACTION_STOP = "com.masstext.app.SEQ_STOP"
    const val EXTRA_OPEN_SEQUENCE = "open_sequence"

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = notificationManager(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Secuencia de llamadas",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Progreso de las llamadas consecutivas"
        nm.createNotificationChannel(channel)
    }

    fun update(
        context: Context,
        current: Int,
        total: Int,
        text: String,
        name: String? = null,
        paused: Boolean = false
    ) {
        ensureChannel(context)
        val nm = notificationManager(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_SEQUENCE, true)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val pauseLabel = if (paused) "Reanudar" else "Pausar"
        val pauseIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, SequenceActionReceiver::class.java).setAction(pauseAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, SequenceActionReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullText = if (name.isNullOrBlank()) text else "$name\n$text"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notif)
            .setContentTitle("Llamadas consecutivas · $current de $total")
            .setContentText(if (name.isNullOrBlank()) text else "$text · $name")
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, pauseLabel, pauseIntent)
            .addAction(0, "Detener", stopIntent)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        notificationManager(context).cancel(NOTIFICATION_ID)
    }
}