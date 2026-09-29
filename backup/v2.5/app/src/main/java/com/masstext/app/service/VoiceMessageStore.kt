package com.masstext.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

// Guarda el "mensaje pregrabado" que se reproduce automáticamente cuando una
// llamada se conecta: se graba con el micrófono o se importa desde un archivo
// de audio del teléfono. Todo bajo almacenamiento privado de la app.
object VoiceMessageStore {

    private const val PREFS = "masstext_prefs"
    private const val KEY_ENABLED = "voice_message_enabled"
    private const val FILE_NAME = "voice_msg.m4a"

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var appContext: Context? = null

    val recording = MutableStateFlow(false)
    val playing = MutableStateFlow(false)
    val hasMessage = MutableStateFlow(false)

    fun init(context: Context) {
        hasMessage.value = exists(context)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).exists() && file(context).length() > 0

    fun startRecording(context: Context) {
        if (recording.value) return
        stopPreview()
        appContext = context.applicationContext
        val f = file(context)
        f.delete()
        runCatching {
            f.parentFile?.mkdirs()
            val r = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(f.absolutePath)
                prepare()
                start()
            }
            recorder = r
            recording.value = true
        }
    }

    fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
        recording.value = false
        appContext?.let { ctx -> hasMessage.value = exists(ctx) }
    }

    fun stopPreview() {
        val p = player
        player = null
        runCatching { p?.stop() }
        runCatching { p?.release() }
        playing.value = false
    }

    fun playPreview(context: Context) {
        if (playing.value) {
            stopPreview()
            return
        }
        if (!exists(context)) return
        val f = file(context)
        runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(f.absolutePath)
                setOnCompletionListener { m ->
                    runCatching { m.release() }
                    if (player === m) player = null
                    playing.value = false
                }
            }
            mp.prepare()
            mp.start()
            player = mp
            playing.value = true
        }
    }

    fun stop() {
        stopRecording()
        stopPreview()
    }

    fun importAudio(context: Context, uri: Uri): Boolean {
        stopPreview()
        val f = file(context)
        return runCatching {
            f.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { output -> input.copyTo(output) }
            }
            val ok = f.exists() && f.length() > 0
            hasMessage.value = ok
            ok
        }.getOrElse { false }
    }

    fun delete(context: Context) {
        stopPreview()
        runCatching { file(context).delete() }
        hasMessage.value = false
    }
}