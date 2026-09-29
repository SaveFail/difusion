package com.masstext.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.masstext.app.MainActivity
import com.masstext.app.R
import com.masstext.app.ui.theme.ThemeConfig

// Sonido y vibración que usa la app para avisar: al llegar un mensaje, al
// terminar un envío masivo y al terminar la secuencia de llamadas. El timbre
// se elige desde Apariencia entre los sonidos de notificación del sistema.
object AppFeedback {

    private const val CHANNEL_ID = "mensajes_masstext"
    private const val CHANNEL_NAME = "Mensajes entrantes y avisos"
    private const val NOTIFICATION_ID_BASE = 9100

    fun notificationUri(config: ThemeConfig): Uri? {
        val custom = runCatching { Uri.parse(config.notificationSound) }.getOrNull()
        return if (custom != null && !custom.toString().isBlank()) {
            custom
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    // Aviso simple: suena el timbre elegido (y vibra si está activado).
    fun notify(context: Context, config: ThemeConfig) {
        if (config.soundEnabled) {
            playTone(context, notificationUri(config))
        }
        if (config.vibrationEnabled) {
            vibrate(context)
        }
    }

    fun playTone(context: Context, uri: Uri?) {
        try {
            val tone = RingtoneManager.getRingtone(context, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            tone.play()
        } catch (_: Exception) {
        }
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // Notificación en la barra del sistema para un mensaje entrante, con el
    // timbre y la vibración elegidos. Si no hay permiso de notificación, avisa
    // igual con sonido y vibración directos.
    fun notifyIncoming(context: Context, config: ThemeConfig, sender: String, body: String) {
        ensureChannel(context, config)
        if (!canPostNotifications(context)) {
            notify(context, config)
            return
        }
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(if (sender.isBlank()) "Nuevo mensaje" else sender)
                .setContentText(body.ifBlank { "(Mensaje sin texto)" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
            val id = NOTIFICATION_ID_BASE + (sender.hashCode() and 0x7F)
            nm.notify(id, builder.build())
        } catch (_: Exception) {
            notify(context, config)
        }
    }

    // Crea/actualiza el canal con el timbre y la vibración actuales. Al
    // cambiarlos se vuelve a crear el canal para aplicar los ajustes.
    private fun ensureChannel(context: Context, config: ThemeConfig) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisa con el timbre elegido cuando llega un mensaje o se termina un proceso."
                if (config.soundEnabled) {
                    setSound(notificationUri(config), attrs)
                } else {
                    setSound(null, null)
                    enableVibration(config.vibrationEnabled)
                }
                if (config.vibrationEnabled) {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 180, 250)
                } else {
                    enableVibration(false)
                }
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        } catch (_: Exception) {
        }
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(350)
            }
        } catch (_: Exception) {
        }
    }
}

// Sonidos de notificación del sistema disponibles para elegir.
object NotificationTones {

    data class Tone(val title: String, val uri: Uri)

    fun list(context: Context): List<Tone> {
        val result = mutableListOf<Tone>()
        try {
            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_NOTIFICATION)
            }
            val cursor = manager.cursor
            try {
                while (cursor.moveToNext()) {
                    val uri = manager.getRingtoneUri(cursor.position) ?: continue
                    val title = listOf("title", "_display_name", "title_key")
                        .firstNotNullOfOrNull { col ->
                            val i = cursor.getColumnIndex(col)
                            if (i >= 0) i else null
                        }
                        ?.let { cursor.getString(it) }
                        ?: continue
                    result += Tone(title, uri)
                }
            } finally {
                try {
                    cursor.close()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return result
    }
}