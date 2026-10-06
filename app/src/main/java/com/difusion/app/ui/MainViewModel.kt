package com.difusion.app.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.difusion.app.data.AppDatabase
import com.difusion.app.data.CallRecord
import com.difusion.app.data.Contact
import com.difusion.app.data.Conversation
import com.difusion.app.data.MessageTemplate
import com.difusion.app.data.PurgedSms
import com.difusion.app.data.SendRecord
import com.difusion.app.data.SmsMessage
import com.difusion.app.import.ParsedRow
import com.difusion.app.service.CallPrefs
import com.difusion.app.service.CallSequencer
import com.difusion.app.service.MmsInbox
import com.difusion.app.service.MmsSender
import com.difusion.app.service.SmsBatchTask
import com.difusion.app.service.SmsController
import com.difusion.app.service.SmsInbox
import com.difusion.app.service.SmsSendService
import com.difusion.app.storage.UserStore
import com.difusion.app.ui.theme.ThemeConfig
import com.difusion.app.ui.theme.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.work.await
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    private companion object {
        const val KEY_DELAY_MS = "delay_ms"
        const val KEY_CALL_DELAY_MS = "call_delay_ms"
        const val KEY_RING_DURATION_MS = "ring_duration_ms"
        const val KEY_MAX_CALL_MS = "max_call_ms"
        const val KEY_SAFE_MODE = "safe_mode"
    }
    val contacts = db.contactDao().getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val templates = db.templateDao().getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sendRecords = db.sendDao().getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val callRecords = db.callDao().getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val failureBreakdown = db.smsMessageDao().failuresByLabel(com.difusion.app.data.SmsStatus.FAILED)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val messages = db.smsMessageDao().allOrdered()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bandeja: agrupa los mensajes por conversación y les asigna el nombre del contacto si existe.
    val conversations: StateFlow<List<Conversation>> =
        combine(messages, contacts) { msgs, contacts -> deriveConversations(msgs, contacts) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Papelera: mensajes borrados (aún recuperables) y conversaciones de papelera.
    private val trashMessages = db.smsMessageDao().allInTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashConversations: StateFlow<List<Conversation>> =
        combine(trashMessages, contacts) { msgs, contacts -> deriveConversations(msgs, contacts) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smsSender = SmsController.get(application)
    val callSequencer = CallSequencer.getInstance(application)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()
    var isSendingValue
        get() = _isSending.value
        set(value) { _isSending.value = value }

    init {
        // Al abrir la app, respalda la bandeja del sistema en la base local.
        viewModelScope.launch {
            SmsInbox.syncNow(application, db)
            MmsInbox.syncNow(application, db)
        }
        // El estado "enviando" se mantiene en sincronía con el envío en segundo plano.
        viewModelScope.launch {
            smsSender.isRunning.collect { running ->
                _isSending.value = running
            }
        }
    }

    private val _selectedContacts = MutableStateFlow<Set<Long>>(emptySet())
    val selectedContacts: StateFlow<Set<Long>> = _selectedContacts.asStateFlow()

    private val _messageBody = MutableStateFlow("")
    val messageBody: StateFlow<String> = _messageBody.asStateFlow()

    private val _delayMs = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("difusion_prefs", Context.MODE_PRIVATE)
            .getLong(KEY_DELAY_MS, 2000L)
    )
    val delayMs: StateFlow<Long> = _delayMs.asStateFlow()

    // Pausa entre llamadas consecutivas de la secuencia (máx 60 s).
    private val _callDelayMs = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("difusion_prefs", Context.MODE_PRIVATE)
            .getLong(KEY_CALL_DELAY_MS, 30_000L)
    )
    val callDelayMs: StateFlow<Long> = _callDelayMs.asStateFlow()

    // Espera de timbre antes de dar por "sin respuesta" (segundos).
    private val _ringDurationMs = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("difusion_prefs", Context.MODE_PRIVATE)
            .getLong(KEY_RING_DURATION_MS, 30_000L)
    )
    val ringDurationMs: StateFlow<Long> = _ringDurationMs.asStateFlow()

    // Tope de duración de una llamada ya contestada (0 = sin límite).
    private val _maxCallMs = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("difusion_prefs", Context.MODE_PRIVATE)
            .getLong(KEY_MAX_CALL_MS, 0L)
    )
    val maxCallMs: StateFlow<Long> = _maxCallMs.asStateFlow()

    // Modo de envío de SMS: true = MODO SEGURO (bloques + contador de 5 min),
    // false = MODO DESATENDIDO (consecutivo, sin contador).
    private val _safeMode = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("difusion_prefs", Context.MODE_PRIVATE)
            .getBoolean(KEY_SAFE_MODE, true)
    )
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()

    private val _themeConfig = MutableStateFlow(ThemePrefs.read(getApplication()))
    val themeConfig: StateFlow<ThemeConfig> = _themeConfig.asStateFlow()

    // ---------- Usuarios (nombres asignados a contactos) ----------

    private val appContext: Context = getApplication<Application>()

    private val KEY_USERS = "assigned_users"
    private val KEY_DRIVE_URL = "drive_url"
    private val KEY_DRIVE_LAST_SHEET = "drive_last_sheet"
    private val KEY_DRIVE_QUICK = "drive_quick"
    private val _users = MutableStateFlow(loadUsers())
    val users: StateFlow<List<String>> = _users.asStateFlow()

    // Sincronización rápida: usar la última hoja y (opcional) importar sin revisar.
    private val _driveQuick = MutableStateFlow(usersPrefs().getBoolean(KEY_DRIVE_QUICK, true))
    val driveQuick: StateFlow<Boolean> = _driveQuick.asStateFlow()
    private var pendingQuickImport = false

    private fun usersPrefs(): SharedPreferences =
        appContext.getSharedPreferences("difusion_prefs", Context.MODE_PRIVATE)

    private fun loadUsers(): List<String> =
        usersPrefs().getString(KEY_USERS, "").orEmpty()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    private fun saveUsers(list: List<String>) {
        usersPrefs().edit().putString(KEY_USERS, list.joinToString("\n")).apply()
    }

    fun addUser(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val updated = (_users.value + n).distinct().sorted()
        _users.value = updated
        saveUsers(updated)
    }

    fun removeUser(name: String) {
        val updated = _users.value.filter { it != name }
        _users.value = updated
        saveUsers(updated)
        viewModelScope.launch {
            db.contactDao().clearAssignmentByName(name)
        }
    }

    fun renameUser(oldName: String, newName: String) {
        val n = newName.trim()
        if (n.isEmpty() || n == oldName) return
        val updated = _users.value.map { if (it == oldName) n else it }.distinct().sorted()
        _users.value = updated
        saveUsers(updated)
        viewModelScope.launch {
            db.contactDao().renameAssignment(oldName, n)
        }
    }

    fun assignUserToContacts(userName: String, ids: Set<Long>) {
        if (ids.isEmpty()) return
        val n = userName.trim()
        if (n.isEmpty()) return
        if (n !in _users.value) addUser(n)
        viewModelScope.launch {
            db.contactDao().setAssignment(ids, n)
        }
        _selectedContacts.value = emptySet()
    }

    fun clearAssignment(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            db.contactDao().clearAssignmentByIds(ids)
        }
        _selectedContacts.value = emptySet()
    }

    fun setThemeConfig(config: ThemeConfig) {
        _themeConfig.value = config
        ThemePrefs.write(getApplication(), config)
    }

    private val _appUser = MutableStateFlow(UserStore.getUser(getApplication()))
    val appUser: StateFlow<String> = _appUser.asStateFlow()

    fun setAppUser(name: String) {
        val n = name.trim()
        _appUser.value = n
        UserStore.setUser(getApplication(), n)
    }

    // --- Lista de contactos desde Google Drive (hoja compartida como lector) ---
    // El usuario pega el enlace de su hoja; al sincronizar, solo se importan los
    // contactos cuyo "Asignado a" coincide con su Nombre de usuario. La lista
    // resultante REMPLAZA a la lista de contactos a llamar.
    private var _driveUrl = MutableStateFlow(loadDriveUrl())
    val driveUrl: StateFlow<String> = _driveUrl.asStateFlow()

    // Hojas (tabs) visibles del Drive + cuál se selecciona en el picker flotante.
    // listVisibleSheets() enumera TODAS las hojas del libro (bajando el XLSX);
    // al pulsar "Finalizar sincronización" se importa SOLO la hoja seleccionada.
    private var _driveSheets = MutableStateFlow<List<String>>(emptyList())
    val driveSheets: StateFlow<List<String>> = _driveSheets.asStateFlow()

    private var _driveSelectedSheetIndex = MutableStateFlow(-1)
    val driveSelectedSheetIndex: StateFlow<Int> = _driveSelectedSheetIndex.asStateFlow()

    private var _driveSyncStatus = MutableStateFlow("Sin sincronizar")
    val driveSyncStatus: StateFlow<String> = _driveSyncStatus.asStateFlow()

    // Configuración del Web App de Apps Script que ESCRIBE en la hoja (Drive).
    private val _driveSyncUrl = MutableStateFlow(com.difusion.app.storage.DriveSyncPrefs.getUrl(getApplication()))
    val driveSyncUrl: StateFlow<String> = _driveSyncUrl.asStateFlow()
    private val _driveSyncToken = MutableStateFlow(com.difusion.app.storage.DriveSyncPrefs.getToken(getApplication()))
    val driveSyncToken: StateFlow<String> = _driveSyncToken.asStateFlow()
    private val _driveSyncEnabled = MutableStateFlow(com.difusion.app.storage.DriveSyncPrefs.isEnabled(getApplication()))
    val driveSyncEnabled: StateFlow<Boolean> = _driveSyncEnabled.asStateFlow()

    fun setDriveSyncUrl(url: String) {
        _driveSyncUrl.value = url
        com.difusion.app.storage.DriveSyncPrefs.setUrl(getApplication(), url)
    }

    fun setDriveSyncToken(token: String) {
        _driveSyncToken.value = token
        com.difusion.app.storage.DriveSyncPrefs.setToken(getApplication(), token)
    }

    fun setDriveSyncEnabled(enabled: Boolean) {
        _driveSyncEnabled.value = enabled
        com.difusion.app.storage.DriveSyncPrefs.setEnabled(getApplication(), enabled)
    }

    /** Prueba la escritura a la hoja (Web App de Apps Script). */
    fun testDriveConnection() {
        viewModelScope.launch {
            val cfg = com.difusion.app.service.DriveSyncService.config(getApplication())
            val (ok, msg) = withContext(Dispatchers.IO) {
                com.difusion.app.service.DriveSyncService.ping(cfg)
            }
            Toast.makeText(
                getApplication(),
                if (ok) "Drive conectado: $msg" else "Drive: $msg",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // --- Correo masivo ---
    private val _emailSyncUrl = MutableStateFlow(com.difusion.app.storage.EmailSyncPrefs.getUrl(getApplication()))
    val emailSyncUrl: StateFlow<String> = _emailSyncUrl.asStateFlow()
    private val _emailSyncToken = MutableStateFlow(com.difusion.app.storage.EmailSyncPrefs.getToken(getApplication()))
    val emailSyncToken: StateFlow<String> = _emailSyncToken.asStateFlow()
    private val _emailSyncEnabled = MutableStateFlow(com.difusion.app.storage.EmailSyncPrefs.isEnabled(getApplication()))
    val emailSyncEnabled: StateFlow<Boolean> = _emailSyncEnabled.asStateFlow()

    fun setEmailSyncUrl(url: String) {
        _emailSyncUrl.value = url
        com.difusion.app.storage.EmailSyncPrefs.setUrl(getApplication(), url)
    }

    fun setEmailSyncToken(token: String) {
        _emailSyncToken.value = token
        com.difusion.app.storage.EmailSyncPrefs.setToken(getApplication(), token)
    }

    fun setEmailSyncEnabled(enabled: Boolean) {
        _emailSyncEnabled.value = enabled
        com.difusion.app.storage.EmailSyncPrefs.setEnabled(getApplication(), enabled)
    }

    // --- Envío de correos en segundo plano ---
    private val _emailProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val emailProgress: StateFlow<Pair<Int, Int>?> = _emailProgress.asStateFlow()
    private val _emailResult = MutableStateFlow<String?>(null)
    val emailResult: StateFlow<String?> = _emailResult.asStateFlow()

    fun clearEmailResult() { _emailResult.value = null }

    fun sendBulkEmailBackground(recipients: List<String>, subject: String, body: String) {
        val list = recipients.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (list.isEmpty()) {
            _emailResult.value = "Sin destinatarios"
            return
        }
        val data = androidx.work.workDataOf(
            com.difusion.app.worker.EmailSendWorker.KEY_RECIPIENTS to org.json.JSONArray(list).toString(),
            com.difusion.app.worker.EmailSendWorker.KEY_SUBJECT to subject,
            com.difusion.app.worker.EmailSendWorker.KEY_BODY to body
        )
        val req = androidx.work.OneTimeWorkRequestBuilder<com.difusion.app.worker.EmailSendWorker>()
            .setInputData(data)
            .build()
        val wm = androidx.work.WorkManager.getInstance(getApplication())
        wm.enqueue(req)
        _emailResult.value = null
        _emailProgress.value = 0 to list.size
        viewModelScope.launch {
            while (true) {
                val info = runCatching { wm.getWorkInfoById(req.id).await() }.getOrNull() ?: break
                val sent = info.progress.getInt(com.difusion.app.worker.EmailSendWorker.KEY_SENT, 0)
                val total = info.progress.getInt(com.difusion.app.worker.EmailSendWorker.KEY_TOTAL, list.size)
                if (info.state.isFinished) {
                    _emailProgress.value = null
                    val fsent = info.outputData.getInt(com.difusion.app.worker.EmailSendWorker.KEY_SENT, sent)
                    val ffailed = info.outputData.getInt("failed", 0)
                    val ferr = info.outputData.getString("error") ?: ""
                    _emailResult.value = if (fsent == 0 && ferr.isNotBlank()) {
                        "Error: $ferr"
                    } else {
                        "Enviado: $fsent · Fallidos: $ffailed" + if (ferr.isNotBlank()) " ($ferr)" else ""
                    }
                    break
                }
                _emailProgress.value = sent to total
                delay(400)
            }
        }
    }

    /** Prueba la conexión con el Gmail del Web App y avisa con qué cuenta quedó. */
    fun testEmailConnection() {
        viewModelScope.launch {
            val cfg = com.difusion.app.service.EmailSyncService.config(getApplication())
            val res = withContext(Dispatchers.IO) {
                com.difusion.app.service.EmailSyncService.ping(cfg)
            }
            val msg = if (res.success) {
                if (res.email.isBlank()) "Conexión correcta con tu Gmail"
                else "Conectado con ${res.email} · ${res.unread} sin leer"
            } else {
                "No se pudo conectar: ${res.error}"
            }
            Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
        }
    }

    suspend fun sendBulkEmail(
        recipients: List<String>,
        subject: String,
        body: String,
        html: Boolean = false
    ): com.difusion.app.service.EmailSyncService.EmailResult =
        com.difusion.app.service.EmailSyncService.sendSmart(
            getApplication(),
            recipients,
            subject,
            body
        )

    // --- Envíos programados ---
    val scheduledSends: StateFlow<List<com.difusion.app.data.ScheduledSend>> =
        db.scheduledSendDao().getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Programa un envío (SMS o correo) para una fecha/hora concreta. */
    fun scheduleSend(
        contacts: List<Contact>,
        message: String,
        subject: String,
        channel: Int,
        scheduledAtMillis: Long,
        extraEmails: List<String> = emptyList()
    ) {
        if (contacts.isEmpty() && extraEmails.isEmpty()) return
        if (message.isBlank()) return
        if (scheduledAtMillis <= System.currentTimeMillis()) return
        viewModelScope.launch {
            val phones = org.json.JSONArray()
            val emails = org.json.JSONArray()
            // Snapshot: reimportar contactos NO debe afectar esta programación.
            val snapshot = org.json.JSONArray()
            contacts.forEach { c ->
                if (c.phone.isNotBlank()) phones.put(c.phone)
                if (c.email.isNotBlank()) emails.put(c.email)
                snapshot.put(
                    org.json.JSONObject().apply {
                        put("name", c.name)
                        put("phone", c.phone)
                        put("email", c.email)
                    }
                )
            }
            // Correos precargados manualmente (sin contacto asociado).
            extraEmails.forEach { e ->
                if (e.isNotBlank()) {
                    emails.put(e)
                    snapshot.put(
                        org.json.JSONObject().apply {
                            put("name", e)
                            put("phone", "")
                            put("email", e)
                        }
                    )
                }
            }
            val item = com.difusion.app.data.ScheduledSend(
                phonesJson = phones.toString(),
                emailsJson = emails.toString(),
                recipientsJson = snapshot.toString(),
                message = message,
                subject = subject,
                channel = channel,
                scheduledAt = scheduledAtMillis,
                status = 0
            )
            val id = db.scheduledSendDao().insert(item)
            val delay = scheduledAtMillis - System.currentTimeMillis()
            val req = androidx.work.OneTimeWorkRequestBuilder<com.difusion.app.worker.ScheduledSendWorker>()
                .setInitialDelay(delay.coerceAtLeast(0), java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            androidx.work.WorkManager.getInstance(getApplication()).enqueue(req)
            _driveSyncStatus.value = "Envío programado para ${formatStamp(scheduledAtMillis)} (${contacts.size} destinatarios)"
        }
    }

    fun cancelScheduledSend(item: com.difusion.app.data.ScheduledSend) {
        viewModelScope.launch {
            db.scheduledSendDao().update(item.copy(status = 3))
        }
    }

    fun deleteScheduledSend(item: com.difusion.app.data.ScheduledSend) {
        viewModelScope.launch {
            db.scheduledSendDao().delete(item)
        }
    }

    fun clearSentScheduled() {
        viewModelScope.launch {
            db.scheduledSendDao().clearSent()
        }
    }

    private fun formatStamp(ms: Long): String =
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(ms))

    // Hojas visibles del libro: del XLSX se saca la lista de tabs incluidas.
    private fun loadDriveUrl(): String =
        usersPrefs().getString(KEY_DRIVE_URL, "").orEmpty().trim()

    fun setDriveUrl(url: String) {
        val u = url.trim()
        _driveUrl.value = u
        usersPrefs().edit().putString(KEY_DRIVE_URL, u).apply()
        _driveSyncStatus.value = if (u.isNotEmpty()) "Listo para sincronizar" else "Sin sincronizar"
    }

    fun syncFromDrive() {
        val url = _driveUrl.value.trim()
        if (url.isEmpty()) {
            _driveSyncStatus.value = "Pega primero el enlace de tu hoja."
            return
        }
        val userName = _appUser.value.trim()
        if (userName.isEmpty()) {
            _driveSyncStatus.value = "Guarda primero tu Nombre de usuario para filtrar la hoja."
            return
        }
        viewModelScope.launch {
            _driveSyncStatus.value = "Enumerando las hojas del libro…"
            try {
                // Baja el XLSX del libro y devuelve los NOMBRES de TODAS sus hojas
                // visibles (una misma hoja de cálculo puede tener varias pestañas:
                // 'Enero', 'Febrero', 'Agentes Cantabria'…). El usuario elige CUÁL
                // importar en la ventana flotante.
                val sheetNames = withContext(Dispatchers.IO) {
                    com.difusion.app.import.Importer.listVisibleSheets(url)
                }
                if (sheetNames.isEmpty()) {
                    _driveSyncStatus.value =
                        "El libro no tiene hojas visibles. Comparte el enlace como " +
                            "'Cualquier persona con el enlace → Lector'."
                    return@launch
                }
                _driveSheets.value = sheetNames
                _driveSelectedSheetIndex.value = -1
                val resumen = if (sheetNames.size == 1) {
                    "El libro tiene 1 hoja. Toca Finalizar sincronización."
                } else {
                    "El libro tiene ${sheetNames.size} hojas. Elige una y toca Finalizar."
                }
                _driveSyncStatus.value =
                    "Hojas listadas: $resumen (hojas: ${sheetNames.joinToString(", ")})"
            } catch (e: Exception) {
                _driveSyncStatus.value = "Error enumerando hojas: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun selectDriveSheet(index: Int) {
        if (index in _driveSheets.value.indices) {
            _driveSelectedSheetIndex.value = index
            _driveSyncStatus.value = "Hoja elegida: ${_driveSheets.value[index]} — toca Finalizar sincronización."
        }
    }

    fun setDriveQuick(enabled: Boolean) {
        _driveQuick.value = enabled
        usersPrefs().edit().putBoolean(KEY_DRIVE_QUICK, enabled).apply()
    }

    /**
     * Sincronización de 1 toque: usa el enlace guardado y la ÚLTIMA hoja usada.
     * Si "rápido" está activo, importa directo sin abrir la ventana de revisión.
     */
    fun quickSyncFromDrive() {
        val url = _driveUrl.value.trim()
        if (url.isEmpty()) {
            _driveSyncStatus.value = "Pega primero el enlace de tu hoja."
            Toast.makeText(getApplication(), "Pega el enlace de Google Drive primero.", Toast.LENGTH_LONG).show()
            return
        }
        viewModelScope.launch {
            try {
                _driveSyncStatus.value = "Sincronizando (rápido)…"
                val sheetNames = withContext(Dispatchers.IO) {
                    com.difusion.app.import.Importer.listVisibleSheets(url)
                }
                if (sheetNames.isEmpty()) {
                    _driveSyncStatus.value = "El libro no tiene hojas visibles."
                    return@launch
                }
                val last = usersPrefs().getString(KEY_DRIVE_LAST_SHEET, "").orEmpty()
                val idx = sheetNames.indexOfFirst { it.equals(last, ignoreCase = true) }
                    .takeIf { it >= 0 } ?: 0
                _driveSheets.value = sheetNames
                _driveSelectedSheetIndex.value = idx
                pendingQuickImport = _driveQuick.value
                finalizeSyncFromDrive()
            } catch (e: Exception) {
                _driveSyncStatus.value = "Error sincronizando: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    // Filas listas para importar que se muestran en la ventana flotante de
    // revisión (ImportPreviewOverlay). Vacía = no hay revisión pendiente.
    private var _drivePreviewRows = MutableStateFlow<List<ParsedRow>>(emptyList())
    val drivePreviewRows: StateFlow<List<ParsedRow>> = _drivePreviewRows.asStateFlow()

    // Datos de la hoja pendiente de confirmar (para el mensaje final).
    private var pendingSheetName: String = ""
    private var pendingSheetTotal: Int = 0
    private var pendingDuplicates: Int = 0

    fun finalizeSyncFromDrive() {
        val sheetIndex = _driveSelectedSheetIndex.value
        val sheetName = _driveSheets.value.getOrNull(sheetIndex)
        if (sheetName == null) {
            _driveSyncStatus.value = "Primero elige una hoja en la ventana flotante."
            return
        }
        // Cierra el selector de hoja mientras se descarga y se abre la revisión.
        _driveSheets.value = emptyList()
        _driveSelectedSheetIndex.value = -1
        val url = _driveUrl.value.trim()
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                _driveSyncStatus.value = "Descargando libro y leyendo la hoja \"$sheetName\"…"
                Log.d("DIFUSION-Sync", "finalize: sheetIndex=$sheetIndex sheet=$sheetName")
                val rows = withContext(Dispatchers.IO) {
                    com.difusion.app.import.Importer.importSheet(url, sheetIndex)
                }
                Log.d("DIFUSION-Sync", "finalize: importSheet -> ${rows.size} filas")
                if (rows.isEmpty()) {
                    val msg = "La hoja \"$sheetName\" no tiene filas con nombre y teléfono."
                    _driveSyncStatus.value = msg
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val total = rows.size
                val userName = _appUser.value.trim()
                // Si la hoja trae columna de asignación ("Asignado a"/EJECUTIVO/…),
                // se muestran SOLO las filas asignadas a mi usuario (ignorando
                // mayúsculas y tildes). Si la hoja no tiene esa columna, se muestran todas.
                val hasAssignment = rows.any { it.assignment.isNotBlank() }
                val mine = if (hasAssignment && userName.isNotEmpty()) {
                    rows.filter { normName(it.assignment) == normName(userName) }
                } else {
                    rows
                }
                if (hasAssignment && userName.isNotEmpty() && mine.isEmpty()) {
                    val msg = "No hay contactos con \"Asignado a\" = \"$userName\" " +
                        "en la hoja \"$sheetName\"."
                    _driveSyncStatus.value = msg
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                    return@launch
                }
                // Dedup por cédula: dos filas con la MISMA cédula (normalizada a
                // solo dígitos: 28.100.165 == 28100165) son el mismo contacto y
                // solo queda 1. Un mismo nombre con cédula DIFERENTE es otra
                // persona y se conserva.
                val seenCedulas = mutableSetOf<String>()
                var repetidas = 0
                val unicas = mine.filter { row ->
                    val key = row.cedula.trim().filter { it.isDigit() }
                    if (key.isNotEmpty() && !seenCedulas.add(key)) {
                        repetidas++
                        false
                    } else {
                        true
                    }
                }
                // No se importa todavía: se abre la ventana flotante de revisión
                // para que el usuario marque qué filas importar.
                pendingSheetName = sheetName
                pendingSheetTotal = total
                pendingDuplicates = repetidas
                // Recuerda esta hoja para la próxima "Sincronización rápida".
                usersPrefs().edit().putString(KEY_DRIVE_LAST_SHEET, sheetName).apply()
                if (pendingQuickImport) {
                    // Modo rápido: importa de una vez, sin ventana de revisión.
                    pendingQuickImport = false
                    commitDriveImport(unicas)
                } else {
                    _drivePreviewRows.value = unicas
                    fun distintos(selector: (ParsedRow) -> String): Int =
                        rows.map { selector(it).trim() }.filter { it.isNotEmpty() }.distinct().size
                    val nTipif = distintos { it.gestion }
                    val nEstados = distintos { it.estado }
                    val nMedios = distintos { it.medio }
                    val resumenCat = buildString {
                        append("tipificaciones: $nTipif")
                        if (nEstados > 0) append(" · estados: $nEstados")
                        if (nMedios > 0) append(" · medios: $nMedios")
                    }
                    _driveSyncStatus.value =
                        "Revisa la ventana flotante: ${unicas.size} contactos listos " +
                            "(de $total filas · $repetidas duplicados por cédula · $resumenCat)."
                    Log.d("DIFUSION-Sync", "finalize: preview ${unicas.size} filas")
                }
            } catch (e: Throwable) {
                val msg = "Error al importar \"$sheetName\": ${e.message ?: e.javaClass.simpleName}"
                _driveSyncStatus.value = msg
                Log.e("DIFUSION-Sync", "finalize FAIL", e)
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Confirma la importación de las filas marcadas en la ventana de revisión. */
    fun commitDriveImport(rows: List<ParsedRow>) {
        if (rows.isEmpty()) {
            _driveSyncStatus.value = "No marcaste ningún contacto. La lista no cambió."
            _drivePreviewRows.value = emptyList()
            return
        }
        viewModelScope.launch {
            db.contactDao().clear()
            rows.forEach { row ->
                db.contactDao().insert(
                    Contact(
                        name = row.name,
                        phone = row.phone.trim(),
                        cedula = row.cedula.trim(),
                        assignment = row.assignment.trim(),
                        gestion = row.gestion.trim(),
                        estado = row.estado.trim(),
                        medio = row.medio.trim(),
                        idCuota = row.idCuota.trim(),
                        monto = row.monto.trim(),
                        fechaGestion = row.fechaGestion.trim(),
                        email = row.email.trim()
                    )
                )
            }
            // Guarda los valores vistos en la hoja para los desplegables de gestión.
            runCatching {
                com.difusion.app.data.GestionPresets.saveFromImport(
                    getApplication(),
                    rows.map { it.gestion },
                    rows.map { it.estado },
                    rows.map { it.medio },
                    rows.map { it.assignment }
                )
            }
            val sheet = pendingSheetName.ifBlank { "la hoja" }
            val msg = "Lista actualizada desde \"$sheet\". " +
                "En la hoja: $pendingSheetTotal filas · Cargados: ${rows.size} · " +
                "Duplicados por cédula: $pendingDuplicates"
            _driveSyncStatus.value = msg
            _drivePreviewRows.value = emptyList()
            _driveSheets.value = emptyList()
            _driveSelectedSheetIndex.value = -1
            pendingSheetName = ""
            pendingSheetTotal = 0
            pendingDuplicates = 0
            Log.d("DIFUSION-Sync", "commit OK: $msg")
            Toast.makeText(
                getApplication(),
                "Cargados ${rows.size} contactos",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Cancela la revisión: no se toca la lista de contactos actual. */
    fun cancelDrivePreview() {
        _drivePreviewRows.value = emptyList()
        _driveSheets.value = emptyList()
        _driveSelectedSheetIndex.value = -1
        pendingSheetName = ""
        pendingSheetTotal = 0
        pendingDuplicates = 0
        _driveSyncStatus.value = "Importación cancelada. La lista de contactos no cambió."
    }

    fun viewDriveStatus() = _driveSyncStatus.value

    /** Normaliza un nombre para comparar "Asignado a": sin tildes, minúsculas y espacios simples. */
    private fun normName(value: String): String {
        val n = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (c in n) if (c.code < 128) sb.append(Character.toLowerCase(c))
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /** Cierra la ventana flotante y limpia la lista de hojas. */
    fun cancelDriveSync() {
        _driveSheets.value = emptyList()
        _driveSelectedSheetIndex.value = -1
        _driveSyncStatus.value = "Sincronización cancelada."
    }

    fun setCallDelay(ms: Long) {
        val v = ms.coerceIn(0L, 60_000L)
        _callDelayMs.value = v
        usersPrefs().edit().putLong(KEY_CALL_DELAY_MS, v).apply()
    }

    fun setRingDuration(ms: Long) {
        val v = ms.coerceIn(0L, 300_000L)
        _ringDurationMs.value = v
        usersPrefs().edit().putLong(KEY_RING_DURATION_MS, v).apply()
        callSequencer.setRingDuration(v)
    }

    fun setMaxCallDuration(ms: Long) {
        val v = ms.coerceAtLeast(0L)
        _maxCallMs.value = v
        usersPrefs().edit().putLong(KEY_MAX_CALL_MS, v).apply()
        callSequencer.setMaxCallDuration(v)
    }

    private val _preferBluetooth = MutableStateFlow(CallPrefs.preferBluetooth(appContext))
    val preferBluetooth: StateFlow<Boolean> = _preferBluetooth.asStateFlow()

    fun setPreferBluetooth(value: Boolean) {
        _preferBluetooth.value = value
        CallPrefs.setPreferBluetooth(appContext, value)
    }

    fun messagesForThread(threadId: Long): Flow<List<SmsMessage>> =
        db.smsMessageDao().messagesForThread(threadId)

    fun markThreadRead(threadId: Long) {
        viewModelScope.launch {
            db.smsMessageDao().markThreadRead(threadId)
        }
    }

    fun reloadInbox(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            var count = SmsInbox.syncNow(getApplication(), db)
            count += MmsInbox.syncNow(getApplication(), db)
            onDone(count)
        }
    }

    fun deleteThread(threadId: Long) {
        viewModelScope.launch {
            db.smsMessageDao().deleteThread(threadId)
        }
    }

    // Mueve mensajes concretos a la papelera (borrado reversible).
    fun moveMessageToTrash(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            db.smsMessageDao().moveToTrash(ids)
        }
    }

    // Mueve toda la conversación a la papelera.
    fun moveThreadToTrash(threadId: Long) {
        viewModelScope.launch {
            db.smsMessageDao().moveThreadToTrash(threadId)
        }
    }

    fun moveThreadsToTrash(threadIds: List<Long>) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            db.smsMessageDao().moveThreadsToTrash(threadIds)
        }
    }

    // Restaura mensajes desde la papelera a su banda de entrada.
    fun restoreFromTrash(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            db.smsMessageDao().restore(ids)
        }
    }

    fun restoreThreadFromTrash(threadId: Long) {
        viewModelScope.launch {
            val msgs = db.smsMessageDao().allInTrashOnce().filter { it.threadId == threadId }
            if (msgs.isNotEmpty()) db.smsMessageDao().restore(msgs.map { it.id })
        }
    }

    /** Borrado DEFINITIVO: elimina del proveedor de SMS del sistema, registra el
     *  id como purgado (para que no se vuelva a importar al recargar) y quita los
     *  mensajes de la base local. Después de esto el chat ya no existe en el equipo. */
    private suspend fun purgePermanently(msgs: List<SmsMessage>) {
        if (msgs.isEmpty()) return
        val ctx = getApplication<Application>()
        for (m in msgs) {
            val pid = m.providerId
            if (pid != null) {
                db.smsMessageDao().insertPurged(PurgedSms(providerId = pid, isMms = m.isMms))
                if (m.isMms) {
                    // MMS: no se puede borrar del proveedor directamente; se
                    // registra como purgado para que no vuelva a importarse.
                    runCatching {
                        ctx.contentResolver.delete(
                            Uri.parse("content://mms"),
                            "${android.provider.Telephony.Mms._ID} = ?",
                            arrayOf(pid.toString())
                        )
                    }
                } else {
                    runCatching {
                        ctx.contentResolver.delete(
                            android.provider.Telephony.Sms.CONTENT_URI,
                            "${android.provider.Telephony.Sms._ID} = ?",
                            arrayOf(pid.toString())
                        )
                    }
                }
            }
            // Elimina también el archivo multimedia guardado localmente.
            m.mediaPath?.let { path ->
                runCatching { File(path).delete() }
            }
        }
        db.smsMessageDao().purge(msgs.map { it.id })
    }

    fun purgeMessagesFromTrash(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val msgs = db.smsMessageDao().allInTrashOnce().filter { it.id in ids }
            purgePermanently(msgs)
        }
    }

    fun purgeThreadFromTrash(threadId: Long) {
        viewModelScope.launch {
            val msgs = db.smsMessageDao().allInTrashOnce().filter { it.threadId == threadId }
            purgePermanently(msgs)
        }
    }

    // Vacía la papelera: los chats/mensajes desaparecen para siempre del equipo.
    fun emptyTrash() {
        viewModelScope.launch {
            val msgs = db.smsMessageDao().allInTrashOnce()
            purgePermanently(msgs)
        }
    }

    fun sendSingleMessage(address: String, body: String) {
        val subId = com.difusion.app.service.SimManager.getSmsSubId(getApplication())
        viewModelScope.launch {
            smsSender.sendSingle(address, body, subId)
        }
    }

    fun sendMms(
        address: String,
        caption: String,
        mediaPath: String?,
        mediaMime: String?,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = MmsSender.send(
                getApplication(),
                address,
                null,
                caption,
                mediaPath,
                mediaMime
            ) >= 0
            onDone(ok)
        }
    }

    fun resendMessage(address: String, message: SmsMessage) {
        viewModelScope.launch {
            try {
                smsSender.resend(address, message.body, message.id)
            } catch (_: Exception) {
            }
        }
    }

    // Reenvía el último mensaje no enviado de cada chat seleccionado.
    fun resendFailedForThreads(threads: List<Conversation>, onDone: (sent: Int, skipped: Int) -> Unit) {
        if (threads.isEmpty()) return
        viewModelScope.launch {
            val (sent, skipped) = withContext(Dispatchers.IO) {
                var s = 0
                var k = 0
                for (conv in threads) {
                    val lastFailed = db.smsMessageDao().lastFailedForThread(
                        conv.threadId, com.difusion.app.data.SmsStatus.FAILED
                    )
                    if (lastFailed == null || lastFailed.isMms) {
                        k++
                        continue
                    }
                    try {
                        smsSender.resend(conv.address, lastFailed.body, lastFailed.id)
                        s++
                    } catch (e: Exception) {
                        k++
                    }
                }
                s to k
            }
            onDone(sent, skipped)
        }
    }

    fun setCallLabel(callId: Long, label: String) {
        viewModelScope.launch {
            db.callDao().updateLabel(callId, label)
        }
    }

    private fun deriveConversations(msgs: List<SmsMessage>, contacts: List<Contact>): List<Conversation> {
        val byPhone = contacts
            .groupBy { it.phone.replace(Regex("[^0-9+]"), "") }
        fun nameFor(address: String): String {
            val digit = address.replace(Regex("[^0-9+]"), "")
            val match = if (digit.isNotEmpty()) byPhone[digit]?.firstOrNull() else null
            return match?.name ?: address
        }
        val grouped = msgs.groupBy { it.threadId }
        return grouped.map { (threadId, list) ->
            val last = list.maxByOrNull { it.date } ?: return@map null
            Conversation(
                threadId = threadId,
                address = last.address,
                name = nameFor(last.address),
                lastBody = last.body,
                lastDate = last.date,
                // -1 = entrante (sin estado saliente); 0/1/2 = enviando/enviado/fallido.
                lastStatus = if (last.isIncoming) -1 else last.status,
                unreadCount = list.count { it.isIncoming && !it.read },
                lastIsIncoming = last.isIncoming
            )
        }.filterNotNull().sortedByDescending { it.lastDate }
    }

    fun toggleContact(id: Long) {
        val current = _selectedContacts.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        _selectedContacts.value = current
    }

    fun selectAll(ids: List<Long>) {
        _selectedContacts.value = ids.toSet()
    }

    fun clearSelection() {
        _selectedContacts.value = emptySet()
    }

    fun deleteContacts(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            db.contactDao().deleteByIds(ids)
            _selectedContacts.value = emptySet()
        }
    }

    fun setMessageBody(body: String) {
        _messageBody.value = body
    }

    fun setDelay(delay: Long) {
        _delayMs.value = delay
        usersPrefs().edit().putLong(KEY_DELAY_MS, delay).apply()
    }

    fun setSafeMode(enabled: Boolean) {
        _safeMode.value = enabled
        usersPrefs().edit().putBoolean(KEY_SAFE_MODE, enabled).apply()
    }

    fun saveContact(name: String, phone: String, assignment: String = "", email: String = "") {
        viewModelScope.launch {
            // Se guarda el teléfono tal cual lo escribe el usuario.
            db.contactDao().insert(
                Contact(
                    name = name,
                    phone = phone.trim(),
                    assignment = assignment.trim(),
                    email = email.trim()
                )
            )
        }
    }

    fun addContacts(rows: List<ParsedRow>) {
        viewModelScope.launch {
            runCatching {
                com.difusion.app.data.GestionPresets.saveFromImport(
                    getApplication(),
                    rows.map { it.gestion },
                    rows.map { it.estado },
                    rows.map { it.medio },
                    rows.map { it.assignment }
                )
            }
            for (row in rows) {
                // Se importan los teléfonos exactamente como vienen del archivo.
                db.contactDao().insert(
                    Contact(
                        name = row.name,
                        phone = row.phone.trim(),
                        cedula = row.cedula.trim(),
                        assignment = row.assignment,
                        gestion = row.gestion.trim(),
                        estado = row.estado.trim(),
                        medio = row.medio.trim(),
                        idCuota = row.idCuota.trim(),
                        monto = row.monto.trim(),
                        fechaGestion = row.fechaGestion.trim(),
                        email = row.email.trim()
                    )
                )
            }
        }
    }

    fun saveTemplate(name: String, body: String, subject: String = "") {
        viewModelScope.launch {
            // Si ya existe una plantilla con ese nombre se ACTUALIZA (no se
            // duplica). Así, al reenviar una plantilla existente no se crean
            // copias repetidas.
            val existing = db.templateDao().getByName(name)
            if (existing != null) {
                db.templateDao().updateBodyAndSubject(existing.id, body, subject)
            } else {
                db.templateDao().insert(MessageTemplate(name = name, body = body, subject = subject))
            }
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            db.contactDao().update(contact)
            com.difusion.app.service.DriveSyncService.enqueue(getApplication(), contact)
        }
    }

    fun updateContactGestion(contactId: Long, gestion: String, estado: String, medio: String, fechaGestion: String) {
        viewModelScope.launch {
            val c = db.contactDao().getById(contactId) ?: return@launch
            val updated = c.copy(
                gestion = gestion.trim(),
                estado = estado.trim(),
                medio = medio.trim(),
                fechaGestion = fechaGestion.trim()
            )
            db.contactDao().update(updated)
            com.difusion.app.service.DriveSyncService.enqueue(getApplication(), updated)
        }
    }

    fun deleteTemplate(template: MessageTemplate) {
        viewModelScope.launch {
            db.templateDao().delete(template)
        }
    }

    fun startSend(contacts: List<Contact>, message: String, delayMs: Long) {
        if (_isSending.value) return
        // El envío corre en un servicio en primer plano, así puede continuar en
        // segundo plano mientras el usuario hace llamadas o navega por la app.
        val subId = com.difusion.app.service.SimManager.getSmsSubId(getApplication())
        SmsBatchTask.set(SmsBatchTask.PendingBatch(contacts, message, delayMs, _safeMode.value, subId))
        viewModelScope.launch {
            try {
                SmsSendService.start(getApplication())
            } catch (e: Exception) {
                SmsBatchTask.take()
            }
        }
    }

    fun stopSending() {
        smsSender.cancel()
    }

    private val _needPhoneRepair = MutableStateFlow(false)
    val needPhoneRepair: StateFlow<Boolean> = _needPhoneRepair.asStateFlow()

    // Detecta si un teléfono necesita corrección de verdad:
    // - artefacto del "9" de Excel (11 dígitos con prefijo venezolano)
    // - le falta el 0 inicial (10 dígitos con prefijo venezolano y sin 0)
    // El formato con guiones/espacios NO cuenta como error.
    private fun phoneNeedsRepair(phone: String): Boolean {
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        return com.difusion.app.import.Importer.repairScientificArtifact(cleaned) != cleaned ||
            com.difusion.app.import.Importer.normalizePhone(cleaned.toString()) != cleaned
    }

    // Cuenta cuántos contactos guardados tienen un número al que le falta el 0.
    fun requestPhoneRepairCheck() {
        viewModelScope.launch {
            val all = db.contactDao().getAllOnce()
            _needPhoneRepair.value = all.any { phoneNeedsRepair(it.phone) }
        }
    }

    // Recorre todos los contactos, les agrega el 0 faltante y corrige los números
    // que quedaron con el "9" parásito de la notación científica de Excel.
    suspend fun repairPhonesNow(): Int {
        val all = db.contactDao().getAllOnce()
        var fixed = 0
        for (c in all) {
            val cleaned = c.phone.replace(Regex("[^0-9+]"), "")
            val fixedPhone = com.difusion.app.import.Importer.repairScientificArtifact(cleaned)
                .let { com.difusion.app.import.Importer.normalizePhone(it) }
            if (fixedPhone != c.phone) {
                db.contactDao().update(c.copy(phone = fixedPhone))
                fixed++
            }
        }
        _needPhoneRepair.value = false
        return fixed
    }
}