package com.masstext.app.service

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.masstext.app.data.AppDatabase
import com.masstext.app.data.SmsMessage
import com.masstext.app.data.SmsStatus
import com.masstext.app.ui.theme.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Lee los MMS del sistema (el dispositivo los descarga automáticamente del
// centro MMSC del operador) desde content://mms y los respalda en la base
// local: texto, remitente y adjuntos (imagen/audio/video) copiados a
// almacenamiento privado. Los enviados (msg_box=2) también se importan para
// tener el historial completo.
object MmsInbox {

    suspend fun syncNow(context: Context, db: AppDatabase): Int = withContext(Dispatchers.IO) {
        var imported = 0
        val freshIncoming = mutableListOf<Pair<String, String>>()
        val now = System.currentTimeMillis()
        try {
            val addresses = threadAddresses(context)
            val mmsUri = Uri.parse("content://mms")
            context.contentResolver.query(
                mmsUri,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.SUBJECT,
                    Telephony.Mms.READ
                ),
                null,
                null,
                "${Telephony.Mms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val mmsId = cursor.getLong(0)
                    // Borrados de forma definitiva o ya importados: se saltan.
                    if (db.smsMessageDao().isPurged(mmsId, true) > 0) continue
                    if (db.smsMessageDao().byProviderId(mmsId, true) != null) continue

                    val threadId = cursor.getLong(1)
                    val address = addresses[threadId] ?: continue
                    // 1 = INBOX (entrante), 2 = SENT (enviado), otros (borradores, fallidos) se omiten.
                    val msgBox = cursor.getInt(3)
                    if (msgBox != Telephony.Mms.MESSAGE_BOX_INBOX && msgBox != Telephony.Mms.MESSAGE_BOX_SENT) continue
                    val date = cursor.getLong(2)
                    val read = cursor.getInt(5) == 1
                    val subject = cursor.getString(4)

                    val parts = readParts(context, mmsId)
                    val body = parts.joinToString("\n") { it.text.orEmpty() }
                    val media = parts.firstOrNull { it.mediaPath != null }

                    db.smsMessageDao().insert(
                        SmsMessage(
                            providerId = mmsId,
                            threadId = threadId,
                            address = address,
                            body = body,
                            date = date,
                            isIncoming = msgBox == Telephony.Mms.MESSAGE_BOX_INBOX,
                            status = SmsStatus.SENT,
                            read = read,
                            isMms = true,
                            mediaPath = media?.mediaPath,
                            mediaMime = media?.mime,
                            subject = subject?.takeIf { it.isNotBlank() }
                        )
                    )
                    imported++
                    if (msgBox == Telephony.Mms.MESSAGE_BOX_INBOX && date >= now - 60_000L) {
                        val preview = body.ifBlank { "Mensaje multimedia (${media?.mime ?: "MMS"})" }
                        freshIncoming += address to preview
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

    // Mapa thread_id -> número remitente/receptor a partir de las direcciones
    // canónicas del proveedor (URI no pública en este nivel de API).
    private fun threadAddresses(context: Context): Map<Long, String> {
        val result = HashMap<Long, String>()
        runCatching {
            val uri = Uri.parse("content://mms-sms/canonical-addresses")
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idCol = c.getColumnIndex("_id")
                val addrCol = c.getColumnIndex("address")
                while (c.moveToNext()) {
                    if (idCol < 0 || addrCol < 0) break
                    val id = c.getLong(idCol)
                    val addr = c.getString(addrCol)
                    if (addr.isNullOrBlank()) continue
                    result[id] = addr
                }
            }
        }
        return result
    }

    private data class Part(
        val text: String? = null,
        val mediaPath: String? = null,
        val mime: String? = null
    )

    private fun readParts(context: Context, mmsId: Long): List<Part> {
        val parts = mutableListOf<Part>()
        runCatching {
            val project = arrayOf("_id", "ct", "text", "_data", "name", "mime")
            val uri = Uri.parse("content://mms/part")
            context.contentResolver.query(
                uri, project, "mid = ?", arrayOf(mmsId.toString()), "seq ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val ct = c.getString(1)?.lowercase() ?: ""
                    val text = c.getString(2)
                    val dataPath = c.getString(3)
                    val name = c.getString(4)
                    if (ct.startsWith("text/")) {
                        val body = text?.takeIf { it.isNotBlank() } ?: "application/smil".takeIf { ct == "application/smil" && false }
                        if (!body.isNullOrEmpty()) parts += Part(text = body)
                        continue
                    }
                    // Adjunto multimedia: se copia a almacenamiento privado.
                    if (dataPath != null && dataPath.isNotEmpty()) {
                        val f = File(dataPath)
                        if (f.exists()) {
                            val ext = extFor(ct, name)
                            val dir = File(context.filesDir, "mms").apply { mkdirs() }
                            val target = File(dir, "${mmsId}_$id$ext")
                            runCatching {
                                f.copyTo(target, overwrite = true)
                                parts += Part(mediaPath = target.absolutePath, mime = ct)
                            }
                        }
                    } else if (text != null && text.length > 200) {
                        // Algunas versiones guardan el adjunto como texto base64.
                        parts += Part(mediaPath = null, mime = ct)
                    }
                }
            }
        }
        return parts
    }

    private fun extFor(mime: String, name: String?): String {
        if (!name.isNullOrBlank() && name.contains(".")) {
            val ext = name.substringAfterLast(".").trim()
            if (ext.length in 1..6 && ext.all { it.isLetterOrDigit() }) return ".${ext.lowercase()}"
        }
        return when {
            mime.startsWith("image/jpeg") -> ".jpg"
            mime.startsWith("image/png") -> ".png"
            mime.startsWith("image/gif") -> ".gif"
            mime.startsWith("image/") -> ".img"
            mime.startsWith("audio/mp4") || mime == "audio/aac" || mime.contains("m4a") -> ".m4a"
            mime.startsWith("audio/mpeg") -> ".mp3"
            mime.startsWith("audio/amr") -> ".amr"
            mime.startsWith("audio/") -> ".audio"
            mime.startsWith("video/mp4") -> ".mp4"
            mime.startsWith("video/") -> ".video"
            else -> ".bin"
        }
    }
}