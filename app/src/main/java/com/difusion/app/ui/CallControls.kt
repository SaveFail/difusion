@file:OptIn(ExperimentalFoundationApi::class)

package com.difusion.app.ui

import android.content.Context
import com.difusion.app.MainActivity
import com.difusion.app.EXTRA_OPEN_CALLS_TAB
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.difusion.app.service.CallMonitor
import com.difusion.app.service.CallRecorder
import com.difusion.app.service.RecordingState
import com.difusion.app.service.SimManager

fun openCallCenter(context: Context) {
    runCatching {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(EXTRA_OPEN_CALLS_TAB, true)
            }
        )
    }
}

// Vuelve a la pantalla de llamada (la única de la app).
fun openCallScreen(context: Context) {
    runCatching {
        context.startActivity(
            Intent(context, CallActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
        )
    }
}

@Composable
fun CallCenterContent(
    onMinimize: () -> Unit,
    onCallNumber: (String) -> Unit
) {
    val info by CallMonitor.info.collectAsState()
    val hadCall = remember { mutableStateOf(false) }
    LaunchedEffect(info) {
        if (info != null) {
            hadCall.value = true
        } else if (hadCall.value) {
            hadCall.value = false
            onMinimize()
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (info != null) {
                FullCallPanel(onMinimize = onMinimize)
            } else {
                IdleDialer(onCallNumber = onCallNumber, onClose = onMinimize)
            }
        }
    }
}

@Composable
fun FullCallPanel(
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info by CallMonitor.info.collectAsState()
    val muted by CallMonitor.muted.collectAsState()
    val speaker by CallMonitor.speaker.collectAsState()
    val onHold by CallMonitor.onHold.collectAsState()
    val bluetooth by CallMonitor.bluetooth.collectAsState()
    val current = info
    val call = CallMonitor.currentCall()
    val canSwitchSim = CallMonitor.canSwitchSim(call)
    val context = LocalContext.current
    val activeSimHandle = remember(call) {
        CallMonitor.currentSimHandle ?: runCatching { call?.details?.accountHandle }.getOrNull()
    }
    val currentSim = remember(activeSimHandle) {
        if (activeSimHandle != null) SimManager.getSimByHandle(context, activeSimHandle) else null
    }
    val otherSim = remember(currentSim) {
        if (currentSim != null) SimManager.getOtherSim(context, currentSim.handle) else null
    }
    val incoming = CallMonitor.isIncoming(call)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                IconButton(onClick = onMinimize) {
                    AppIcon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimizar",
                        size = 22.dp
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                if (incoming) "Llamada entrante" else "Llamada en curso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            current?.number ?: "",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            current?.state ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (currentSim != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        Icons.Default.SimCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 16.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "SIM: ${currentSim.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (canSwitchSim && otherSim != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.clickable { CallMonitor.switchSim() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            Icons.Default.SwapCalls,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 16.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Cambiar a ${otherSim.label} antes de que responda",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Aviso de grabación (responsabilidad legal: hay que avisar al interlocutor).
        val recordingState by CallRecorder.state.collectAsState()
        if (recordingState == com.difusion.app.service.RecordingState.RECORDING ||
            recordingState == com.difusion.app.service.RecordingState.PAUSED
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        size = 16.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Llamada siendo grabada — avisa al interlocutor",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (CallRecorder.isEnabled(context)) {
            Spacer(modifier = Modifier.height(8.dp))
            RecordingControls()
        }

        Spacer(modifier = Modifier.weight(1f))

        if (!incoming) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CallActionButton(
                    icon = Icons.Default.Bluetooth,
                    label = "Bluetooth",
                    active = bluetooth,
                    onClick = { CallMonitor.routeToBluetooth() }
                )
                CallActionButton(
                    icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = "Silenciar",
                    active = muted,
                    onClick = { CallMonitor.toggleMute() }
                )
                CallActionButton(
                    icon = Icons.Default.VolumeUp,
                    label = "Altavoz",
                    active = speaker,
                    onClick = { CallMonitor.toggleSpeaker() }
                )
                CallActionButton(
                    icon = Icons.Default.Pause,
                    label = "Espera",
                    active = onHold,
                    onClick = { CallMonitor.toggleHold() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable { CallMonitor.endCall() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        Icons.Default.CallEnd,
                        contentDescription = "Colgar",
                        tint = MaterialTheme.colorScheme.onError,
                        size = 32.dp
                    )
                }
            }
        } else {
            IncomingCallControls()
        }

        Spacer(modifier = Modifier.weight(1f))

        var showDtmf by rememberSaveable { mutableStateOf(false) }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.clickable { showDtmf = !showDtmf }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    Icons.Default.Dialpad,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 18.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (showDtmf) "Ocultar teclado" else "Teclado (tonos DTMF)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showDtmf) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Envía tonos presionando las teclas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CallKeypad(
                        onDigit = { CallMonitor.dtmf(it) },
                        showDelete = false,
                        showLetters = true,
                        rounded = false,
                        rowSpacing = 12.dp,
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdleDialer(
    onCallNumber: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var number by remember { mutableStateOf("") }
    val context = LocalContext.current
    val sims = remember { SimManager.getSims(context) }
    var selectedSim by remember {
        mutableStateOf(SimManager.getDefaultHandle(context))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                IconButton(onClick = onClose) {
                    AppIcon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        size = 22.dp
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Centro de llamadas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                number.ifBlank { "Introduce un número" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (number.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 12.dp)
            )
        }

        // Selector de SIM si hay 2 o más
        if (sims.size >= 2) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sims.forEach { sim ->
                    val selected = selectedSim?.id == sim.handle.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedSim = sim.handle
                                SimManager.setDefaultId(context, sim.handle.id)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                sim.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (sim.number != null) {
                                Text(
                                    sim.number,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        CallKeypad(
            onDigit = { c ->
                if (CallMonitor.currentCall() != null) {
                    CallMonitor.dtmf(c)
                } else if (number.length < 20) {
                    number += c
                }
            },
            onDelete = { number = number.dropLast(1) },
            onDeleteAll = { number = "" }
        )

        Spacer(modifier = Modifier.height(22.dp))

        Surface(
            shape = CircleShape,
            color = if (number.isBlank()) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .clickable(enabled = number.isNotBlank()) {
                        if (sims.isNotEmpty()) {
                            CallMonitor.setSimHandle(selectedSim)
                            SimManager.placeCall(context, number, selectedSim)
                        } else {
                            onCallNumber(number)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    Icons.Default.Call,
                    contentDescription = "Llamar",
                    tint = if (number.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    size = 30.dp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Tecla un número o usa el teclado DTMF durante una llamada.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun IncomingCallControls() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IncomingCallButton(
            icon = Icons.Default.CallEnd,
            label = "Rechazar",
            color = MaterialTheme.colorScheme.error
        ) { CallMonitor.rejectIncomingCall(false) }
        IncomingCallButton(
            icon = Icons.Default.Phone,
            label = "Contestar",
            color = Color(0xFF2ECC71)
        ) { CallMonitor.answerIncomingCall() }
        IncomingCallButton(
            icon = Icons.Default.Phone,
            label = "Buzón",
            color = Color(0xFFF5A623)
        ) { CallMonitor.rejectIncomingCall(true) }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        "El número entrante se puede gestionar aquí o en la pantalla superpuesta.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun IncomingCallButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = color) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    icon,
                    contentDescription = label,
                    tint = Color.White,
                    size = 26.dp
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun CallActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    icon,
                    contentDescription = label,
                    tint = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    size = if (size >= 60.dp) 26.dp else 22.dp
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// Barra compacta de estado de la grabación: indicador + Pausar/Reanudar + Detener.
@Composable
fun RecordingControls() {
    val recState by CallRecorder.state.collectAsState()
    val context = LocalContext.current
    val recording = recState == RecordingState.RECORDING
    val paused = recState == RecordingState.PAUSED
val lastErr = CallRecorder.lastError(context)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pillColor = when {
                recording -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                paused -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val dotColor = when {
                recording -> MaterialTheme.colorScheme.error
                paused -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = pillColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when {
                            recording -> "Grabando"
                            paused -> "Grabación en pausa"
                            else -> "Sin grabación"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (!recording && !paused && lastErr != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    lastErr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
}

        if (recording || paused) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = if (recording) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        IconButton(onClick = {
                            if (recording) CallRecorder.pause() else CallRecorder.resume()
                        }) {
                            AppIcon(
                                if (recording) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (recording) "Pausar grabación" else "Reanudar grabación",
                                tint = if (recording) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                size = 20.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        if (recording) "Pausar" else "Reanudar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        IconButton(onClick = {
                            CallRecorder.stop()
                            Toast_RecordStop(context)
                        }) {
                            AppIcon(
                                Icons.Default.Stop,
                                contentDescription = "Detener grabación y guardar",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                size = 20.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Detener",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun Toast_RecordStop(context: android.content.Context) {
    runCatching {
        android.widget.Toast.makeText(context, "Grabación guardada", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// Para el riel lateral compacto: indicador de grabación que alterna pausa y el botón Detener.
@Composable
fun RecordingRail() {
    val recState by CallRecorder.state.collectAsState()
    if (recState == RecordingState.OFF) return
    val recording = recState == RecordingState.RECORDING
    val context = LocalContext.current
    Surface(
        shape = CircleShape,
        color = if (recording) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        IconButton(onClick = {
            if (recording) CallRecorder.pause() else CallRecorder.resume()
        }) {
            AppIcon(
                if (recording) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (recording) "Pausar grabación" else "Reanudar grabación",
                tint = if (recording) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 20.dp
            )
        }
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        IconButton(onClick = {
            CallRecorder.stop()
            Toast_RecordStop(context)
        }) {
            AppIcon(
                Icons.Default.Stop,
                contentDescription = "Detener grabación y guardar",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                size = 20.dp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit = {},
    onDeleteAll: () -> Unit = {},
    showDelete: Boolean = true,
    showLetters: Boolean = false,
    rounded: Boolean = true,
    rowSpacing: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing)
            ) {
                row.forEach { c ->
                    KKey(
                        text = c.toString(),
                        letters = if (showLetters) lettersFor(c) else null,
                        rounded = rounded,
                        onClick = { onDigit(c) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (showDelete) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                // Botón borrar número: mantenerlo presionado vacía TODO el número.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .combinedClickable(
                            onClick = onDelete,
                            onLongClick = onDeleteAll
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Borrar (mantén para vaciar)",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun lettersFor(c: Char): String? = when (c) {
    '1' -> ".,?!"
    '2' -> "ABC"
    '3' -> "DEF"
    '4' -> "GHI"
    '5' -> "JKL"
    '6' -> "MNO"
    '7' -> "PQRS"
    '8' -> "TUV"
    '9' -> "WXYZ"
    '0' -> "+"
    else -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KKey(
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    text: String? = null,
    letters: String? = null,
    rounded: Boolean = true,
    icon: ImageVector? = null
) {
    val boxModifier = modifier
        .aspectRatio(1f)
        .combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    val content: @Composable () -> Unit = {
        when {
            text != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text,
                    fontSize = if (rounded) 24.sp else 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (letters != null) {
                    Text(
                        letters,
                        fontSize = if (rounded) 10.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
            icon != null -> AppIcon(icon, contentDescription = "Borrar", size = 22.dp)
        }
    }
    if (rounded) {
        Box(
            modifier = boxModifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun CallRail(modifier: Modifier = Modifier) {
    val info by CallMonitor.info.collectAsState()
    val muted by CallMonitor.muted.collectAsState()
    val speaker by CallMonitor.speaker.collectAsState()
    val onHold by CallMonitor.onHold.collectAsState()
    val bluetooth by CallMonitor.bluetooth.collectAsState()
    val context = LocalContext.current

    if (info == null) return

    Column(
        modifier = modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RailIcon(active = bluetooth, icon = Icons.Default.Bluetooth) { CallMonitor.routeToBluetooth() }
        RailIcon(active = muted, icon = Icons.Default.MicOff) { CallMonitor.toggleMute() }
        RailIcon(active = speaker, icon = Icons.Default.VolumeUp) { CallMonitor.toggleSpeaker() }
        RailIcon(active = onHold, icon = Icons.Default.Pause) { CallMonitor.toggleHold() }
        if (CallRecorder.isEnabled(context)) {
            RecordingRail()
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error
        ) {
            IconButton(onClick = { CallMonitor.endCall() }) {
                AppIcon(
                    Icons.Default.CallEnd,
                    contentDescription = "Colgar",
                    tint = MaterialTheme.colorScheme.onError,
                    size = 22.dp
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            IconButton(onClick = { openCallCenter(context) }) {
                AppIcon(
                    Icons.Default.Phone,
                    contentDescription = "Pantalla de llamada",
                    size = 20.dp
                )
            }
        }
    }
}

@Composable
private fun RailIcon(
    active: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        IconButton(onClick = onClick) {
            AppIcon(
                icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 20.dp
            )
        }
    }
}
