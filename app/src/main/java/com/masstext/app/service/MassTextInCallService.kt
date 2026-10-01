package com.masstext.app.service

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.masstext.app.MainActivity
import com.masstext.app.ui.CallActivity

/**
 * Se enlaza automáticamente con Telecom cuando la app es la app de llamadas
 * predeterminada y hay una llamada en curso. Expone la llamada real a
 * [CallMonitor] para la pantalla propia y el riel lateral del modo masivo.
 */
class MassTextInCallService : InCallService() {

    private val tracked = mutableMapOf<Call, Call.Callback>()

    // Llamadas que tuvimos en DIALING/CONNECTING: si llegan a RINGING son
    // llamadas salientes (timbrando del lado del cliente), no entrantes.
    private val outgoingCalls = mutableSetOf<Call>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallMonitor.setContext(this)
        CallMonitor.attachService(this)
        CallNotification.ensureChannel(this)
        CallRecorder.speakerRouteRequest = {
            runCatching { setAudioRoute(android.telecom.CallAudioState.ROUTE_SPEAKER) }
        }
        outgoingCalls.remove(call)
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
              try {
                CallMonitor.refresh(call)
                CallNotification.update(this@MassTextInCallService)
                if (CallMonitor.notifyFullScreen) CallOverlay.update(this@MassTextInCallService)
                if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                    outgoingCalls.add(call)
                }
                // Llamada entrante: mostramos la pantalla superpuesta (Contestar /
                // Buzón / Rechazar) con el número que llama, sobre cualquier app y
                // también sobre el bloqueo de pantalla. Si no hay permiso de
                // superposición, abrimos el centro de llamadas como respaldo.
                val isIncomingRing = state == Call.STATE_RINGING && !outgoingCalls.contains(call)
                if (isIncomingRing) {
                    CallOverlay.hide(this@MassTextInCallService)
                    val number = runCatching { call.details?.handle?.schemeSpecificPart }
                        .getOrNull()?.takeIf { it.isNotBlank() } ?: "Desconocido"
                    // Única pantalla de llamada (sobre el bloqueo) con
                    // Contestar / Buzón / Rechazar.
                    openCallScreen()
                    // Respaldo inmediato por si el sistema tarda en abrir la
                    // pantalla completa; la propia pantalla lo oculta al aparecer.
                    if (IncomingCallOverlay.canDraw(this@MassTextInCallService)) {
                        IncomingCallOverlay.show(this@MassTextInCallService, number)
                    }
                } else {
                    IncomingCallOverlay.hide(this@MassTextInCallService)
                    // Llamada saliente (marcando/conectando): abrir también la
                    // pantalla de llamada, sin esperar a tocar la notificación.
                    if ((state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) &&
                        CallMonitor.notifyFullScreen
                    ) {
                        openCallScreen()
                    }
                }
                if (state == Call.STATE_ACTIVE) {
                    applyBluetoothRoute()
                    // Mensaje pregrabado automático. NO forzamos la ruta ni el
                    // altavoz: el mensaje sale por donde esté la llamada
                    // (auricular, audífonos con cable/USB, Bluetooth) y el
                    // altavoz SOLO se activa si el usuario pulsa su botón.
                    // Al terminar el audio, se corta la llamada.
                    if (VoiceMessageStore.isEnabled(this@MassTextInCallService) &&
                        VoiceMessageStore.exists(this@MassTextInCallService)
                    ) {
                        android.util.Log.i(
                            "DIFUSION-Call",
                            "Mensaje: ruta respetada (wired=${CallMonitor.hasWiredHeadset()}, " +
                                "bt=${CallMonitor.bluetoothSupported()}, " +
                                "altavoz=${CallMonitor.speaker.value})"
                        )
                        CallMessagePlayer.play(this@MassTextInCallService) {
                            CallMonitor.endCall()
                        }
                    }
                    // La grabación arranca cuando la llamada se conecta. Si ya había una de
                    // marcado, se descarta y se empieza de nuevo aquí: es cuando
                    // existe audio real de los dos lados.
                    val number = runCatching { call.details?.handle?.schemeSpecificPart }
                        .getOrNull()?.takeIf { it.isNotBlank() }
                        ?: CallMonitor.info.value?.number
                        ?: "Desconocido"
                    CallRecorder.restartRecorder(this@MassTextInCallService, number)
                    // Automático: si el teléfono bloqueó la captura directa y la
                    // grabación cayó al micrófono, enrutamos la llamada al altavoz
                    // para que el micrófono capture AMBOS lados. PERO solo si no
                    // hay audífonos conectados (cable/USB/Bluetooth): con ellos
                    // puestos el altavoz no debe activarse solo.
                    if (CallRecorder.micFallbackActive() && !CallMonitor.hasExternalAudio()) {
                        runCatching {
                            setAudioRoute(android.telecom.CallAudioState.ROUTE_SPEAKER)
                        }.onSuccess {
                            CallRecorderLog.append(
                                this@MassTextInCallService,
                                "Grabación por micrófono: llamada enrutada al altavoz (automático)"
                            )
                        }
                    }
                }
              } catch (e: Exception) {
                // Nunca dejamos que un fallo aquí tumbe el proceso durante la llamada.
                CallRecorderLog.append(this@MassTextInCallService, "onStateChanged error: ${e.message}")
              }
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                CallMonitor.refresh(call)
                CallNotification.update(this@MassTextInCallService)
                if (CallMonitor.notifyFullScreen) CallOverlay.update(this@MassTextInCallService)
            }

            override fun onCallDestroyed(call: Call) {
                outgoingCalls.remove(call)
                tracked.remove(call)?.let { cb ->
                    runCatching { call.unregisterCallback(cb) }
                }
                refreshActiveCall()
            }
        }
        call.registerCallback(callback)
        tracked[call] = callback
        CallMonitor.attach(call, this)
        CallNotification.update(this)
        if (CallMonitor.notifyFullScreen) {
            openCallScreen()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        outgoingCalls.remove(call)
        tracked.remove(call)?.let { cb ->
            runCatching { call.unregisterCallback(cb) }
        }
        refreshActiveCall()
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallMonitor.onCallAudioStateChanged(audioState.route, audioState.isMuted)
            CallNotification.update(this)
            if (CallMonitor.notifyFullScreen) CallOverlay.update(this)
        }
    }

    private fun refreshActiveCall() {
        val live = tracked.keys.firstOrNull { it.state != Call.STATE_DISCONNECTED }
        if (live == null) {
            CallRecorder.stop()
            CallRecorder.speakerRouteRequest = null
            CallMessagePlayer.stop()
            CallUplinkAudio.stop()
            CallMicMute.restore(this)
            CallMonitor.detachService()
            CallMonitor.clear()
            CallNotification.cancel(this)
            CallOverlay.hide(this)
            IncomingCallOverlay.hide(this)
        } else {
            CallMonitor.refresh(live)
            CallNotification.update(this)
        }
    }

    // Si hay preferencia de Bluetooth y un auricular conectado, la llamada sale
    // por defecto por ese medio (por ejemplo los audífonos auriculares).
    private fun applyBluetoothRoute() {
        if (!CallPrefs.preferBluetooth(this)) return
        if (CallMonitor.bluetoothSupported()) {
            CallMonitor.routeToBluetooth()
        }
    }

    private fun openCallCenter() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
            )
        }
    }

    // Abre la única pantalla de llamada. Funciona desde segundo plano porque la
    // app es el marcador predeterminado y tiene permiso de superposición; además
    // hay una notificación con pantalla completa de respaldo.
    private fun openCallScreen() {
        runCatching {
            startActivity(
                Intent(this, CallActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                }
            )
        }.onFailure {
            CallRecorderLog.append(this, "No se pudo abrir pantalla de llamada: ${it.message}")
        }
    }

    override fun onDestroy() {
        CallRecorder.speakerRouteRequest = null
        CallMonitor.detachService()
        super.onDestroy()
    }
}
