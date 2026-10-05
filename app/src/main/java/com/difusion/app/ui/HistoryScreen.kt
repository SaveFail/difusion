package com.difusion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.difusion.app.data.CallLabels
import com.difusion.app.data.CallRecord
import com.difusion.app.data.FailureCount
import com.difusion.app.data.ScheduledSend
import com.difusion.app.data.SendRecord
import com.difusion.app.storage.RecordStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(
    records: List<SendRecord>,
    callRecords: List<CallRecord>,
    failureCounts: List<FailureCount> = emptyList(),
    onExportExcel: () -> Unit,
    onExportBackup: () -> Unit,
    onSetLabel: (Long, String) -> Unit,
    folderConfigured: Boolean,
    onConfigureFolder: () -> Unit,
    scheduledSends: List<ScheduledSend> = emptyList(),
    onCancelScheduled: (ScheduledSend) -> Unit = {},
    onDeleteScheduled: (ScheduledSend) -> Unit = {},
    onClearSentScheduled: () -> Unit = {}
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val successCalls = callRecords.filter { it.success }
    val failedCalls = callRecords.filter { !it.success }
    val totalSent = records.sumOf { it.sent }
    val totalFailed = records.sumOf { it.failed }
    val scope = rememberCoroutineScope()
    var recordingCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        scope.launch {
            recordingCount = withContext(Dispatchers.IO) { queryRecordingCount(context) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            title = "Historial",
            subtitle = "${records.size} envíos · ${callRecords.size} llamadas · $recordingCount grabaciones",
            icon = Icons.Default.History
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryChip(icon = Icons.Default.Sms, text = "$totalSent enviados", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            SummaryChip(icon = Icons.Default.ErrorOutline, text = "$totalFailed fallidos", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            SummaryChip(icon = Icons.Default.Call, text = "${successCalls.size} exitosas", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
            SummaryChip(icon = Icons.Default.CallMissed, text = "${failedCalls.size} sin contacto", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }

        if (failureCounts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                failureCounts.forEach { fc ->
                    SummaryChip(
                        icon = Icons.Default.ErrorOutline,
                        text = "${fc.n}× ${fc.label}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        BackupCard(
            configured = folderConfigured,
            folderName = if (folderConfigured) RecordStore.folderName(context) else "",
            onExportBackup = onExportBackup,
            onConfigureFolder = onConfigureFolder
        )

        TabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Mensajes") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Llamadas") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Programados") })
        }

        when (tab) {
            0 -> MessagesTab(records, onExportExcel)
            1 -> CallsTab(successCalls, failedCalls, onSetLabel)
            else -> ScheduledTab(
                items = scheduledSends,
                onCancel = onCancelScheduled,
                onDelete = onDeleteScheduled,
                onClearSent = onClearSentScheduled
            )
        }
    }
}

@Composable
private fun SummaryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(icon, contentDescription = null, tint = color, size = 15.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BackupCard(
    configured: Boolean,
    folderName: String,
    onExportBackup: () -> Unit,
    onConfigureFolder: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (configured) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    if (configured) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (configured) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Respaldo en carpeta",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (configured) "Activo en: $folderName"
                    else "Sin carpeta configurada. Elige una carpeta en el teléfono para que los registros no se borren al desinstalar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Spacer(Modifier.width(8.dp))
            if (configured) {
                TextButton(onClick = onExportBackup) { Text("Exportar CSV") }
            } else {
                Button(onClick = onConfigureFolder) { Text("Configurar") }
            }
        }
    }
}

@Composable
private fun MessagesTab(records: List<SendRecord>, onExportExcel: () -> Unit) {
    if (records.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Sms,
            title = "Sin envíos todavía",
            description = "Cuando hagas un envío masivo verás aquí el resumen.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onExportExcel) {
                    AppIcon(Icons.Default.Download, contentDescription = null, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Reporte Excel")
                }
            }
        }
        item { SectionTitle("Envíos masivos", count = records.size) }
        items(records, key = { it.id }) { record ->
            HistoryCard(record)
        }
    }
}

@Composable
private fun CallsTab(
    successCalls: List<CallRecord>,
    failedCalls: List<CallRecord>,
    onSetLabel: (Long, String) -> Unit
) {
    if (successCalls.isEmpty() && failedCalls.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Call,
            title = "Sin llamadas todavía",
            description = "Cuando hagas la secuencia de llamadas verás aquí las exitosas y las que no contactaron.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            val promiseCount = successCalls.count { it.label == CallLabels.PROMISE }
            val reluctantCount = successCalls.count { it.label == CallLabels.RELUCTANT }
            SectionCard {
                Text(
                    "Resumen de llamadas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryStat("Contestaron", successCalls.size, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    SummaryStat("Sin contacto", failedCalls.size, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryStat("Prometieron", promiseCount, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    SummaryStat("Renuentes", reluctantCount, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                }
            }
        }
        item { SectionTitle("Conectadas exitosas", count = successCalls.size) }
        if (successCalls.isEmpty()) {
            item { NoItemsRow("Ninguna todavía") }
        }
        items(successCalls, key = { it.id }) { call ->
            CallHistoryCard(call = call, onSetLabel = { label -> onSetLabel(call.id, label) })
        }
        item { SectionTitle("No lograron contactar", count = failedCalls.size) }
        if (failedCalls.isEmpty()) {
            item { NoItemsRow("Ninguna todavía") }
        }
        items(failedCalls, key = { it.id }) { call ->
            CallHistoryCard(call = call, onSetLabel = { label -> onSetLabel(call.id, label) })
        }
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun NoItemsRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
    )
}

@Composable
private fun CallHistoryCard(
    call: CallRecord,
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
                        .size(38.dp)
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
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        call.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (call.success) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            if (call.success) "Exitosa" else "Sin contacto",
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
                    LabelChip(
                        text = CallLabels.PROMISE,
                        selected = call.label == CallLabels.PROMISE,
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = { onSetLabel(if (call.label == CallLabels.PROMISE) CallLabels.NONE else CallLabels.PROMISE) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    LabelChip(
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
private fun LabelChip(
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
private fun HistoryCard(record: SendRecord) {
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(record.date))
    val hasFailures = record.failed > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasFailures) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    if (hasFailures) Icons.Default.ErrorOutline else Icons.Default.TaskAlt,
                    contentDescription = null,
                    tint = if (hasFailures) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${record.sent} enviados · ${record.failed} fallidos · ${record.total} destinatarios",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (record.user.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Usuario: ${record.user}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (record.message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        record.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
@Composable
private fun ScheduledTab(
    items: List<ScheduledSend>,
    onCancel: (ScheduledSend) -> Unit,
    onDelete: (ScheduledSend) -> Unit,
    onClearSent: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val sentCount = items.count { it.status == 1 }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${items.count { it.status == 0 }} pendientes · $sentCount enviados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (sentCount > 0) {
                TextButton(onClick = onClearSent) { Text("Limpiar enviados") }
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No hay envíos programados.\nPrográmalos desde Mensaje ▸ Nuevo mensaje.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                val (label, color) = when (item.status) {
                    0 -> "Pendiente" to MaterialTheme.colorScheme.primary
                    1 -> "Enviado" to MaterialTheme.colorScheme.tertiary
                    2 -> "Fallido" to MaterialTheme.colorScheme.error
                    else -> "Cancelado" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                val recipients = remember(item.recipientsJson, item.phonesJson) {
                    runCatching {
                        val arr = org.json.JSONArray(item.recipientsJson)
                        if (arr.length() > 0) {
                            (0 until arr.length()).map { i ->
                                val o = arr.getJSONObject(i)
                                o.optString("name").ifBlank {
                                    o.optString("email").ifBlank { o.optString("phone") }
                                }
                            }
                        } else {
                            val p = org.json.JSONArray(item.phonesJson)
                            (0 until p.length()).map { i -> p.optString(i) }
                        }
                    }.getOrDefault(emptyList())
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = color,
                                size = 18.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                (if (item.channel == 1) "Correo" else "SMS") + " · " +
                                    sdf.format(Date(item.scheduledAt)),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (item.subject.isNotBlank()) {
                            Text(
                                "Asunto: ${item.subject}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            item.message.take(140),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Destinatarios (${recipients.size}): " +
                                recipients.take(8).joinToString(", ") +
                                if (recipients.size > 8) " y ${recipients.size - 8} más…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.result.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Resultado: ${item.result}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            if (item.status == 0) {
                                TextButton(onClick = { onCancel(item) }) { Text("Cancelar") }
                            } else {
                                TextButton(onClick = { onDelete(item) }) {
                                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
