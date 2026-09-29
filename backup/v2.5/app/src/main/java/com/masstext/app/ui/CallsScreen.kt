package com.masstext.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masstext.app.data.Contact
import com.masstext.app.service.CallSequencer

@Composable
fun CallsScreen(
    sequencer: CallSequencer,
    hasCallPermission: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    val currentContact by sequencer.currentContact.collectAsState()
    val isRunning by sequencer.isRunning.collectAsState()
    val isPaused by sequencer.isPaused.collectAsState()
    val status by sequencer.status.collectAsState()
    val progress by sequencer.progress.collectAsState()
    val total = sequencer.contactsCount.collectAsState().value
    val contacts by sequencer.contacts.collectAsState()
    val currentIdx by sequencer.currentIdx.collectAsState()
    val callState by sequencer.callState.collectAsState()
    val repeatTimes by sequencer.repeatTimes.collectAsState()
    val repeatMode by sequencer.repeatMode.collectAsState()
    val round by sequencer.round.collectAsState()
    val context = LocalContext.current

    if (!hasCallPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptyState(
                icon = Icons.Default.Phone,
                title = "Se necesita permiso para llamar",
                description = "Concede el permiso para poder realizar las llamadas secuenciales.",
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryActionButton(
                text = "Conceder permiso",
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Phone
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 10.dp, top = 60.dp)
                .size(40.dp)
                .clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 22.dp
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            ScreenHeader(
                title = "Llamadas secuenciales",
                subtitle = if (repeatTimes > 1) {
                    "$progress / $total completadas · Ronda $round de $repeatTimes"
                } else {
                    "$progress / $total completadas"
                },
                icon = Icons.Default.PhoneIphone
            )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentContact != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Llamando a:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            currentContact!!.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            currentContact!!.phone,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                EmptyState(
                    icon = Icons.Default.Phone,
                    title = "Preparando llamadas...",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { if (total == 0) 0f else progress.toFloat() / total },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (total > 0 && progress > 0) {
                    "$status  �  ${((progress * 100) / total).coerceIn(0, 100)}%"
                } else status,
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    callState == "En llamada" -> MaterialTheme.colorScheme.tertiary
                    isPaused -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.Medium
            )

            if (repeatTimes > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (repeatMode == com.masstext.app.service.RepeatMode.QUEUE) {
                        "Por cola · reinicia la lista completa"
                    } else {
                        "Por usuario · repite cada cliente $repeatTimes veces"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (callState == "En llamada") {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = if (callState == "En llamada") MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 16.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            callState,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (callState == "En llamada") MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isPaused) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Cola en pausa. La llamada actual terminará y la secuencia quedará en espera hasta que toques Reanudar.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Números de la secuencia",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "$currentIdx llamados",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(contacts.size) { i ->
                SequenceNumberRow(
                    contact = contacts[i],
                    index = i,
                    state = when {
                        i < currentIdx -> NumberState.CALLED
                        else -> NumberState.PENDING
                    },
                    onRedial = {
                        val ok = sequencer.callSpecific(contacts[i])
                        if (!ok) {
                            Toast.makeText(
                                context,
                                "Ya hay una llamada en curso. Espera a que termine.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (isRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (isPaused) sequencer.resume() else sequencer.pause()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    AppIcon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isPaused) "REANUDAR" else "PAUSAR",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Button(
                    onClick = { sequencer.stop() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    AppIcon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("DETENER", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            PrimaryActionButton(
                text = "Volver",
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                icon = Icons.Default.PhoneIphone
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        }

        CallRail(modifier = Modifier.align(Alignment.CenterEnd))
    }
}

private enum class NumberState { CALLED, PENDING }

@Composable
private fun SequenceNumberRow(
    contact: Contact,
    index: Int,
    state: NumberState,
    onRedial: () -> Unit
) {
    val called = state == NumberState.CALLED
    val container = if (called) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (called) container else MaterialTheme.colorScheme.surface),
        elevation = if (called) CardDefaults.cardElevation(defaultElevation = 1.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (called) Modifier.clickable(onClick = onRedial) else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (called) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (called) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    contact.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (called) {
                Text(
                    "Llamado · Toca para re-llamar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
                AppIcon(
                    Icons.Default.Repeat,
                    contentDescription = "Re-llamar",
                    tint = MaterialTheme.colorScheme.primary,
                    size = 18.dp
                )
            } else {
                Text(
                    "Pendiente",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}