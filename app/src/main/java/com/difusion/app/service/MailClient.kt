package com.difusion.app.service

import android.content.Context
import android.util.Log
import com.difusion.app.storage.SmtpPrefs
import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

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
    fun send(context: Context, to: String, subject: String, body: String): Boolean {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) return false
        return sendMessage(user, pass, to, subject, body, null, null)
    }

    private fun sendMessage(
        user: String,
        pass: String,
        to: String,
        subject: String,
        body: String,
        inReplyTo: String?,
        references: String?
    ): Boolean = try {
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
        true
    } catch (e: Exception) {
        Log.e(TAG, "send: ${e.javaClass.name}: ${e.message}", e)
        false
    }

    // --- Bandeja ---
    fun listInbox(context: Context, max: Int = 15): EmailSyncService.InboxResult {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) {
            return EmailSyncService.InboxResult(false, error = "Correo no configurado")
        }
        return try {
            val store = session(user, pass).getStore("imaps")
            store.connect(IMAP_HOST, user, pass)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val count = inbox.messageCount
            val uidFolder = inbox as? UIDFolder
            val out = ArrayList<EmailSyncService.MailSummary>()
            var taken = 0
            var i = count
            while (i >= 1 && taken < max) {
                val m = inbox.getMessage(i)
                val uid = uidFolder?.getUID(m) ?: i.toLong()
                out.add(
                    EmailSyncService.MailSummary(
                        threadId = uid.toString(),
                        from = firstAddress(m.from),
                        subject = m.subject ?: "",
                        snippet = textFrom(m).replace(Regex("\\s+"), " ").take(140),
                        date = (m.receivedDate ?: m.sentDate)?.toString() ?: "",
                        unread = !m.isSet(Flags.Flag.SEEN),
                        messageCount = 1
                    )
                )
                taken++
                i--
            }
            inbox.close(false)
            store.close()
            EmailSyncService.InboxResult(true, out)
        } catch (e: Exception) {
            Log.e(TAG, "listInbox: ${e.javaClass.name}: ${e.message}", e)
            EmailSyncService.InboxResult(false, error = e.message ?: e.javaClass.simpleName)
        }
    }

    // --- Leer un mensaje ---
    fun readMessage(context: Context, uid: String): EmailSyncService.MailThread? {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) return null
        return try {
            val id = uid.toLongOrNull() ?: return null
            val store = session(user, pass).getStore("imaps")
            store.connect(IMAP_HOST, user, pass)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
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
    fun reply(context: Context, uid: String, body: String): Boolean {
        val user = SmtpPrefs.getEmail(context)
        val pass = SmtpPrefs.getPassword(context)
        if (user.isBlank() || pass.isBlank()) return false
        return try {
            val id = uid.toLongOrNull() ?: return false
            val store = session(user, pass).getStore("imaps")
            store.connect(IMAP_HOST, user, pass)
            val inbox = store.getFolder("INBOX")
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
            sendMessage(user, pass, to, subject, body, messageId, messageId)
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
