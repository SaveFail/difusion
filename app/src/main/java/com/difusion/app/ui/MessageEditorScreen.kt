package com.difusion.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.difusion.app.data.MessageTemplate
import com.difusion.app.data.ScheduledSend
import com.difusion.app.storage.RecipientsPrefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MessageEditorScreen(
    messageBody: String,
    onMessageChange: (String) -> Unit,
    templates: List<MessageTemplate>,
    onSaveTemplate: (String, String, String) -> Unit,
    onDeleteTemplate: (MessageTemplate) -> Unit,
    selectedCount: Int,
    showSmsCounter: Boolean = true,
    onSend: () -> Unit,
    // Correo masivo (opcional). mode: 0=seleccionados, 1=lista, 2=individual.
    onSendEmail: ((subject: String, body: String, mode: Int, emails: List<String>) -> Unit)? = null,
    emailAvailable: Boolean = false,
    // Envío programado: canal 0 = SMS, 1 = correo.
    onSchedule: ((subject: String, body: String, channel: Int, atMillis: Long, mode: Int, emails: List<String>) -> Unit)? = null,
    scheduledSends: List<ScheduledSend> = emptyList(),
    onCancelScheduled: ((ScheduledSend) -> Unit)? = null
) {
    var selectedTemplateId by rememberSaveable { mutableStateOf(-1L) }
    var selectedTemplateBody by rememberSaveable { mutableStateOf("") }
    var templateName by rememberSaveable { mutableStateOf("") }

    // Canal: 0 = SMS, 1 = Correo.
    var channel by rememberSaveable { mutableStateOf(0) }
    var subject by rememberSaveable { mutableStateOf("") }
    // Correos precargados (se guardan para el próximo envío).
    val localContext = LocalContext.current
    var emailList by rememberSaveable { mutableStateOf(RecipientsPrefs.get(localContext)) }
    // Destinatarios del correo: 0 = contactos seleccionados, 1 = lista, 2 = individual.
    var emailMode by rememberSaveable { mutableStateOf(0) }
    var singleEmail by rememberSaveable { mutableStateOf("") }

    // Programación.
    var scheduleEnabled by rememberSaveable { mutableStateOf(false) }
    val now = Calendar.getInstance()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = now.timeInMillis)
    val timePickerState = rememberTimePickerState(
        initialHour = now.get(Calendar.HOUR_OF_DAY),
        initialMinute = now.get(Calendar.MINUTE),
        is24Hour = true
    )
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val dateText = datePickerState.selectedDateMillis?.let { dateFmt.format(Date(it)) } ?: "Elegir fecha"
    val timeText = String.format(Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute)

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        ScreenHeader(
            title = "Nuevo mensaje",
            subtitle = "Personaliza con {nombre} por cada cliente",
            icon = Icons.Default.ChatBubbleOutline
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Selector de canal.
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = channel == 0,
                    onClick = { channel = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = if (emailAvailable) 2 else 1),
                    label = { Text("SMS") }
                )
                if (emailAvailable) {
                    SegmentedButton(
                        selected = channel == 1,
                        onClick = { channel = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("Correo") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (channel == 1) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Asunto del correo") },
                    placeholder = { Text("Ej: Aviso de pago") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Destinatarios",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = emailMode == 0,
                        onClick = { emailMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        label = { Text("Seleccionados") }
                    )
                    SegmentedButton(
                        selected = emailMode == 1,
                        onClick = { emailMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        label = { Text("Lista") }
                    )
                    SegmentedButton(
                        selected = emailMode == 2,
                        onClick = { emailMode = 2 },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        label = { Text("Individual") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                when (emailMode) {
                    0 -> Text(
                        "Un correo INDIVIDUAL a cada uno de los $selectedCount contactos " +
                            "seleccionados que tengan correo (sin CC ni CCO).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    1 -> {
                        OutlinedTextField(
                            value = emailList,
                            onValueChange = {
                                emailList = it
                                RecipientsPrefs.set(localContext, it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp),
                            label = { Text("Correos precargados (coma o línea)") },
                            placeholder = { Text("cliente1@correo.com, cliente2@correo.com") },
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${RecipientsPrefs.parse(emailList).size} correos válidos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> OutlinedTextField(
                        value = singleEmail,
                        onValueChange = { singleEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Correo individual") },
                        placeholder = { Text("persona@correo.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (templates.isNotEmpty()) {
                Text(
                    "Plantillas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates) { template ->
                        InputChip(
                            selected = template.id == selectedTemplateId,
                            onClick = {
                                selectedTemplateId = template.id
                                selectedTemplateBody = template.body
                                templateName = template.name
                                onMessageChange(template.body)
                                if (template.subject.isNotBlank()) subject = template.subject
                            },
                            label = { Text(template.name) },
                            trailingIcon = {
                                AppIcon(
                                    Icons.Default.BookmarkAdd,
                                    contentDescription = null,
                                    size = 16.dp
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (selectedTemplateId >= 0) {
                        "Plantilla \"$templateName\" cargada. Puedes enviarla directamente; " +
                            "solo guarda si cambias el texto."
                    } else {
                        "Toca una plantilla para cargarla. No necesitas volver a guardarla para enviar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                if (channel == 1) {
                    "Usa {nombre} para personalizar con el nombre de cada cliente."
                } else {
                    "Usa {nombre} para personalizar con el nombre de cada cliente (y {telefono} para el número)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(6.dp))

            val smsCount = calculateSmsCount(messageBody)

            OutlinedTextField(
                value = messageBody,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp),
                label = { Text("Mensaje") },
                placeholder = { Text("Hola {nombre}, le informamos...") },
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${messageBody.length} caracteres",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showSmsCounter && channel == 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("$smsCount SMS") }
                    )
                }
            }

            if (channel == 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Sugerencia: incluye una forma de no recibir más mensajes (opt-out).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            if (!messageBody.contains("STOP", ignoreCase = true)) {
                                onMessageChange(
                                    messageBody.trimEnd() +
                                        "\nResponde STOP para no recibir más mensajes."
                                )
                            }
                        },
                        label = { Text("+ Opt-out") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = templateName,
                onValueChange = { templateName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            scope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    },
                label = { Text("Nombre de la plantilla") },
                placeholder = { Text("Ej: Promoción noviembre") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val editingExisting = selectedTemplateId >= 0
            val bodyChanged = editingExisting && messageBody != selectedTemplateBody
            val canSave = messageBody.isNotBlank() && templateName.isNotBlank() &&
                (!editingExisting || bodyChanged)

            SecondaryActionButton(
                text = when {
                    editingExisting && !bodyChanged -> "Plantilla \"$templateName\" ya guardada"
                    editingExisting -> "Guardar cambios en \"$templateName\""
                    else -> "Guardar como plantilla \"$templateName\""
                },
                onClick = {
                    if (templateName.isNotBlank()) {
                        onSaveTemplate(templateName, messageBody, subject)
                        selectedTemplateBody = messageBody
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.BookmarkAdd,
                enabled = canSave
            )
            if (channel == 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "La plantilla guarda también el Asunto, para reutilizarla en correos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Programar envío ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = scheduleEnabled,
                    onCheckedChange = { scheduleEnabled = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Programar envío para una fecha y hora")
            }

            if (scheduleEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(dateText)
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(timeText) }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Se enviarán ${selectedCount} destinatarios por " +
                        (if (channel == 1) "correo" else "SMS") +
                        " a la fecha y hora indicadas, aunque cierres la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (channel == 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Enviar con:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SimPickerButton()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val emailReady = when (emailMode) {
                0 -> selectedCount > 0
                1 -> RecipientsPrefs.parse(emailList).isNotEmpty()
                else -> singleEmail.contains("@")
            }
            val canAct = messageBody.isNotBlank() &&
                (channel == 0 || (subject.isNotBlank() && emailReady))
            val label = when {
                scheduleEnabled -> "Programar envío (${selectedCount})"
                channel == 1 -> "Enviar correo a $selectedCount"
                selectedCount > 0 -> "Enviar a $selectedCount destinatarios"
                else -> "Enviar SMS"
            }

            PrimaryActionButton(
                text = label,
                onClick = {
                    if (scheduleEnabled) {
                        val date = datePickerState.selectedDateMillis ?: return@PrimaryActionButton
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = date
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onSchedule?.invoke(
                            subject, messageBody, channel, cal.timeInMillis, emailMode,
                            if (emailMode == 1) RecipientsPrefs.parse(emailList)
                            else if (emailMode == 2) listOf(singleEmail.trim()).filter { it.isNotBlank() }
                            else emptyList()
                        )
                    } else if (channel == 1) {
                        onSendEmail?.invoke(
                            subject, messageBody, emailMode,
                            if (emailMode == 1) RecipientsPrefs.parse(emailList)
                            else if (emailMode == 2) listOf(singleEmail.trim()).filter { it.isNotBlank() }
                            else emptyList()
                        )
                    } else {
                        onSend()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = if (channel == 1) Icons.Default.Email else Icons.Default.Send,
                enabled = canAct
            )

            // Envíos ya programados (pendientes).
            val pending = scheduledSends.filter { it.status == 0 }
            if (pending.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "Envíos programados",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                pending.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    (if (item.channel == 1) "Correo — " else "SMS — ") +
                                        sdf.format(Date(item.scheduledAt)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    item.message.take(60),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            TextButton(onClick = { onCancelScheduled?.invoke(item) }) {
                                Text("Cancelar")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

private fun calculateSmsCount(text: String): Int {
    if (text.isEmpty()) return 0
    return if (text.length <= 160) 1 else (text.length / 160) + 1
}
