package com.difusion.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Biblioteca de mensajes de voz pregrabados. Puedes GRABAR varios o IMPORTAR
 * audios, y elegir cuál se reproduce cuando el cliente contesta la llamada.
 *
 * Todo se guarda en el almacenamiento privado de la app, en la carpeta
 * "voice_messages" (archivos de audio).
 */
object VoiceMessageStore {
    private const val PREFS = "difusion_prefs"
    private const val KEY_ENABLED = "voice_message_enabled"
    private const val KEY_SELECTED = "voice_message_selected"
    private const val DIR = "voice_messages"

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var preview: MediaPlayer? = null

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    /** Nombre del archivo que se está reproduciendo en la vista previa (o null). */
    private val _previewing = MutableStateFlow<String?>(null)
    val previewing: StateFlow<String?> = _previewing.asStateFlow()

    /** Se incrementa cuando cambia la lista (grabar/importar/borrar) para refrescar la UI. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun list(context: Context): List<File> =
        (dir(context).listFiles() ?: emptyArray())
            .filter { it.isFile && it.length() > 0 }
            .sortedByDescending { it.lastModified() }

    fun hasMessages(context: Context): Boolean = list(context).isNotEmpty()

    /** Archivo seleccionado; si no hay selección válida, el más reciente. */
    fun selected(context: Context): File? {
        val name = prefs(context).getString(KEY_SELECTED, null)
        if (name != null) {
            val f = File(dir(context), name)
            if (f.exists() && f.length() > 0) return f
        }
        return list(context).firstOrNull()
    }

    fun setSelected(context: Context, file: File) =
        prefs(context).edit().putString(KEY_SELECTED, file.name).apply()

    fun isSelected(context: Context, file: File): Boolean =
        selected(context)?.absolutePath == file.absolutePath

    // ---- Grabar ----
    fun startRecording(context: Context) {
        if (_recording.value) return
        stopPreview()
        val name = "grabacion_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".m4a"
        val f = File(dir(context), name)
        recordingFile = f
        runCatching {
            val r = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(f.absolutePath)
                prepare()
                start()
            }
            recorder = r
            _recording.value = true
        }.onFailure {
            recordingFile = null
            runCatching { f.delete() }
        }
    }

    fun stopRecording(context: Context) {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
        _recording.value = false
        val f = recordingFile
        recordingFile = null
        if (f != null && f.exists() && f.length() > 0) {
            setSelected(context, f)
        }
        _revision.value++
    }

    // ---- Importar ----
    fun importAudio(context: Context, uri: Uri): File? {
        stopPreview()
        return runCatching {
            val ext = context.contentResolver.getType(uri)?.let { mime ->
                when {
                    mime.contains("mp3") -> "mp3"
                    mime.contains("wav") -> "wav"
                    mime.contains("ogg") -> "ogg"
                    mime.contains("aac") -> "aac"
                    else -> "m4a"
                }
            } ?: "m4a"
            val name = "importado_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".$ext"
            val f = File(dir(context), name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { output -> input.copyTo(output) }
            }
            if (f.exists() && f.length() > 0) {
                setSelected(context, f)
                _revision.value++
                f
            } else {
                null
            }
        }.getOrNull()
    }

    fun delete(context: Context, file: File) {
        runCatching { file.delete() }
        if (prefs(context).getString(KEY_SELECTED, null) == file.name) {
            prefs(context).edit().remove(KEY_SELECTED).apply()
        }
        _revision.value++
    }

    // ---- Vista previa ----
    fun playPreview(context: Context, file: File) {
        if (_previewing.value == file.name) {
            stopPreview()
            return
        }
        stopPreview()
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { m ->
                    runCatching { m.release() }
                    if (preview === m) preview = null
                    _previewing.value = null
                }
                setOnErrorListener { m, _, _ ->
                    runCatching { m.release() }
                    if (preview === m) preview = null
                    _previewing.value = null
                    true
                }
                prepare()
                start()
            }.also { preview = it }
            _previewing.value = file.name
        }.onFailure {
            preview = null
            _previewing.value = null
        }
    }

    fun stopPreview() {
        val p = preview
        preview = null
        _previewing.value = null
        runCatching { p?.stop() }
        runCatching { p?.release() }
    }

    fun stop() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        _recording.value = false
        stopPreview()
    }
}
