package com.masstext.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masstext.app.data.Conversation

/**
 * Papelera de mensajes: los chats/mensajes borrados se guardan aquí y pueden
 * restaurarse o eliminarse de forma DEFINITIVA del dispositivo (vaciar papelera).
 */
@Composable
fun TrashScreen(
    conversations: List<Conversation>,
    onBack: () -> Unit,
    onRestore: (Conversation) -> Unit,
    onPurge: (Conversation) -> Unit,
    onEmptyTrash: () -> Unit
) {
    var confirmPurgeConversation by remember { mutableStateOf<Conversation?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    AppIcon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Papelera",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Mensajes borrados guardados aquí",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { if (conversations.isNotEmpty()) confirmEmpty = true },
                    enabled = conversations.isNotEmpty()
                ) {
                    AppIcon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        size = 18.dp
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Vaciar papelera", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (confirmPurgeConversation != null) {
            val conv = confirmPurgeConversation!!
            AlertDialog(
                onDismissRequest = { confirmPurgeConversation = null },
                title = { Text("Eliminar definitivamente") },
                text = {
                    Text(
                        "La conversación con ${conv.name} se borrará de forma PERMANENTE del dispositivo. " +
                            "No podrá recuperarse ni reaparecerá aunque la app se recargue. ¿Continuar?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmPurgeConversation = null
                        onPurge(conv)
                    }) { Text("Eliminar para siempre", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmPurgeConversation = null }) { Text("Cancelar") }
                }
            )
        }

        if (confirmEmpty) {
            AlertDialog(
                onDismissRequest = { confirmEmpty = false },
                title = { Text("Vaciar papelera") },
                text = {
                    Text(
                        "Se eliminarán TODOS los chats de la papelera de forma permanente del dispositivo. " +
                            "No podrán recuperarse ni reaparecerán aunque la app se recargue. ¿Continuar?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmEmpty = false
                        onEmptyTrash()
                    }) { Text("Vaciar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmEmpty = false }) { Text("Cancelar") }
                }
            )
        }

        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Sms,
                    title = "La papelera está vacía",
                    description = "Los mensajes que borres aparecerán aquí hasta que los elimines definitivamente o vacíes la papelera."
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(conversations, key = { it.threadId }) { conv ->
                    TrashRow(
                        conversation = conv,
                        onRestore = { onRestore(conv) },
                        onPurge = { confirmPurgeConversation = conv }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 72.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashRow(
    conversation: Conversation,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatAvatar(conversation.name, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                conversation.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                conversation.lastBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatConversationTime(conversation.lastDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.clickable(onClick = onRestore)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    Icons.Default.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 16.dp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Restaurar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            modifier = Modifier.clickable(onClick = onPurge)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    size = 16.dp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Eliminar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}