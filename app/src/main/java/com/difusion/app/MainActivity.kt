package com.difusion.app


import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.difusion.app.data.Conversation
import com.difusion.app.data.Contact
import com.difusion.app.data.SendRecord
import com.difusion.app.data.ThreadResolver
import com.difusion.app.import.Importer
import com.difusion.app.import.ParsedRow
import com.difusion.app.report.ReportExporter
import com.difusion.app.service.AppFeedback
import com.difusion.app.service.CallBluetooth
import com.difusion.app.service.CallMonitor
import com.difusion.app.storage.RecordStore
import com.difusion.app.ui.*
import com.difusion.app.ui.theme.ThemeConfig
import com.difusion.app.ui.theme.appPadding
import com.difusion.app.ui.theme.appRadius
import com.difusion.app.ui.theme.appShape
import com.difusion.app.ui.theme.exportConfig
import com.difusion.app.ui.theme.installCustomFont
import com.difusion.app.ui.theme.readConfigFromUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.difusion.app.service.CallMonitor.setContext(applicationContext)
        com.difusion.app.service.CallMonitor.onRedialRequested = { phone ->
            redialWithOtherSim(this, phone)
        }
        requestOpenSequenceFromIntent(intent)
        setContent {
            val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            val accent = Color(themeConfig.accent)
            val accentDark = accent.luminance() < 0.5f
            val bgArgb = Color(themeConfig.background).toArgb()
            SideEffect {
                if (themeConfig.statusBarTint) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(accent.toArgb(), accent.toArgb()) {
                            themeConfig.darkMode || accentDark
                        },
                        navigationBarStyle = SystemBarStyle.auto(bgArgb, bgArgb) { themeConfig.darkMode }
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) {
                            themeConfig.darkMode
                        },
                        navigationBarStyle = SystemBarStyle.auto(bgArgb, bgArgb) { themeConfig.darkMode }
                    )
                }
            }
            com.difusion.app.ui.theme.DifusionTheme(config = themeConfig) {
                // Escala de letra e iconos configurables (Apariencia), con base
                // uniforme para que el texto se vea proporcional en todo dispositivo.
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = themeConfig.textScale),
                    LocalIconScale provides themeConfig.iconScale,
                    LocalAppShape provides appShape(themeConfig.cornerStyle),
                    LocalAppRadius provides appRadius(themeConfig.cornerStyle),
                    LocalAppPadding provides appPadding(themeConfig.spacing),
                    LocalUseGradient provides themeConfig.useGradient
                ) {
                    // Términos y condiciones en el primer arranque.
                    val termsPrefs = remember {
                        getSharedPreferences("difusion_prefs", MODE_PRIVATE)
                    }
                    var termsAccepted by remember {
                        mutableStateOf(termsPrefs.getInt("terms_version", 0) >= TERMS_VERSION)
                    }
                    if (termsAccepted) {
                        MainScreen(viewModel)
                    } else {
                        TermsScreen(onAccept = {
                            termsPrefs.edit().putInt("terms_version", TERMS_VERSION).apply()
                            termsAccepted = true
                        })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestOpenSequenceFromIntent(intent)
    }

    private fun requestOpenSequenceFromIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(
                com.difusion.app.service.SequenceNotification.EXTRA_OPEN_SEQUENCE, false
            ) == true
        ) {
            MainActivityDelegate.openSequence.value = true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_CALLS_TAB, false) == true) {
            MainActivityDelegate.openCallsTab.value = true
        }
    }

}

// Señal de "reabrir la lista de la secuencia" pedida desde la notificación.
const val EXTRA_OPEN_CALLS_TAB = "com.difusion.app.extra.OPEN_CALLS_TAB"

object MainActivityDelegate {
    val openSequence = kotlinx.coroutines.flow.MutableStateFlow(false)
    val openCallsTab = kotlinx.coroutines.flow.MutableStateFlow(false)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()

    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val selected by viewModel.selectedContacts.collectAsStateWithLifecycle()
    val messageBody by viewModel.messageBody.collectAsStateWithLifecycle()
    val delayMs by viewModel.delayMs.collectAsStateWithLifecycle()
    val safeMode by viewModel.safeMode.collectAsStateWithLifecycle()
    val callDelayMs by viewModel.callDelayMs.collectAsStateWithLifecycle()
    val ringDurationMs by viewModel.ringDurationMs.collectAsStateWithLifecycle()
    val maxCallMs by viewModel.maxCallMs.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var recordingEnabled by remember { mutableStateOf(com.difusion.app.service.CallRecorder.isEnabled(context)) }

    // APK ya descargado a la espera de que el usuario permita instalar apps
    // desconocidas (se reintenta al volver a la app).
    var pendingApk by remember { mutableStateOf<File?>(null) }

    // Si la secuencia sigue corriendo (se reabrió la app mientras avanzaba),
    // la pantalla de llamadas se muestra desde el primer momento.
    var showCalls by remember { mutableStateOf(viewModel.callSequencer.isRunning.value) }

    // Al volver a la app (p. ej. tras terminar una llamada en el dialer del
    // sistema) re-verifica el estado de la secuencia para no quedarse colgada.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.callSequencer.kick()
                overlayGranted = Settings.canDrawOverlays(context)
                micGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                // Si quedó una actualización descargada, se instala al conceder
                // el permiso de "instalar apps desconocidas".
                pendingApk?.let { apk ->
                    if (com.difusion.app.service.UpdateManager.canInstallPackages(context)) {
                        pendingApk = null
                        com.difusion.app.service.UpdateManager.installApk(context, apk)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Cuando corre la secuencia masiva, una llamada no debe abrir la pantalla
    // completa del centro de llamadas: se mantiene el riel lateral en la lista.
    val sequenceRunning by viewModel.callSequencer.isRunning.collectAsStateWithLifecycle()
    LaunchedEffect(sequenceRunning) {
        CallMonitor.notifyFullScreen = !sequenceRunning
        if (sequenceRunning) {
            CallMonitor.clear()
        }
    }

    // Si la app se abre desde el marcador (tecla llamada / DIAL del sistema)
    // llevamos al usuario al centro de llamadas.
    LaunchedEffect(Unit) {
        val action = findActivityInternal(context)?.intent?.action
        if (action == android.content.Intent.ACTION_DIAL || action == "android.intent.action.CALL_PRIVILEGED") {
            openCallCenter(context)
        }
    }

    // Si la secuencia ya estaba en curso al abrir (o se reabre desde la
    // notificación), mostramos la lista de llamadas desde el primer momento.
    LaunchedEffect(Unit) {
        if (viewModel.callSequencer.isRunning.value) {
            showCalls = true
        }
    }
    val openCallsTab by MainActivityDelegate.openCallsTab.collectAsStateWithLifecycle()
    LaunchedEffect(openCallsTab) {
        if (openCallsTab) {
            MainActivityDelegate.openCallsTab.value = false
            navController.navigate("calls") {
                popUpTo("calls") { inclusive = true }
            }
        }
    }

    // Selector de hoja y revisión de importación como diálogos DENTRO de la app
    // (no dependen del permiso de superposición del sistema).
    val driveSheets by viewModel.driveSheets.collectAsStateWithLifecycle()
    val driveSelectedSheetIndex by viewModel.driveSelectedSheetIndex.collectAsStateWithLifecycle()
    if (driveSheets.isNotEmpty()) {
        SheetPickerDialog(
            sheets = driveSheets,
            selectedIndex = driveSelectedSheetIndex,
            onPick = { viewModel.selectDriveSheet(it) },
            onFinalize = { viewModel.finalizeSyncFromDrive() },
            onCancel = { viewModel.cancelDriveSync() }
        )
    }

    val drivePreviewRows by viewModel.drivePreviewRows.collectAsStateWithLifecycle()
    if (drivePreviewRows.isNotEmpty()) {
        ImportReviewDialog(
            rows = drivePreviewRows,
            onImport = { chosen -> viewModel.commitDriveImport(chosen) },
            onCancel = { viewModel.cancelDrivePreview() }
        )
    }
val openSeq by MainActivityDelegate.openSequence.collectAsStateWithLifecycle()
    LaunchedEffect(openSeq) {
        if (openSeq) {
            MainActivityDelegate.openSequence.value = false
            if (viewModel.callSequencer.isRunning.value) {
                showCalls = true
            }
        }
    }

    var wasSending by remember { mutableStateOf(false) }
    LaunchedEffect(isSending) {
        if (wasSending && !isSending) {
            AppFeedback.notify(context, viewModel.themeConfig.value)
        }
        wasSending = isSending
    }

    val sendRecords by viewModel.sendRecords.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()

    val needPhoneRepair by viewModel.needPhoneRepair.collectAsStateWithLifecycle()
    var showRepairDialog by remember { mutableStateOf(false) }

    // Cada vez que se abre la app, revisamos si hay números con el 0 faltante.
    LaunchedEffect(Unit) {
        viewModel.requestPhoneRepairCheck()
    }
    LaunchedEffect(needPhoneRepair) {
        if (needPhoneRepair) {
            showRepairDialog = true
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEmailToSelected by remember { mutableStateOf(false) }
    var emailProgressHidden by remember { mutableStateOf(false) }
    var importedRows by remember { mutableStateOf<List<ParsedRow>>(emptyList()) }
    var showImport by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var showSendScreen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showNewMessage by remember { mutableStateOf(false) }
    var showManualNumber by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showUsers by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var openThread by remember { mutableStateOf<Conversation?>(null) }

    var pendingSingleCall by remember { mutableStateOf<Contact?>(null) }

    var backupConfigured by remember { mutableStateOf(RecordStore.isConfigured(context)) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.CALL_PHONE] == true && grants[Manifest.permission.READ_PHONE_STATE] == true) {
            val single = pendingSingleCall
            pendingSingleCall = null
            if (single != null) {
                val intent = android.content.Intent(android.content.Intent.ACTION_CALL)
                intent.data = android.net.Uri.parse("tel:${com.difusion.app.import.Importer.normalizePhone(single.phone)}")
                try { context.startActivity(intent) } catch (_: Exception) {}
            } else {
                showCalls = true
            }
        } else {
            Toast.makeText(context, "Se necesita permiso para llamar y conocer el estado de la llamada", Toast.LENGTH_LONG).show()
        }
    }

    val sendSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showSendScreen = true
        } else {
            Toast.makeText(context, "Permiso para enviar SMS no concedido. Actívalo en Ajustes del sistema > Difusión > Permisos.", Toast.LENGTH_LONG).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        showSendScreen = true
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
    }

    fun openSendScreen() {
        showSendScreen = true
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun findContactName(phone: String): String {
        val digit = phone.replace(Regex("[^0-9+]"), "")
        return contacts.firstOrNull {
            it.phone.replace(Regex("[^0-9+]"), "") == digit && it.name.isNotBlank()
        }?.name ?: phone
    }

    fun openNewChat(address: String, name: String?) {
        val normalized = Importer.normalizePhone(address)
        val displayName = name?.takeIf { it.isNotBlank() } ?: findContactName(normalized)
        openThread = Conversation(
            threadId = ThreadResolver.resolve(context, normalized),
            address = normalized,
            name = displayName,
            lastBody = "",
            lastDate = System.currentTimeMillis(),
            lastStatus = -1,
            unreadCount = 0
        )
    }

    val pickPhoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            val info = queryContactPhone(context, uri)
            if (info != null) {
                openNewChat(info.first, info.second)
            } else {
                Toast.makeText(context, "El contacto no tiene número de teléfono", Toast.LENGTH_LONG).show()
            }
        }
    }

    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val name = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull() ?: "Respaldo"
            RecordStore.saveFolder(context, uri, name)
            backupConfigured = true
            Toast.makeText(context, "Carpeta de respaldo: $name", Toast.LENGTH_LONG).show()
        }
    }

    fun openBackupFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        backupFolderLauncher.launch(intent)
    }

    fun exportBackup() {
        scope.launch {
            RecordStore.exportAll(context, sendRecords, viewModel.callRecords.value)
            Toast.makeText(
                context,
                "Registros guardados en la carpeta de respaldo",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val readSmsPrefs = context.getSharedPreferences("difusion_prefs", Context.MODE_PRIVATE)
    val readSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.reloadInbox()
            Toast.makeText(context, "Lectura de mensajes habilitada", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Sin permiso de lectura no se mostrarán los chats. Actívalo en Ajustes del sistema > Difusión > Permisos.", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED &&
            !readSmsPrefs.getBoolean("asked_read_sms", false)
        ) {
            readSmsPrefs.edit().putBoolean("asked_read_sms", true).apply()
            readSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    // Secuencia de roles predeterminados: 0 = inactivo, 1 = SMS, 2 = Teléfono.
    var roleStep by remember { mutableStateOf(0) }

    val defaultSmsRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Al terminar el rol de SMS, si falta el de teléfono se pide enseguida.
        if (!isDefaultDialerApp(context)) {
            roleStep = 2
        } else {
            roleStep = 0
            Toast.makeText(context, "Mensajes y llamadas predeterminadas listas", Toast.LENGTH_LONG).show()
        }
    }

    val dialerRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        roleStep = 0
        val sms = isDefaultSmsApp(context)
        val dial = isDefaultDialerApp(context)
        Toast.makeText(
            context,
            when {
                sms && dial -> "Listo: Mensajes y Llamadas son Difusión"
                dial -> "Llamadas predeterminadas listas. Falta Mensajes."
                else -> "No se aplicó. Revisa Ajustes > Aplicaciones > Aplicaciones predeterminadas."
            },
            Toast.LENGTH_LONG
        ).show()
    }

    fun makeDefaultMessagingAndDialer() {
        when {
            !isDefaultSmsApp(context) -> roleStep = 1
            !isDefaultDialerApp(context) -> roleStep = 2
            else -> Toast.makeText(context, "Mensajes y llamadas ya son Difusión", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(roleStep) {
        when (roleStep) {
            1 -> makeDefaultSmsApp(context, defaultSmsRoleLauncher)
            2 -> makeDefaultDialerApp(context, dialerRoleLauncher)
        }
    }

    // --- Todos los permisos de runtime de una sola vez ---
    val allPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val denied = grants.filterValues { !it }.keys
        if (denied.isEmpty()) {
            Toast.makeText(context, "Todos los permisos concedidos", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                context,
                "Quedaron ${denied.size} permisos sin conceder. Actívalos en Ajustes del sistema > Difusión > Permisos.",
                Toast.LENGTH_LONG
            ).show()
        }
        micGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        overlayGranted = Settings.canDrawOverlays(context)
    }

    fun requestAllPermissions() {
        val missing = APP_RUNTIME_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            Toast.makeText(context, "Todos los permisos ya están concedidos", Toast.LENGTH_SHORT).show()
        } else {
            allPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    // --- Permisos especiales (superposición + pantalla completa) ---
    var specialStep by remember { mutableStateOf(0) }

    val overlaySpecialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= 34 && !hasFullScreenIntent(context)) {
            specialStep = 2
        } else {
            specialStep = 0
            overlayGranted = Settings.canDrawOverlays(context)
            Toast.makeText(context, "Permisos especiales revisados", Toast.LENGTH_SHORT).show()
        }
    }

    val fullScreenSpecialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        specialStep = 0
        Toast.makeText(context, "Permisos especiales revisados", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(specialStep) {
        when (specialStep) {
            1 -> runCatching {
                overlaySpecialLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
            2 -> runCatching {
                fullScreenSpecialLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }
    }

    fun requestSpecialPermissions() {
        when {
            !Settings.canDrawOverlays(context) -> specialStep = 1
            Build.VERSION.SDK_INT >= 34 && !hasFullScreenIntent(context) -> specialStep = 2
            else -> Toast.makeText(context, "Los permisos especiales ya están activados", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Buscar actualizaciones (descarga e instala con un toque) ---
    var updateStatus by remember { mutableStateOf("") }
    // Versión publicada en el repositorio (se lee al buscar), para mostrarla
    // siempre real y no un texto fijo.
    var repoVersion by remember { mutableStateOf("") }

    fun checkForUpdates() {
        if (updateStatus.startsWith("Descargando")) return
        updateStatus = "Buscando actualizaciones…"
        scope.launch {
            val info = com.difusion.app.service.UpdateManager.fetchLatest()
            if (info == null) {
                updateStatus = "No se pudo comprobar. Revisa tu conexión."
                return@launch
            }
            repoVersion = info.versionName
            val installed = com.difusion.app.service.UpdateManager.currentVersionName
            if (!com.difusion.app.service.UpdateManager.isNewer(info.versionName, installed)) {
                updateStatus = "Estás al día: instalada $installed · repositorio ${info.versionName}."
                return@launch
            }
            updateStatus = "Descargando ${info.versionName} (tienes $installed)…"
            try {
                val apk = com.difusion.app.service.UpdateManager.downloadApk(context, info.apkUrl)
                if (com.difusion.app.service.UpdateManager.canInstallPackages(context)) {
                    updateStatus = "Descarga lista. Confirma la instalación."
                    com.difusion.app.service.UpdateManager.installApk(context, apk)
                } else {
                    pendingApk = apk
                    updateStatus = "Activa \"Instalar apps desconocidas\" y vuelve a la app."
                    Toast.makeText(
                        context,
                        "Permite instalar apps desconocidas para Difusión y vuelve a la app.",
                        Toast.LENGTH_LONG
                    ).show()
                    com.difusion.app.service.UpdateManager.openInstallPermissionSettings(context)
                }
            } catch (e: Exception) {
                updateStatus = "Error al descargar: ${e.message}"
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                try {
                    val rows = Importer.parseFile(context, uri)
                    importedRows = rows
                    showImport = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al leer el archivo: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    importing = false
                }
            }
        }
    }

    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val ok = installCustomFont(context, uri)
            if (ok) {
                viewModel.setThemeConfig(viewModel.themeConfig.value.copy(font = "custom"))
                Toast.makeText(context, "Fuente instalada y aplicada", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "No se pudo instalar la fuente", Toast.LENGTH_LONG).show()
            }
        }
    }

    val configImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val cfg = readConfigFromUri(context, uri)
            if (cfg != null) {
                viewModel.setThemeConfig(cfg)
                Toast.makeText(context, "Configuración de apariencia importada", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "El archivo no es una configuración válida", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun callContact(contact: Contact, simHandle: android.telecom.PhoneAccountHandle? = null) {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCallPermission) {
            val handle = simHandle ?: com.difusion.app.service.SimManager.getDefaultHandle(context)
            com.difusion.app.service.CallMonitor.setSimHandle(handle)
            com.difusion.app.service.SimManager.placeCall(
                context,
                com.difusion.app.import.Importer.normalizePhone(contact.phone),
                handle
            )
        } else {
            pendingSingleCall = contact
            callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
        }
    }

    fun callPhone(name: String, phone: String, simHandle: android.telecom.PhoneAccountHandle? = null) {
        callContact(Contact(name = name, phone = phone), simHandle)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "contacts"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentRoute == "inbox",
                    onClick = {
                        navController.navigate("inbox") {
                            popUpTo("inbox") { inclusive = true }
                        }
                    },
                    icon = { AppIcon(Icons.Default.Sms, contentDescription = null) },
                    label = null
                )
                NavigationBarItem(
                    selected = currentRoute == "calls",
                    onClick = {
                        navController.navigate("calls") {
                            popUpTo("calls") { inclusive = true }
                        }
                    },
                    icon = { AppIcon(Icons.Default.Phone, contentDescription = null) },
                    label = null
                )
                NavigationBarItem(
                    selected = currentRoute == "contacts",
                    onClick = {
                        navController.navigate("contacts") {
                            popUpTo("contacts") { inclusive = true }
                        }
                    },
                    icon = { AppIcon(Icons.Default.Contacts, contentDescription = null) },
                    label = null
                )
                NavigationBarItem(
                    selected = currentRoute == "editor",
                    onClick = {
                        if (viewModel.isSendingValue) {
                            showSendScreen = true
                        } else {
                            navController.navigate("editor") {
                                popUpTo("editor") { inclusive = true }
                            }
                        }
                    },
                    icon = {
                        Box {
                            AppIcon(Icons.Default.Edit, contentDescription = null)
                            if (isSending) {
                                Badge(modifier = Modifier.align(Alignment.TopEnd)) {}
                            }
                        }
                    },
                    label = null
                )
                NavigationBarItem(
                    selected = currentRoute == "history",
                    onClick = {
                        navController.navigate("history") {
                            popUpTo("history") { inclusive = true }
                        }
                    },
                    icon = { AppIcon(Icons.Default.History, contentDescription = null) },
                    label = null
                )
                NavigationBarItem(
                    selected = currentRoute == "recordings",
                    onClick = {
                        navController.navigate("recordings") {
                            popUpTo("recordings") { inclusive = true }
                        }
                    },
                    icon = { AppIcon(Icons.Default.Mic, contentDescription = null) },
                    label = null
                )
                NavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo("settings") { inclusive = true }
                        }
                    },
                    icon = { AppIcon(Icons.Default.Settings, contentDescription = null) },
                    label = null
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = "inbox") {
                composable("inbox") {
                    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
                    InboxScreen(
                        conversations = conversations,
                        onOpenThread = { conv ->
                            openThread = conv
                            viewModel.markThreadRead(conv.threadId)
                        },
                        onNewMessage = { showNewMessage = true },
                        onReload = {
                            viewModel.reloadInbox { n ->
                                Toast.makeText(
                                    context,
                                    if (n > 0) "Se cargaron $n mensajes nuevos" else "Sin mensajes nuevos",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onResendSelected = { selected ->
                            viewModel.resendFailedForThreads(selected) { sent, skipped ->
                                val base = if (sent > 0) "Se reenviaron $sent mensajes"
                                else "No había mensajes fallidos en los chats seleccionados"
                                val extra = if (skipped > 0 && sent > 0) " · $skipped sin pendientes" else ""
                                Toast.makeText(context, base + extra, Toast.LENGTH_LONG).show()
                            }
                        },
                        onDeleteSelected = { selected ->
                            viewModel.moveThreadsToTrash(selected.map { it.threadId })
                            Toast.makeText(
                                context,
                                "${selected.size} chat(s) movidos a la papelera",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onOpenTrash = { showTrash = true },
                        onOpenCalls = { showCalls = true }
                    )
                }
                composable("contacts") {
                    val users by viewModel.users.collectAsStateWithLifecycle()
                    ContactsScreen(
                        contacts = contacts,
                        selected = selected,
                        users = users,
                        onToggle = { viewModel.toggleContact(it) },
                        onSelectAll = { viewModel.selectAll(it) },
                        onClearSelection = { viewModel.clearSelection() },
                        onAddManual = { showAddDialog = true },
                        onImport = {
                            filePicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel"))
                        },
                        onExportContacts = {
                            exportContacts(context, contacts.filter { selected.contains(it.id) })
                        },
                        onExportVisible = { list -> exportContacts(context, list) },
                        onExportTemplate = {
                            exportTemplate(context)
                        },
                        onCallContact = { contact ->
                            callContact(contact)
                        },
                        onCallSelected = {
                            if (selected.isEmpty()) {
                                Toast.makeText(context, "Selecciona al menos un contacto", Toast.LENGTH_LONG).show()
                            } else {
                                val hasCallPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CALL_PHONE
                                ) == PackageManager.PERMISSION_GRANTED &&
                                        ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.READ_PHONE_STATE
                                        ) == PackageManager.PERMISSION_GRANTED
                                if (hasCallPermission) {
                                    showCalls = true
                                } else {
                                    callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
                                }
                            }
                        },
                        onEmailSelected = {
                            if (selected.isEmpty()) {
                                Toast.makeText(context, "Selecciona al menos un contacto", Toast.LENGTH_LONG).show()
                            } else {
                                showEmailToSelected = true
                            }
                        },
                        onDeleteSelected = {
                            if (selected.isEmpty()) {
                                Toast.makeText(context, "Selecciona al menos un contacto", Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.deleteContacts(selected)
                                Toast.makeText(context, "Contactos eliminados", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onExportByAssignment = { exportContactsByAssignment(context, contacts) },
                        onAssignUser = { ids, userName ->
                            viewModel.assignUserToContacts(userName, ids)
                            Toast.makeText(
                                context,
                                "${ids.size} contactos asignados a $userName",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onClearAssignment = { ids ->
                            viewModel.clearAssignment(ids)
                            Toast.makeText(context, "Asignación quitada (${ids.size} contactos)", Toast.LENGTH_SHORT).show()
                        },
                        onManageUsers = { showUsers = true },
                        hasAssignment = contacts.any { it.assignment.isNotBlank() }
                    )
                }
                composable("editor") {
                  var msgTab by remember { mutableStateOf(0) }
                  Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = msgTab) {
                        Tab(selected = msgTab == 0, onClick = { msgTab = 0 }, text = { Text("Redactar") })
                        Tab(selected = msgTab == 1, onClick = { msgTab = 1 }, text = { Text("Correo") })
                    }
                    Box(Modifier.weight(1f)) {
                    if (msgTab == 0) {
                    MessageEditorScreen(
                        messageBody = messageBody,
                        onMessageChange = { viewModel.setMessageBody(it) },
                        templates = templates,
                        onSaveTemplate = { name, body, subject ->
                            scope.launch { viewModel.saveTemplate(name, body, subject) }
                        },
                        onDeleteTemplate = {
                            scope.launch { viewModel.deleteTemplate(it) }
                        },
                        selectedCount = selected.size,
                        showSmsCounter = viewModel.themeConfig.collectAsStateWithLifecycle().value.showSmsCounter,
                        onSend = {
                            if (viewModel.isSendingValue) {
                                // Ya hay un envío en segundo plano: solo vemos su progreso.
                                showSendScreen = true
                            } else if (selected.isEmpty()) {
                                Toast.makeText(context, "Selecciona al menos un contacto", Toast.LENGTH_LONG).show()
                            } else if (ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.SEND_SMS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                openSendScreen()
                            } else {
                                sendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            }
                        },
                        emailAvailable = viewModel.emailSyncEnabled.collectAsStateWithLifecycle().value,
                        onSendEmail = { subject, body, mode, emails ->
                            val sel = contacts.filter { selected.contains(it.id) }
                            val contactEmails = sel.mapNotNull {
                                it.email.trim().takeIf { e -> e.isNotBlank() }
                            }
                            val recipients = (if (mode == 0) contactEmails else emails).distinct()
                            val hasProvider =
                                com.difusion.app.storage.SmtpPrefs.isConfigured(context) ||
                                    com.difusion.app.service.GmailAuth.token(context) != null ||
                                    com.difusion.app.storage.EmailSyncPrefs.getUrl(context).isNotBlank()
                            if (!hasProvider) {
                                Toast.makeText(
                                    context,
                                    "Configura tu correo primero en Mensaje ▸ Correo.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (recipients.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Agrega correos precargados o selecciona contactos con correo.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                emailProgressHidden = false
                                viewModel.sendBulkEmailBackground(recipients, subject, body)
                            }
                        },
                        onSchedule = { subject, body, channel, at, mode, emails ->
                            val sel = contacts.filter { selected.contains(it.id) }
                            val useContacts = mode == 0
                            if (at <= System.currentTimeMillis()) {
                                Toast.makeText(context, "Elige una fecha y hora futuras", Toast.LENGTH_LONG).show()
                            } else if ((useContacts && sel.isEmpty()) || (!useContacts && emails.isEmpty())) {
                                Toast.makeText(context, "Selecciona contactos o agrega correos", Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.scheduleSend(
                                    if (useContacts) sel else emptyList(),
                                    body, subject, channel, at,
                                    if (useContacts) emptyList() else emails
                                )
                                Toast.makeText(context, "Envío programado", Toast.LENGTH_LONG).show()
                            }
                        },
                        scheduledSends = viewModel.scheduledSends.collectAsStateWithLifecycle().value,
                        onCancelScheduled = { item ->
                            viewModel.cancelScheduledSend(item)
                            Toast.makeText(context, "Envío cancelado", Toast.LENGTH_SHORT).show()
                        }
                    )
                    } else {
                        CorreoInboxScreen()
                    }
                    }
                  }
                }
                composable("history") {
                    val callRecords by viewModel.callRecords.collectAsStateWithLifecycle()
                    val failureCounts by viewModel.failureBreakdown.collectAsStateWithLifecycle()
                    HistoryScreen(
                        records = sendRecords,
                        callRecords = callRecords,
                        failureCounts = failureCounts,
                        onExportExcel = { exportHistory(context, sendRecords, contacts) },
                        onExportBackup = { exportBackup() },
                        onSetLabel = { callId, label ->
                            viewModel.setCallLabel(callId, label)
                        },
                        folderConfigured = backupConfigured,
                        onConfigureFolder = { openBackupFolderPicker() },
                        scheduledSends = viewModel.scheduledSends.collectAsStateWithLifecycle().value,
                        onCancelScheduled = { viewModel.cancelScheduledSend(it) },
                        onDeleteScheduled = { viewModel.deleteScheduledSend(it) },
                        onClearSentScheduled = { viewModel.clearSentScheduled() }
                    )
                }
                composable("calls") {
                    val callRecords by viewModel.callRecords.collectAsStateWithLifecycle()
                    CallsSection(
                        callRecords = callRecords,
                        onCallNumber = { n ->
                            callPhone(findContactName(n), n)
                        },
                        onRedial = { call ->
                            callPhone(call.contactName, call.phone)
                        },
                        onSetLabel = { callId, label ->
                            viewModel.setCallLabel(callId, label)
                        }
                    )
                }
                composable("recordings") {
                    RecordingsTab()
                }
                composable("settings") {
                    SettingsScreen(
                        delaySeconds = (delayMs / 1000).toInt(),
                        onDelayChange = { viewModel.setDelay(it) },
                        safeMode = safeMode,
                        onSafeModeChange = { viewModel.setSafeMode(it) },
                        callDelaySeconds = (callDelayMs / 1000).toInt(),
                        onCallDelayChange = { viewModel.setCallDelay(it) },
                        ringDurationSeconds = (ringDurationMs / 1000).toInt(),
                        onRingDurationChange = { viewModel.setRingDuration(it) },
                        maxCallSeconds = (maxCallMs / 1000).toInt(),
                        onMaxCallChange = { viewModel.setMaxCallDuration(it) },
                        repeatTimes = viewModel.callSequencer.repeatTimes.collectAsStateWithLifecycle().value,
                        onRepeatTimesChange = { viewModel.callSequencer.setRepeatTimes(it) },
                        repeatMode = viewModel.callSequencer.repeatMode.collectAsStateWithLifecycle().value,
                        onRepeatModeChange = { viewModel.callSequencer.setRepeatMode(it) },
                        voiceEnabled = com.difusion.app.service.VoiceMessageStore.isEnabled(context),
                        onVoiceEnabledChange = { enabled ->
                            com.difusion.app.service.VoiceMessageStore.setEnabled(context, enabled)
                        },
                        smsGranted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED,
                        onRequestSmsPermission = { sendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS) },
                        onMakeDefaultSms = { makeDefaultSmsApp(context, defaultSmsRoleLauncher) },
                        onRequestAllPermissions = { requestAllPermissions() },
                        onRequestSpecialPermissions = { requestSpecialPermissions() },
                        onOpenSystemPermissions = { openSystemAppPermissions(context) },
                        fullScreenGranted = hasFullScreenIntent(context),
                        onRepairPhones = {
                            scope.launch {
                                val fixed = viewModel.repairPhonesNow()
                                Toast.makeText(
                                    context,
                                    if (fixed > 0) "$fixed números corregidos con el 0 que faltaba"
                                    else "No se necesitaba corregir ningún número",
                                    Toast.LENGTH_LONG
                                ).show()
                                viewModel.requestPhoneRepairCheck()
                            }
                        },
                        onOpenAppearance = { showAppearance = true },
                        onResetAppearance = {
                            viewModel.setThemeConfig(com.difusion.app.ui.theme.ThemeConfig())
                        },
                        appVersion = com.difusion.app.service.UpdateManager.currentVersionName,
                        repoVersion = repoVersion,
                        updateStatus = updateStatus,
                        onCheckUpdates = { checkForUpdates() },
                        userName = viewModel.appUser.collectAsStateWithLifecycle().value,
                        onUserChange = { viewModel.setAppUser(it) },
                        isDefaultSms = isDefaultSmsApp(context),
                        isDefaultDialer = isDefaultDialerApp(context),
                        onMakeDefaultMessagingAndDialer = { makeDefaultMessagingAndDialer() },
                        bluetoothPrefer = viewModel.preferBluetooth.collectAsStateWithLifecycle().value,
                        onBluetoothPreferChange = { viewModel.setPreferBluetooth(it) },
                        bluetoothDevices = remember {
                            CallBluetooth.connectedBluetoothDevices(context)
                                .map { it.productName ?: "Bluetooth" }
                                .joinToString(", ")
                        },
                        overlayGranted = overlayGranted,
                        onRequestOverlay = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        },
                        recordingEnabled = recordingEnabled,
                        onRecordingEnabledChange = { enabled ->
                            recordingEnabled = enabled
                            com.difusion.app.service.CallRecorder.setEnabled(context, enabled)
                        },
                        micGranted = micGranted,
                        onRequestMic = {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        driveUrl = viewModel.driveUrl.collectAsStateWithLifecycle().value,
                        onDriveUrlChange = { viewModel.setDriveUrl(it) },
                        onScanQr = {
                            scanQrCode(context) { value ->
                                val url = extractHttpUrl(value)
                                if (url != null) {
                                    viewModel.setDriveUrl(url)
                                    Toast.makeText(
                                        context,
                                        "Enlace leído. Sincronizando…",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    viewModel.syncFromDrive()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "El código QR no contiene un enlace",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        driveSyncStatus = viewModel.driveSyncStatus.collectAsStateWithLifecycle().value,
                        onSyncFromDrive = { viewModel.syncFromDrive() },
                        driveSheets = viewModel.driveSheets.collectAsStateWithLifecycle().value,
                        driveSelectedSheetIndex = viewModel.driveSelectedSheetIndex.collectAsStateWithLifecycle().value,
                        onDriveSheetSelect = { viewModel.selectDriveSheet(it) },
                        onFinalizeSyncFromDrive = { viewModel.finalizeSyncFromDrive() },
                        driveSyncUrl = viewModel.driveSyncUrl.collectAsStateWithLifecycle().value,
                        onDriveSyncUrlChange = { viewModel.setDriveSyncUrl(it) },
                        driveSyncToken = viewModel.driveSyncToken.collectAsStateWithLifecycle().value,
                        onDriveSyncTokenChange = { viewModel.setDriveSyncToken(it) },
                        driveSyncEnabled = viewModel.driveSyncEnabled.collectAsStateWithLifecycle().value,
                        onDriveSyncEnabledChange = { viewModel.setDriveSyncEnabled(it) },
                        emailSyncUrl = viewModel.emailSyncUrl.collectAsStateWithLifecycle().value,
                        onEmailSyncUrlChange = { viewModel.setEmailSyncUrl(it) },
                        emailSyncToken = viewModel.emailSyncToken.collectAsStateWithLifecycle().value,
                        onEmailSyncTokenChange = { viewModel.setEmailSyncToken(it) },
                        emailSyncEnabled = viewModel.emailSyncEnabled.collectAsStateWithLifecycle().value,
                        onEmailSyncEnabledChange = { viewModel.setEmailSyncEnabled(it) },
                        onTestEmailConnection = { viewModel.testEmailConnection() }
                    )
                }
            }
        }
    }

    if (showTrash) {
        BackHandler { showTrash = false }
        val trashConversations by viewModel.trashConversations.collectAsStateWithLifecycle()
        TrashScreen(
            conversations = trashConversations,
            onBack = { showTrash = false },
            onRestore = { conv ->
                viewModel.restoreThreadFromTrash(conv.threadId)
                Toast.makeText(context, "Conversación restaurada", Toast.LENGTH_SHORT).show()
            },
            onPurge = { conv ->
                viewModel.purgeThreadFromTrash(conv.threadId)
                Toast.makeText(context, "Chat eliminado definitivamente del dispositivo", Toast.LENGTH_LONG).show()
            },
            onEmptyTrash = {
                viewModel.emptyTrash()
                Toast.makeText(context, "Papelera vaciada: los chats ya no existen en el dispositivo", Toast.LENGTH_LONG).show()
            }
        )
    }

    openThread?.let { conv ->
        BackHandler { openThread = null }
        ConversationScreen(
            conversation = conv,
            viewModel = viewModel,
            onBack = { openThread = null },
            onDelete = {
                viewModel.moveThreadToTrash(conv.threadId)
                Toast.makeText(context, "Chat movido a la papelera", Toast.LENGTH_LONG).show()
                openThread = null
            },
            onCall = { _: String, _: String -> callPhone(conv.name, conv.address) },
            onOpenCalls = { _: String, _: String -> callPhone(conv.name, conv.address) }
        )
    }

    if (showAppearance) {
        BackHandler { showAppearance = false }
        val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()
        AppearanceScreen(
            config = themeConfig,
            onConfigChange = { viewModel.setThemeConfig(it) },
            onBack = { showAppearance = false },
            onPickFont = {
                fontPicker.launch(
                    arrayOf("font/ttf", "font/otf", "application/vnd.ms-opentype", "application/octet-stream", "*/*")
                )
            },
            onExportConfig = {
                val msg = exportConfig(context, themeConfig)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            },
            onImportConfig = {
                configImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            },
            onSaveConfig = {
                // Ya se aplica al instante; aquí se persiste y se confirma.
                viewModel.setThemeConfig(themeConfig)
                Toast.makeText(context, "Apariencia guardada", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showUsers) {
        BackHandler { showUsers = false }
        val users by viewModel.users.collectAsStateWithLifecycle()
        UsersScreen(
            users = users,
            countFor = { user -> contacts.count { it.assignment == user } },
            onAdd = { viewModel.addUser(it) },
            onRename = { old, new -> viewModel.renameUser(old, new) },
            onRemove = {
                viewModel.removeUser(it)
                Toast.makeText(context, "Usuario eliminado", Toast.LENGTH_SHORT).show()
            },
            onBack = { showUsers = false }
        )
    }

    if (showNewMessage) {
        ModalBottomSheet(onDismissRequest = { showNewMessage = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 30.dp)
            ) {
                Text(
                    "Nuevo mensaje",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showNewMessage = false
                            pickPhoneLauncher.launch(
                                Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)
                            )
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Elegir de contactos", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Usa un contacto guardado en el teléfono",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showNewMessage = false
                            showManualNumber = true
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Escribir número", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ingrésalo manualmente en este momento",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showManualNumber) {
        var manualNumber by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualNumber = false },
            title = { Text("Escribir número") },
            text = {
                OutlinedTextField(
                    value = manualNumber,
                    onValueChange = { manualNumber = it },
                    label = { Text("Número telefónico") },
                    placeholder = { Text("Ej: 04121234567") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = manualNumber.trim()
                    if (n.isEmpty()) {
                        Toast.makeText(context, "Escribe un número", Toast.LENGTH_SHORT).show()
                    } else {
                        showManualNumber = false
                        openNewChat(n, null)
                    }
                }) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { showManualNumber = false }) { Text("Cancelar") }
            }
        )
    }

    val emailProgress by viewModel.emailProgress.collectAsStateWithLifecycle()
    val emailResult by viewModel.emailResult.collectAsStateWithLifecycle()
    LaunchedEffect(emailResult) {
        val r = emailResult
        if (!r.isNullOrBlank()) {
            Toast.makeText(context, r, Toast.LENGTH_LONG).show()
            viewModel.clearEmailResult()
        }
    }
    if (emailProgress != null && !emailProgressHidden) {
        val prog = emailProgress!!
        AlertDialog(
            onDismissRequest = { emailProgressHidden = true },
            confirmButton = {
                TextButton(onClick = { emailProgressHidden = true }) {
                    Text("Segundo plano")
                }
            },
            title = { Text("Enviando correos") },
            text = {
                Column {
                    Text("${prog.first} de ${prog.second}")
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (prog.second > 0) prog.first.toFloat() / prog.second else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Toca \"Segundo plano\" para seguir usando la app: el envío continúa " +
                            "y verás el avance en la notificación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, email ->
                viewModel.saveContact(name, phone, email = email)
                showAddDialog = false
            }
        )
    }

    if (showEmailToSelected) {
        var subj by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf("") }
        var scheduledAt by remember { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { showEmailToSelected = false },
            title = { Text("Correo individual a los seleccionados") },
            text = {
                Column {
                    Text(
                        "Se enviará un correo aparte a cada contacto con correo " +
                            "(sin CC ni CCO, uno por uno).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = subj,
                        onValueChange = { subj = it },
                        label = { Text("Asunto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = msg,
                        onValueChange = { msg = it },
                        label = { Text("Mensaje") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    ScheduleControls(onAtMillis = { scheduledAt = it })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = subj.isNotBlank() && msg.isNotBlank(),
                    onClick = {
                        val sel = contacts.filter { selected.contains(it.id) }
                        val recipients = sel.mapNotNull {
                            it.email.trim().takeIf { e -> e.isNotBlank() }
                        }.distinct()
                        if (recipients.isEmpty()) {
                            Toast.makeText(
                                context,
                                "Ningún seleccionado tiene correo. Agrégalo al contacto o impórtalo con una columna Correo/Email.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (scheduledAt != null) {
                            if (scheduledAt!! <= System.currentTimeMillis()) {
                                Toast.makeText(context, "Elige una fecha y hora futuras", Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.scheduleSend(sel, msg, subj, 1, scheduledAt!!)
                                Toast.makeText(context, "Envío de correo programado", Toast.LENGTH_LONG).show()
                                showEmailToSelected = false
                            }
                        } else {
                            showEmailToSelected = false
                            emailProgressHidden = false
                            viewModel.sendBulkEmailBackground(recipients, subj, msg)
                        }
                    }
                ) { Text(if (scheduledAt != null) "Programar" else "Enviar") }
            },
            dismissButton = {
                TextButton(onClick = { showEmailToSelected = false }) { Text("Cancelar") }
            }
        )
    }

    if (showImport) {
        ImportPreviewScreen(
            rows = importedRows,
            loading = importing,
            onConfirm = {
                scope.launch {
                    viewModel.addContacts(importedRows)
                    Toast.makeText(context, "${importedRows.size} contactos agregados", Toast.LENGTH_SHORT).show()
                    showImport = false
                }
            },
            onCancel = { showImport = false }
        )
    }

    if (showSendScreen) {
        val selectedContacts = contacts.filter { selected.contains(it.id) }
        SendFlowScreen(
            viewModel = viewModel,
            selectedContacts = selectedContacts,
            messageBody = messageBody,
            delayMs = delayMs,
            isSending = isSending,
            onClose = {
                showSendScreen = false
                // El envío continúa en segundo plano (servicio), no se cancela.
                viewModel.requestPhoneRepairCheck()
            },
            onStop = {
                viewModel.stopSending()
                showSendScreen = false
                Toast.makeText(context, "Envío detenido", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showCalls) {
        val selectedContacts = contacts.filter { selected.contains(it.id) }
        LaunchedEffect(Unit) {
            if (!viewModel.callSequencer.isRunning.value) {
                val simForCalls = com.difusion.app.service.SimManager.getDefaultHandle(context)
                viewModel.callSequencer.setBetweenCalls(viewModel.callDelayMs.value)
                viewModel.callSequencer.setRingDuration(viewModel.ringDurationMs.value)
                viewModel.callSequencer.setMaxCallDuration(viewModel.maxCallMs.value)
                viewModel.callSequencer.start(selectedContacts, simForCalls)
            }
        }
        CallsScreen(
            sequencer = viewModel.callSequencer,
            hasCallPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED,
            onRequestPermission = {
                callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
            },
            onBack = {
                viewModel.callSequencer.stop()
                showCalls = false
                viewModel.requestPhoneRepairCheck()
            }
        )
    }

    if (showRepairDialog) {
        AlertDialog(
            onDismissRequest = { showRepairDialog = false },
            title = { Text("Corregir números de teléfono") },
            text = {
                Text(
                    "Algunos números cargados (típicamente desde Excel) quedaron sin el 0 inicial, por ejemplo 4122873438 en lugar de 04122873438. Esto hace que SMS y llamadas a esos números fallen.\n\n¿Quieres corregirlos ahora? Se actualizarán automáticamente en tu lista."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val fixed = viewModel.repairPhonesNow()
                            Toast.makeText(
                                context,
                                if (fixed > 0) "$fixed números corregidos con el 0 que faltaba"
                                else "No se necesitaba corregir ningún número",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        showRepairDialog = false
                        showSendScreen = false
                    }
                ) { Text("Corregir ahora") }
            },
            dismissButton = {
                TextButton(onClick = { showRepairDialog = false }) { Text("Ahora no") }
            }
        )
    }
}

private fun exportTemplate(context: android.content.Context) {
    try {
        val bytes = ReportExporter.buildTemplateExcel()
        val fileName = "plantilla_contactos.xlsx"
        var savedMessage = "Plantilla guardada en Descargas"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                savedMessage = "No se pudo guardar en Descargas"
            }
        } else {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            savedMessage = "Plantilla guardada en ${file.absolutePath}"
        }
        Toast.makeText(context, savedMessage, Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error al exportar plantilla: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun makeDefaultSmsApp(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val roleManager = context.getSystemService(RoleManager::class.java)
            val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
            if (intent != null) {
                launcher.launch(intent)
                return
            }
        } catch (_: Exception) {
        }
    }
    try {
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir el selector del sistema", Toast.LENGTH_LONG).show()
    }
}

private fun isDefaultDialerApp(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        return runCatching {
            context.getSystemService(android.app.role.RoleManager::class.java)
                ?.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)
        }.getOrDefault(false) ?: false
    }
    return runCatching {
        context.getSystemService(android.telecom.TelecomManager::class.java)
            ?.defaultDialerPackage == context.packageName
    }.getOrDefault(false)
}

/** ¿La app es la app de SMS predeterminada? (rol en Android 10+, paquete antes). */
private fun isDefaultSmsApp(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        return runCatching {
            context.getSystemService(android.app.role.RoleManager::class.java)
                ?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
        }.getOrDefault(false) ?: false
    }
    return runCatching {
        android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }.getOrDefault(false)
}

/**
 * Abre la pantalla de permisos de la app en Ajustes del sistema, donde está el
 * acceso a "Permisos restringidos" / "Permitir ajustes restringidos". Android no
 * deja otorgarlos por código (protección del sistema), pero así se llega en un
 * toque. Intenta primero los editores de permisos de fabricantes (MIUI/EMUI…).
 */
private fun openSystemAppPermissions(context: Context) {
    val pkg = context.packageName
    val oemIntents = listOf(
        Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg),
        Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("package_name", pkg),
        Intent().setClassName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
        ),
        Intent("com.samsung.android.settings.permissionmanagement.APP_PERMISSION_MANAGER")
            .putExtra("extra_pkgname", pkg)
    )
    for (intent in oemIntents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    // Genérico: ficha de la app en Ajustes (desde ahí, "Permisos" y el menú ⋮
    // "Permitir ajustes restringidos").
    val generic = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$pkg")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(generic) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** Android 14+: ¿está permitido el uso de pantalla completa (llamada entrante)? */
private fun hasFullScreenIntent(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 34) return true
    return runCatching {
        context.getSystemService(android.app.NotificationManager::class.java)
            ?.canUseFullScreenIntent() == true
    }.getOrDefault(false)
}

/** Abre el escáner de QR de Google (Play Services) y devuelve el texto leído. */
private fun scanQrCode(context: android.content.Context, onResult: (String) -> Unit) {
    try {
        val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue
                if (value.isNullOrBlank()) {
                    Toast.makeText(context, "No se pudo leer el código", Toast.LENGTH_SHORT).show()
                } else {
                    onResult(value)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "No se pudo abrir el escáner (${e.message ?: "sin detalle"}). " +
                        "Actualiza 'Servicios de Google Play' e inténtalo de nuevo.",
                    Toast.LENGTH_LONG
                ).show()
            }
    } catch (e: Throwable) {
        Toast.makeText(context, "Escáner no disponible: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/** Devuelve el primer enlace http(s) del texto leído, o el texto si ya es un enlace. */
private fun extractHttpUrl(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) return trimmed
    return Regex("""https?://[^\s"']+""").find(trimmed)?.value
}

// Permisos "de runtime" (los que el sistema pide con diálogo). Se piden todos
// de una sola vez desde el botón "Dar todos los permisos".
private val APP_RUNTIME_PERMISSIONS: List<String> = buildList {
    add(android.Manifest.permission.SEND_SMS)
    add(android.Manifest.permission.RECEIVE_SMS)
    add(android.Manifest.permission.READ_SMS)
    add(android.Manifest.permission.CALL_PHONE)
    add(android.Manifest.permission.READ_PHONE_STATE)
    add(android.Manifest.permission.READ_CALL_LOG)
    add(android.Manifest.permission.RECORD_AUDIO)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        add(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** Vuelve a llamar con la otra SIM después de que CallMonitor cortara la actual. */
private fun redialWithOtherSim(context: Context, phone: String) {
    val current = com.difusion.app.service.CallMonitor.currentSimHandle
    val other = com.difusion.app.service.SimManager.getOtherSim(context, current)
    val handle = other?.handle ?: com.difusion.app.service.SimManager.getDefaultHandle(context)
    com.difusion.app.service.CallMonitor.setSimHandle(handle)
    com.difusion.app.service.SimManager.placeCall(context, phone, handle)
}

private fun makeDefaultDialerApp(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        try {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            val intent = roleManager?.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
            if (intent != null) {
                launcher.launch(intent)
                return
            }
        } catch (_: Exception) {
        }
    }
    try {
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir el selector del sistema", Toast.LENGTH_LONG).show()
    }
}

private fun findActivityInternal(context: android.content.Context): android.app.Activity? {
    var current = context
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}

private fun exportContacts(context: android.content.Context, contacts: List<com.difusion.app.data.Contact>) {
    try {
        // Orden por fecha de gestión y nombre con el rango de fechas.
        val sorted = ReportExporter.sortByFechaGestion(contacts)
        val bytes = ReportExporter.buildContactExcel(sorted)
        val fileName = ReportExporter.exportBaseName(sorted) + ".xlsx"
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        Toast.makeText(context, "Exportado: $fileName", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun exportContactsByAssignment(context: android.content.Context, contacts: List<com.difusion.app.data.Contact>) {
    try {
        val grouped = contacts
            .groupBy { it.assignment.ifBlank { "Sin asignación" } }
            .toSortedMap()
        var saved = 0
        val resolver = context.contentResolver
        for ((assignment, list) in grouped) {
            val sortedList = ReportExporter.sortByFechaGestion(list)
            val bytes = ReportExporter.buildContactExcel(sortedList)
            val safe = assignment
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .trim()
                .ifBlank { "Sin-asignacion" }
            val gestionPart = ReportExporter.exportBaseName(sortedList).removePrefix("contactos_")
            val fileName = "contactos_${safe}_${gestionPart}.xlsx"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Reparto")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    saved++
                }
            } else {
                val dir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    "Reparto"
                )
                if (!dir.exists()) dir.mkdirs()
                File(dir, fileName).writeBytes(bytes)
                saved++
            }
        }
        Toast.makeText(context, "Se generaron $saved archivos en Descargas/Reparto", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error al exportar por asignación: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun exportHistory(context: android.content.Context, records: List<SendRecord>, contacts: List<com.difusion.app.data.Contact>) {
    try {
        val pairs = records.map { it to contacts }
        val bytes = ReportExporter.buildHistoryExcel(pairs)
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "historial_envios.xlsx")
        FileOutputStream(file).use { it.write(bytes) }
        Toast.makeText(context, "Reporte en ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun SendFlowScreen(
    viewModel: MainViewModel,
    selectedContacts: List<Contact>,
    messageBody: String,
    delayMs: Long,
    isSending: Boolean,
    onClose: () -> Unit,
    onStop: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (!isSending) {
            viewModel.startSend(selectedContacts, messageBody, delayMs)
        }
    }
    val stillWorking = isSending
    SendProgressScreen(
        isSending = stillWorking,
        sender = viewModel.smsSender,
        total = selectedContacts.size,
        onContinue = onClose,
        onStop = onStop,
        onDone = onClose
    )
}

private fun queryContactPhone(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val number = runCatching { cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER)) }.getOrNull()
            val name = runCatching { cursor.getString(cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)) }.getOrNull()
            if (number.isNullOrBlank()) null else (number to (name ?: number))
        }
    } catch (_: Exception) {
        null
    }
}