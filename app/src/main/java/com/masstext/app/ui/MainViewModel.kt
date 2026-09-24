package com.masstext.app.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masstext.app.data.AppDatabase
import com.masstext.app.data.CallRecord
import com.masstext.app.data.Contact
import com.masstext.app.data.Conversation
import com.masstext.app.data.MessageTemplate
import com.masstext.app.data.PurgedSms
import com.masstext.app.data.SendRecord
import com.masstext.app.data.SmsMessage
import com.masstext.app.import.ParsedRow
import com.masstext.app.service.CallPrefs
import com.masstext.app.service.CallSequencer
import com.masstext.app.service.MmsInbox
import com.masstext.app.service.MmsSender
import com.masstext.app.service.SmsBatchTask
import com.masstext.app.service.SmsController
import com.masstext.app.service.SmsInbox
import com.masstext.app.service.SmsSendService
import com.masstext.app.storage.UserStore
import com.masstext.app.ui.theme.ThemeConfig
import com.masstext.app.ui.theme.ThemePrefs
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
    val failureBreakdown = db.smsMessageDao().failuresByLabel(com.masstext.app.data.SmsStatus.FAILED)
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
        getApplication<Application>().getSharedPreferences("masstext_prefs", Context.MODE_PRIVATE)
            .getLong(KEY_DELAY_MS, 2000L)
    )
    val delayMs: StateFlow<Long> = _delayMs.asStateFlow()

    // Pausa entre llamadas consecutivas de la secuencia (máx 60 s).
    private val _callDelayMs = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("masstext_prefs", Context.MODE_PRIVATE)
            .getLong(KEY_CALL_DELAY_MS, 30_000L)
    )
    val callDelayMs: StateFlow<Long> = _callDelayMs.asStateFlow()

    // Espera de timbre antes de dar por "sin respuesta" (segundos).
    private val _ringDurationMs = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("masstext_prefs", Context.MODE_PRIVATE)
            .getLong(KEY_RING_DURATION_MS, 30_000L)
    )
    val ringDurationMs: StateFlow<Long> = _ringDurationMs.asStateFlow()

    // Tope de duración de una llamada ya contestada (0 = sin límite).
    private val _maxCallMs = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("masstext_prefs", Context.MODE_PRIVATE)
            .getLong(KEY_MAX_CALL_MS, 0L)
    )
    val maxCallMs: StateFlow<Long> = _maxCallMs.asStateFlow()

    // Modo de envío de SMS: true = MODO SEGURO (bloques + contador de 5 min),
    // false = MODO DESATENDIDO (consecutivo, sin contador).
    private val _safeMode = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("masstext_prefs", Context.MODE_PRIVATE)
            .getBoolean(KEY_SAFE_MODE, true)
    )
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()

    private val _themeConfig = MutableStateFlow(ThemePrefs.read(getApplication()))
    val themeConfig: StateFlow<ThemeConfig> = _themeConfig.asStateFlow()

    // ---------- Usuarios (nombres asignados a contactos) ----------

    private val appContext: Context = getApplication<Application>()

    private val KEY_USERS = "assigned_users"
    private val KEY_DRIVE_URL = "drive_url"
    private val _users = MutableStateFlow(loadUsers())
    val users: StateFlow<List<String>> = _users.asStateFlow()

    private fun usersPrefs(): SharedPreferences =
        appContext.getSharedPreferences("masstext_prefs", Context.MODE_PRIVATE)

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
            db.contactDao().getAllOnce()
                .filter { it.assignment == name }
                .forEach { db.contactDao().update(it.copy(assignment = "")) }
        }
    }

    fun renameUser(oldName: String, newName: String) {
        val n = newName.trim()
        if (n.isEmpty() || n == oldName) return
        val updated = _users.value.map { if (it == oldName) n else it }.distinct().sorted()
        _users.value = updated
        saveUsers(updated)
        viewModelScope.launch {
            db.contactDao().getAllOnce()
                .filter { it.assignment == oldName }
                .forEach { db.contactDao().update(it.copy(assignment = n)) }
        }
    }

    fun assignUserToContacts(userName: String, ids: Set<Long>) {
        if (ids.isEmpty()) return
        val n = userName.trim()
        if (n.isEmpty()) return
        if (n !in _users.value) addUser(n)
        viewModelScope.launch {
            db.contactDao().getByIds(ids)
                .forEach { db.contactDao().update(it.copy(assignment = n)) }
        }
        _selectedContacts.value = emptySet()
    }

    fun clearAssignment(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            db.contactDao().getByIds(ids)
                .forEach { db.contactDao().update(it.copy(assignment = "")) }
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
                    com.masstext.app.import.Importer.listVisibleSheets(url)
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

    fun finalizeSyncFromDrive() {
        val sheetIndex = _driveSelectedSheetIndex.value
        val sheetName = _driveSheets.value.getOrNull(sheetIndex)
        if (sheetName == null) {
            _driveSyncStatus.value = "Primero elige una hoja en la ventana flotante."
            return
        }
        val url = _driveUrl.value.trim()
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                _driveSyncStatus.value = "Descargando libro y leyendo la hoja \"$sheetName\"…"
                Log.d("LEX-Sync", "finalize: sheetIndex=$sheetIndex sheet=$sheetName")
                val rows = withContext(Dispatchers.IO) {
                    com.masstext.app.import.Importer.importSheet(url, sheetIndex)
                }
                Log.d("LEX-Sync", "finalize: importSheet -> ${rows.size} filas")
                if (rows.isEmpty()) {
                    val msg = "La hoja \"$sheetName\" no tiene filas con nombre y teléfono."
                    _driveSyncStatus.value = msg
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val total = rows.size
                val userName = _appUser.value.trim()
                // Si la hoja trae columna de asignación ("Asignado a"/EJECUTIVO/…),
                // se importan SOLO las filas asignadas a mi usuario (ignorando
                // mayúsculas y tildes). Si la hoja no tiene esa columna, se importan todas.
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
                _driveSyncStatus.value =
                    "Importando ${unicas.size} contactos de \"$sheetName\"…"
                db.contactDao().clear()
                unicas.forEach { row ->
                    db.contactDao().insert(
                        Contact(
                            name = row.name,
                            phone = row.phone.trim(),
                            cedula = row.cedula.trim(),
                            assignment = row.assignment.trim()
                        )
                    )
                }
                val filtro = if (hasAssignment && userName.isNotEmpty())
                    "asignados a \"$userName\"" else "de la hoja"
                val msg = "Lista actualizada desde \"$sheetName\" ($filtro). " +
                    "En la hoja: $total filas · Asignados a ti: ${mine.size} · " +
                    "Cargados: ${unicas.size} · Duplicados por cédula: $repetidas"
                _driveSyncStatus.value = msg
                Log.d("LEX-Sync", "finalize OK: $msg")
                Toast.makeText(
                    app,
                    "Cargados ${unicas.size} contactos · $repetidas duplicados",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Throwable) {
                val msg = "Error al importar \"$sheetName\": ${e.message ?: e.javaClass.simpleName}"
                _driveSyncStatus.value = msg
                Log.e("LEX-Sync", "finalize FAIL", e)
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
            }
        }
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
        viewModelScope.launch {
            smsSender.sendSingle(address, body)
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
                        conv.threadId, com.masstext.app.data.SmsStatus.FAILED
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
                lastStatus = if (last.isIncoming) 0 else last.status,
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

    fun saveContact(name: String, phone: String, assignment: String = "") {
        viewModelScope.launch {
            // Se guarda el teléfono tal cual lo escribe el usuario.
            db.contactDao().insert(Contact(name = name, phone = phone.trim(), assignment = assignment.trim()))
        }
    }

    fun addContacts(rows: List<ParsedRow>) {
        viewModelScope.launch {
            for (row in rows) {
                // Se importan los teléfonos exactamente como vienen del archivo.
                db.contactDao().insert(Contact(name = row.name, phone = row.phone.trim(), assignment = row.assignment))
            }
        }
    }

    fun saveTemplate(name: String, body: String) {
        viewModelScope.launch {
            db.templateDao().insert(MessageTemplate(name = name, body = body))
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
        SmsBatchTask.set(SmsBatchTask.PendingBatch(contacts, message, delayMs, _safeMode.value))
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
        return com.masstext.app.import.Importer.repairScientificArtifact(cleaned) != cleaned ||
            com.masstext.app.import.Importer.normalizePhone(cleaned.toString()) != cleaned
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
            val fixedPhone = com.masstext.app.import.Importer.repairScientificArtifact(cleaned)
                .let { com.masstext.app.import.Importer.normalizePhone(it) }
            if (fixedPhone != c.phone) {
                db.contactDao().update(c.copy(phone = fixedPhone))
                fixed++
            }
        }
        _needPhoneRepair.value = false
        return fixed
    }
}