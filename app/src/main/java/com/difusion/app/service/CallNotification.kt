package com.difusion.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.difusion.app.R

/**
 * Notificación permanente de alta prioridad mientras haya una llamada en curso.
 * Siempre visible (no se puede deslizar) y al frente del panel de notificaciones;
 * al tocarla se vuelve a la pantalla de llamada. Incluye acciones de altavoz y
 * de colgar sin necesidad de abrir la app.
 */
object CallNotification {

    const val ACTION_END_CALL = "com.difusion.app.action.END_CALL"
    const val ACTION_TOGGLE_SPEAKER = "com.difusion.app.action.TOGGLE_SPEAKER"
    const val ACTION_TOGGLE_MUTE = "com.difusion.app.action.TOGGLE_MUTE"

    private const val CHANNEL_ID = "active_call"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Llamada en curso",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Te permite volver a la llamada en curso"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun update(context: Context) {
        val info = CallMonitor.info.value
        if (info == null) return cancel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)

        // Al tocar la notificación volvemos a la pantalla de llamada.
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, com.difusion.app.ui.CallActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun broadcast(action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, CallActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val speaker = CallMonitor.speaker.value
        val muted = CallMonitor.muted.value
        val isRinging = CallMonitor.isIncoming(CallMonitor.currentCall())
        val statusLine = buildString {
            append(info.state)
            if (muted) append(" · Micro silenciado")
            append(" · Toca para volver a la llamada")
        }

        // Pantalla completa para la llamada: abre la única pantalla de llamada
        // (Contestar / Buzón / Rechazar, o controles en curso), incluso con el
        // teléfono bloqueado.
        val incomingIntent = PendingIntent.getActivity(
            context,
            3,
            Intent(context, com.difusion.app.ui.CallActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notif)
            .setContentTitle(info.number)
            .setContentText(statusLine)
            .setContentIntent(if (isRinging) incomingIntent else contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_call_notif,
                if (speaker) "Altavoz: SÍ" else "Altavoz",
                broadcast(ACTION_TOGGLE_SPEAKER, 2)
            )
            .addAction(
                R.drawable.ic_call_notif,
                "Colgar",
                broadcast(ACTION_END_CALL, 1)
            )

        if (isRinging) {
            builder.setFullScreenIntent(incomingIntent, true)
        }

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}