package com.difusion.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.difusion.app.data.AppDatabase
import com.difusion.app.data.CallRecord
import com.difusion.app.data.Contact
import com.difusion.app.storage.RecordStore
import com.difusion.app.storage.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RepeatMode { QUEUE, USER }

class CallSequencer private constructor(private val context: Context) {

    init {
        com.difusion.app.service.CallMonitor.setContext(context.applicationContext)
    }

    companion object {
        @Volatile
        private var instance: CallSequencer? = null

        fun getInstance(context: Context): CallSequencer =
            instance ?: synchronized(this) {
                instance ?: CallSequencer(context.applicationContext).also { instance = it }
            }
    }

    private val telephony: TelephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var phoneListener: PhoneStateListener? = null
    private var simHandle: android.telecom.PhoneAccountHandle? = null
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // Un solo scope de IO reutilizable (antes se creaba uno nuevo por llamada).
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var transitionJob: Job? = null
    private var watchdogJob: Job? = null
    private var maxCallJob: Job? = null
    private var telecomWatchJob: Job? = null
    private var callToken = 0L
    private var lastActionMs = 0L
    private var connectedAtMs = 0L
    private var currentIndex = 0
    private var total = 0
    // Estos dos valores se exponen como StateFlow (ver _callActive/_callConnected)
    // para que la UI (CallActivity) sepa cuándo la llamada está en curso y cuándo
    // el cliente contestó (para habilitar "Gestionar cliente").
    private var callActive: Boolean
        get() = _callActive.value
        set(value) { _callActive.value = value }
    private var callConnected: Boolean
        get() = _callConnected.value
        set(value) { _callConnected.value = value }
    private var redialActive = false
    private var resumeFast = false
    private var repeats = 1
    private var repMode = RepeatMode.QUEUE
    private var globalTotal = 0
    private var callsPlaced = 0
    private var roundIndex = 1
    private var userRepeat = 0

    // Números cuya llamada fue VÁLIDA (el cliente contestó). No se vuelven a
    // marcar: al repetir (por cola o por usuario) se saltan automáticamente.
    private val validPhones = mutableSetOf<String>()

    private val _currentContact = MutableStateFlow<Contact?>(null)
    val currentContact: StateFlow<Contact?> = _currentContact.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _status = MutableStateFlow<String>("Listo")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _ringDurationMs = MutableStateFlow(30000L)
    val ringDurationMs: StateFlow<Long> = _ringDurationMs.asStateFlow()

    private val _contactsCount = MutableStateFlow(0)
    val contactsCount: StateFlow<Int> = _contactsCount.asStateFlow()

    private val _betweenCallsMs = MutableStateFlow(30_000L)
    val betweenCallsMs: StateFlow<Long> = _betweenCallsMs.asStateFlow()

    // Tope de duración de una llamada YA contestada (0 = sin límite). Si el
    // cliente deja la llamada excesivamente larga, se cuelga y se pasa al
    // siguiente cliente.
    private val _maxCallMs = MutableStateFlow(0L)
    val maxCallMs: StateFlow<Long> = _maxCallMs.asStateFlow()

    // Repeticiones de la secuencia (1 a 10). "Por cola" repite la lista
    // completa desde el principio; "por usuario" repite cada número antes de
    // pasar al siguiente.
    private val _repeatTimes = MutableStateFlow(1)
    val repeatTimes: StateFlow<Int> = _repeatTimes.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.QUEUE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _round = MutableStateFlow(1)
    val round: StateFlow<Int> = _round.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _currentIdx = MutableStateFlow(0)
    val currentIdx: StateFlow<Int> = _currentIdx.asStateFlow()

    private val _callState = MutableStateFlow<String>("Detenida")
    val callState: StateFlow<String> = _callState.asStateFlow()

    private val _callActive = MutableStateFlow(false)
    val callActiveFlow: StateFlow<Boolean> = _callActive.asStateFlow()

    private val _callConnected = MutableStateFlow(false)
    val callConnectedFlow: StateFlow<Boolean> = _callConnected.asStateFlow()

    private var contactsList: List<Contact> = emptyList()

    fun setBetweenCalls(ms: Long) {
        _betweenCallsMs.value = ms.coerceIn(0L, 60_000L)
    }

    fun setRingDuration(ms: Long) {
        _ringDurationMs.value = ms
    }

    fun setMaxCallDuration(ms: Long) {
        _maxCallMs.value = ms.coerceAtLeast(0L)
    }

    fun setRepeatTimes(n: Int) {
        _repeatTimes.value = n.coerceIn(1, 10)
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
    }

    fun start(contacts: List<Contact>, simHandle: android.telecom.PhoneAccountHandle? = null) {
        this.simHandle = simHandle
        CallMonitor.setSimHandle(simHandle)
        contactsList = contacts
        _contacts.value = contacts
        repeats = _repeatTimes.value
        repMode = _repeatMode.value
        globalTotal = contacts.size * repeats
        _contactsCount.value = globalTotal
        stopListener()
        currentIndex = 0
        callsPlaced = 0
        roundIndex = 1
        userRepeat = 0
        _round.value = 1
        _currentIdx.value = 0
        total = contacts.size
        callActive = false
        callConnected = false
        redialActive = false
        resumeFast = false
        callToken = 0L
        validPhones.clear()
        _isPaused.value = false
        _progress.value = 0
        transitionJob?.cancel()
        transitionJob = null
        maxCallJob?.cancel()
        maxCallJob = null

        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        // OJO: OFFHOOK se dispara al EMPEZAR A MARCAR, no cuando
                        // el cliente contesta (verificado en el TECNO: pasa a
                        // OFFHOOK ~1s después de marcar, mientras sigue DIALING).
                        // Si la app es el marcador predeterminado, la respuesta
                        // real llega por Telecom (watchTelecomAnswer, STATE_ACTIVE).
                        // Si no lo es, se usa el respaldo de antes: asumir
                        // contestada tras una breve espera.
                        if (callActive && !callConnected) {
                            val token = callToken
                            scope.launch {
                                delay(1500)
                                if (callActive && !callConnected && token == callToken &&
                                    !CallMonitor.hasTelecomView()
                                ) {
                                    callConnected = true
                                    onConnected(token)
                                    runCatching {
                                        CallRecorder.autoStart(
                                            context,
                                            _currentContact.value?.let { contractPhone(it) } ?: "Desconocido"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (callActive) {
                            callActive = false
                            val recordContact = _currentContact.value
                            val wasConnected = callConnected
                            callConnected = false
                            maxCallJob?.cancel()
                            maxCallJob = null
                            stopFallbackRecorder()
                            onCallFinished(recordContact, wasConnected)
                        }
                    }
                }
            }
        }
        phoneListener = listener
        runCatching { telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) }
            .onFailure { _status.value = "No se pudo supervisar el estado de llamada" }
        _isRunning.value = true
        _status.value = "Iniciando secuencia de $globalTotal llamadas"
        lastActionMs = System.currentTimeMillis()
        syncNotification()
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (true) {
                delay(2000)
                watchdogCheck()
            }
        }
        scope.launch {
            delay(800)
            callNext(contacts)
        }
    }

    // Comprueba periódicamente que la secuencia siga viva. Si la llamada
    // terminó pero el evento de estado no llegó (sucede al volver de la app de
    // teléfono), la detecta y continúa; si no hay nada pendiente programado,
    // retoma el avance por sí sola sin quedarse colgada.
    private fun watchdogCheck() {
        if (!_isRunning.value) return
        if (callActive) {
            val realState = runCatching { telephony.callState }
                .getOrDefault(TelephonyManager.CALL_STATE_IDLE)
            when (realState) {
                TelephonyManager.CALL_STATE_IDLE -> {
                    // La llamada ya terminó y no llegó el evento: finalizamos y seguimos.
                    val c = _currentContact.value
                    val finishedConnected = callConnected
                    callActive = false
                    callConnected = false
                    maxCallJob?.cancel()
                    maxCallJob = null
                    stopFallbackRecorder()
                    _status.value = "Llamada terminada detectada, continuando..."
                    onCallFinished(c, finishedConnected)
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    // La contestación real la detecta watchTelecomAnswer (Telecom)
                    // o el respaldo del listener; aquí solo se re-verifica el tope
                    // de duración de una llamada ya conectada.
                    if (callConnected) {
                        checkMaxCallTimeout(callToken)
                    }
                }
            }
            return
        }
        // Nada en curso y nada programado durante un rato: la secuencia se habría
        // quedado esperando, así que la reanudamos.
        if (!_isPaused.value &&
            transitionJob?.isActive != true &&
            callsPlaced < globalTotal &&
            System.currentTimeMillis() - lastActionMs > 2500
        ) {
            _status.value = "Secuencia reanudada automáticamente"
            scheduleNext(if (resumeFast) 300 else _betweenCallsMs.value)
        }
    }

    // Permite re-verificar el estado al volver a la app (onResume).
    fun kick() {
        watchdogCheck()
    }

    private fun callNext(contacts: List<Contact>) {
        if (!_isRunning.value) return
        if (currentIndex >= total) {
            finish()
            return
        }
        callsPlaced++
        _progress.value = callsPlaced
        _currentIdx.value = callsPlaced
        val contact = contacts[currentIndex]
        placeCall(contact, callLabelFor(contact))
        syncNotification()
    }

    private fun callLabelFor(contact: Contact): String = when (repMode) {
        RepeatMode.QUEUE -> "Llamando a ${contact.name} (ronda $roundIndex de $repeats)"
        RepeatMode.USER -> "Llamando a ${contact.name} (vez ${userRepeat + 1} de $repeats)"
    }

    private fun placeCall(contact: Contact, statusText: String) {
        _currentContact.value = contact
        // Red de seguridad: si un cierre anormal dejó el micrófono mudo, se
        // restaura antes de iniciar la nueva llamada.
        CallMicMute.restore(context)
        callActive = true
        callConnected = false
        connectedAtMs = 0L
        callToken++
        lastActionMs = System.currentTimeMillis()
        _callState.value = "Marcando / Sonando…"
        _status.value = statusText
        CallMonitor.setSimHandle(simHandle)

        val phone = contractPhone(contact)
        if (!isPlausibleNumber(phone)) {
            // Número incompleto o mal formado: la red lo rechazaría a los pocos
            // segundos sin dejarlo sonar (verificado: Movilnet responde
            // CM_RESOURCE_UNAVAIL_UNSPECIFIED a los ~3s). Se marca como fallido
            // de inmediato y se pasa al siguiente sin gastar la espera entre
            // llamadas, porque la red ni siquiera fue consultada.
            callActive = false
            callConnected = false
            _status.value = "Número inválido: ${contact.name} ($phone)"
            resumeFast = true
            onCallFinished(contact, false)
            return
        }

        try {
            markCallStarted(contact)
            val ok = com.difusion.app.service.SimManager.placeCall(context, phone, simHandle)
            if (!ok) {
                callActive = false
                callConnected = false
                _status.value = "No se pudo llamar a ${contact.name}"
                onCallFinished(contact, false)
                return
            }
        } catch (e: Exception) {
            callActive = false
            callConnected = false
            _status.value = "No se pudo llamar a ${contact.name}"
            onCallFinished(contact, false)
            return
        }
        watchTelecomAnswer()
        armRingTimer(contact)
    }

    // Un número venezolano válido tras normalizar es 0 + 10 dígitos (móviles
    // 04XX y fijos 02XX) o el formato internacional +58 / 58. Se aceptan otros
    // internacionales razonables para no bloquear números legítimos.
    private fun isPlausibleNumber(phone: String): Boolean {
        val p = phone.replace(Regex("[^0-9+]"), "")
        return when {
            p.startsWith("+58") -> p.length == 13
            p.startsWith("58") -> p.length == 12
            p.startsWith("0") -> p.length == 11
            p.startsWith("+") -> p.length in 8..15
            else -> p.length >= 10
        }
    }

    // Observa el estado REAL de la llamada vía Telecom (InCallService), que solo
    // existe cuando la app es el marcador predeterminado. La contestación de
    // verdad es STATE_ACTIVE — no el OFFHOOK de TelephonyManager, que salta al
    // marcar. El consecutivo evita confundir el ACTIVE de la llamada anterior.
    private fun watchTelecomAnswer() {
        telecomWatchJob?.cancel()
        val startSeq = CallMonitor.telecomState.value.seq
        val token = callToken
        telecomWatchJob = scope.launch {
            CallMonitor.telecomState.collect { tc ->
                if (!callActive || callConnected || token != callToken) return@collect
                if (tc.seq == startSeq) return@collect
                if (tc.state == android.telecom.Call.STATE_ACTIVE) {
                    callConnected = true
                    onConnected(token)
                    runCatching {
                        CallRecorder.autoStart(
                            context,
                            _currentContact.value?.let { contractPhone(it) } ?: "Desconocido"
                        )
                    }
                }
            }
        }
    }

    private fun contractPhone(contact: Contact): String =
        com.difusion.app.import.Importer.normalizePhone(contact.phone)

    // Una llamada es "válida" cuando el cliente contestó (se registró conectada).
    private fun isValid(contact: Contact): Boolean =
        validPhones.contains(contractPhone(contact))

    // Llamada conectada (el cliente contestó): cancela el temporizador de timbrado
    // para que la conversación no se interrumpa, muestra el nombre y arma el
    // tope de duración, si está configurado. La siguiente llamada solo se
    // programa cuando esta llama termina (estado IDLE real).
    private fun onConnected(token: Long) {
        if (token != callToken) return
        connectedAtMs = System.currentTimeMillis()
        // El mensaje pregrabado y el silenciado del micrófono los maneja
        // DifusionInCallService (cubre tanto la secuencia como llamadas sueltas).
        transitionJob?.cancel()
        transitionJob = null
        _callState.value = "En llamada"
        _currentContact.value?.let { c ->
            _status.value = "En llamada con ${c.name}"
        } ?: run { _status.value = "En llamada" }
        syncNotification()
        armMaxCallTimer(token)
    }

    // Tope de duración de una llamada contestada: si se excede, cuelga y pasa al
    // siguiente cliente. El "conteo" solo avanza al colgar de verdad.
    private fun armMaxCallTimer(token: Long) {
        maxCallJob?.cancel()
        maxCallJob = null
        val limit = _maxCallMs.value
        if (limit <= 0) return
        maxCallJob = scope.launch {
            delay(limit)
            checkMaxCallTimeout(token)
        }
    }

    private fun checkMaxCallTimeout(token: Long) {
        if (!_isRunning.value || !callActive || !callConnected || token != callToken) return
        val limit = _maxCallMs.value
        // Sin tope configurado (0) jamás se cuelga por tiempo. Y con tope, solo
        // se cuelga cuando el tiempo YA venció: el watchdog re-verifica cada 2s
        // y antes colgaba la llamada inmediatamente al detectarla conectada.
        if (limit <= 0) return
        if (System.currentTimeMillis() - connectedAtMs < limit) return
        val contact = _currentContact.value
        _status.value = "Llamada superó el tiempo máximo, pasando al siguiente"
        forceEndCall()
        callActive = false
        callConnected = false
        maxCallJob?.cancel()
        maxCallJob = null
        onCallFinished(contact, true)
    }

    private fun forceEndCall() {
        CallMonitor.endCall()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            runCatching {
                (context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)
                    ?.endCall()
            }
        }
    }

    // Re-llama a un contacto concreto del listado sin alterar el avance de la
    // secuencia principal. Cuando termina, la secuencia continúa donde iba.
    fun callSpecific(contact: Contact?): Boolean {
        val c = contact ?: return false
        if (!_isRunning.value) return false
        if (callActive) return false
        redialActive = true
        transitionJob?.cancel()
        transitionJob = null
        placeCall(c, "Re-llamando a ${c.name}")
        return true
    }

    private fun armRingTimer(contact: Contact) {
        transitionJob?.cancel()
        lastActionMs = System.currentTimeMillis()
        val token = callToken
        transitionJob = scope.launch {
            delay(_ringDurationMs.value)
            if (callActive && _isRunning.value && token == callToken) {
                // Si el cliente contestó, la conversación la gobierna el tope de
                // duración (maxCallTimer), no el temporizador de timbrado.
                if (callConnected) return@launch
                // Nadie contestó dentro del tiempo de timbrado: si la llamada
                // sigue viva (marcando/sonando) se cuelga de verdad; si ya
                // murió (rechazo de red) solo se avanza.
                val realState = runCatching { telephony.callState }
                    .getOrDefault(TelephonyManager.CALL_STATE_IDLE)
                if (realState != TelephonyManager.CALL_STATE_IDLE) {
                    forceEndCall()
                }
                _status.value = "Sin respuesta de ${contact.name}, pasando al siguiente"
                callActive = false
                callConnected = false
                maxCallJob?.cancel()
                maxCallJob = null
                onCallFinished(contact, false)
            }
        }
    }

    private fun onCallFinished(contact: Contact?, success: Boolean) {
        CallMessagePlayer.stop()
        CallMicMute.restore(context)
        // Si la llamada fue válida (contestada), queda marcada para no repetirla.
        if (success && contact != null) {
            validPhones.add(contractPhone(contact))
        }
        lastActionMs = System.currentTimeMillis()
        _callState.value = "Llamada terminada"
        recordCall(contact, success)
        transitionJob?.cancel()
        transitionJob = null
        maxCallJob?.cancel()
        maxCallJob = null
        telecomWatchJob?.cancel()
        telecomWatchJob = null
        syncNotification()
        if (redialActive) {
            redialActive = false
            continueAfterRedial()
        } else {
            advanceOrFinish()
        }
    }

    private fun nowStamp(): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

    // Al iniciar una llamada se marca el contacto como contactado por LLAMADA
    // y se actualiza la fecha de gestión.
    private fun markCallStarted(contact: Contact) {
        ioScope.launch {
            try {
                val dao = AppDatabase.getInstance(context).contactDao()
                val fresh = dao.getById(contact.id) ?: contact
                val updated = fresh.copy(medio = "LLAMADA", fechaGestion = nowStamp())
                dao.update(updated)
                DriveSyncService.enqueue(context, updated)
            } catch (_: Exception) {
            }
        }
    }

    private fun recordCall(contact: Contact?, success: Boolean) {
        val c = contact ?: return
        ioScope.launch {
            try {
                val rec = CallRecord(
                    date = System.currentTimeMillis(),
                    contactName = c.name,
                    phone = c.phone,
                    success = success,
                    label = com.difusion.app.data.CallLabels.NONE,
                    user = UserStore.getUser(context)
                )
                AppDatabase.getInstance(context).callDao().insert(rec)
                RecordStore.appendCall(context, rec)
                // Si la llamada NO fue contestada, el seguimiento queda en
                // "NO CONTESTA" (con medio LLAMADA y fecha de gestión al día).
                if (!success) {
                    val dao = AppDatabase.getInstance(context).contactDao()
                    val fresh = dao.getById(c.id) ?: c
                    val updated = fresh.copy(
                        medio = "LLAMADA",
                        gestion = "NO CONTESTA",
                        fechaGestion = nowStamp()
                    )
                    dao.update(updated)
                    DriveSyncService.enqueue(context, updated)
                }
            } catch (_: Exception) {
            }
        }
    }

    // Después de una re-llamada retoma la secuencia principal desde el punto
    // donde quedaba (sin importar qué número se re-llamó).
    private fun continueAfterRedial() {
        transitionJob?.cancel()
        transitionJob = null
        if (!_isRunning.value) return
        if (_isPaused.value) {
            _status.value = "Cola en pausa, reanudar para continuar"
            return
        }
        _status.value = "Retomando secuencia donde quedaba"
        scheduleNext(_betweenCallsMs.value)
    }

    private fun advanceOrFinish() {
        transitionJob?.cancel()
        transitionJob = null
        if (!_isRunning.value) return
        when (repMode) {
            RepeatMode.QUEUE -> advanceQueue()
            RepeatMode.USER -> advanceUser()
        }
    }

    // Por cola: se recorre la lista y se reinicia hasta completar las rondas.
    // Las llamadas VÁLIDAS (contestadas) se omiten en las rondas siguientes.
    private fun advanceQueue() {
        if (_isPaused.value) {
            _status.value = "Cola en pausa, reanudar para continuar"
            return
        }
        // Siguiente contacto NO válido dentro de esta ronda.
        var next = currentIndex + 1
        while (next < total && isValid(contactsList[next])) next++
        if (next < total) {
            currentIndex = next
            scheduleNext(betweenOrFast())
            return
        }
        // Fin de la ronda: solo continúa si quedan contactos sin llamada válida.
        roundIndex++
        _round.value = roundIndex
        val firstPending = contactsList.indexOfFirst { !isValid(it) }
        if (roundIndex > repeats || firstPending < 0) {
            finish()
            return
        }
        currentIndex = firstPending
        _status.value = "Ronda $roundIndex de $repeats: reiniciando lista (se omiten las llamadas válidas)"
        scheduleNext(betweenOrFast())
    }

    // Por usuario: al cliente actual se le llama hasta N veces, pero si una
    // llamada fue VÁLIDA no se repite y se salta al siguiente de inmediato.
    private fun advanceUser() {
        if (_isPaused.value) {
            _status.value = "Cola en pausa, reanudar para continuar"
            return
        }
        val current = contactsList.getOrNull(currentIndex)
        if (current != null && isValid(current)) {
            _status.value = "Llamada válida con ${current.name}: se pasa al siguiente"
            userRepeat = 0
            currentIndex++
            if (currentIndex >= total) {
                finish()
                return
            }
            scheduleNext(betweenOrFast())
            return
        }
        userRepeat++
        if (userRepeat < repeats) {
            _status.value = "Repitiendo con el mismo cliente (${userRepeat + 1} de $repeats)"
            scheduleNext(betweenOrFast())
            return
        }
        userRepeat = 0
        currentIndex++
        if (currentIndex >= total) {
            finish()
            return
        }
        scheduleNext(betweenOrFast())
    }

    private fun betweenOrFast(): Long =
        if (resumeFast) {
            resumeFast = false
            300
        } else {
            _betweenCallsMs.value
        }

    private fun scheduleNext(ms: Long) {
        transitionJob?.cancel()
        lastActionMs = System.currentTimeMillis()
        _callState.value = "Esperando siguiente llamada"
        transitionJob = scope.launch {
            delay(ms)
            if (_isRunning.value && !_isPaused.value) {
                transitionJob = null
                callNext(contactsList)
            }
        }
    }

    private fun finish() {
        CallMessagePlayer.stop()
        CallMicMute.restore(context)
        transitionJob?.cancel()
        transitionJob = null
        maxCallJob?.cancel()
        maxCallJob = null
        telecomWatchJob?.cancel()
        telecomWatchJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        stopFallbackRecorder()
        stopListener()
        SequenceNotification.cancel(context)
        _isRunning.value = false
        _isPaused.value = false
        redialActive = false
        resumeFast = false
        _currentContact.value = null
        _callState.value = "Secuencia terminada"
        _status.value = "Secuencia terminada"
        AppFeedback.notify(context, com.difusion.app.ui.theme.ThemePrefs.read(context))
    }

    fun pause() {
        if (!_isRunning.value || _isPaused.value) return
        _isPaused.value = true
        resumeFast = false
        transitionJob?.cancel()
        transitionJob = null
        maxCallJob?.cancel()
        maxCallJob = null
        lastActionMs = System.currentTimeMillis()
        _callState.value = "Secuencia en pausa"
        _status.value = "Secuencia en pausa"
        syncNotification()
    }

    fun resume() {
        if (!_isRunning.value || !_isPaused.value) return
        _isPaused.value = false
        transitionJob?.cancel()
        transitionJob = null
        resumeFast = true
        lastActionMs = System.currentTimeMillis()
        if (callActive) {
            // Hay una llamada en curso (p. ej. sigue sonando). No reiniciamos el
            // temporizador de espera: al terminar la llamada la secuencia continúa
            // sola y pasa al siguiente contacto de inmediato.
            _callState.value = if (callConnected) "En llamada" else "Marcando / Sonando…"
            _status.value = "Llamada en curso... al terminar se continúa automáticamente"
            return
        }
        _callState.value = "Reanudando..."
        _status.value = "Reanudando secuencia"
        scheduleNext(300)
        syncNotification()
    }

    fun stop() {
        CallMessagePlayer.stop()
        CallMicMute.restore(context)
        SequenceNotification.cancel(context)
        _isRunning.value = false
        _isPaused.value = false
        resumeFast = false
        redialActive = false
        transitionJob?.cancel()
        transitionJob = null
        maxCallJob?.cancel()
        maxCallJob = null
        telecomWatchJob?.cancel()
        telecomWatchJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        callActive = false
        callConnected = false
        stopFallbackRecorder()
        stopListener()
        _currentContact.value = null
        _callState.value = "Detenida"
        _status.value = "Secuencia detenida"
    }

    private fun stopFallbackRecorder() {
        runCatching { CallRecorder.autoStop() }
    }

    private fun stopListener() {
        phoneListener?.let { runCatching { telephony.listen(it, PhoneStateListener.LISTEN_NONE) } }
        phoneListener = null
    }

    // Mantiene la notificación permanente "Llamadas consecutivas · N de M"
    // sincronizada con el avance de la secuencia, mostrando siempre el nombre
    // del cliente actual.
    private fun syncNotification() {
        if (!_isRunning.value) return
        SequenceNotification.update(
            context,
            _progress.value,
            globalTotal,
            _status.value,
            _currentContact.value?.name
        )
    }
}