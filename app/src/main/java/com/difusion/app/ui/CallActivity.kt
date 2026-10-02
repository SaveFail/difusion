package com.difusion.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.difusion.app.service.CallMonitor
import com.difusion.app.service.CallOverlay
import com.difusion.app.service.IncomingCallOverlay
import com.difusion.app.ui.theme.DifusionTheme
import com.difusion.app.ui.theme.ThemePrefs

/**
 * ÚNICA pantalla de llamada de la app. Se muestra sobre el bloqueo de pantalla
 * y sobre cualquier aplicación, tanto para llamadas entrantes (Contestar /
 * Buzón / Rechazar) como salientes y en curso (colgar, silenciar, altavoz,
 * espera, teclado DTMF, Bluetooth, grabación). Al minimizar se cierra y deja la
 * barra flotante para volver a ella.
 */
class CallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContent {
            DifusionTheme(config = ThemePrefs.read(this)) {
                CallScreen(onFinished = { finishAndRemoveTask() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Ya estamos en la pantalla de llamada: no hacen falta las barras flotantes.
        CallOverlay.suppressed = true
        CallOverlay.hide(this)
        IncomingCallOverlay.hide(this)
    }

    override fun onStop() {
        super.onStop()
        // Al minimizar/salir con una llamada viva, dejamos la barra para volver.
        CallOverlay.suppressed = false
        if (!isFinishing && CallMonitor.currentCall() != null) {
            CallOverlay.show(this)
        }
    }

    // Permite que la pantalla aparezca sobre el bloqueo y encienda la pantalla.
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun CallScreen(onFinished: () -> Unit) {
    val info by CallMonitor.info.collectAsState()
    var hadCall by remember { mutableStateOf(false) }

    LaunchedEffect(info) {
        if (info != null) {
            hadCall = true
        } else if (hadCall) {
            onFinished()
        }
    }

    if (info == null) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        FullCallPanel(onMinimize = onFinished)
    }
}
