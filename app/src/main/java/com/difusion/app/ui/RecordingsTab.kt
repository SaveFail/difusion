package com.difusion.app.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.difusion.app.service.CallRecorder
import com.difusion.app.service.CallRecorderLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecordingItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val folderPath: String?,
    val treeUri: Uri? = null,
    val treeDocId: String? = null,
    val folderDocId: String? = null,
)

fun queryRecordings(context: Context): List<RecordingItem> {
    val out = mutableListOf<RecordingItem>()
    val hasRelPath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val projection = mutableListOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_ADDED,
    ).apply {
        add(if (hasRelPath) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA)
    }
    // Base de la carpeta elegida (si es en memoria principal) + la predeterminada.
    val customDocId = CallRecorder.treeDocId(context)
    val customBase = CallRecorder.treeBaseRelativePathCompat(customDocId)
    val bases = mutableListOf(
        "Download/llamadas de troncal",
        "Recordings/llamadas de troncal"
    )
    if (customBase != null &&
        customBase != "Download/llamadas de troncal" &&
        customBase != "Recordings/llamadas de troncal"
    ) {
        bases += customBase
    }
    try {
        val selection = if (hasRelPath) {
            buildString {
                bases.forEachIndexed { i, b ->
                    if (i > 0) append(" OR ")
                    append("${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?")
                }
            }
        } else {
            "${MediaStore.Audio.Media.DATA} LIKE ?"
        }
        val args = if (hasRelPath) bases.map { "$it/%" }.toTypedArray() else arrayOf("%llamadas de troncal%")
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            selection,
            args,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val size = c.getLong(2)
                val added = c.getLong(3)
                val path = c.getString(4)
                if (name.isBlank() || size <= 0) continue
                out += RecordingItem(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    name = name,
                    sizeBytes = size,
                    dateAddedMs = added * 1000L,
                    folderPath = if (hasRelPath) {
                        path?.removeSuffix("/")
                    } else {
                        path?.substringBeforeLast("/")
                    },
                )
            }
        }
    } catch (_: Exception) {
    }
    // Carpeta elegida en el gestor del sistema: listamos su contenido real solo
    // cuando no es la memoria principal (ahí MediaStore ya lo cubre y evitamos
    // duplicados).
    CallRecorder.selectedTreeUri(context)?.let { tree ->
        val docId = runCatching {
            android.provider.DocumentsContract.getTreeDocumentId(tree)
        }.getOrNull()
        if (docId != null && !docId.startsWith("primary:")) {
            runCatching { walkTree(tree, out, context) }
        }
    }
    // Respaldo: la carpeta pública propia de la app (si Descargas global no está
    // disponible), expuesta con URIs file://.
    try {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (base != null) {
            val root = File(base, FOLDER_NAME)
            walkAudios(root, out)
        }
    } catch (_: Exception) {
    }
    return out.distinctBy { it.uri.toString() }
}

private const val FOLDER_NAME = "llamadas de troncal"

private val AUDIO_EXTS = setOf("m4a", "mp4", "3gp", "amr")

// Recorrido recursivo de la carpeta elegida (DocumentsProvider).
private fun walkTree(tree: Uri, out: MutableList<RecordingItem>, context: Context) {
    val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree) ?: return
    val treeDocId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
    walkDoc(root, treeDocId, out)
}

private fun walkDoc(dir: androidx.documentfile.provider.DocumentFile, treeDocId: String?, out: MutableList<RecordingItem>) {
    if (!dir.isDirectory) return
    dir.listFiles()?.forEach { f ->
        val docId = runCatching { android.provider.DocumentsContract.getDocumentId(f.uri) }.getOrNull()
        when {
            f.isDirectory -> walkDoc(f, treeDocId, out)
            f.isFile && f.name?.let { n ->
                AUDIO_EXTS.any { n.endsWith(it, ignoreCase = true) }
            } == true && f.length() > 0 ->
                out += RecordingItem(
                    id = f.uri.toString().hashCode().toLong(),
                    uri = f.uri,
                    name = f.name ?: "audio",
                    sizeBytes = f.length(),
                    dateAddedMs = f.lastModified(),
                    folderPath = dir.name,
                    treeUri = treeDocId?.let { d -> buildTreeUriFor(d) },
                    treeDocId = treeDocId,
                    folderDocId = docId?.substringBeforeLast("/"),
                )
        }
    }
}

private fun buildTreeUriFor(docId: String): Uri? = runCatching {
    android.provider.DocumentsContract.buildTreeDocumentUri(
        "com.android.externalstorage.documents",
        docId
    )
}.getOrNull()

private fun walkAudios(dir: File, out: MutableList<RecordingItem>) {
    if (!dir.exists() || !dir.isDirectory) return
    dir.listFiles()?.forEach { f ->
        when {
            f.isDirectory -> walkAudios(f, out)
            f.isFile && AUDIO_EXTS.any { f.extension.equals(it, ignoreCase = true) } && f.length() > 0 ->
                out += RecordingItem(
                    id = f.absolutePath.hashCode().toLong(),
                    uri = Uri.fromFile(f),
                    name = f.name,
                    sizeBytes = f.length(),
                    dateAddedMs = f.lastModified(),
                    folderPath = f.parentFile?.absolutePath,
                )
        }
    }
}

fun queryRecordingCount(context: Context): Int = queryRecordings(context).size

// ¿Esta app es la de llamadas predeterminada? (Roles en Android 10+, Telecom en
// versiones anteriores). Es requisito para que el sistema nos enlace el
// InCallService y así se graben las llamadas.
fun isDesignatedDialer(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            context.getSystemService(android.app.role.RoleManager::class.java)
                ?.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)
        }.getOrDefault(false) ?: false
    } else {
        runCatching {
            (context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)
                ?.defaultDialerPackage == context.packageName
        }.getOrDefault(false)
    }
}

private fun isMicGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        runCatching {
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

private fun requestDialerRoleIntent(context: Context): android.content.Intent {
    val role = runCatching {
        context.getSystemService(android.app.role.RoleManager::class.java)
            ?.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
    }.getOrNull()
    return role ?: android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Composable
fun RecordingsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recordings by remember { mutableStateOf<List<RecordingItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var playingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var showLog by remember { mutableStateOf(false) }
    val logContent by remember { mutableStateOf(CallRecorderLog.content(context)) }
    var micGranted by remember { mutableStateOf(isMicGranted(context)) }
    var isDialerDefault by remember { mutableStateOf(isDesignatedDialer(context)) }
    val recEnabled = CallRecorder.isEnabled(context)
    val recError = CallRecorder.lastError(context)
    var folderLabel by remember { mutableStateOf(CallRecorder.folderDisplayPath(context)) }
    val hasCustomFolder = remember { CallRecorder.selectedTreeUri(context) != null }

    fun load() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { queryRecordings(context) }.getOrElse {
                    error = it.message
                    emptyList()
                }
            }
            recordings = list
            error = null
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val docId = runCatching {
                android.provider.DocumentsContract.getTreeDocumentId(tree)
            }.getOrNull()
            val label = CallRecorder.humanizeDocId(docId) ?: "Carpeta elegida"
            CallRecorder.setFolderTree(context, tree, label)
            folderLabel = CallRecorder.folderDisplayPath(context)
            load()
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDialerDefault = isDesignatedDialer(context)
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        micGranted = isMicGranted(context)
    }



    val player = remember {
        MediaPlayer().apply {
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnCompletionListener {
                isPlaying = false
            }
        }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }

    val queryFilter = query.trim()
    val filtered = recordings?.filter {
        queryFilter.isEmpty() ||
            it.name.contains(queryFilter, ignoreCase = true) ||
            friendlyName(it.name).contains(queryFilter, ignoreCase = true)
    }

    fun releasePlayer() {
        runCatching {
            if (isPlaying) player.stop()
            player.reset()
        }
        playingUri = null
        isPlaying = false
    }

    DisposableEffect(Unit) {
        onDispose { releasePlayer() }
    }



    fun resetFolder() {
        CallRecorder.setFolderTree(context, null, null)
        folderLabel = CallRecorder.folderDisplayPath(context)
        load()
    }

    fun deleteItem(item: RecordingItem) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (item.uri.scheme == "file") {
                    runCatching { File(item.uri.path ?: "").delete() }
                } else {
                    val authority = item.uri.authority
                    if (authority != null && authority.contains(".documents")) {
                        runCatching {
                            android.provider.DocumentsContract.deleteDocument(
                                context.contentResolver, item.uri
                            )
                        }
                    } else {
                        runCatching { context.contentResolver.delete(item.uri, null, null) }
                    }
                }
            }
            if (playingUri == item.uri.toString()) releasePlayer()
            load()
        }
    }

    LaunchedEffect(Unit) { load() }

    // Progreso en vivo del reproductor mientras suena.
    LaunchedEffect(isPlaying, playingUri) {
        while (isPlaying) {
            positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
            durationMs = runCatching { player.duration.toLong() }.getOrDefault(0L)
            delay(500)
        }
    }

    fun togglePlay(item: RecordingItem) {
        if (playingUri == item.uri.toString()) {
            if (isPlaying) {
                runCatching { player.pause() }
                isPlaying = false
            } else {
                runCatching { player.start() }
                isPlaying = true
            }
            return
        }
        scope.launch {
            runCatching {
                player.reset()
                player.setDataSource(context, item.uri)
                player.prepare()
                player.start()
            }.onFailure {
                Toast_RecordMsg(context, "No se pudo reproducir")
                releasePlayer()
            }
            if (isPlayingSafe(player)) {
                val dur = runCatching { player.duration.toLong() }.getOrDefault(0L)
                if (dur <= 0) {
                    // El archivo existe pero no tiene audio decodificable.
                    Toast_RecordMsg(context, "Audio inválido o vacío: mira su ubicación")
                    releasePlayer()
                    return@launch
                }
                playingUri = item.uri.toString()
                isPlaying = true
                durationMs = dur
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Grabaciones de llamadas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { load() }) {
                AppIcon(Icons.Default.Refresh, contentDescription = null, size = 18.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Actualizar")
            }
            TextButton(onClick = { showLog = true }) {
                AppIcon(Icons.Default.Info, contentDescription = null, size = 18.dp)
                Spacer(modifier = Modifier.width(2.dp))
                Text("Registro")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Buscar por número…") },
            leadingIcon = { AppIcon(Icons.Default.Search, contentDescription = null, size = 20.dp) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        AppIcon(Icons.Default.Close, contentDescription = "Limpiar", size = 18.dp)
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Dónde se guardan las grabaciones (carpeta elegida por el usuario).
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 18.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Se guardan en:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            folderLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.weight(1f)) {
                        AppIcon(Icons.Default.CreateNewFolder, contentDescription = null, size = 16.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cambiar carpeta")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { resetFolder() },
                        enabled = hasCustomFolder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Predeterminada")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (playingUri != null) {
            val current = recordings?.firstOrNull { it.uri.toString() == playingUri }
            if (current != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Reproduciendo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            current.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (durationMs > 0) {
                                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        when {
            error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error al leer grabaciones", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(error ?: "", style = MaterialTheme.typography.bodySmall)
                }
            }
            recordings == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            recordings!!.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AppIcon(
                        Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 40.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Aún no hay grabaciones.\nCuando una llamada se conecte se guardará en Descargas/llamadas de troncal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Diagnóstico",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            DiagnosticRow(ok = recEnabled, text = "Grabación de llamadas activa (Ajustes)")
                            DiagnosticRow(ok = micGranted, text = "Permiso de micrófono concedido")
                            DiagnosticRow(ok = isDialerDefault, text = "App de llamadas predeterminada")
                            DiagnosticRow(ok = recError == null, text = "Última grabación: ${recError ?: "correcta"} ")
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (!recEnabled) {
                        Text(
                            "Activa «Grabación de llamadas» en Ajustes.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (!micGranted) {
                        Button(onClick = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }) {
                            Text("Conceder micrófono")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    if (!isDialerDefault) {
                        Button(onClick = { roleLauncher.launch(requestDialerRoleIntent(context)) }) {
                            Text("Establecer como app de llamadas")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val list = filtered.orEmpty()
                    if (list.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Sin resultados para «$queryFilter»",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(list, key = { it.uri.toString() }) { item ->
                            RecordingRow(
                                item = item,
                                isThisPlaying = playingUri == item.uri.toString(),
                                isPlaying = isPlaying,
                                timeFormat = timeFormat,
                                dateFormat = dateFormat,
                                onToggle = { togglePlay(item) },
                                onDelete = { deleteTarget = item },
                                onReveal = { revealInFileManager(context, item) },
                                onOpenExternal = { openWithSystemPlayer(context, item) }
                            )
                        }
                    }
                }
            }
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Eliminar grabación") },
                text = {
                    Text(
                        "Se borrará del dispositivo la grabación:\n${friendlyName(target.name)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                confirmButton = {
                    TextButton(onClick = { deleteTarget = null; deleteItem(target) }) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showLog) {
            AlertDialog(
                onDismissRequest = { showLog = false },
                title = { Text("Registro de grabación") },
                text = {
                    Column {
                        Text(
                            "Eventos de esta sesión de grabación (a través del servicio en primer plano).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val lines = CallRecorderLog.content(context)
                        if (lines.isBlank()) {
                            Text(
                                "Sin eventos todavía.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                lines,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 24,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}

@Composable
private fun DiagnosticRow(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            size = 16.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun isPlayingSafe(player: MediaPlayer): Boolean =
    runCatching { player.isPlaying }.getOrDefault(false)

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
private fun RecordingRow(
    item: RecordingItem,
    isThisPlaying: Boolean,
    isPlaying: Boolean,
    timeFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onReveal: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isThisPlaying) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isThisPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        if (isThisPlaying && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isThisPlaying) "Pausar" else "Reproducir",
                        tint = if (isThisPlaying) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                        size = 20.dp
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    friendlyName(item.name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${dateFormat.format(Date(item.dateAddedMs))} · ${formatSize(item.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val timeInName = timeInName(item.name)
                if (timeInName != null) {
                    Text(
                        "Hora de la llamada: $timeInName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                val folder = item.folderPath
                if (folder != null && folder.isNotBlank()) {
                    Text(
                        folder,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onReveal) {
                    AppIcon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Ver en archivos",
                        tint = MaterialTheme.colorScheme.primary,
                        size = 20.dp
                    )
                }
                IconButton(onClick = onOpenExternal) {
                    AppIcon(
                        Icons.Default.OpenInNew,
                        contentDescription = "Abrir con otro reproductor",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 18.dp
                    )
                }
            }
            IconButton(onClick = onDelete) {
                AppIcon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 20.dp
                )
            }
        }
    }
}

// Abre la carpeta donde está la grabación en el gestor de archivos del sistema
// (DocumentsUI). Si no se puede abrir la carpeta exacta, abre el almacenamiento
// interno y muestra la ruta completa en un aviso para que sepas dónde está.
fun revealInFileManager(context: Context, item: RecordingItem) {
    // Carpeta elegida por el usuario: abrimos el directorio dentro del árbol.
    val tree = item.treeUri
    val folderDoc = item.folderDocId
    if (tree != null && folderDoc != null) {
        try {
            val dirUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, folderDoc
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    dirUri,
                    android.provider.DocumentsContract.Document.MIME_TYPE_DIR
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }.onSuccess { return }
        } catch (_: Exception) {
        }
        openStorageRoot(context)
        Toast_RecordMsg(context, "Ubicación: ${item.folderPath ?: folderDoc}")
        return
    }
    val folder = item.folderPath?.takeIf { it.isNotBlank() } ?: "Download/llamadas de troncal"
    val docBase = if (folder.startsWith("file:/") || folder.startsWith("/")) {
        "Download/llamadas de troncal"
    } else {
        folder
    }
    val docId = "primary:${docBase.trimStart('\\', '/').replace('\\', '/')}"
    try {
        val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download"
        )
        val dirUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, docId
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                dirUri,
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .getOrElse {
                openStorageRoot(context)
                Toast_RecordMsg(context, "Ubicación: $docBase")
            }
    } catch (_: Exception) {
        openStorageRoot(context)
        Toast_RecordMsg(context, "Ubicación: $docBase")
    }
}

private fun openStorageRoot(context: Context) {
    runCatching {
        val rootUri = android.provider.DocumentsContract.buildRootUri(
            "com.android.externalstorage.documents",
            "primary"
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(rootUri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }
}

// Reproduce el archivo con el reproductor del sistema (prueba externa de que el
// audio es válido y está completo).
fun openWithSystemPlayer(context: Context, item: RecordingItem) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast_RecordMsg(context, "No hay reproductor para esta grabación")
    }
}

// `04122873738_-14-30-25.m4a` -> "04122873738 · 14:30:25"
private fun friendlyName(name: String): String {
    val base = stripExt(name)
    val idx = base.lastIndexOf("_-")
    if (idx > 0) {
        val num = base.substring(0, idx)
        val time = base.substring(idx + 2).replace("-", ":")
        return "$num · $time"
    }
    return base
}

private fun timeInName(name: String): String? {
    val base = stripExt(name)
    val idx = base.lastIndexOf("_-")
    if (idx > 0) {
        return base.substring(idx + 2).replace("-", ":")
    }
    return null
}

private fun stripExt(name: String): String =
    name.removeSuffix(".m4a").removeSuffix(".amr").removeSuffix(".3gp")

private fun formatSize(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1000.0)
        else -> "$bytes B"
    }

private fun Toast_RecordMsg(context: Context, message: String) {
    runCatching {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}