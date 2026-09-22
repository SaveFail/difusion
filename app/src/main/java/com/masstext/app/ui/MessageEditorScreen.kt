package com.masstext.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masstext.app.data.MessageTemplate

@Composable
fun MessageEditorScreen(
    messageBody: String,
    onMessageChange: (String) -> Unit,
    templates: List<MessageTemplate>,
    onSaveTemplate: (String, String) -> Unit,
    onDeleteTemplate: (MessageTemplate) -> Unit,
    selectedCount: Int,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                            selected = false,
                            onClick = { onMessageChange(template.body) },
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
                Spacer(modifier = Modifier.height(12.dp))
            }

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
                AssistChip(
                    onClick = {},
                    label = { Text("$smsCount SMS") }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Usa {nombre} para personalizar con el nombre de cada cliente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.height(8.dp))

            var templateName by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = templateName,
                onValueChange = { templateName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre de la plantilla") },
                placeholder = { Text("Ej: Promoción noviembre") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            SecondaryActionButton(
                text = "Guardar como plantilla \"$templateName\"",
                onClick = {
                    if (templateName.isNotBlank()) {
                        onSaveTemplate(templateName, messageBody)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.BookmarkAdd,
                enabled = messageBody.isNotBlank() && templateName.isNotBlank()
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