package com.masstext.app.service

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallInfo(
    val number: String,
    val state: String
)

/**
 * Estado de la llamada actual visto desde Telecom (InCallService).
 * Solo se alimenta cuando la app es la app de llamadas predeterminada;
 * mientras tanto la secuencia sigue usando TelephonyManager.
 *
 * El audio (mute/altavoz/Bluetooth) se controla con [InCallService.setAudioRoute]
 * que es la API oficial de Telecom. AudioManager se usa solo para detectar
 * el estado actual de los dispositivos de audio.
 */
object CallMonitor {

    private const val TAG = "CallMonitor"

    private var theCall: Call? = null
    private var context: Context? = null
    @Volatile private var inCallService: InCallService? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Si hay una llamada en curso y no estamos en el modo masivo, se abre la
     * pantalla completa del centro de llamadas. En el modo masivo se mantiene
     * solo el riel lateral para no interrumpir el listado. */
    var notifyFullScreen: Boolean = true

    /** SIM usada en la llamada actual. */
    var currentSimHandle: PhoneAccountHandle? = null
        private set

    /** Callback para recolgar cuando el usuario pide cambiar de SIM durante DIALING. */
    var onRedialRequested: ((phone: String) -> Unit)? = null

    private val _info = MutableStateFlow<CallInfo?>(null)
    val info: StateFlow<CallInfo?> = _info.asStateFlow()

    // Estado crudo de la llamada según Telecom, con un consecutivo que cambia
    // cada vez que aparece una llamada NUEVA. Permite al secuenciador saber
    // cuándo el cliente contestó de verdad (STATE_ACTIVE) sin confundirse con
    // estados viejos de la llamada anterior.
    data class TelecomCallState(val seq: Long, val state: Int?)

    private val _telecomState = MutableStateFlow(TelecomCallState(0L, null))
    val telecomState: StateFlow<TelecomCallState> = _telecomState.asStateFlow()

    /** true cuando la app es el marcador predeterminado y Telecom nos reporta
     *  el estado real de la llamada (vía InCallService). */
    fun hasTelecomView(): Boolean = inCallService != null

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _speaker = MutableStateFlow(false)
    val speaker: StateFlow<Boolean> = _speaker.asStateFlow()

    private val _onHold = MutableStateFlow(false)
    val onHold: StateFlow<Boolean> = _onHold.asStateFlow()

    private val _bluetooth = MutableStateFlow(false)
    val bluetooth: StateFlow<Boolean> = _bluetooth.asStateFlow()

    fun setContext(ctx: Context?) {
        context = ctx?.applicationContext
    }

    /** Llamado desde MassTextInCallService.onCallAdded para guardar la referencia
     *  al servicio (necesaria para usar setAudioRoute). */
    fun attachService(service: InCallService) {
        inCallService = service
    }

    /** Libera la referencia al InCallService cuando ya no hay llamada viva, para
     *  no invocar un binder muerto en la siguiente llamada. */
    fun detachService() {
        inCallService = null
    }

    fun setSimHandle(handle: PhoneAccountHandle?) {
        currentSimHandle = handle
    }

    fun currentCall(): Call? = theCall

    // Llamadas que estuvieron en DIALING/CONNECTING: si llegan a RINGING son
    // salientes (timbrando del lado del cliente), no entrantes.
    private val outgoingCalls = mutableSetOf<Call>()

    /** Solo en API 29+ (Call.Details.getCallDirection extra); en API 28 se usa
     *  la heurística de [outgoingCalls]. */
    fun isIncoming(call: Call?): Boolean {
        val c = call ?: return false
        if (c.state != Call.STATE_RINGING) return false
        if (outgoingCalls.contains(c)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dir = runCatching { c.details.callDirection }
                .getOrDefault(Call.Details.DIRECTION_UNKNOWN)
            if (dir == Call.Details.DIRECTION_OUTGOING) return false
            if (dir == Call.Details.DIRECTION_INCOMING) return true
        }
        return true
    }

    fun attach(call: Call, ctx: Context?) {
        context = ctx?.applicationContext
        theCall = call
        // Llamada nueva: el consecutivo avanza para que los observadores no
        // confundan estados viejos de la llamada anterior con esta.
        _telecomState.value = TelecomCallState(_telecomState.value.seq + 1, call.state)
        refresh(call)
    }

    fun refresh(call: Call) {
        if (theCall !== call) {
            theCall = call
            _telecomState.value = TelecomCallState(_telecomState.value.seq + 1, call.state)
        } else {
            _telecomState.value = TelecomCallState(_telecomState.value.seq, call.state)
        }
        if (call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING) {
            outgoingCalls.add(call)
        }
        val number = runCatching { call.details?.handle?.schemeSpecificPart }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "Desconocido"
        _info.value = CallInfo(number, stateText(call.state))
        _onHold.value = call.state == Call.STATE_HOLDING
        refreshAudio()
    }

    /** Llamado desde MassTextInCallService.onCallAudioStateChanged para
     *  sincronizar el estado de audio con el framework telecom. */
    fun onCallAudioStateChanged(route: Int, isMuted: Boolean) {
        _muted.value = isMuted
        _speaker.value = (route and CallAudioState.ROUTE_SPEAKER) != 0
        _bluetooth.value = (route and CallAudioState.ROUTE_BLUETOOTH) != 0
        Log.i(TAG, "onCallAudioStateChanged: route=$route, muted=$isMuted")
    }

    fun refreshAudio() {
        // Con InCallService el estado real llega por onCallAudioStateChanged
        // (telecom). AudioManager en Honor/Huawei no refleja el ruteo real.
        if (inCallService != null) return
        val audio = context?.let { it.getSystemService(AUDIO_SERVICE) as? AudioManager }
        if (audio == null) return
        _muted.value = audio.isMicrophoneMute
        val commDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.communicationDevice
        } else {
            null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            _speaker.value = commDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                audio.isSpeakerphoneOn
            _bluetooth.value = commDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                audio.isBluetoothScoOn
        } else {
            _speaker.value = audio.isSpeakerphoneOn
            _bluetooth.value = audio.isBluetoothScoOn
        }
    }

    fun clear() {
        theCall = null
        outgoingCalls.clear()
        _telecomState.value = TelecomCallState(_telecomState.value.seq, null)
        _info.value = null
        _muted.value = false
        _speaker.value = false
        _onHold.value = false
        _bluetooth.value = false
    }

    private fun stateText(state: Int): String = when (state) {
        Call.STATE_DIALING -> "Marcando…"
        Call.STATE_RINGING -> "Sonando…"
        Call.STATE_ACTIVE -> "En llamada"
        Call.STATE_HOLDING -> "En espera"
        Call.STATE_DISCONNECTING -> "Finalizando…"
        Call.STATE_DISCONNECTED -> "Terminada"
        Call.STATE_CONNECTING -> "Conectando…"
        Call.STATE_AUDIO_PROCESSING -> "Procesando audio…"
        else -> "Llamada"
    }

    fun endCall() {
        val c = theCall ?: return
        if (c.state != Call.STATE_DISCONNECTED) {
            runCatching { c.disconnect() }
        }
    }

    /** Contesta la llamada entrante usando el auricular/altavoz por defecto. */
    fun answerIncomingCall() {
        val c = theCall ?: return
        if (c.state != Call.STATE_RINGING) {
            Log.w(TAG, "answerIncomingCall: no hay llamada entrante (state=${c.state})")
            return
        }
        runCatching { c.answer(CallAudioState.ROUTE_WIRED_OR_EARPIECE) }
            .onFailure { Log.w(TAG, "answerIncomingCall fallo: ${it.message}") }
    }

    /** Rechaza la llamada entrante o, si [sendToVoicemail] es true, la envía al buzón. */
    fun rejectIncomingCall(sendToVoicemail: Boolean = false) {
        val c = theCall ?: return
        if (c.state != Call.STATE_RINGING) {
            Log.w(TAG, "rejectIncomingCall: no hay llamada entrante (state=${c.state})")
            return
        }
        runCatching { c.reject(sendToVoicemail, null) }
            .onFailure { Log.w(TAG, "rejectIncomingCall fallo: ${it.message}") }
    }

    fun toggleMute() {
        val svc = inCallService
        if (svc != null) {
            val next = !_muted.value
            runCatching { svc.setMuted(next) }
            _muted.value = next
            Log.i(TAG, "toggleMute (via InCallService): muted=$next")
            return
        }
        val audio = context?.let { it.getSystemService(AUDIO_SERVICE) as? AudioManager }
            ?: return
        val next = !audio.isMicrophoneMute
        runCatching { audio.isMicrophoneMute = next }
        _muted.value = next
    }

    fun toggleSpeaker() {
        val call = theCall
        if (call == null) {
            Log.w(TAG, "toggleSpeaker: no active call")
            return
        }
        Log.i(TAG, "toggleSpeaker: actual speaker=${_speaker.value}")
        if (_speaker.value) {
            setEarpiece()
        } else {
            setSpeaker()
        }
    }

    /**
     * Activa el altavoz usando InCallService.setAudioRoute() — la API oficial
     * de Telecom que Honor/Huawei SÍ respeta (a diferencia de AudioManager).
     */
    private fun setSpeaker() {
        val svc = inCallService
        if (svc != null) {
            runCatching {
                svc.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }.onFailure {
                Log.w(TAG, "InCallService.setAudioRoute(SPEAKER) fallo: ${it.message}")
                setSpeakerFallback()
            }
            Log.i(TAG, "setSpeaker via InCallService: route=ROUTE_SPEAKER")
        } else {
            Log.w(TAG, "setSpeaker: InCallService null, usando fallback AudioManager")
            setSpeakerFallback()
        }
        refreshAudio()
    }

    private fun setEarpiece() {
        val svc = inCallService
        if (svc != null) {
            runCatching {
                svc.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            }.onFailure {
                Log.w(TAG, "InCallService.setAudioRoute(EARPIECE) fallo: ${it.message}")
                setEarpieceFallback()
            }
            Log.i(TAG, "setEarpiece via InCallService: route=ROUTE_EARPIECE")
        } else {
            Log.w(TAG, "setEarpiece: InCallService null, usando fallback AudioManager")
            setEarpieceFallback()
        }
        refreshAudio()
    }

    /** Fallback: usa AudioManager directamente (funciona en algunos dispositivos). */
    private fun setSpeakerFallback() {
        val audio = context?.let { it.getSystemService(AUDIO_SERVICE) as? AudioManager }
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = availableDevice(audio, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            if (device != null) {
                runCatching { audio.setCommunicationDevice(device) }
                    .onFailure { Log.w(TAG, "setCommunicationDevice(speaker): ${it.message}") }
            }
        }
        runCatching {
            audio.isSpeakerphoneOn = true
            audio.isBluetoothScoOn = false
        }.onFailure { Log.w(TAG, "legacy setSpeaker: ${it.message}") }
    }

    private fun setEarpieceFallback() {
        val audio = context?.let { it.getSystemService(AUDIO_SERVICE) as? AudioManager }
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = availableDevice(audio, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
            if (device != null) {
                runCatching { audio.setCommunicationDevice(device) }
                    .onFailure { Log.w(TAG, "setCommunicationDevice(earpiece): ${it.message}") }
            }
        }
        runCatching {
            audio.isSpeakerphoneOn = false
            audio.isBluetoothScoOn = false
        }.onFailure { Log.w(TAG, "legacy setEarpiece: ${it.message}") }
    }

    /** Cambia la llamada actual a la otra SIM (desconecta + recolga). */
    fun switchSim() {
        val call = theCall ?: return
        val number = _info.value?.number ?: return
        if (!canSwitchSim(call)) return
        Log.i(TAG, "switchSim: disconnecting call to $number for SIM switch")
        call.disconnect()
        scope.launch {
            delay(300)
            onRedialRequested?.invoke(number)
        }
    }

    /** Solo se puede cambiar de chip mientras no se haya contestado. */
    fun canSwitchSim(call: Call?): Boolean =
        call != null && (call.state == Call.STATE_DIALING ||
            call.state == Call.STATE_CONNECTING ||
            call.state == Call.STATE_RINGING)

    fun bluetoothSupported(): Boolean {
        val ctx = context ?: return false
        return CallBluetooth.hasConnectedHeadset(ctx)
    }

    /** true si hay audífonos con cable/USB conectados. */
    fun hasWiredHeadset(): Boolean {
        val ctx = context ?: return false
        val audio = ctx.getSystemService(AUDIO_SERVICE) as? AudioManager ?: return false
        return runCatching {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET)
            }
        }.getOrDefault(false)
    }

    /** true si la llamada debe respetar un dispositivo externo (cable o Bluetooth). */
    fun hasExternalAudio(): Boolean = hasWiredHeadset() || bluetoothSupported()

    fun routeToBluetooth() {
        val ctx = context ?: return
        val audio = ctx.getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        val bt = CallBluetooth.connectedBluetoothDevices(ctx)
        val device = bt.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?: bt.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    }
            } else {
                null
            }
        if (device != null) {
            routeToDevice(audio, device)
        }
        refreshAudio()
    }

    private fun availableDevice(audio: AudioManager, type: Int): AudioDeviceInfo? =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == type }

    private fun routeToDevice(audio: AudioManager, device: AudioDeviceInfo): Boolean {
        val result = runCatching { audio.setCommunicationDevice(device) }
            .onFailure { Log.w(TAG, "setCommunicationDevice(${device.type}) fallo: ${it.message}") }
        return result.getOrDefault(audio.communicationDevice?.type == device.type)
    }

    fun toggleHold() {
        val c = theCall ?: return
        if (c.state == Call.STATE_HOLDING) {
            runCatching { c.unhold() }
        } else {
            runCatching { c.hold() }
        }
    }

    fun dtmf(digit: Char) {
        val c = theCall ?: return
        runCatching { c.playDtmfTone(digit) }
    }
}
