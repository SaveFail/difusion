package com.masstext.app.ui

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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masstext.app.data.MessageTemplate
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageEditorScreen(
    messageBody: String,
    onMessageChange: (String) -> Unit,
    templates: List<MessageTemplate>,
    onSaveTemplate: (String, String) -> Unit,
    onDeleteTemplate: (MessageTemplate) -> Unit,
    selectedCount: Int,
    showSmsCounter: Boolean = true,
    onSend: () -> Unit
) {
    // Plantilla seleccionada: id (-1 = ninguna), nombre y texto original. Sirve
    // para saber si el mensaje ya está guardado y solo pedir guardar si cambió.
    var selectedTemplateId by rememberSaveable { mutableStateOf(-1L) }
    var selectedTemplateBody by rememberSaveable { mutableStateOf("") }
    var templateName by rememberSaveable { mutableStateOf("") }

    // Para que el teclado no tape el campo del nombre de la plantilla: se sube
    // con imePadding y se desplaza a la vista al enfocarlo.
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
                                // Cargar la plantilla existente (no hay que guardarla).
                                selectedTemplateId = template.id
                                selectedTemplateBody = template.body
                                templateName = template.name
                                onMessageChange(template.body)
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

            // Ayuda de variables justo encima del cuadro del mensaje.
            Text(
                "Usa {nombre} para personalizar con el nombre de cada cliente (y {telefono} para el número).",
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
                if (showSmsCounter) {
                    AssistChip(
                        onClick = {},
                        label = { Text("$smsCount SMS") }
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
                        onSaveTemplate(templateName, messageBody)
                        // Queda como guardada: el botón no vuelve a pedir guardar.
                        selectedTemplateBody = messageBody
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.BookmarkAdd,
                enabled = canSave
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryActionButton(
                text = if (selectedCount > 0) "Enviar a $selectedCount destinatarios" else "Enviar SMS",
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Send,
                enabled = messageBody.isNotBlank()
            )
        }
    }
}

private fun calculateSmsCount(text: String): Int {
    if (text.isEmpty()) return 0
    return if (text.length <= 160) 1 else (text.length / 153) + 1
}
