package com.difusion.app.service

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Cliente directo de la API de Gmail (sin intermediarios). Usa el token de
 * acceso obtenido con [GmailAuth]. Devuelve los mismos tipos que el puente de
 * Apps Script para que la interfaz sea la misma.
 */
object GmailApiService {
    private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
    private const val JSON = "application/json; charset=utf-8"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun getJson(token: String, url: String): JSONObject? = try {
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string() ?: return null
            if (r.isSuccessful) JSONObject(s) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun postJson(token: String, url: String, body: JSONObject): JSONObject? = try {
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON.toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string() ?: return null
            if (r.isSuccessful) JSONObject(s) else null
        }
    } catch (_: Exception) {
        null
    }

    // --- Listar bandeja ---
    fun listInbox(token: String, max: Int = 15): EmailSyncService.InboxResult =
        listFolder(token, "INBOX", max)

    /** label: INBOX, SENT, SPAM, TRASH, ALL (todos los correos) */
    fun listFolder(token: String, label: String, max: Int = 15): EmailSyncService.InboxResult {
        val url = if (label == "ALL") {
            "$BASE/threads?maxResults=$max"
        } else {
            "$BASE/threads?maxResults=$max&labelIds=$label"
        }
        val list = getJson(token, url)
            ?: return EmailSyncService.InboxResult(false, error = "No se pudo leer la carpeta")
        val arr = list.optJSONArray("threads") ?: JSONArray()
        val out = ArrayList<EmailSyncService.MailSummary>()
        for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).optString("id")
            val snippet = arr.getJSONObject(i).optString("snippet")
            val t = getJson(
                token,
                "$BASE/threads/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date"
            ) ?: continue
            val msgs = t.optJSONArray("messages") ?: continue
            if (msgs.length() == 0) continue
            val first = msgs.getJSONObject(0)
            val headers = first.optJSONObject("payload")?.optJSONArray("headers")
            val from = header(headers, "From")
            val subject = header(headers, "Subject")
            val unread = (first.optJSONArray("labelIds")?.toString() ?: "").contains("UNREAD")
            out.add(
                EmailSyncService.MailSummary(
                    threadId = id,
                    from = from,
                    subject = subject,
                    snippet = snippet,
                    date = header(headers, "Date"),
                    unread = unread,
                    messageCount = msgs.length()
                )
            )
        }
        return EmailSyncService.InboxResult(true, out)
    }

    private fun header(headers: JSONArray?, name: String): String {
        if (headers == null) return ""
        for (i in 0 until headers.length()) {
            val h = headers.getJSONObject(i)
            if (h.optString("name").equals(name, ignoreCase = true)) return h.optString("value")
        }
        return ""
    }

    // --- Leer conversación ---
    fun readThread(token: String, threadId: String): EmailSyncService.MailThread? {
        val t = getJson(token, "$BASE/threads/$threadId?format=full") ?: return null
        val msgs = t.optJSONArray("messages") ?: return null
        val list = ArrayList<EmailSyncService.MailMessage>()
        var subject = ""
        for (i in 0 until msgs.length()) {
            val m = msgs.getJSONObject(i)
            val payload = m.optJSONObject("payload")
            val headers = payload?.optJSONArray("headers")
            if (subject.isBlank()) subject = header(headers, "Subject")
            list.add(
                EmailSyncService.MailMessage(
                    from = header(headers, "From"),
                    to = header(headers, "To"),
                    date = header(headers, "Date"),
                    body = extractBody(payload).trim(),
                    isFromMe = (m.optJSONArray("labelIds")?.toString() ?: "").contains("SENT")
                )
            )
        }
        // Marca como leído (quita UNREAD).
        runCatching {
            postJson(token, "$BASE/threads/$threadId/modify", JSONObject().put("removeLabelIds", JSONArray().put("UNREAD")))
        }
        return EmailSyncService.MailThread(subject, list)
    }

    private fun extractBody(payload: JSONObject?): String {
        if (payload == null) return ""
        val body = payload.optJSONObject("body")
        val data = body?.optString("data")
        if (!data.isNullOrBlank()) return decode(data)
        val parts = payload.optJSONArray("parts") ?: return ""
        // Prefiere text/plain.
        var htmlFallback = ""
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val mime = p.optString("mimeType")
            if (mime == "text/plain") {
                val d = p.optJSONObject("body")?.optString("data")
                if (!d.isNullOrBlank()) return decode(d)
            } else if (mime == "text/html") {
                val d = p.optJSONObject("body")?.optString("data")
                if (!d.isNullOrBlank()) htmlFallback = decode(d)
            } else if (mime.startsWith("multipart/")) {
                val nested = extractBody(p)
                if (nested.isNotBlank()) return nested
            }
        }
        return stripHtml(htmlFallback)
    }

    private fun decode(data: String): String = try {
        val bytes = Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        String(bytes, Charsets.UTF_8)
    } catch (_: Exception) {
        ""
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&")
            .replace(Regex("\\s+"), " ").trim()

    // --- Enviar ---
    private fun rawMessage(
        to: String,
        subject: String,
        body: String,
        inReplyTo: String? = null,
        references: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append("To: ").append(to).append("\r\n")
        sb.append("Subject: ").append(subject).append("\r\n")
        if (!inReplyTo.isNullOrBlank()) sb.append("In-Reply-To: ").append(inReplyTo).append("\r\n")
        if (!references.isNullOrBlank()) sb.append("References: ").append(references).append("\r\n")
        sb.append("MIME-Version: 1.0\r\n")
        sb.append("Content-Type: text/plain; charset=\"UTF-8\"\r\n")
        sb.append("Content-Transfer-Encoding: 8bit\r\n\r\n")
        sb.append(body)
        val raw = sb.toString().toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun send(token: String, to: String, subject: String, body: String): Boolean {
        val payload = JSONObject().put("raw", rawMessage(to, subject, body))
        return postJson(token, "$BASE/messages/send", payload)?.has("id") == true
    }

    fun reply(token: String, threadId: String, body: String): Boolean {
        // Necesita el Message-ID del último mensaje y su remitente.
        val t = getJson(
            token,
            "$BASE/threads/$threadId?format=metadata&metadataHeaders=Message-ID&metadataHeaders=From&metadataHeaders=Subject"
        ) ?: return false
        val msgs = t.optJSONArray("messages") ?: return false
        if (msgs.length() == 0) return false
        val last = msgs.getJSONObject(msgs.length() - 1)
        val headers = last.optJSONObject("payload")?.optJSONArray("headers")
        val messageId = header(headers, "Message-ID")
        val from = header(headers, "From")
        var subject = header(headers, "Subject")
        if (!subject.startsWith("Re:", ignoreCase = true)) subject = "Re: $subject"
        val payload = JSONObject()
            .put("raw", rawMessage(from, subject, body, messageId, messageId))
            .put("threadId", threadId)
        return postJson(token, "$BASE/messages/send", payload)?.has("id") == true
    }
}
