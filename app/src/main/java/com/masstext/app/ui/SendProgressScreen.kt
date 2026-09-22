package com.masstext.app.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masstext.app.service.SmsSender
import kotlinx.coroutines.delay

@Composable
fun SendProgressScreen(
    isSending: Boolean,
    sender: SmsSender,
    total: Int,
    onContinue: () -> Unit,
    onStop: () -> Unit,
    onDone: () -> Unit
) {
    val sent by sender.sentCount.collectAsState()
    val failed by sender.failedCount.collectAsState()
    val windowTarget by sender.windowTarget.collectAsState()
    val windowSent by sender.windowSent.collectAsState()
    val nextWindowAtMs by sender.nextWindowAtMs.collectAsState()

    // Ticker de 1 s para que el conteo de los 5 minutos se actualice en vivo.
    var nowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(isSending) {
        while (isSending) {
            nowMs = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val windowRemaining = (nextWindowAtMs - nowMs).coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ScreenHeader(
            title = "Enviando mensajes",
            subtitle = "${sent + failed} / $total",
            icon = Icons.Default.Send
        )
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    if (isSending) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else if (failed > 0) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    progress = { if (total == 0) 0f else (sent + failed).toFloat() / total },
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                AppIcon(
                    if (failed > 0) Icons.Default.Cancel else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    size = 48.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            if (isSending) "Progreso" else "Completado",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "$sent / $total enviados",
            style = MaterialTheme.typography.titleMedium
        )
        if (failed > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$failed fallidos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (isSending && windowTarget > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Bloque actual: $windowSent / $windowTarget mensajes",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (nextWindowAtMs > 0L) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Próxima ventana en ${formatRemaining(windowRemaining)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isSending) {
            PrimaryActionButton(
                text = "Continuar en segundo plano",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Send
            )
            Spacer(modifier = Modifier.height(10.dp))
            SecondaryActionButton(
                text = "Detener envío",
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Cancel,
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        } else {
            PrimaryActionButton(
                text = "Aceptar",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.CheckCircle
            )
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}