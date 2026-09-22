package com.masstext.app.service

import android.content.Context
import android.provider.Telephony
import com.masstext.app.data.AppDatabase
import com.masstext.app.data.SmsMessage
import com.masstext.app.data.SmsStatus
import com.masstext.app.data.ThreadResolver
import com.masstext.app.ui.theme.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Lee la bandeja del sistema (content://sms/) y la respalda en la base local,
// de modo que los mensajes no se pierdan aunque cambie la app de SMS predeterminada.
object SmsInbox {

    suspend fun syncNow(context: Context, db: AppDatabase): Int = withContext(Dispatchers.IO) {
        var imported = 0
        val freshIncoming = mutableListOf<Pair<String, String>>()
        val now = System.currentTimeMillis()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val providerId = cursor.getLong(0)
                    val type = cursor.getInt(5)
                    if (type != Telephony.Sms.MESSAGE_TYPE_INBOX && type != Telephony.Sms.MESSAGE_TYPE_SENT) continue
                    // Mensajes borrados de forma definitiva (papelera vaciada o
                    // borrado permanente): no vuelven a importarse, aunque el
                    // proveedor de SMS todavía los tenga.
                    if (db.smsMessageDao().isPurged(providerId, false) > 0) continue
                    if (db.smsMessageDao().byProviderId(providerId, false) != null) continue
                    val threadId = cursor.getLong(1)
                    val address = cursor.getString(2) ?: ""
                    val body = cursor.getString(3) ?: ""
                    val date = cursor.getLong(4)
                    val read = cursor.getInt(6) == 1
                    db.smsMessageDao().insert(
                        SmsMessage(
                            providerId = providerId,
                            threadId = threadId,
                            address = address,
                            body = body,
                            date = date,
                            isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                            status = SmsStatus.SENT,
                            read = read
                        )
                    )
                    imported++
                    if (type == Telephony.Sms.MESSAGE_TYPE_INBOX && date >= now - 60_000L) {
                        freshIncoming += address to body
                    }
                }
            }
            if (freshIncoming.isNotEmpty()) {
                val config = ThemePrefs.read(context)
                freshIncoming.forEach { (address, body) ->
                    AppFeedback.notifyIncoming(context, config, address, body)
                }
            }
        } catch (_: Exception) {
        }
        imported
    }

    // Guarda un mensaje entrante (desde SMS_DELIVER) en la bandeja del sistema
    // y en la base local. Devuelve el id local.
    suspend fun persistIncoming(
        context: Context,
        db: AppDatabase,
        address: String,
        body: String,
        date: Long
    ): Long = withContext(Dispatchers.IO) {
        val threadId = ThreadResolver.resolve(context, address)
        var providerId: Long? = null
        try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, date)
                put(Telephony.Sms.DATE_SENT, date)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.THREAD_ID, threadId)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            providerId = uri?.lastPathSegment?.toLongOrNull()
        } catch (_: Exception) {
        }
        db.smsMessageDao().insert(
            SmsMessage(
                providerId = providerId,
                threadId = threadId,
                address = address,
                body = body,
                date = date,
                isIncoming = true,
                status = SmsStatus.SENT,
                read = false
            )
        )
    }
}