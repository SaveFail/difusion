package com.masstext.app.service

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// Registro persistente de lo que ocurre con cada intento de grabación, para
// que el diagnóstico de la pantalla Grabaciones muestre información real (el
// archivo se guarda en filesDir/recording_log.txt).
object CallRecorderLog {

    private const val FILE = "recording_log.txt"
    private const val MAX_BYTES = 256 * 1024L

    // Las escrituras se hacen fuera del hilo principal: antes cada append
    // bloqueaba la UI (y crecía sin límite), lo que contribuía a los tirones y
    // al ANR durante las llamadas.
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CallRecorderLog").apply { isDaemon = true }
    }

    private fun logFile(context: Context): File = File(context.filesDir, FILE)

    fun append(context: Context, line: String) {
        Log.i("CallRecorderLog", line)
        val appCtx = context.applicationContext
        io.execute {
            try {
                val file = logFile(appCtx)
                if (file.length() > MAX_BYTES) {
                    val keep = file.readLines().takeLast(400)
                    file.writeText(keep.joinToString("\n", postfix = "\n"))
                }
                val stamp = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
                file.appendText("[$stamp] $line\n")
            } catch (_: Exception) {
            }
        }
    }

    fun last(context: Context): String? {
        return try {
            logFile(context).readLines().filter { it.isNotBlank() }.lastOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun content(context: Context, maxLines: Int = 40): String {
        return try {
            logFile(context).readLines().filter { it.isNotBlank() }.takeLast(maxLines).joinToString("\n")
        } catch (_: Exception) {
            "Sin registro todavía"
        }
    }
}