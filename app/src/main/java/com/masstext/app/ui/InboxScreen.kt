package com.masstext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masstext.app.data.Conversation
import com.masstext.app.data.SmsStatus

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(
    conversations: List<Conversation>,
    onOpenThread: (Conversation) -> Unit,
    onNewMessage: () -> Unit,
    onReload: () -> Unit,
    onResendSelected: (List<Conversation>) -> Unit,
    onDeleteSelected: (List<Conversation>) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenCalls: () -> Unit
) {
    var selecting by remember { mutableStateOf(false) }
    var selectedThreads by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var query by rememberSaveable { mutableStateOf("") }
    // 0 = Todos, 1 = Sin leer, 2 = Sin responder
    var filter by rememberSaveable { mutableStateOf(0) }
    val unreadChats = conversations.count { it.unreadCount > 0 }
    val unansweredChats = conversations.count { it.lastIsIncoming }
    val baseConversations = when (filter) {
        1 -> conversations.filter { it.unreadCount > 0 }
        2 -> conversations.filter { it.lastIsIncoming }
        else -> conversations
    }
    val searchResults = if (query.isBlank()) baseConversations
        else baseConversations.filter { c ->
            c.name.contains(query, true) ||
                c.address.contains(query, true) ||
                c.lastBody.contains(query, true)
        }

    val allThreadIds = conversations.map { it.threadId }.toSet()
    val allSelected = conversations.isNotEmpty() && selectedThreads.containsAll(allThreadIds)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box {
                if (!selecting) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Mensajes",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (conversations.isEmpty()) "" else "${conversations.size} chats",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { selecting = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 48.dp)
                    ) {
                        AppIcon(
                            Icons.Default.Restore,
                            contentDescription = "Reenviar mensajes no enviados",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(
                        onClick = onReload,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        AppIcon(
                            Icons.Default.Refresh,
                            contentDescription = "Cargar mensajes",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(
                        onClick = onOpenTrash,
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 96.dp)
                    ) {
                        AppIcon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Papelera",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            selecting = false
                            selectedThreads = emptySet()
                        }) {
                            AppIcon(
                                Icons.Default.Close,
                                contentDescription = "Cancelar selección"
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Reenviar no enviados",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Selecciona los chats (su último mensaje fallido se reenviará)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = {
                            selectedThreads = if (allSelected) emptySet() else allThreadIds
                        }) {
                            AppIcon(
                                Icons.Default.DoneAll,
                                contentDescription = null,
                                size = 18.dp
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (allSelected) "Quitar todos" else "Seleccionar todos")
                        }
                        TextButton(
                            onClick = {
                                val selected = conversations.filter { it.threadId in selectedThreads }
                                if (selected.isNotEmpty()) {
                                    selecting = false
                                    selectedThreads = emptySet()
                                    onDeleteSelected(selected)
                                }
                            }
                        ) {
                            AppIcon(
                                Icons.Default.DeleteSweep,
                                contentDescription = null,
                                size = 18.dp
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Borrar (${selectedThreads.size})", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (!selecting) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    placeholder = {
                        Text(
                            "Buscar por nombre, telefono o contenido",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { AppIcon(Icons.Default.Search, contentDescription = "Buscar") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter == 0,
                        onClick = { filter = 0 },
                        label = { Text("Todos") }
                    )
                    FilterChip(
                        selected = filter == 1,
                        onClick = { filter = 1 },
                        label = {
                            Text(
                                if (unreadChats > 0) "Sin leer ($unreadChats)"
                                else "Sin leer"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.MarkEmailUnread,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                    FilterChip(
                        selected = filter == 2,
                        onClick = { filter = 2 },
                        label = {
                            Text(
                                if (unansweredChats > 0) "Sin responder ($unansweredChats)"
                                else "Sin responder"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.MarkChatUnread,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }

            if (searchResults.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (filter) {
                        1 -> EmptyState(
                            icon = Icons.Default.MarkEmailUnread,
                            title = "No hay chats sin leer",
                            description = "Aquí aparecerán los chats que tengan mensajes entrantes sin ver. Al abrir un chat y leerlo, desaparecerá de esta lista."
                        )
                        2 -> EmptyState(
                            icon = Icons.Default.MarkChatUnread,
                            title = "No hay chats sin responder",
                            description = "Aquí aparecerán los chats cuyo último mensaje es del contacto y todavía no has respondido. Cuando contestes, desaparecerán de esta lista."
                        )
                        else -> EmptyState(
                            icon = Icons.Default.Sms,
                            title = "No hay conversaciones todavía",
                            description = "Los mensajes que recibas o envíes aparecerán aquí y quedan respaldados en el dispositivo. Usa el botón + para iniciar un mensaje nuevo."
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(searchResults, key = { it.threadId }) { conv ->
                        ConversationRow(
                            conversation = conv,
                            selecting = selecting,
                            selected = conv.threadId in selectedThreads,
                            onClick = {
                                if (selecting) {
                                    selectedThreads = selectedThreads.toMutableSet().apply {
                                        if (!add(conv.threadId)) remove(conv.threadId)
                                    }
                                } else {
                                    onOpenThread(conv)
                                }
                            },
                            onLongClick = {
                                selecting = true
                                selectedThreads = selectedThreads + conv.threadId
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(start = 72.dp)
                        )
                    }
                }
            }
        }

        if (!selecting) {
            FloatingActionButton(
                onClick = onNewMessage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                AppIcon(Icons.Default.Add, contentDescription = "Nuevo mensaje")
            }
        } else {
            ExtendedFloatingActionButton(
                onClick = {
                    if (selectedThreads.isEmpty()) return@ExtendedFloatingActionButton
                    val selected = conversations.filter { it.threadId in selectedThreads }
                    selecting = false
                    selectedThreads = emptySet()
                    onResendSelected(selected)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                icon = {
                    AppIcon(Icons.Default.Send, contentDescription = null, size = 20.dp)
                },
                text = {
                    Text("Reenviar (${selectedThreads.size})")
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val hasUnread = conversation.unreadCount > 0
    Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            AppIcon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (selected) "Seleccionado" else "No seleccionado",
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 24.dp,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
        ChatAvatar(conversation.name, size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatConversationTime(conversation.lastDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.lastStatus >= 0) {
                    OutgoingStatusIcon(
                        status = conversation.lastStatus,
                        size = 14.dp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Text(
                    conversation.lastBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (hasUnread) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    conversation.unreadCount.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ChatAvatar(
    name: String,
    size: androidx.compose.ui.unit.Dp
) {
    val initial = name.trim().firstOrNull()?.uppercase()?.take(1) ?: "?"
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initial,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OutgoingStatusIcon(
    status: Int,
    size: androidx.compose.ui.unit.Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector
    val tint: Color
    when (status) {
        SmsStatus.SENDING -> {
            icon = Icons.Default.Schedule
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
        SmsStatus.FAILED -> {
            icon = Icons.Default.Close
            tint = MaterialTheme.colorScheme.error
        }
        else -> {
            icon = Icons.Default.Check
            tint = MaterialTheme.colorScheme.primary
        }
    }
    AppIcon(
        icon,
        contentDescription = when (status) {
            SmsStatus.SENDING -> "Enviando"
            SmsStatus.FAILED -> "No enviado"
            else -> "Enviado"
        },
        tint = tint,
        modifier = modifier,
        size = size
    )
}