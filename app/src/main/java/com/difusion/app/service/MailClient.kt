package com.difusion.app.service

import android.content.Context
import android.util.Log
import com.difusion.app.storage.SmtpPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.SubjectTerm
import javax.mail.Authenticator
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import com.sun.mail.imap.IMAPFolder

/**
 * Envía y lee correo de Gmail por SMTP/IMAP usando la cuenta y la
 * "contraseña de aplicación" del usuario. No requiere Google Cloud.
 */
object MailClient {
    private const val TAG = "DIFUSION-MAIL"
    private const val SMTP_HOST = "smtp.gmail.com"
    private const val SMTP_PORT = "465"
    private const val IMAP_HOST = "imap.gmail.com"
    private const val IMAP_PORT = "993"

    private fun baseProps(): Properties = Properties().apply {
        put("mail.smtp.host", SMTP_HOST)
        put("mail.smtp.port", SMTP_PORT)
        put("mail.smtp.auth", "true")
        put("mail.smtp.ssl.enable", "true")
        put("mail.smtp.connectiontimeout", "15000")
        put("mail.smtp.timeout", "25000")
        put("mail.imaps.host", IMAP_HOST)
        put("mail.imaps.port", IMAP_PORT)
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.connectiontimeout", "15000")
        put("mail.imaps.timeout", "25000")
        put("mail.mime.address.strict", "false")
    }

    private fun session(user: String, pass: String): Session =
        Session.getInstance(baseProps(), object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(user, pass)
        })

    fun isConfigured(context: Context) = SmtpPrefs.isConfigured(context)

    // --- Prueba de conexión ---
    fun testConnection(context: Context): Pair<Boolean, String> {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) return false to "Faltan el correo o la contraseña de aplicación"
        return try {
            val store = session(user, pass).getStore("imaps")
            store.connect(IMAP_HOST, user, pass)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val n = inbox.messageCount
            inbox.close(false)
            store.close()
            true to "Conectado ($n mensajes en la bandeja)"
        } catch (e: Exception) {
            Log.e(TAG, "testConnection: ${e.javaClass.name}: ${e.message}", e)
            false to (e.message ?: e.javaClass.simpleName)
        }
    }

    // --- Enviar ---
    fun send(context: Context, to: String, subject: String, body: String): Boolean =
        sendWithError(context, to, subject, body).first

    /** Devuelve (éxito, mensajeDeError). */
    fun sendWithError(context: Context, to: String, subject: String, body: String): Pair<Boolean, String> {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) return false to "Correo no configurado"
        val err = sendMessageOrError(user, pass, to, subject, body, null, null)
        return if (err == null) true to "" else false to err
    }

    data class BulkResult(val sent: Int, val failed: Int, val firstError: String)

    /**
     * Envía a muchos destinatarios reutilizando UNA sola conexión SMTP (mucho
     * más rápido que abrir conexión por cada correo). Llama a [onProgress] con
     * (enviados+fallidos, total) después de cada mensaje.
     */
    fun sendBulk(
        context: Context,
        recipients: List<String>,
        subject: String,
        body: String,
        onProgress: ((Int, Int) -> Unit)? = null,
        delayMs: Long = 0L
    ): BulkResult {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) {
            return BulkResult(0, recipients.size, "Correo no configurado")
        }
        if (recipients.isEmpty()) return BulkResult(0, 0, "")

        var sent = 0
        var failed = 0
        var firstError = ""
        val s = session(user, pass)
        val transport = s.getTransport("smtp")
        try {
            transport.connect(SMTP_HOST, SMTP_PORT.toInt(), user, pass)
            for (r in recipients) {
                try {
                    val msg = MimeMessage(s)
                    msg.setFrom(InternetAddress(user, "Difusión"))
                    val toAddrs = InternetAddress.parse(r, false).map { it as javax.mail.Address }.toTypedArray()
                    msg.setRecipients(Message.RecipientType.TO, toAddrs)
                    msg.setSubject(subject, "UTF-8")
                    msg.setText(body, "UTF-8")
                    msg.setSentDate(Date())
                    transport.sendMessage(msg, msg.allRecipients)
                    sent++
                } catch (e: Exception) {
                    failed++
                    val m = e.message ?: e.javaClass.simpleName
                    if (firstError.isBlank()) firstError = m
                    Log.e(TAG, "sendBulk item ($r): ${e.javaClass.name}: $m")
                }
                onProgress?.invoke(sent + failed, recipients.size)
                if (delayMs > 0) Thread.sleep(delayMs)
            }
        } catch (e: Exception) {
            // Falló la conexión: cuenta el resto como fallidos.
            val m = e.message ?: e.javaClass.simpleName
            if (firstError.isBlank()) firstError = m
            Log.e(TAG, "sendBulk connect: ${e.javaClass.name}: $m", e)
            failed += recipients.size - (sent + failed)
        } finally {
            runCatching { transport.close() }
        }
        return BulkResult(sent, failed, firstError)
    }

    private fun sendMessageOrError(
        user: String,
        pass: String,
        to: String,
        subject: String,
        body: String,
        inReplyTo: String?,
        references: String?
    ): String? = try {
        val s = session(user, pass)
        val msg = MimeMessage(s)
        msg.setFrom(InternetAddress(user, "Difusión"))
        val toAddrs = InternetAddress.parse(to, false).map { it as javax.mail.Address }.toTypedArray()
        msg.setRecipients(Message.RecipientType.TO, toAddrs)
        msg.setSubject(subject, "UTF-8")
        msg.setText(body, "UTF-8")
        msg.setSentDate(Date())
        if (!inReplyTo.isNullOrBlank()) msg.setHeader("In-Reply-To", inReplyTo)
        if (!references.isNullOrBlank()) msg.setHeader("References", references)
        Transport.send(msg)
        null
    } catch (e: Exception) {
        Log.e(TAG, "send: ${e.javaClass.name}: ${e.message}", e)
        e.message ?: e.javaClass.simpleName
    }

    fun listInbox(context: Context, max: Int = 15): EmailSyncService.InboxResult =
        listFolder(context, "inbox", max)

    /** Carpetas: inbox, sent, spam, trash. */
    private fun resolveFolder(store: Store, key: String): Folder? = try {
        when (key) {
            "sent" -> special(store, "\\Sent") ?: store.getFolder("[Gmail]/Sent Mail")
            "spam" -> special(store, "\\Junk") ?: store.getFolder("[Gmail]/Spam")
            "trash" -> special(store, "\\Trash") ?: store.getFolder("[Gmail]/Trash")
            "all" -> special(store, "\\All") ?: store.getFolder("[Gmail]/All Mail")
            else -> store.getFolder("INBOX")
        }
    } catch (_: Exception) {
        null
    }

    private fun special(store: Store, attr: String): Folder? {
        val def = store.defaultFolder ?: return null
        val top = def.list("*") ?: return null
        for (f in top) {
            val attrs = (f as? IMAPFolder)?.attributes
            if (attrs != null && attrs.any { it.equals(attr, ignoreCase = true) }) return f
        }
        for (f in top) {
            val sub = runCatching { f.list("*") }.getOrNull() ?: continue
            for (g in sub) {
                val attrs = (g as? IMAPFolder)?.attributes
                if (attrs != null && attrs.any { it.equals(attr, ignoreCase = true) }) return g
            }
        }
        return null
    }

    private val shortFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    private fun fmtShort(d: Date?): String =
        if (d == null) "" else runCatching { shortFmt.format(d) }.getOrDefault("")

    private fun summaryOf(m: Message, uid: Long): EmailSyncService.MailSummary =
        EmailSyncService.MailSummary(
            threadId = uid.toString(),
            from = runCatching { firstAddress(m.from) }.getOrDefault(""),
            subject = runCatching { m.subject ?: "" }.getOrDefault(""),
            snippet = "",
            date = runCatching { fmtShort(m.receivedDate ?: m.sentDate) }.getOrDefault(""),
            unread = runCatching { !m.isSet(Flags.Flag.SEEN) }.getOrDefault(false),
            messageCount = 1
        )

    /** Búsqueda por remitente/asunto en la carpeta (búsqueda del servidor IMAP). */
    fun search(context: Context, key: String, query: String, max: Int = 30): EmailSyncService.InboxResult {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) {
            return EmailSyncService.InboxResult(false, error = "Correo no configurado")
        }
        val q = query.trim()
        if (q.isBlank()) return listFolder(context, key, max)
        return try {
            val store = session(user, pass).getStore("imaps")
            store.connect(IMAP_HOST, user, pass)
            val folder = resolveFolder(store, key) ?: run {
                store.close()
                return EmailSyncService.InboxResult(false, error = "No encontré la carpeta")
            }
            folder.open(Folder.READ_ONLY)
            val term = OrTerm(FromStringTerm(q), SubjectTerm(q))
            val found = runCatching { folder.search(term) }.getOrDefault(emptyArray())
            val msgs = if (found.size > max) found.copyOfRange(found.size - max, found.size) else found
            if (msgs.isNotEmpty()) {
                val fp = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.FLAGS)
                    add(FetchProfile.Item.CONTENT_INFO)
                }
                runCatching { folder.fetch(msgs, fp) }
            }
            val uidFolder = folder as? UIDFolder
            val out = ArrayList<EmailSyncService.MailSummary>()
            for (m in msgs.reversed()) {
                val uid = runCatching { uidFolder?.getUID(m) }.getOrNull() ?: 0L
                out.add(summaryOf(m, uid))
            }
            folder.close(false)
            store.close()
            EmailSyncService.InboxResult(true, out)
        } catch (e: Exception) {
            Log.e(TAG, "search($key,'$q'): ${e.javaClass.name}: ${e.message}", e)
            EmailSyncService.InboxResult(false, error = e.message ?: e.javaClass.simpleName)
        }
    }

    fun listFolder(context: Context, key: String, max: Int = 15): EmailSyncService.InboxResult {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) {
            return EmailSyncService.InboxResult(false, error = "Correo no configurado")
        }
        return try {
            val store = session(user, pass).getStore("imaps")
            store.connect(IMAP_HOST, user, pass)
            val folder = resolveFolder(store, key) ?: run {
                store.close()
                return EmailSyncService.InboxResult(false, error = "No encontré la carpeta")
            }
            folder.open(Folder.READ_ONLY)
            val count = folder.messageCount
            val out = ArrayList<EmailSyncService.MailSummary>()
            if (count > 0) {
                val start = maxOf(1, count - max + 1)
                val msgs = folder.getMessages(start, count)
                // Trae SOLO lo necesario (sobre, banderas y tamaño). NO descarga
                // los cuerpos: por eso la bandeja carga rápido.
                val fp = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.FLAGS)
                    add(FetchProfile.Item.CONTENT_INFO)
                }
                runCatching { folder.fetch(msgs, fp) }
                val uidFolder = folder as? UIDFolder
                for (idx in msgs.indices.reversed()) {
                    val m = msgs[idx]
                    val uid = runCatching { uidFolder?.getUID(m) }.getOrNull() ?: (start + idx).toLong()
                    out.add(summaryOf(m, uid))
                }
            }
            folder.close(false)
            store.close()
            EmailSyncService.InboxResult(true, out)
        } catch (e: Exception) {
            Log.e(TAG, "listFolder($key): ${e.javaClass.name}: ${e.message}", e)
            EmailSyncService.InboxResult(false, error = e.message ?: e.javaClass.simpleName)
        }
    }

    // --- Leer un mensaje ---
    fun readMessage(context: Context, key: String, uid: String): EmailSyncService.MailThread? {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) return null
        return try {
            val id = uid.toLongOrNull() ?: return null
            val store = session(user, pass).getStore("imaps")
            store.connect(IMAP_HOST, user, pass)
            val inbox = resolveFolder(store, key) ?: run { store.close(); return null }
            runCatching { inbox.open(Folder.READ_WRITE) }.onFailure { inbox.open(Folder.READ_ONLY) }
            val uidFolder = inbox as? UIDFolder ?: run {
                inbox.close(false); store.close(); return null
            }
            val m = uidFolder.getMessageByUID(id) ?: run {
                inbox.close(false); store.close(); return null
            }
            val result = EmailSyncService.MailThread(
                subject = m.subject ?: "",
                messages = listOf(
                    EmailSyncService.MailMessage(
                        from = firstAddress(m.from),
                        to = firstAddress(m.getRecipients(Message.RecipientType.TO)),
                        date = (m.receivedDate ?: m.sentDate)?.toString() ?: "",
                        body = textFrom(m).trim(),
                        isFromMe = false
                    )
                )
            )
            m.setFlag(Flags.Flag.SEEN, true)
            inbox.close(true)
            store.close()
            result
        } catch (e: Exception) {
            Log.e(TAG, "readMessage: ${e.javaClass.name}: ${e.message}", e)
            null
        }
    }

    // --- Responder ---
    fun reply(context: Context, key: String, uid: String, body: String): Boolean {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) return false
        return try {
            val id = uid.toLongOrNull() ?: return false
            val store = session(user, pass).getStore("imaps")
            store.connect(IMAP_HOST, user, pass)
            val inbox = resolveFolder(store, key) ?: run { store.close(); return false }
            inbox.open(Folder.READ_ONLY)
            val uidFolder = inbox as? UIDFolder ?: run {
                inbox.close(false); store.close(); return false
            }
            val m = uidFolder.getMessageByUID(id) ?: run {
                inbox.close(false); store.close(); return false
            }
            val to = firstAddress(m.from)
            var subject = m.subject ?: ""
            if (!subject.startsWith("Re:", ignoreCase = true)) subject = "Re: $subject"
            val messageId = m.getHeader("Message-ID")?.firstOrNull()
            inbox.close(false)
            store.close()
            sendMessageOrError(user, pass, to, subject, body, messageId, messageId) == null
        } catch (e: Exception) {
            Log.e(TAG, "reply: ${e.javaClass.name}: ${e.message}", e)
            false
        }
    }

    private fun firstAddress(addrs: Array<javax.mail.Address>?): String {
        val a = addrs?.firstOrNull() ?: return ""
        return (a as? InternetAddress)?.address ?: a.toString()
    }

    private fun textFrom(part: Part): String {
        return try {
            if (part.isMimeType("text/plain")) {
                part.content?.toString() ?: ""
            } else if (part.isMimeType("text/html")) {
                stripHtml(part.content?.toString() ?: "")
            } else if (part.isMimeType("multipart/*")) {
                val mp = part.content as? Multipart ?: return ""
                var html = ""
                for (i in 0 until mp.count) {
                    val p = mp.getBodyPart(i)
                    if (p.isMimeType("text/plain")) {
                        val t = p.content?.toString() ?: ""
                        if (t.isNotBlank()) return t
                    } else if (p.isMimeType("text/html")) {
                        html = stripHtml(p.content?.toString() ?: "")
                    } else if (p.isMimeType("multipart/*")) {
                        val t = textFrom(p)
                        if (t.isNotBlank()) return t
                    }
                }
                html
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
}
