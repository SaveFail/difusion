package com.difusion.app.service

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class RecordingState { OFF, RECORDING, PAUSED }

// Grabadora profesional de llamadas.
//
// Por que un archivo salia de 6 KB y no se reproducia:
//  - Habia DOS tentativas de inicio en paralelo (una desde el servicio en
//    primer plano y otra desde la pantalla/InCallService al conectarse la
//    llamada). Dos MediaRecorder + dos filas de MediaStore al mismo tiempo
//    causaban que el que "ganaba" fuese uno roto: archivo corrupto e
//    irrecuperable. AHORA toda la maquinaria esta sincronizada (un solo hilo
//    logico mediante bloqueo) y el reinicio al conectar la llamada es atomico.
//  - Si el telefono bloquea la captura de llamada, MediaRecorder se caia en
//    silencio y guardabamos basura. AHORA se registra un error, y despues de
//    guardar se VERIFICA el archivo con MediaMetadataRetriever; si no se puede
//    reproducir (duracion nula), se descarta y se informa.
//  - Desde la pantalla se puede activar el modo "alto-parlante": la llamada se
//    envia al altavoz y se graba por el microfono, captando AMBOS lados incluso
//    en telefonos que bloquean la captura directa de la llamada.
object CallRecorder {

    private const val TAG = "CallRecorder"
    const val FOLDER = "llamadas de troncal"
    const val EXTENSION = "m4a"
    const val MIME = "audio/mp4"

    private const val PREFS = "recording_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_FOLDER_TREE = "folder_tree"
    private const val KEY_FOLDER_LABEL = "folder_label"

    const val DEFAULT_FOLDER_LABEL = "Grabaciones/llamadas de troncal"

    // Grabaciones en curso con menos de este tiempo se descartan al conectar
    // la llamada y se reinician limpias (momento con audio real).
    private const val RESTART_THRESHOLD_MS = 30_000L

    // Por debajo de esto el archivo no es util (no se puede reproducir).
    private const val MIN_PLAYABLE_MS = 300L

    private val lock = Any()

    // TODO el trabajo pesado de grabacion (MediaRecorder, MediaStore, archivos,
    // verificacion con MediaMetadataRetriever) corre en este hilo dedicado para
    // NO bloquear el hilo principal (causa del ANR/congelamiento en llamadas).
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CallRecorder-worker").apply { isDaemon = true }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(RecordingState.OFF)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var pendingDest: RecordDest? = null
    private var startedAt = 0L
    private var recordingContext: Context? = null
    private var numberForName: String? = null
    @Volatile private var lastAppCtx: Context? = null
    @Volatile private var telephony: TelephonyManager? = null
    @Volatile private var autoListener: PhoneStateListener? = null
    @Volatile private var currentSource = -1
    private val errorFlag = AtomicBoolean(false)

    // Enrutador de altavoz que aporta el InCallService cuando la grabacion usa
    // el microfono (cambio de ruta a nivel Telecom, mas fiable que AudioManager).
    @Volatile
    var speakerRouteRequest: (() -> Unit)? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }


    fun canRecord(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun lastError(context: Context): String? =
        prefs(context).getString(KEY_LAST_ERROR, null)?.takeIf { it.isNotBlank() }

    private fun setLastError(ctx: Context, error: String?) {
        runCatching { prefs(ctx).edit().putString(KEY_LAST_ERROR, error).apply() }
    }

    // ---- Carpeta elegida por el usuario ----

    fun folderTreeLabel(context: Context): String? =
        prefs(context).getString(KEY_FOLDER_LABEL, null)

    fun selectedTreeUri(context: Context): Uri? =
        prefs(context).getString(KEY_FOLDER_TREE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setFolderTree(context: Context, treeUri: Uri?, label: String?) {
        prefs(context).edit()
            .putString(KEY_FOLDER_TREE, treeUri?.toString())
            .putString(KEY_FOLDER_LABEL, label)
            .apply()
    }

    fun folderDisplayPath(context: Context): String =
        folderTreeLabel(context)?.takeIf { it.isNotBlank() } ?: DEFAULT_FOLDER_LABEL

    fun treeBaseRelativePathCompat(docId: String?): String? =
        docId?.takeIf { it.startsWith("primary:") }?.substringAfter(":")

    fun humanizeDocId(docId: String?): String? = when {
        docId == null -> null
        docId.startsWith("primary:") -> "Descargas/" + docId.substringAfter(":")
        else -> "Otra memoria/" + docId.substringAfter(":", docId)
    }

    fun treeDocId(context: Context): String? =
        selectedTreeUri(context)?.let {
            runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull()
        }

    // ---- Entradas publicas (todas sincronizadas: un solo flujo) ----

    fun autoStart(ctx: Context, number: String) {
        val appCtx = ctx.applicationContext
        lastAppCtx = appCtx
        if (!isEnabled(appCtx)) {
            setLastError(appCtx, "Grabacion desactivada en Ajustes")
            CallRecorderLog.append(appCtx, "autoStart ignorado: grabacion desactivada")
            return
        }
        if (!canRecord(appCtx)) {
            setLastError(appCtx, "Sin permiso de microfono")
            CallRecorderLog.append(appCtx, "autoStart ignorado: sin permiso de microfono")
            return
        }
        // El arranque real (servicio en primer plano o MediaRecorder directo) se
        // hace fuera del hilo principal para no congelar la llamada.
        worker.execute { synchronized(lock) { beginRecording(appCtx, number) } }
        armAutoListener(appCtx)
    }

    fun autoStop() = stop()

    // Arranca la grabacion: primero el servicio en primer plano (posee el mic);
    // si no se puede, arranca el MediaRecorder directamente. Siempre en worker.
    private fun beginRecording(appCtx: Context, number: String) {
        var serviceStarted = false
        runCatching {
            val intent = Intent(appCtx, CallRecordingService::class.java)
                .setAction(CallRecordingService.ACTION_START)
                .putExtra(CallRecordingService.EXTRA_NUMBER, number)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appCtx.startForegroundService(intent)
            } else {
                appCtx.startService(intent)
            }
            serviceStarted = true
        }.onFailure {
            CallRecorderLog.append(appCtx, "El servicio de grabacion no pudo iniciarse: ${it.message}")
        }
        if (!serviceStarted) {
            CallRecorderLog.append(appCtx, "Sin servicio en primer plano: grabacion directa")
            startInternal(appCtx, number)
        }
    }

    // true mientras la grabacion en curso usa el microfono (porque el telefono
    // bloqueo la captura directa). Quien este en la llamada debe enrutar esta
    // al altavoz para capturar AMBOS lados.
    fun micFallbackActive(): Boolean =
        _state.value == RecordingState.RECORDING &&
            currentSource == MediaRecorder.AudioSource.MIC

    fun restartRecorder(ctx: Context, number: String) {
        val appCtx = ctx.applicationContext
        worker.execute {
            synchronized(lock) {
                lastAppCtx = appCtx
                val st = _state.value
                if ((st == RecordingState.RECORDING || st == RecordingState.PAUSED) &&
                    (System.currentTimeMillis() - startedAt) < RESTART_THRESHOLD_MS
                ) {
                    cancelCurrentLocked()
                }
                if (!isEnabled(appCtx)) {
                    CallRecorderLog.append(appCtx, "restartRecorder ignorado: grabacion desactivada en Ajustes")
                } else if (!canRecord(appCtx)) {
                    CallRecorderLog.append(appCtx, "restartRecorder ignorado: sin permiso de microfono")
                } else {
                    beginRecording(appCtx, number)
                }
            }
        }
        armAutoListener(appCtx)
    }

    fun start(ctx: Context, number: String) {
        val appCtx = ctx.applicationContext
        worker.execute { synchronized(lock) { startInternal(appCtx, number) } }
    }

    fun pause() {
        worker.execute {
            synchronized(lock) {
                if (_state.value != RecordingState.RECORDING) return@synchronized
                runCatching { recorder?.pause() }
                _state.value = RecordingState.PAUSED
            }
        }
    }

    fun resume() {
        worker.execute {
            synchronized(lock) {
                if (_state.value != RecordingState.PAUSED) return@synchronized
                runCatching { recorder?.resume() }
                _state.value = RecordingState.RECORDING
            }
        }
    }

    fun stop() {
        disarmAutoListener()
        worker.execute {
            synchronized(lock) {
                val stateNow = _state.value
                if (stateNow == RecordingState.OFF) {
                    stopServiceLocked()
                    return@synchronized
                }
                stopServiceLocked()
                finishAndVerifyLocked()
            }
        }
    }

    // ---- Logica interna (siempre bajo el mismo lock) ----

    private fun startInternal(ctx: Context, number: String) {
        if (_state.value == RecordingState.RECORDING || _state.value == RecordingState.PAUSED) {
            return
        }
        val appCtx = ctx.applicationContext
        lastAppCtx = appCtx
        if (!isEnabled(appCtx)) {
            CallRecorderLog.append(appCtx, "startInternal ignorado: grabacion desactivada")
            return
        }
        if (!canRecord(appCtx)) {
            CallRecorderLog.append(appCtx, "startInternal ignorado: sin permiso de microfono")
            return
        }

        // Graba por el microfono (plan A): la llamada se enruta automaticamente
        // al altavoz y asi se captan AMBOS lados desde el ambiente. Es el unico
        // metodo audible en telefonos (p. ej. Honor/Huawei) que callan la
        // captura directa de la llamada. Como plan B, se intenta capturar la
        // llamada en limpio (VOICE_COMMUNICATION) en equipos que lo permiten.
        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )

        // Prueba cada fuente de audio de principio a fin: si VOICE_COMMUNICATION
        // falla al arrancar (muchos telefonos bloquean la captura de la llamada),
        // se intenta justo despues MIC como plan B, sin descolgar la llamada.
        var started = false
        var attempts = ""
        for (src in sources) {
            val mr = newRecorder(appCtx, src) ?: continue
            val dest = openDestination(appCtx, number)
            if (dest == null) {
                runCatching { mr.release() }
                continue
            }
            try {
                if (dest.fd != null) {
                    mr.setOutputFile(dest.fd!!.fileDescriptor)
                } else {
                    mr.setOutputFile(dest.file?.absolutePath)
                }
                mr.prepare()
                mr.start()
                recorder = mr
                pendingDest = dest
                recordingContext = appCtx
                numberForName = number
                startedAt = System.currentTimeMillis()
                errorFlag.set(false)
                currentSource = src
                _state.value = RecordingState.RECORDING
                setLastError(appCtx, null)
                CallRecorderLog.append(
                    appCtx,
                    "Grabando para $number [fuente ${sourceName(src)}] -> ${dest.displayPath()}"
                )
                started = true
                if (src == MediaRecorder.AudioSource.MIC) {
                    routeToSpeakerBestEffort(appCtx)
                    runCatching { speakerRouteRequest?.invoke() }
                }
                break
            } catch (e: Exception) {
                attempts += "${sourceName(src)} (${e.message?.take(60) ?: "error"}); "
                Log.e(TAG, "Fuente ${sourceName(src)} no empezo", e)
                runCatching { mr.release() }
                dest.discard(appCtx)
            }
        }
        if (!started) {
            val msg = if (attempts.isBlank()) {
                "No hay fuente de audio disponible en este telefono"
            } else {
                "El telefono bloqueo la captura de audio en ambas fuentes; se intentara de nuevo en la proxima llamada. ($attempts)"
            }
            setLastError(appCtx, msg)
            CallRecorderLog.append(appCtx, "ERROR: $msg")
        }
    }

    private fun newRecorder(appCtx: Context, src: Int): MediaRecorder? {
        val mr = runCatching { MediaRecorder() }.getOrNull() ?: return null
        runCatching { mr.setOnErrorListener { _, what, extra ->
            errorFlag.set(true)
            val msg = "MediaRecorder dio error durante la llamada (what=$what, extra=$extra)"
            Log.e(TAG, msg)
            runCatching { lastAppCtx?.let { CallRecorderLog.append(it, "ERROR: $msg") } }
        } }
        return try {
            mr.setAudioSource(src)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(16000)
            mr.setAudioEncodingBitRate(48000)
            mr.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stop()
                }
            }
            mr
        } catch (_: Exception) {
            runCatching { mr.release() }
            null
        }
    }

    private fun sourceName(src: Int): String = when (src) {
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "??"
    }

    private fun routeToSpeakerBestEffort(ctx: Context) {
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { audio.mode = AudioManager.MODE_IN_COMMUNICATION }
        runCatching { audio.isSpeakerphoneOn = true }
        runCatching { audio.isBluetoothScoOn = false }
    }

    private fun cancelCurrentLocked() {
        val rec = recorder
        val dest = pendingDest
        val ctx = recordingContext
        recorder = null
        pendingDest = null
        recordingContext = null
        numberForName = null
        currentSource = -1
        _state.value = RecordingState.OFF
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        if (ctx != null && dest != null) {
            dest.discard(ctx)
            CallRecorderLog.append(ctx, "Grabacion previa descartada (llamada conectada)")
        }
    }

    // Cierra, guarda y VERIFICA el archivo. Si el audio no se puede reproducir
    // (captura bloqueada por el fabricante o cortada), se descarta y se informa,
    // para que la lista de Grabaciones solo contenga audios reproducibles.
    private fun finishAndVerifyLocked() {
        val rec = recorder
        val dest = pendingDest
        val ctx = recordingContext
        val number = numberForName
        recorder = null
        pendingDest = null
        recordingContext = null
        numberForName = null
        currentSource = -1
        _state.value = RecordingState.OFF

        if (rec == null || dest == null || ctx == null) {
            runCatching { rec?.release() }
            dest?.discard(ctx ?: return)
            CallRecorderLog.append(ctx ?: lastAppCtx ?: return, "stop sin grabacion en curso")
            return
        }
        var stopOk = true
        runCatching { rec.stop() }.onFailure {
            stopOk = false
            Log.e(TAG, "MediaRecorder.stop() salio con error: ${it.message}")
        }
        runCatching { rec.release() }

        val where = dest.finalize(ctx)
        val durationMs = dest.verify(ctx)

        if (errorFlag.get()) {
            dest.discard(ctx)
            val msg = "El telefono corto la captura del audio de la llamada"
            setLastError(ctx, msg)
            CallRecorderLog.append(ctx, "ERROR: $msg (archivo descartado)")
            return
        }
        if (durationMs < MIN_PLAYABLE_MS) {
            dest.discard(ctx)
            val msg = "El audio no se puede reproducir (duracion $durationMs ms). El telefono bloqueo la captura de la llamada. Prueba el modo alto-parlante."
            setLastError(ctx, msg)
            CallRecorderLog.append(ctx, "ERROR: $msg")
            return
        }
        if (!stopOk) {
            dest.discard(ctx)
            val msg = "La grabacion quedo incompleta (se cerraria corrupta)"
            setLastError(ctx, msg)
            CallRecorderLog.append(ctx, "ERROR: $msg")
            return
        }
        setLastError(ctx, null)
        CallRecorderLog.append(
            ctx,
            "Grabacion OK para ${number ?: "desconocido"} ($durationMs ms) -> $where"
        )
    }

    private fun stopServiceLocked() {
        val ctx = lastAppCtx ?: return
        lastAppCtx = null
        runCatching { ctx.stopService(Intent(ctx, CallRecordingService::class.java)) }
    }

    private fun armAutoListener(ctx: Context) {
        val appCtx = ctx.applicationContext
        mainHandler.post {
            disarmAutoListenerLocked()
            val tm = appCtx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return@post
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_IDLE) {
                        stop()
                    }
                }
            }
            autoListener = listener
            telephony = tm
            runCatching { tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) }
                .onFailure {
                    Log.w(TAG, "No se pudo escuchar el estado de llamada: ${it.message}")
                    CallRecorderLog.append(appCtx, "Aviso: sin eventos de estado (${it.message})")
                }
        }
    }

    private fun disarmAutoListener() {
        mainHandler.post { disarmAutoListenerLocked() }
    }

    private fun disarmAutoListenerLocked() {
        val l = autoListener
        if (l != null) {
            runCatching { telephony?.listen(l, PhoneStateListener.LISTEN_NONE) }
        }
        autoListener = null
        telephony = null
    }

    // ---- Destino del archivo ----

    private fun openDestination(ctx: Context, number: String): RecordDest? {
        val cal = Calendar.getInstance()
        val timePart = SimpleDateFormat("HH-mm-ss", Locale.US).format(cal.time)
        val name = "${number}_-${timePart}.$EXTENSION"
        val year = cal.get(Calendar.YEAR)
        val month = String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1)
        val day = String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH))

        val tree = selectedTreeUri(ctx)
        if (tree != null) {
            val doc = treeDest(ctx, tree, name, year, month, day)
            if (doc != null) return doc
            CallRecorderLog.append(ctx, "Fallo la carpeta elegida; uso la predeterminada")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            // Android no permite "Download" como carpeta raíz de audio en
            // MediaStore; se usa "Recordings" (permitida e indexada por el
            // sistema, visible en cualquier reproductor y en la app).
            val relativePath = "${Environment.DIRECTORY_RECORDINGS}/$FOLDER/$year/$month/$day/"
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    val pfd = resolver.openFileDescriptor(uri, "w")
                    if (pfd != null) return MediaStoreDest(ctx, uri, pfd, name, relativePath)
                    runCatching { resolver.delete(uri, null, null) }
                }
            }.onFailure { Log.e(TAG, "MediaStore insert fallo", it) }
            CallRecorderLog.append(ctx, "MediaStore no disponible; uso carpeta propia")
            return privateFileDest(ctx, name, year, month, day)
        }

        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloads.mkdirs()
            val dir = File(File(downloads, FOLDER), year.toString())
            dir.mkdirs()
            val dayDir = File(dir, month).let { it.mkdirs(); File(it, day).apply { mkdirs() } }
            return FileDest(File(dayDir, name), isPrivate = false)
        }
        return privateFileDest(ctx, name, year, month, day)
    }

    private fun treeDest(
        ctx: Context,
        tree: Uri,
        name: String,
        year: Int,
        month: String,
        day: String
    ): TreeDest? {
        return runCatching {
            val root = DocumentFile.fromTreeUri(ctx, tree) ?: return null
            var folder = root
            for (part in listOf(year.toString(), month, day)) {
                folder = folder.findFile(part) ?: folder.createDirectory(part) ?: return null
            }
            val doc = folder.createFile(MIME, name) ?: return null
            val pfd = ctx.contentResolver.openFileDescriptor(doc.uri, "w") ?: return null
            val docId = DocumentsContract.getTreeDocumentId(tree)
            TreeDest(ctx, tree, docId, doc.uri, pfd, name)
        }.getOrNull()
    }

    private fun privateFileDest(ctx: Context, name: String, year: Int, month: String, day: String): FileDest? {
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val dir = File(base, "${FOLDER}/$year/$month/$day").apply { mkdirs() }
        return FileDest(File(dir, name), isPrivate = true)
    }

    private sealed class RecordDest {
        abstract val fd: ParcelFileDescriptor?
        abstract val file: File?
        abstract val uri: Uri?
        abstract fun finalize(ctx: Context): String
        abstract fun discard(ctx: Context)
        abstract fun displayPath(): String
        // Devuelve duracion en ms; 0 o negativo = no reproducible.
        fun verify(ctx: Context): Long {
            val u = uri ?: return 0L
            return runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(ctx, u)
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                } finally {
                    runCatching { mmr.release() }
                }
            }.getOrDefault(0L)
        }
    }

    private class MediaStoreDest(
        val ctx: Context,
        val contentUri: Uri,
        val pfd: ParcelFileDescriptor,
        val name: String,
        val relativePath: String
    ) : RecordDest() {
        override val fd: ParcelFileDescriptor? = pfd
        override val file: File? = null
        override val uri: Uri? = contentUri
        override fun finalize(ctx: Context): String {
            runCatching { pfd.close() }
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                ctx.contentResolver.update(contentUri, values, null, null)
            }
            return "$relativePath$name"
        }
        override fun discard(ctx: Context) {
            runCatching { pfd.close() }
            runCatching { ctx.contentResolver.delete(contentUri, null, null) }
        }
        override fun displayPath(): String = "$relativePath$name"
    }

    private class TreeDest(
        val ctx: Context,
        val tree: Uri,
        val docId: String,
        val docUri: Uri,
        val pfd: ParcelFileDescriptor,
        val name: String
    ) : RecordDest() {
        override val fd: ParcelFileDescriptor? = pfd
        override val file: File? = null
        override val uri: Uri? = docUri
        override fun finalize(ctx: Context): String {
            runCatching { pfd.close() }
            if (docId.startsWith("primary:")) {
                val rel = docId.substringAfter(":")
                val abs = File(Environment.getExternalStorageDirectory(), rel)
                if (abs.exists()) {
                    runCatching {
                        MediaScannerConnection.scanFile(ctx, arrayOf(abs.absolutePath), arrayOf(MIME), null)
                    }
                }
            }
            return "${humanizeDocId(docId) ?: docId}/$name"
        }
        override fun discard(ctx: Context) {
            runCatching { pfd.close() }
            runCatching { ctx.contentResolver.delete(docUri, null, null) }
        }
        override fun displayPath(): String = "${humanizeDocId(docId) ?: docId}/$name"
    }

    private class FileDest(
        val target: File,
        val isPrivate: Boolean
    ) : RecordDest() {
        override val fd: ParcelFileDescriptor? = null
        override val file: File? = target
        override val uri: Uri? = Uri.fromFile(target)
        override fun finalize(ctx: Context): String {
            if (target.exists() && target.length() > 0) {
                MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf(MIME), null)
            }
            return target.absolutePath
        }
        override fun discard(ctx: Context) {
            runCatching { if (target.exists()) target.delete() }
        }
        override fun displayPath(): String = target.absolutePath
    }
}