package com.difusion.app.service

import android.content.Context
import android.util.Log
import com.difusion.app.data.Contact
import com.difusion.app.storage.DriveSyncPrefs
import com.difusion.app.storage.UserStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Escribe los cambios de gestión en la hoja de Google Drive a través de un
 * Web App de Google Apps Script. Cada petición identifica la fila por
 * ID CUOTA (o Cédula + Teléfono), por lo que varias personas pueden trabajar
 * a la vez sin pisarse.
 */
object DriveSyncService {
    private const val TAG = "DIFUSION-DriveSync"
    private const val JSON = "application/json; charset=utf-8"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    data class SyncConfig(val url: String, val token: String = "")

    fun isEnabled(context: Context): Boolean = DriveSyncPrefs.isEnabled(context)

    fun config(context: Context): SyncConfig = SyncConfig(
        url = DriveSyncPrefs.getUrl(context),
        token = DriveSyncPrefs.getToken(context)
    )

    // Cola serial con un pequeño espaciado para no saturar el Web App cuando se
    // envían muchos SMS en un lote (cada contacto se sincroniza en su turno).
    private val syncExecutor = Executors.newSingleThreadExecutor()
    private val lastSyncAt = AtomicLong(0L)
    private const val MIN_GAP_MS = 300L

    /** Prueba la conexión con el Web App de Drive. Devuelve (ok, mensaje). */
    fun ping(config: SyncConfig): Pair<Boolean, String> {
        if (config.url.isBlank()) return false to "Falta la URL del Web App"
        return try {
            val json = JSONObject().apply {
                put("action", "ping")
                if (config.token.isNotBlank()) put("token", config.token)
            }
            val body = json.toString().toRequestBody(JSON.toMediaType())
            val request = Request.Builder()
                .url(config.url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val str = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return false to "HTTP ${resp.code}"
                val obj = runCatching { JSONObject(str) }.getOrNull()
                    ?: return false to "Respuesta inválida"
                if (obj.optBoolean("ok", false)) {
                    true to obj.optString("message", "Conectado")
                } else {
                    false to obj.optString("error", "Error del servidor")
                }
            }
        } catch (e: Exception) {
            false to (e.message ?: "Error de conexión")
        }
    }

    /** Punto de entrada simple: revisa si está activado y sincroniza. Bloquea
     *  (llamar desde un hilo/dispatcher de IO). */
    fun syncNow(context: Context, contact: Contact): Boolean {
        if (!isEnabled(context)) return false
        return syncContact(context, contact, config(context))
    }

    /** Encola la sincronización en segundo plano (no bloquea al que llama).
     *  Útil desde el motor de envío masivo y desde llamadas. */
    fun enqueue(context: Context, contact: Contact) {
        if (!isEnabled(context)) return
        val appContext = context.applicationContext
        syncExecutor.execute {
            try {
                val gap = MIN_GAP_MS - (System.currentTimeMillis() - lastSyncAt.get())
                if (gap > 0) Thread.sleep(gap)
                lastSyncAt.set(System.currentTimeMillis())
                syncContact(appContext, contact, config(appContext))
            } catch (_: Exception) {
            }
        }
    }

    fun syncContact(context: Context, contact: Contact, config: SyncConfig): Boolean {
        if (config.url.isBlank()) return false
        return try {
            val json = JSONObject().apply {
                put("idCuota", contact.idCuota)
                put("cedula", contact.cedula)
                put("phone", contact.phone)
                put("name", contact.name)
                put("assignment", contact.assignment)
                put("gestion", contact.gestion)
                put("estado", contact.estado)
                put("medio", contact.medio)
                put("fechaGestion", contact.fechaGestion)
                put("monto", contact.monto)
                put("user", UserStore.getUser(context))
                if (config.token.isNotBlank()) put("token", config.token)
            }
            val body = json.toString().toRequestBody(JSON.toMediaType())
            val request = Request.Builder()
                .url(config.url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val ok = resp.isSuccessful
                if (!ok) Log.d(TAG, "HTTP ${resp.code} al sincronizar ${contact.name}")
                ok
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error de sincronización: ${e.message}")
            false
        }
    }
}
