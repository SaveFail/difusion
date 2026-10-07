package com.difusion.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.difusion.app.data.GestionPresets

/**
 * Gestión PERSONALIZABLE de las listas de gestión: Seguimiento (Tipificación),
 * Status y Medio de contacto. Se pueden agregar, editar y borrar opciones sin
 * límite. Lo que se guarda aquí es lo que aparece en los desplegables de la
 * gestión del cliente.
 */
@Composable
fun GestionOptionsManager() {
    val context = LocalContext.current
    var gestion by remember { mutableStateOf(GestionPresets.getGestion(context)) }
    var estado by remember { mutableStateOf(GestionPresets.getEstado(context)) }
    var medio by remember { mutableStateOf(GestionPresets.getMedio(context)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Opciones de gestión",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Personaliza las opciones que aparecen en los desplegables al gestionar " +
                "un cliente. Puedes agregar, editar y borrar sin límite.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        OptionListEditor(
            title = "Seguimiento (Tipificación)",
            options = gestion,
            onChange = {
                gestion = it
                GestionPresets.setGestion(context, it)
            }
        )
        Spacer(Modifier.height(12.dp))
        OptionListEditor(
            title = "Status",
            options = estado,
            onChange = {
                estado = it
                GestionPresets.setEstado(context, it)
            }
        )
        Spacer(Modifier.height(12.dp))
        OptionListEditor(
            title = "Medio de contacto",
            options = medio,
            onChange = {
                medio = it
                GestionPresets.setMedio(context, it)
            }
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionListEditor(
    title: String,
    options: List<String>,
    onChange: (List<String>) -> Unit
) {
    var newValue by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }

    SectionCard {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${options.size} opciones · sin límite",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (options.isEmpty()) {
            Text(
                "No hay opciones. Agrega la primera abajo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        editingIndex = index
                        editingText = option
                    }) {
                        AppIcon(Icons.Default.Edit, contentDescription = "Editar", size = 20.dp)
                    }
                    IconButton(onClick = {
                        val l = options.toMutableList()
                        l.removeAt(index)
                        onChange(l)
                    }) {
                        AppIcon(
                            Icons.Default.Delete,
                            contentDescription = "Borrar",
                            tint = MaterialTheme.colorScheme.error,
                            size = 20.dp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newValue,
                onValueChange = { newValue = it },
                label = { Text("Nueva opción") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val v = newValue.trim()
                    if (v.isNotBlank()) {
                        onChange((options + v).distinctBy { it.lowercase() })
                        newValue = ""
                    }
                },
                enabled = newValue.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                AppIcon(Icons.Default.Add, contentDescription = null, size = 18.dp)
                Spacer(Modifier.width(4.dp))
                Text("Agregar")
            }
        }
    }

    val idx = editingIndex
    if (idx != null && idx in options.indices) {
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("Editar opción") },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    label = { Text("Valor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = editingText.trim()
                    if (v.isNotBlank()) {
                        val l = options.toMutableList()
                        l[idx] = v
                        onChange(l.distinctBy { it.lowercase() })
                    }
                    editingIndex = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = null }) { Text("Cancelar") }
            }
        )
    }
}
