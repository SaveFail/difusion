package com.difusion.app.service

import android.content.Context
import android.util.Log
import com.difusion.app.storage.WaPrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente del worker de WhatsApp (Baileys, open source). El worker corre en un
 * PC/servidor y expone HTTP; la app solo se conecta con URL + token.
 */
object WhatsAppBridge {
    private const val TAG = "DIFUSION-WA"
    private const val JSON = "application/json; charset=utf-8"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    data class Status(
        val ok: Boolean,
        val state: String = "",
        val qr: String? = null,
        val pairing: String? = null,
        val error: String = ""
    )

    data class Rule(
        val keyword: String,
        val reply: String,
        val match: String = "contains",
        val stop: Boolean = true
    )

    private fun url(context: Context, path: String): String =
        WaPrefs.getUrl(context) + path

    private fun request(context: Context): Request.Builder {
        val b = Request.Builder().addHeader("Accept", "application/json")
        val token = WaPrefs.getToken(context)
        if (token.isNotBlank()) b.addHeader("x-token", token)
        return b
    }

    fun status(context: Context): Status {
        if (!WaPrefs.isConfigured(context)) return Status(false, error = "Falta la URL del worker")
        return try {
            val req = request(context).url(url(context, "/status")).get().build()
            http.newCall(req).execute().use { r ->
                val text = r.body?.string() ?: ""
                if (!r.isSuccessful) return Status(false, error = "HTTP ${r.code}")
                val o = JSONObject(text)
                Status(
                    ok = o.optBoolean("ok", false),
                    state = o.optString("state", ""),
                    qr = o.optString("qr", "").ifBlank { null },
                    pairing = o.optString("pairing", "").ifBlank { null },
                    error = o.optString("error", "")
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "status: ${e.message}")
            Status(false, error = e.message ?: "Error de conexión")
        }
    }

    fun ping(context: Context): Pair<Boolean, String> {
        if (!WaPrefs.isConfigured(context)) return false to "Falta la URL del worker"
        return try {
            val req = request(context).url(url(context, "/")).get().build()
            http.newCall(req).execute().use { r ->
                val text = r.body?.string() ?: ""
                if (!r.isSuccessful) return false to "HTTP ${r.code}"
                val o = JSONObject(text)
                if (o.optBoolean("ok", false)) true to "Conectado (estado: ${o.optString("state", "?")})"
                else false to o.optString("error", "Error")
            }
        } catch (e: Exception) {
            false to (e.message ?: "Error de conexión")
        }
    }

    fun pair(context: Context, phone: String): Pair<Boolean, String> {
        return try {
            val body = JSONObject().put("phone", phone).toString().toRequestBody(JSON.toMediaType())
            val req = request(context).url(url(context, "/pair")).post(body).build()
            http.newCall(req).execute().use { r ->
                val text = r.body?.string() ?: ""
                val o = runCatching { JSONObject(text) }.getOrNull()
                val ok = r.isSuccessful && (o?.optBoolean("ok", false) == true)
                ok to (o?.optString("error", "") ?: "")
            }
        } catch (e: Exception) {
            false to (e.message ?: "Error de conexión")
        }
    }

    fun send(context: Context, to: String, text: String): Pair<Boolean, String> {
        if (!WaPrefs.isConfigured(context)) return false to "WhatsApp no configurado"
        return try {
            val body = JSONObject().apply {
                put("to", to)
                put("text", text)
            }.toString().toRequestBody(JSON.toMediaType())
            val req = request(context).url(url(context, "/send")).post(body).build()
            http.newCall(req).execute().use { r ->
                val t = r.body?.string() ?: ""
                val o = runCatching { JSONObject(t) }.getOrNull()
                val ok = r.isSuccessful && (o?.optBoolean("ok", false) == true)
                ok to (o?.optString("error", "") ?: "")
            }
        } catch (e: Exception) {
            false to (e.message ?: "Error de conexión")
        }
    }

    fun rulesToJson(rules: List<Rule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("keyword", r.keyword)
                put("reply", r.reply)
                put("match", r.match)
                put("stop", r.stop)
            })
        }
        return arr.toString()
    }

    fun parseRules(json: String): List<Rule> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val k = o.optString("keyword", "")
            val r = o.optString("reply", "")
            if (k.isBlank() || r.isBlank()) null
            else Rule(k, r, o.optString("match", "contains"), o.optBoolean("stop", true))
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Envía al worker la configuración del bot (habilitado + reglas). */
    fun pushBot(context: Context, enabled: Boolean, rules: List<Rule>): Pair<Boolean, String> {
        if (!WaPrefs.isConfigured(context)) return false to "WhatsApp no configurado"
        return try {
            val body = JSONObject().apply {
                put("enabled", enabled)
                put("rules", JSONArray(rulesToJson(rules)))
            }.toString().toRequestBody(JSON.toMediaType())
            val req = request(context).url(url(context, "/bot")).post(body).build()
            http.newCall(req).execute().use { r ->
                val t = r.body?.string() ?: ""
                val o = runCatching { JSONObject(t) }.getOrNull()
                val ok = r.isSuccessful && (o?.optBoolean("ok", false) == true)
                ok to (o?.optString("error", "") ?: "")
            }
        } catch (e: Exception) {
            false to (e.message ?: "Error de conexión")
        }
    }
}
