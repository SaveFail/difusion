package com.masstext.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.masstext.app.data.AppDatabase
import com.masstext.app.data.SmsMessage
import com.masstext.app.data.SmsStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// Envía MMS compaginando el mensaje (PDU multipart/related) y entregándolo al
// módem. Android no expone una API para enviar adjuntos "por campos", por eso
// el mensaje se arma como un mensaje MMS real: SMIL + texto + adjunto.
object MmsSender {

    const val ACTION_SENT = "com.masstext.app.MMS_SENT"
    const val EXTRA_ROW_ID = "row_id"

    suspend fun send(
        context: Context,
        phone: String,
        subject: String?,
        caption: String,
        mediaPath: String?,
        mediaMime: String?
    ): Long = withContext(Dispatchers.IO) {
        var rowId = -1L
        val db = AppDatabase.getInstance(context)
        try {
            val threadId = Telephony.Threads.getOrCreateThreadId(context, phone)
            val row = SmsMessage(
                threadId = threadId,
                address = phone,
                body = caption,
                date = System.currentTimeMillis(),
                isIncoming = false,
                status = SmsStatus.SENDING,
                read = true,
                isMms = true,
                mediaPath = mediaPath,
                mediaMime = mediaMime,
                subject = subject
            )
            rowId = db.smsMessageDao().insert(row)

            val mediaRef = mediaPath?.let { File(it) }
            val pdu = buildSendReqPdu(phone, subject, caption, mediaRef, mediaMime)

            val dir = File(context.cacheDir, "mms").apply { mkdirs() }
            val pduFile = File(dir, "mms_${rowId}.mms")
            FileOutputStream(pduFile, false).use { it.write(pdu) }

            val pduUri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", pduFile
            )

            val pi = PendingIntent.getBroadcast(
                context,
                rowId.toInt(),
                Intent(ACTION_SENT).putExtra(EXTRA_ROW_ID, rowId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val sms = SmsManager.getDefault()
            sms.sendMultimediaMessage(context, pduUri, null, null, pi)
            rowId
        } catch (_: Exception) {
            runCatching { db.smsMessageDao().setStatusAndMms(rowId, SmsStatus.FAILED, true) }
            -1L
        }
    }

    // Construye el PDU m-send-req (headers binarios + cuerpo multipart/related).
    private fun buildSendReqPdu(
        to: String,
        subject: String?,
        text: String,
        media: File?,
        mime: String?
    ): ByteArray {
        val boundary = "----=_mt_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val hasText = text.isNotBlank()
        val hasMedia = media != null && media.exists()

        // Partes del cuerpo: [0] SMIL siempre; [1] texto si hay; [2] adjunto si hay.
        val parts = ArrayList<Triple<String, String, ByteArray>>() // (contentType, contentId, data)
        parts.add(Triple("application/smil", "smil", buildSmil(hasText, hasMedia).toByteArray(Charsets.UTF_8)))
        if (hasText) parts.add(Triple("text/plain; charset=UTF-8", "text", text.toByteArray(Charsets.UTF_8)))
        if (hasMedia) {
            val ct = (mime ?: "application/octet-stream").trim()
            parts.add(Triple(ct, "image", media.readBytes()))
        }

        // --- Cuerpo multipart/related ---
        val body = ByteArrayOutputStream()
        parts.forEach { (ct, cid, data) ->
            body.writeBytesRaw("--$boundary\r\n")
            body.writeBytesRaw("Content-Type: $ct\r\n")
            body.writeBytesRaw("Content-ID: <$cid>\r\n")
            body.writeBytesRaw("Content-Location: $cid\r\n")
            body.writeBytesRaw("Content-Transfer-Encoding: binary\r\n\r\n")
            body.write(data, 0, data.size)
            body.writeBytesRaw("\r\n")
        }
        body.writeBytesRaw("--$boundary--\r\n")

        // --- Headers binarios del PDU ---
        val h = ByteArrayOutputStream()
        // Message-Type (0x0C) = m-send-req (0x80)
        h.write(0x0C); h.write(0x80)
        // Transaction-Id (0x1C)
        h.write(0x1C)
        val txid = UUID.randomUUID().toString().replace("-", "").take(8).toByteArray(Charsets.US_ASCII)
        h.writeUintvar(txid.size + 1); h.write(txid, 0, txid.size); h.write(0)
        // MMS-Version (0x0D) = 1.0 (0x10)
        h.write(0x0D); h.write(0x10)
        // Date (0x05) = longint epoch
        h.write(0x05)
        val epoch = System.currentTimeMillis() / 1000
        h.write(((epoch ushr 24) and 0xFF).toInt())
        h.write(((epoch ushr 16) and 0xFF).toInt())
        h.write(((epoch ushr 8) and 0xFF).toInt())
        h.write((epoch and 0xFF).toInt())
        // From (0x09) = insert-address-token (los mensajes se envían como "yo")
        h.write(0x09); h.write(0x81)
        // To (0x1B) = byte-address (0x85) + encoded-string con el número
        h.write(0x1B); h.write(0x85)
        val toBytes = to.toByteArray(Charsets.US_ASCII)
        h.writeUintvar(toBytes.size + 1); h.write(toBytes, 0, toBytes.size); h.write(0)
        // Subject (0x1A) opcional
        if (!subject.isNullOrBlank()) {
            h.write(0x1A)
            val s = subject.toByteArray(Charsets.UTF_8)
            h.writeUintvar(s.size + 1); h.write(s, 0, s.size); h.write(0)
        }
        // Content-Type (0x04): application/vnd.wap.multipart.related
        h.write(0x04)
        h.write(0x0A) // token multipart/related
        h.write(0x0A); h.writeQuoted("<smil>")            // start
        h.write(0x0B); h.writeBytesRaw("application/smil"); h.write(0) // type
        h.write(0x0C); h.writeQuoted(boundary)            // boundary

        val out = ByteArrayOutputStream()
        out.write(h.toByteArray())
        out.write(body.toByteArray())
        return out.toByteArray()
    }

    private fun buildSmil(hasText: Boolean, hasMedia: Boolean): String {
        val sb = StringBuilder()
        sb.append("<smil xmlns=\"http://www.w3.org/2001/SMIL20/Language\">\n")
        sb.append("<head><layout><root-layout/>\n")
        if (hasMedia) sb.append("<region id=\"Image\" left=\"0\" top=\"0\" width=\"100%\" height=\"60%\" fit=\"meet\"/>\n")
        if (hasText) sb.append("<region id=\"Text\" left=\"0\" top=\"60%\" width=\"100%\" height=\"40%\" fit=\"scroll\"/>\n")
        sb.append("</layout></head>\n")
        sb.append("<body><par dur=\"5000ms\">\n")
        if (hasMedia) sb.append("<img src=\"cid:image\" region=\"Image\"/>\n")
        if (hasText) sb.append("<text src=\"cid:text\" region=\"Text\"/>\n")
        sb.append("</par></body>\n")
        sb.append("</smil>\n")
        return sb.toString()
    }

    private fun ByteArrayOutputStream.writeBytesRaw(s: String) {
        val b = s.toByteArray(Charsets.ISO_8859_1)
        write(b, 0, b.size)
    }

    private fun ByteArrayOutputStream.writeUintvar(value: Int) {
        var v = value
        val tmp = IntArray(4)
        var i = 0
        tmp[i++] = v and 0x7F
        v = v shr 7
        while (v > 0) {
            tmp[i++] = (v and 0x7F) or 0x80
            v = v shr 7
        }
        while (i > 0) write(tmp[--i])
    }

    private fun ByteArrayOutputStream.writeQuoted(s: String) {
        write(0x22)
        val b = s.toByteArray(Charsets.US_ASCII)
        write(b, 0, b.size)
        write(0x22)
    }
}