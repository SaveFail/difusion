package com.masstext.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.masstext.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Comprobación e instalación de actualizaciones sin pasar por Google Play.
 *
 * Consulta la última Release del repositorio público de descargas (API de
 * GitHub, sin caché) y, si hay una versión más nueva, descarga el APK adjunto y
 * abre el instalador del sistema (el usuario solo confirma). El APK debe estar
 * firmado con la misma clave que el instalado. El repositorio de código puede
 * seguir privado: solo el APK se publica en el canal público.
 */
object UpdateManager {

    private const val TAG = "LEX-Update"
    private const val RELEASES_API =
        "https://api.github.com/repos/SaveFail/lex-recover-releases/releases/latest"

    data class Info(
        val versionName: String,
        val apkUrl: String,
        val notes: String
    )

    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** ¿la versión remota es más nueva que la instalada? (1.4 > 1.3, 1.10 > 1.9). */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.trim().removePrefix("v").split('.', '-', ' ').mapNotNull { it.toIntOrNull() }
        val b = current.trim().removePrefix("v").split('.', '-', ' ').mapNotNull { it.toIntOrNull() }
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Lee la última Release publicada. Devuelve null si no se pudo. */
    suspend fun fetchLatest(): Info? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(RELEASES_API).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "LEX-RECOVER")
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (connection.responseCode !in 200..299) {
                    Log.w(TAG, "API de releases respondió ${connection.responseCode}")
                    return@withContext null
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val tag = json.optString("tag_name").removePrefix("v").trim()
                val notes = json.optString("body", "")
                var apkUrl = ""
                val assets = json.optJSONArray("assets")
                for (i in 0 until (assets?.length() ?: 0)) {
                    val asset = assets!!.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (tag.isEmpty() || apkUrl.isEmpty()) {
                    Log.w(TAG, "Release sin versión o sin APK")
                    null
                } else {
                    Info(tag, apkUrl, notes)
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo consultar la actualización: ${e.message}")
            null
        }
    }

    /** Descarga el APK a la caché y devuelve el archivo. */
    suspend fun downloadApk(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val target = File(dir, "lex-recover-${System.currentTimeMillis()}.apk")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "LEX-RECOVER")
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive,*/*")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("La descarga respondió ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
            }
        } finally {
            connection.disconnect()
        }
        target
    }

    /** ¿La app tiene permiso para instalar paquetes? (Android 8+). */
    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    /** Abre la pantalla para permitir "Instalar apps desconocidas" a esta app. */
    fun openInstallPermissionSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /** Lanza el instalador del sistema con el APK descargado. */
    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
