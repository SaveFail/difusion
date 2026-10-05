package com.difusion.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Control reutilizable para programar un envío (fecha + hora).
 * Llama a [onAtMillis] con la marca de tiempo elegida, o null si está apagado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleControls(
    onAtMillis: (Long?) -> Unit,
    label: String = "Programar envío para una fecha y hora"
) {
    var enabled by remember { mutableStateOf(false) }
    val now = remember { Calendar.getInstance() }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = now.timeInMillis)
    val timeState = rememberTimePickerState(
        initialHour = now.get(Calendar.HOUR_OF_DAY),
        initialMinute = now.get(Calendar.MINUTE),
        is24Hour = true
    )
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val dateText = dateState.selectedDateMillis?.let { dateFmt.format(Date(it)) } ?: "Elegir fecha"
    val timeText = String.format(Locale.getDefault(), "%02d:%02d", timeState.hour, timeState.minute)

    LaunchedEffect(enabled, dateState.selectedDateMillis, timeState.hour, timeState.minute) {
        if (!enabled) {
            onAtMillis(null)
        } else {
            val cal = Calendar.getInstance().apply {
                timeInMillis = dateState.selectedDateMillis ?: System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, timeState.hour)
                set(Calendar.MINUTE, timeState.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onAtMillis(cal.timeInMillis)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = enabled, onCheckedChange = { enabled = it })
            Spacer(Modifier.width(8.dp))
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        if (enabled) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                    Text(dateText)
                }
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                    Text(timeText)
                }
            }
        }
    }

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { showDate = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = dateState)
        }
    }
    if (showTime) {
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { showTime = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancelar") } },
            text = { TimePicker(state = timeState) }
        )
    }
}
