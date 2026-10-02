package com.difusion.app.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.difusion.app.data.CallRecord
import com.difusion.app.data.SendRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Registro (bitácora) de SMS y llamadas en la carpeta Descargas del dispositivo,
// dentro de `Descargas/llamadas de troncal/` (la misma carpeta de los audios de
// llamada). En Android 10+ se escribe vía MediaStore (sin permisos); en versiones
// antiguas se usa la carpeta pública de Descargas. Si además se configuró una
// carpeta propia (SAF), se copia también allí como respaldo adicional.
object RecordStore {

    private const val PREFS = "difusion_prefs"
    private const val KEY_FOLDER_URI = "backup_folder_uri"
    private const val KEY_FOLDER_NAME = "backup_folder_name"

    const val FOLDER = "llamadas de troncal"
    const val FILE_SMS = "registro_sms.csv"
    const val FILE_CALLS = "registro_llamadas.csv"

    private const val HEADER_SMS = "fecha;enviados;fallidos;total;mensaje;usuario;causas"
    private const val HEADER_CALLS = "fecha;contacto;telefono;resultado;clasificacion;usuario"

    fun saveFolder(context: Context, uri: Uri, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .putString(KEY_FOLDER_NAME, name)
            .apply()
    }

    fun folderUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FOLDER_URI, null)
        return raw?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    fun isConfigured(context: Context): Boolean = folderUri(context) != null

    fun folderName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FOLDER_NAME, null) ?: "Respaldo"

    suspend fun appendSend(context: Context, r: SendRecord) {
        val line = listOf(
            now(), r.sent.toString(), r.failed.toString(), r.total.toString(),
            csv(r.message), csv(r.user), csv(r.failureBreakdown)
        ).joinToString(";")
        appendToDownloads(context, FILE_SMS, HEADER_SMS, "$line\n")
        appendToSafe(context, FILE_SMS, HEADER_SMS, "$line\n")
    }

    suspend fun appendCall(context: Context, r: CallRecord) {
        val resultado = if (r.success) "Exitosa" else "Sin contacto"
        val line = listOf(
            now(), csv(r.contactName), csv(r.phone), resultado, if (r.label.isBlank()) "" else r.label, csv(r.user)
        ).joinToString(";")
        appendToDownloads(context, FILE_CALLS, HEADER_CALLS, "$line\n")
        appendToSafe(context, FILE_CALLS, HEADER_CALLS, "$line\n")
    }

    suspend fun exportAll(context: Context, sends: List<SendRecord>, calls: List<CallRecord>) {
        withContext(Dispatchers.IO) {
            val sms = buildString {
                append("\uFEFF$HEADER_SMS\n")
                sends.forEach { r ->
                    append("${dateFmt(r.date)};${r.sent};${r.failed};${r.total};${csv(r.message)};${csv(r.user)};${csv(r.failureBreakdown)}\n")
                }
            }
            val callsCsv = buildString {
                append("\uFEFF$HEADER_CALLS\n")
                calls.forEach { c ->
                    val resultado = if (c.success) "Exitosa" else "Sin contacto"
                    append("${dateFmt(c.date)};${csv(c.contactName)};${csv(c.phone)};$resultado;${if (c.label.isBlank()) "" else c.label};${csv(c.user)}\n")
                }
            }
            overwriteInDownloads(context, FILE_SMS, sms)
            overwriteInDownloads(context, FILE_CALLS, callsCsv)
            // Copia de respaldo en la carpeta SAF si se configuró.
            val uri = folderUri(context) ?: return@withContext
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, uri) ?: return@withContext
                writeToSafeTree(context, tree, FILE_SMS, sms, HEADER_SMS)
                writeToSafeTree(context, tree, FILE_CALLS, callsCsv, HEADER_CALLS)
            }
        }
    }

    // ---------- Escritura en Descargas (MediaStore / carpeta pública) ----------

    private suspend fun appendToDownloads(context: Context, fileName: String, header: String, data: String) {
        withContext(Dispatchers.IO) {
            try {
                writeToDownloads(context, fileName, header, data, append = true)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun overwriteInDownloads(context: Context, fileName: String, content: String) {
        withContext(Dispatchers.IO) {
            try {
                writeToDownloads(context, fileName, null, content, append = false)
            } catch (_: Exception) {
            }
        }
    }

    private fun writeToDownloads(context: Context, fileName: String, header: String?, data: String, append: Boolean) {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val relativePath = "Download/${FOLDER}/"
            var uri = findDownloadUri(context, collection, fileName, relativePath)
            val isNew = uri == null
            if (isNew) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                uri = resolver.insert(collection, values)
            }
            uri ?: return
            try {
                resolver.openOutputStream(uri, if (append) "wa" else "w")?.bufferedWriter(Charsets.UTF_8)?.use { w ->
                    if (isNew && header != null) w.write("\uFEFF$header\n")
                    w.write(data)
                } ?: return
                if (isNew) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, values, null, null)
                }
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            val isNew = !file.exists()
            file.bufferedWriter(Charsets.UTF_8).use { w ->
                w.write(if (isNew && header != null) "\uFEFF$header\n" else "")
                w.write(data)
            }
        }
    }

    private fun findDownloadUri(context: Context, collection: Uri, fileName: String, relativePath: String): Uri? {
        val resolver = context.contentResolver
        return try {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(fileName, relativePath),
                null
            )?.use { c ->
                if (c.moveToFirst()) android.content.ContentUris.withAppendedId(collection, c.getLong(0)) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---------- Copia adicional en la carpeta SAF elegida por el usuario ----------

    private suspend fun appendToSafe(context: Context, fileName: String, header: String, line: String) {
        val uri = folderUri(context) ?: return
        withContext(Dispatchers.IO) {
            try {
                val tree = DocumentFile.fromTreeUri(context, uri) ?: return@withContext
                writeToSafeTree(context, tree, fileName, line, header, append = true)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun writeToSafeTree(
        context: Context,
        tree: DocumentFile,
        fileName: String,
        content: String,
        header: String,
        append: Boolean = false
    ) {
        var file = tree.findFile(fileName)
        val isNew = file == null
        if (isNew) file = tree.createFile("text/csv", fileName)
        file ?: return
        val out = context.contentResolver.openOutputStream(file.uri, if (append) "wa" else "w") ?: return
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            if (isNew) w.write("\uFEFF$header\n")
            w.write(content)
        }
    }

    private fun csv(v: String): String =
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else v

    private fun now(): String = dateFmt(System.currentTimeMillis())

    private fun dateFmt(ts: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(ts))
}