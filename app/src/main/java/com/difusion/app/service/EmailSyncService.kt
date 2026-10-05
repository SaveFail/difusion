package com.difusion.app.service

import android.content.Context
import android.util.Log
import com.difusion.app.storage.EmailSyncPrefs
import com.difusion.app.storage.UserStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Envía correos masivos a través de un Web App de Google Apps Script.
 * El envío se hace desde una cuenta de Google (GmailApp), no desde el
 * dispositivo, así que NO se abre el cliente de correo y se respetan los
 * límites de envío de la cuenta, reduciendo el riesgo de spam.
 */
object EmailSyncService {
    private const val TAG = "DIFUSION-Email"
    private const val JSON = "application/json; charset=utf-8"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    data class EmailConfig(val url: String, val token: String = "")

    data class EmailResult(
        val success: Boolean,
        val sent: Int = 0,
        val failed: Int = 0,
        val total: Int = 0,
        val error: String = ""
    )

    data class MailSummary(
        val threadId: String,
        val from: String,
        val subject: String,
        val snippet: String,
        val date: String,
        val unread: Boolean,
        val messageCount: Int
    )

    data class MailMessage(
        val from: String,
        val to: String,
        val date: String,
        val body: String,
        val isFromMe: Boolean
    )

    data class MailThread(
        val subject: String,
        val messages: List<MailMessage>
    )

    data class InboxResult(
        val success: Boolean,
        val messages: List<MailSummary> = emptyList(),
        val unread: Int = 0,
        val error: String = ""
    )

    data class PingResult(
        val success: Boolean,
        val email: String = "",
        val unread: Int = 0,
        val error: String = ""
    )

    /** Prueba la conexión con el Web App y devuelve la cuenta de Gmail conectada. */
    fun ping(config: EmailConfig): PingResult {
        if (config.url.isBlank()) return PingResult(false, error = "Falta la URL del Web App")
        val obj = post(
            config,
            JSONObject().apply {
                put("action", "ping")
                if (config.token.isNotBlank()) put("token", config.token)
            }
        ) ?: return PingResult(false, error = "No se pudo conectar (revisa la URL /exec)")
        if (!obj.optBoolean("ok", false)) {
            return PingResult(false, error = obj.optString("error", "Error del servidor"))
        }
        return PingResult(
            success = true,
            email = obj.optString("email", ""),
            unread = obj.optInt("unread", 0)
        )
    }

    fun isEnabled(context: Context): Boolean = EmailSyncPrefs.isEnabled(context)

    fun config(context: Context): EmailConfig = EmailConfig(
        url = EmailSyncPrefs.getUrl(context),
        token = EmailSyncPrefs.getToken(context)
    )

    fun sendBulk(
        context: Context,
        config: EmailConfig,
        recipients: List<String>,
        subject: String,
        body: String,
        html: Boolean = false
    ): EmailResult {
        if (config.url.isBlank()) return EmailResult(false, error = "URL del Web App no configurada")
        if (recipients.isEmpty()) return EmailResult(false, error = "Sin destinatarios")
        return try {
            val json = JSONObject().apply {
                put("recipients", JSONArray(recipients))
                put("subject", subject)
                put("body", body)
                put("html", html)
                put("user", UserStore.getUser(context))
                if (config.token.isNotBlank()) put("token", config.token)
            }
            val bodyReq = json.toString().toRequestBody(JSON.toMediaType())
            val request = Request.Builder()
                .url(config.url)
                .post(bodyReq)
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val str = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return EmailResult(false, error = "HTTP ${resp.code}: $str")
                }
                try {
                    val obj = JSONObject(str)
                    EmailResult(
                        success = obj.optBoolean("ok", obj.optBoolean("success", false)),
                        sent = obj.optInt("sent", 0),
                        failed = obj.optInt("failed", 0),
                        total = obj.optInt("total", recipients.size),
                        error = obj.optString("error", "")
                    )
                } catch (e: Exception) {
                    EmailResult(false, error = "Respuesta inválida del servidor")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "error: ${e.message}")
            EmailResult(false, error = e.message ?: "Error desconocido")
        }
    }

    /** Envía un correo suelto a una dirección. */
    fun sendOne(context: Context, config: EmailConfig, to: String, subject: String, body: String): EmailResult =
        sendBulk(context, config, listOf(to), subject, body)

    /**
     * Envía usando lo mejor disponible: si hay sesión de Google activa, usa la
     * API de Gmail directamente; si no, usa el puente de Apps Script.
     */
    fun sendSmart(context: Context, recipients: List<String>, subject: String, body: String): EmailResult {
        val list = recipients.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (list.isEmpty()) return EmailResult(false, error = "Sin destinatarios")
        val token = GmailAuth.token(context)
        if (token != null) {
            var sent = 0
            var failed = 0
            for (r in list) {
                if (GmailApiService.send(token, r, subject, body)) sent++ else failed++
            }
            return EmailResult(success = sent > 0, sent = sent, failed = failed, total = list.size)
        }
        return sendBulk(context, config(context), list, subject, body)
    }

    private fun post(config: EmailConfig, json: JSONObject): JSONObject? {
        if (config.url.isBlank()) return null
        return try {
            val body = json.toString().toRequestBody(JSON.toMediaType())
            val request = Request.Builder()
                .url(config.url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val str = resp.body?.string() ?: return null
                JSONObject(str)
            }
        } catch (e: Exception) {
            Log.d(TAG, "post error: ${e.message}")
            null
        }
    }

    /** Lista la bandeja de entrada (Gmail real). Bloquea: usar en IO. */
    fun listInbox(context: Context, config: EmailConfig, max: Int = 25): InboxResult {
        if (config.url.isBlank()) return InboxResult(false, error = "URL del Web App no configurada")
        val obj = post(
            config,
            JSONObject().apply {
                put("action", "list")
                put("max", max)
                if (config.token.isNotBlank()) put("token", config.token)
            }
        ) ?: return InboxResult(false, error = "No se pudo conectar con el Web App")
        if (!obj.optBoolean("ok", false)) {
            return InboxResult(false, error = obj.optString("error", "Error del servidor"))
        }
        val arr = obj.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MailSummary(
                threadId = o.optString("threadId"),
                from = o.optString("from"),
                subject = o.optString("subject"),
                snippet = o.optString("snippet"),
                date = o.optString("date"),
                unread = o.optBoolean("unread", false),
                messageCount = o.optInt("messageCount", 1)
            )
        }
        return InboxResult(true, messages, obj.optInt("unread", 0))
    }

    /** Lee una conversación completa. Bloquea: usar en IO. */
    fun readThread(config: EmailConfig, threadId: String): MailThread? {
        val obj = post(
            config,
            JSONObject().apply {
                put("action", "read")
                put("threadId", threadId)
                if (config.token.isNotBlank()) put("token", config.token)
            }
        ) ?: return null
        if (!obj.optBoolean("ok", false)) return null
        val arr = obj.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MailMessage(
                from = o.optString("from"),
                to = o.optString("to"),
                date = o.optString("date"),
                body = o.optString("body"),
                isFromMe = o.optBoolean("isFromMe", false)
            )
        }
        return MailThread(obj.optString("subject"), messages)
    }

    /** Responde una conversación. Bloquea: usar en IO. */
    fun reply(config: EmailConfig, threadId: String, body: String): Boolean {
        val obj = post(
            config,
            JSONObject().apply {
                put("action", "reply")
                put("threadId", threadId)
                put("body", body)
                if (config.token.isNotBlank()) put("token", config.token)
            }
        ) ?: return false
        return obj.optBoolean("ok", false)
    }
}
