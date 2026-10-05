package com.difusion.app.ui

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

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(gestion, estado, medio, fechaGestion) }) {
                        Text("Guardar")
                    }
                }
            }
        }
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
