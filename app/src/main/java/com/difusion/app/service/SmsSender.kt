package com.difusion.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.telephony.SmsManager
import com.difusion.app.data.AppDatabase
import com.difusion.app.data.Contact
import com.difusion.app.data.SmsMessage
import com.difusion.app.data.SmsStatus
import com.difusion.app.data.ThreadResolver
import com.difusion.app.import.Importer
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

// Motor de envío masivo de SMS. Envía un lote de mensajes personalizados a una
// lista de contactos respetando un retardo entre mensajes, con la protección
// anti-bloqueo: si la plataforma empieza a rechazar en cadena (10-20 fallos
// seguidos) pausa el envío y notifica al cliente para que revise el número o
// el plan antes de seguir gastando SMS.
class SmsSender(private val context: Context) {

    private
    val db = AppDatabase.getInstance(context)

    var isCancelled = false
        private set

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _sentCount = MutableStateFlow(0)
    val sentCount: StateFlow<Int> = _sentCount.asStateFlow()

    private val _failedCount = MutableStateFlow(0)
    val failedCount: StateFlow<Int> = _failedCount.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // ---- Ventana de envío (ritmo) ----
    // Objetivo aleatorio de mensajes para el bloque actual (0 = sin bloque activo).
    private val _windowTarget = MutableStateFlow(0)
    val windowTarget: StateFlow<Int> = _windowTarget.asStateFlow()

    // Mensajes intentados dentro del bloque actual.
    private val _windowSent = MutableStateFlow(0)
    val windowSent: StateFlow<Int> = _windowSent.asStateFlow()

    // Momento (SystemClock.elapsedRealtime) en que termina el bloque de 5 minutos
    // y se reanuda el envío. 0 = sin bloque activo.
    private val _nextWindowAtMs = MutableStateFlow(0L)
    val nextWindowAtMs: StateFlow<Long> = _nextWindowAtMs.asStateFlow()

    // true mientras se está esperando a que termine el bloque de 5 minutos.
    private val _waitingWindow = MutableStateFlow(false)
    val waitingWindow: StateFlow<Boolean> = _waitingWindow.asStateFlow()

    // SmsManager de la SIM elegida (o la predeterminada si subId es null).
    private fun smsManagerFor(subId: Int?): SmsManager =
        if (subId != null) {
            runCatching { SmsManager.getSmsManagerForSubscriptionId(subId) }
                .getOrDefault(SmsManager.getDefault())
        } else {
            SmsManager.getDefault()
        }

    fun cancel() {
        isCancelled = true
    }

    fun reset() {
        isCancelled = false
        _progress.value = 0
        _sentCount.value = 0
        _failedCount.value = 0
        _paused.value = false
        _windowTarget.value = 0
        _windowSent.value = 0
        _nextWindowAtMs.value = 0L
        _waitingWindow.value = false
    }

    // Envía un solo mensaje (usado desde la bandeja de mensajería).
    suspend fun sendSingle(phone: String, message: String, subId: Int? = null) {
        val normalized = Importer.normalizePhone(phone)
        try {
            sendOne(normalized, message, subId)
            markSmsSentByPhone(normalized)
        } catch (_: Exception) {
        }
    }

    // Reenvía un mensaje que falló: vuelve a marcarlo como "enviando" y reutiliza
    // la misma fila para que el resultado (Enviado/No enviado) se refleje ahí,
    // limpiando la causa del fallo anterior.
    suspend fun resend(phone: String, message: String, rowId: Long, subId: Int? = null) {
        val normalized = Importer.normalizePhone(phone)
        db.smsMessageDao().setStatusAndClearError(rowId, SmsStatus.SENDING)
        try {
            sendOne(normalized, message, subId)
        } catch (e: Exception) {
            db.smsMessageDao().setStatus(rowId, SmsStatus.FAILED)
            throw e
        }
    }

    // Envía un lote de mensajes masivos.
    //  - safeMode = true  (MODO SEGURO): envía por bloques de 70-90 mensajes y,
    //    al enviar el ÚLTIMO mensaje de cada bloque, arranca el contador de 5
    //    minutos antes de seguir con el siguiente bloque.
    //  - safeMode = false (MODO DESATENDIDO): envía todo consecutivo, sin bloques
    //    ni contador (solo respeta la pausa entre mensajes configurada).
    suspend fun sendBatch(
        contacts: List<Contact>,
        template: String,
        delayBetweenMessages: Long,
        safeMode: Boolean = true,
        subId: Int? = null
    ): Result {
        if (_isRunning.value) return Result(0, 0, false, 0L, 0L, false)
        _isRunning.value = true
        reset()
        try {
            var sent = 0
            var failed = 0
            var firstRowId = 0L
            var lastRowId = 0L
            var consecutiveFailures = 0
            var paused = false

            // Bloque de envío (solo en MODO SEGURO): se mandan entre
            // MIN_PER_WINDOW y MAX_PER_WINDOW mensajes con un objetivo aleatorio.
            var windowTarget = 0
            var windowSent = 0
            if (safeMode) {
                windowTarget = Random.nextInt(MIN_PER_WINDOW, MAX_PER_WINDOW + 1)
            }
            _windowTarget.value = windowTarget
            _windowSent.value = 0
            // El contador NO arranca al empezar el bloque, sino al enviar el
            // ÚLTIMO mensaje del bloque.
            _nextWindowAtMs.value = 0L

            for ((index, contact) in contacts.withIndex()) {
                if (isCancelled) break
                val phone = Importer.normalizePhone(contact.phone)
                val message = template
                    .replace("{nombre}", contact.name)
                    .replace("{telefono}", phone)
                    .replace("{name}", contact.name)
                    .replace("{phone}", phone)
                val success = try {
                    val rowId = sendOne(phone, message, subId)
                    if (firstRowId == 0L) firstRowId = rowId
                    lastRowId = rowId
                    true
                } catch (e: Exception) {
                    false
                }
                if (success) {
                    sent++
                    consecutiveFailures = 0
                    markSmsSent(contact)
                } else {
                    failed++
                    consecutiveFailures++
                }
                _sentCount.value = sent
                _failedCount.value = failed
                _progress.value = index + 1
                // Protección anti-bloqueo: si la plataforma empieza a rechazar en
                // cadena (10-20 fallos seguidos), pausa el envío y avisa al cliente
                // para que revise el número o el plan antes de seguir gastando SMS.
                if (consecutiveFailures >= pauseAfterConsecutiveFailures) {
                    paused = true
                    _paused.value = true
                    notifyPaused(context, consecutiveFailures)
                    break
                }

                if (safeMode) {
                    windowSent++
                    _windowSent.value = windowSent
                    if (windowSent >= windowTarget) {
                        // Último mensaje del bloque enviado: AQUÍ arranca el
                        // contador y el descanso de 5 minutos COMPLETOS.
                        val windowEnd = SystemClock.elapsedRealtime() + WINDOW_MS
                        _nextWindowAtMs.value = windowEnd
                        _waitingWindow.value = true
                        while (SystemClock.elapsedRealtime() < windowEnd && !isCancelled) {
                            delay(500)
                        }
                        _waitingWindow.value = false
                        if (isCancelled) break
                        windowTarget = Random.nextInt(MIN_PER_WINDOW, MAX_PER_WINDOW + 1)
                        windowSent = 0
                        _windowTarget.value = windowTarget
                        _windowSent.value = 0
                        // El próximo contador arrancará con el último del próximo bloque.
                        _nextWindowAtMs.value = 0L
                    }
                }

                if (index < contacts.size - 1 && !isCancelled) {
                    delay(delayBetweenMessages)
                }
            }
            return Result(sent, failed, isCancelled, firstRowId, lastRowId, paused)
        } finally {
            _waitingWindow.value = false
            _nextWindowAtMs.value = 0L
            _isRunning.value = false
        }
    }

    private fun nowStamp(): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

    // Al enviar un SMS se marca el contacto: medio = SMS y seguimiento
    // "Enviado por SMS", con la fecha de gestión actualizada.
    private suspend fun markSmsSent(contact: Contact) {
        try {
            val fresh = db.contactDao().getById(contact.id) ?: contact
            val updated = fresh.copy(
                medio = "SMS",
                gestion = "Enviado por SMS",
                fechaGestion = nowStamp()
            )
            db.contactDao().update(updated)
            DriveSyncService.enqueue(context, updated)
        } catch (_: Exception) {
        }
    }

    private suspend fun markSmsSentByPhone(phone: String) {
        try {
            val key = phone.filter { it.isDigit() }.takeLast(10)
            if (key.isBlank()) return
            val found = db.contactDao().getAllOnce().firstOrNull {
                it.phone.filter { c -> c.isDigit() }.takeLast(10) == key
            } ?: return
            markSmsSent(found)
        } catch (_: Exception) {
        }
    }

    // Guarda el mensaje en la base local (respaldo), marca el estado como "enviando",
    // y registra un PendingIntent que el sistema ejecuta al confirmar el envío.
    private suspend fun sendOne(phone: String, message: String, subId: Int? = null): Long {
        val smsManager = smsManagerFor(subId)
        val parts = smsManager.divideMessage(message)
        val threadId = ThreadResolver.resolve(context, phone)
        val rowId = db.smsMessageDao().insert(
            SmsMessage(
                threadId = threadId,
                address = phone,
                body = message,
                date = System.currentTimeMillis(),
                isIncoming = false,
                status = SmsStatus.SENDING,
                read = true
            )
        )
        val sentPi = sentPendingIntent(rowId)
        try {
            if (parts.size > 1) {
                val pendingIntents = ArrayList<PendingIntent>(parts.size)
                repeat(parts.size) { pendingIntents.add(sentPi) }
                smsManager.sendMultipartTextMessage(phone, null, parts, pendingIntents, null)
            } else {
                smsManager.sendTextMessage(phone, null, message, sentPi, null)
            }
        } catch (e: Exception) {
            db.smsMessageDao().setStatus(rowId, SmsStatus.FAILED)
            throw e
        }
        return rowId
    }

    private fun sentPendingIntent(rowId: Long): PendingIntent {
        val intent = Intent(ACTION_SENT)
            .setPackage(context.packageName)
            .putExtra(EXTRA_ROW_ID, rowId)
        return PendingIntent.getBroadcast(
            context,
            rowId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Crea el canal y publica la notificación de pausa al cliente.
    fun notifyPaused(context: Context, consecutiveFailures: Int) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Envío pausado")
            .setContentText("$consecutiveFailures mensajes no se enviaron en cadena. Revisa el número o el plan.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$consecutiveFailures mensajes no se enviaron en cadena. " +
                    "Para no gastar más SMS, el envío se pausó. Revisa el número destintatario " +
                    "o el saldo del plan antes de continuar."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(PAUSE_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Envío de SMS masivo",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(channel)
            }
        }
    }

    data class Result(
        val sent: Int,
        val failed: Int,
        val cancelled: Boolean,
        val firstRowId: Long,
        val lastRowId: Long,
        val paused: Boolean = false
    )

    companion object {
        const val ACTION_SENT = "com.difusion.app.SMS_SENT"
        const val EXTRA_ROW_ID = "row_id"
        const val CHANNEL_ID = "mensajes_difusion"
        const val PAUSE_NOTIFICATION_ID = 9087
        const val pauseAfterConsecutiveFailures = 15

        // Ritmo de envío masivo: por cada bloque de 5 minutos se envían entre 70
        // y 90 mensajes (cantidad aleatoria) y luego se espera a completar el
        // bloque antes de continuar con el siguiente.
        const val WINDOW_MS = 5 * 60_000L
        const val MIN_PER_WINDOW = 70
        const val MAX_PER_WINDOW = 90
    }
}
