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
}
