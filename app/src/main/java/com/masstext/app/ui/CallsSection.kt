package com.masstext.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masstext.app.data.CallLabels
import com.masstext.app.data.CallRecord
import com.masstext.app.service.CallMonitor
import com.masstext.app.service.SimManager
import android.telecom.PhoneAccountHandle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallsSection(
    callRecords: List<CallRecord>,
    onCallNumber: (String) -> Unit,
    onRedial: (CallRecord) -> Unit,
    onSetLabel: (Long, String) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            title = "Llamadas",
            subtitle = "Marcar un número y ver el registro de llamadas",
            icon = Icons.Default.Phone
        )

        TabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Marcar") })
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Registro (${callRecords.size})") }
            )
        }

        when (tab) {
            0 -> DialerPane(onCallNumber = onCallNumber)
            else -> CallsLogPane(
                callRecords = callRecords,
                onRedial = onRedial,
                onSetLabel = onSetLabel
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialerPane(onCallNumber: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    val inCall = CallMonitor.currentCall() != null
    var showKeypad by remember { mutableStateOf(false) }
    val actionSize = 52.dp
    val context = LocalContext.current
    val sims = remember { SimManager.getSims(context) }
    var selectedSim by remember {
        mutableStateOf(SimManager.getDefaultHandle(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    number.ifBlank { if (inCall) "Llamada en curso" else "Escribe el número" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (number.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .size(actionSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .combinedClickable(
                            onClick = { number = number.dropLast(1) },
                            onLongClick = {
                                if (number.isNotBlank()) {
                                    number = ""
                                    Toast.makeText(
                                        context,
                                        "Número vaciado por completo",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Borrar (mantén para vaciar todo)",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        size = 22.dp
                    )
                }
            }
        }

        // Selector de SIM (solo si hay 2 o más)
        if (sims.size >= 2 && !inCall) {
            Spacer(modifier = Modifier.height(6.dp))
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

        Spacer(modifier = Modifier.height(6.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (inCall && !showKeypad) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        IconButton(onClick = { showKeypad = true }) {
                            AppIcon(
                                Icons.Default.Dialpad,
                                contentDescription = "Activar teclado",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                size = 24.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Teclado (tonos DTMF)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val muted by CallMonitor.muted.collectAsState()
                        val speaker by CallMonitor.speaker.collectAsState()
                        val onHold by CallMonitor.onHold.collectAsState()
                        val bluetooth by CallMonitor.bluetooth.collectAsState()
                        RailIcon(active = muted, icon = Icons.Default.MicOff) {
                            CallMonitor.toggleMute()
                        }
                        RailIcon(active = speaker, icon = Icons.Default.VolumeUp) {
                            CallMonitor.toggleSpeaker()
                        }
                        RailIcon(active = onHold, icon = Icons.Default.Pause) {
                            CallMonitor.toggleHold()
                        }
                        RailIcon(active = bluetooth, icon = Icons.Default.Bluetooth) {
                            CallMonitor.routeToBluetooth()
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
                    }
                }
            } else {
                val spacing = 8.dp
                val keySize = minOf(
                    (maxWidth - spacing * 2) / 3,
                    (maxHeight - spacing * 3) / 4
                ).coerceIn(50.dp, 72.dp)
                CallKeypad(
                    onDigit = { c ->
                        if (inCall) {
                            CallMonitor.dtmf(c)
                        } else if (number.length < 20) {
                            number += c
                        }
                    },
                    showDelete = false,
                    showLetters = true,
                    rounded = false,
                    rowSpacing = spacing,
                    modifier = Modifier.width(keySize * 3 + spacing * 2)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .size(actionSize)
                .clip(CircleShape)
                .background(
                    if (number.isBlank() && !inCall) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
                .clickable(enabled = number.isNotBlank() || inCall) {
                    if (inCall) {
                        // En llamada: el botón es solo acceso visual; DTMF va por el teclado.
                    } else if (number.isNotBlank() && sims.isNotEmpty()) {
                        CallMonitor.setSimHandle(selectedSim)
                        SimManager.placeCall(context, number, selectedSim)
                    } else if (number.isNotBlank()) {
                        onCallNumber(number)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                Icons.Default.Call,
                contentDescription = "Llamar",
                tint = if (number.isBlank() && !inCall) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                size = 26.dp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (inCall) "Llamada activa: el teclado se activa al pulsar Teclado."
                else "Tecla el número y pulsa el botón verde.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
        CallRail(modifier = Modifier.align(Alignment.CenterEnd))
    }
}

private enum class CallsFilter { GENERAL, ANSWERED, NOT_ANSWERED }

@Composable
private fun CallsLogPane(
    callRecords: List<CallRecord>,
    onRedial: (CallRecord) -> Unit,
    onSetLabel: (Long, String) -> Unit
) {
    var filter by rememberSaveable { mutableStateOf(CallsFilter.GENERAL) }
    if (callRecords.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Phone,
            title = "Sin llamadas todavía",
            description = "Las llamadas que hagas desde 'Marcar' o desde una secuencia masiva aparecerán aquí.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    val ordered = remember(callRecords) { callRecords.sortedByDescending { it.date } }
    val answeredCount = ordered.count { it.success }
    val failedCount = ordered.size - answeredCount
    val visible = when (filter) {
        CallsFilter.GENERAL -> ordered
        CallsFilter.ANSWERED -> ordered.filter { it.success }
        CallsFilter.NOT_ANSWERED -> ordered.filter { !it.success }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CallsSummary(
                    text = "General (${ordered.size})",
                    icon = Icons.Default.List,
                    color = MaterialTheme.colorScheme.primary,
                    selected = filter == CallsFilter.GENERAL,
                    modifier = Modifier.weight(1f),
                    onClick = { filter = CallsFilter.GENERAL }
                )
                CallsSummary(
                    text = "Contestadas ($answeredCount)",
                    icon = Icons.Default.Call,
                    color = MaterialTheme.colorScheme.tertiary,
                    selected = filter == CallsFilter.ANSWERED,
                    modifier = Modifier.weight(1f),
                    onClick = { filter = CallsFilter.ANSWERED }
                )
                CallsSummary(
                    text = "No contestadas ($failedCount)",
                    icon = Icons.Default.CallMissed,
                    color = MaterialTheme.colorScheme.error,
                    selected = filter == CallsFilter.NOT_ANSWERED,
                    modifier = Modifier.weight(1f),
                    onClick = { filter = CallsFilter.NOT_ANSWERED }
                )
            }
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    "No hay llamadas en esta vista",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
        items(visible, key = { it.id }) { call ->
            CallLogRow(
                call = call,
                onRedial = { onRedial(call) },
                onSetLabel = { label -> onSetLabel(call.id, label) }
            )
        }
    }
}

@Composable
private fun CallsSummary(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = if (selected) 0.22f else 0.08f),
        border = if (selected) BorderStroke(2.dp, color) else null,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(icon, contentDescription = null, tint = color, size = 15.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CallLogRow(
    call: CallRecord,
    onRedial: () -> Unit,
    onSetLabel: (String) -> Unit
) {
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(call.date))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (call.success) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        if (call.success) Icons.Default.Call else Icons.Default.CallMissed,
                        contentDescription = null,
                        tint = if (call.success) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        call.contactName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        call.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            date,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (call.success) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (call.success) "Contestada" else "Se cortó",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (call.success) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    if (call.user.isNotBlank()) {
                        Text(
                            "Usuario: ${call.user}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onRedial) {
                    AppIcon(Icons.Default.Call, contentDescription = null, size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Llamar")
                }
            }
            if (call.success) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Clasificar:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CFilterChip(
                        text = CallLabels.PROMISE,
                        selected = call.label == CallLabels.PROMISE,
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = { onSetLabel(if (call.label == CallLabels.PROMISE) CallLabels.NONE else CallLabels.PROMISE) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CFilterChip(
                        text = CallLabels.RELUCTANT,
                        selected = call.label == CallLabels.RELUCTANT,
                        color = MaterialTheme.colorScheme.error,
                        onClick = { onSetLabel(if (call.label == CallLabels.RELUCTANT) CallLabels.NONE else CallLabels.RELUCTANT) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CFilterChip(
    text: String,
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun RailButtonActive(
    active: Boolean,
    content: Pair<ImageVector, String>
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                content.first,
                contentDescription = content.second,
                tint = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
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

