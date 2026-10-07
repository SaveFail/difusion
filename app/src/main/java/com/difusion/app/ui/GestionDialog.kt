package com.difusion.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.difusion.app.data.Contact
import com.difusion.app.data.GestionPresets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Diálogo de gestión con desplegables por columna del formato estándar:
 * SEGUIMIENTO (gestion), STATUS (estado), MEDIO DE CONTACTO (medio) y
 * FECHA DE GESTION. Cada campo es un desplegable editable: se puede elegir
 * una opción o escribir un valor nuevo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestionDialog(
    contact: Contact?,
    onDismiss: () -> Unit,
    onSave: (gestion: String, estado: String, medio: String, fechaGestion: String) -> Unit
) {
    if (contact == null) return

    val context = LocalContext.current
    val gestionList = remember { GestionPresets.getGestion(context) }
    val estadoList = remember { GestionPresets.getEstado(context) }
    val medioList = remember { GestionPresets.getMedio(context) }

    var gestion by remember { mutableStateOf(contact.gestion) }
    var estado by remember { mutableStateOf(contact.estado) }
    var medio by remember { mutableStateOf(contact.medio) }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    var fechaGestion by remember {
        mutableStateOf(contact.fechaGestion.ifBlank { dateFmt.format(Date()) })
    }

    var gestionExpanded by remember { mutableStateOf(false) }
    var estadoExpanded by remember { mutableStateOf(false) }
    var medioExpanded by remember { mutableStateOf(false) }

    // Selector de fecha para poner la actual o una futura.
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val nowCal = remember { Calendar.getInstance() }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = nowCal.timeInMillis)
    val timeState = rememberTimePickerState(
        initialHour = nowCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = nowCal.get(Calendar.MINUTE),
        is24Hour = true
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    "Gestión del cliente",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                if (contact.phone.isNotBlank()) {
                    Text(
                        contact.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                GestiónDropdown(
                    label = "Seguimiento (Tipificación)",
                    value = gestion,
                    options = gestionList,
                    expanded = gestionExpanded,
                    onExpandedChange = { gestionExpanded = it },
                    onValueChange = { gestion = it }
                )
                Spacer(modifier = Modifier.height(10.dp))

                GestiónDropdown(
                    label = "Status",
                    value = estado,
                    options = estadoList,
                    expanded = estadoExpanded,
                    onExpandedChange = { estadoExpanded = it },
                    onValueChange = { estado = it }
                )
                Spacer(modifier = Modifier.height(10.dp))

                GestiónDropdown(
                    label = "Medio de contacto",
                    value = medio,
                    options = medioList,
                    expanded = medioExpanded,
                    onExpandedChange = { medioExpanded = it },
                    onValueChange = { medio = it }
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = fechaGestion,
                    onValueChange = { fechaGestion = it },
                    label = { Text("Fecha de gestión") },
                    placeholder = { Text("dd/MM/yyyy HH:mm") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Atajos rápidos de fecha.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { fechaGestion = dateFmt.format(Date()) },
                        label = { Text("Ahora") }
                    )
                    AssistChip(
                        onClick = { fechaGestion = stampPlusDays(1) },
                        label = { Text("Mañana") }
                    )
                    AssistChip(
                        onClick = { fechaGestion = stampPlusDays(3) },
                        label = { Text("3 días") }
                    )
                    AssistChip(
                        onClick = { fechaGestion = stampPlusDays(7) },
                        label = { Text("1 semana") }
                    )
                    AssistChip(
                        onClick = { fechaGestion = stampDiaPago() },
                        label = { Text("Día de pago") }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { showDate = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Elegir fecha y hora…") }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        // Recuerda los valores usados para las próximas gestiones.
                        runCatching {
                            GestionPresets.addGestion(context, listOf(gestion))
                            GestionPresets.addEstado(context, listOf(estado))
                            GestionPresets.addMedio(context, listOf(medio))
                        }
                        onSave(gestion, estado, medio, fechaGestion)
                    }) {
                        Text("Guardar")
                    }
                }
            }
        }
    }

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    showTime = true
                }) { Text("Siguiente") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTime) {
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    showTime = false
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = dateState.selectedDateMillis ?: System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    fechaGestion = dateFmt.format(Date(cal.timeInMillis))
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("Cancelar") }
            },
            text = { TimePicker(state = timeState) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestiónDropdown(
    label: String,
    value: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Sin opciones") },
                    onClick = { onExpandedChange(false) }
                )
            } else {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}

private fun stampPlusDays(days: Int): String {
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(cal.timeInMillis))
}

// "Dia de pago": si hoy es <= 15 -> dia 15; si es >= 16 -> ultimo dia del mes.
private fun stampDiaPago(): String {
    val cal = Calendar.getInstance()
    if (cal.get(Calendar.DAY_OF_MONTH) <= 15) {
        cal.set(Calendar.DAY_OF_MONTH, 15)
    } else {
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    }
    cal.set(Calendar.HOUR_OF_DAY, 9)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(cal.timeInMillis))
}
