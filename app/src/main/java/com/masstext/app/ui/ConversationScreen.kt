@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.masstext.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masstext.app.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationScreen(
    conversation: Conversation,
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCall: (String, String) -> Unit = { _: String, _: String -> },
    onOpenCalls: (String, String) -> Unit = onCall
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages by viewModel.messagesForThread(conversation.threadId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var filtering by rememberSaveable { mutableStateOf(false) }
    var showContactInfo by rememberSaveable { mutableStateOf(false) }
    var selectingMsgs by rememberSaveable { mutableStateOf(false) }
    var selectedMsgIds by rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }

    var text by rememberSaveable { mutableStateOf("") }
    var selectionCleared by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var pendingJumpId by remember { mutableStateOf<Long?>(null) }
    var highlightedMsgId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !filtering) listState.scrollToItem(messages.lastIndex)
    }

    LaunchedEffect(filtering, pendingJumpId) {
        val id = pendingJumpId
        if (!filtering && id != null) {
            val idx = messages.indexOfFirst { it.id == id }
            if (idx >= 0) listState.animateScrollToItem(idx)
            highlightedMsgId = id
            pendingJumpId = null
        }
    }

    LaunchedEffect(highlightedMsgId) {
        if (highlightedMsgId != null) {
            delay(1800)
            highlightedMsgId = null
        }
    }

    val filteredMessages = if (!filtering || query.isBlank())
        messages
    else
        messages.filter { it.body.contains(query.trim(), ignoreCase = true) }

    fun copyMessages(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val body = messages.filter { it.id in ids }
            .sortedBy { it.date }
            .joinToString("\n") { it.body }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Mensajes", body))
        Toast.makeText(context, "${ids.size} mensaje(s) copiado(s)", Toast.LENGTH_SHORT).show()
    }

    fun exitSelection() {
        selectingMsgs = false
        selectedMsgIds = emptySet()
        filtering = false
    }

    BackHandler { if (selectingMsgs) exitSelection() else onBack() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (selectingMsgs) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { exitSelection() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar seleccion")
                    }
                    Text(
                        if (selectedMsgIds.isEmpty()) "Selecciona mensajes" else "${selectedMsgIds.size} seleccionado(s)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    val allIds = filteredMessages.map { it.id }.toSet()
                    IconButton(
                        onClick = {
                            selectedMsgIds = if (selectedMsgIds == allIds) emptySet() else allIds
                        }
                    ) { Icon(Icons.Default.DoneAll, contentDescription = "Seleccionar todo") }
                    IconButton(
                        onClick = { copyMessages(selectedMsgIds) },
                        enabled = selectedMsgIds.isNotEmpty()
                    ) { Icon(Icons.Default.ContentCopy, contentDescription = "Copiar") }
                    IconButton(
                        onClick = {
                            viewModel.moveMessageToTrash(selectedMsgIds.toList())
                            Toast.makeText(context, "Mensaje(s) movido(s) a la papelera", Toast.LENGTH_SHORT).show()
                            exitSelection()
                        },
                        enabled = selectedMsgIds.isNotEmpty()
                    ) { Icon(Icons.Default.DeleteSweep, contentDescription = "Mover a la papelera") }
                }
            }
        } else if (filtering) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        filtering = false
                        query = ""
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancela la busqueda") }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Buscar en este chat...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                filtering = false
                                query = ""
                            }) { Icon(Icons.Default.Close, contentDescription = "Cerrar busqueda") }
                        }
                    )
                }
            }
        } else {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChatAvatar(conversation.name, 34.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                conversation.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                conversation.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showContactInfo = true }
                                    .padding(horizontal = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        filtering = true
                        query = ""
                    }) { Icon(Icons.Default.Search, contentDescription = "Buscar en este chat") }
                    IconButton(onClick = { showContactInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Informacion de contacto")
                    }
                }
            )
        }

        if (filteredMessages.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (filtering) "Sin resultados" else "Sin mensajes aun",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(filteredMessages, key = { _, m -> m.id }) { index, message ->
                    val isOutgoing = !message.isIncoming
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart) {
                        Surface(
                            color = if (isOutgoing) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isOutgoing) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            shadowElevation = 1.dp,
                            border = if (message.id == highlightedMsgId)
                                BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .combinedClickable(
                                    onClick = {
                                        if (selectingMsgs) {
                                            selectedMsgIds = if (message.id in selectedMsgIds)
                                                selectedMsgIds - message.id else selectedMsgIds + message.id
                                        } else if (filtering) {
                                            pendingJumpId = message.id
                                            filtering = false
                                            query = ""
                                        }
                                    },
                                    onLongClick = {
                                        selectingMsgs = true
                                        selectedMsgIds = selectedMsgIds + message.id
                                    }
                                )
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                if (selectingMsgs && message.id in selectedMsgIds) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Seleccionado",
                                        Modifier.size(16.dp).align(Alignment.End)
                                    )
                                }
                                if (filtering && query.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                }
                                Text(
                                    highlightedBody(message.body, if (filtering) query else ""),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(message.date)),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    // Estado del mensaje saliente: enviando (reloj),
                                    // enviado (check) o no enviado (X).
                                    if (isOutgoing) {
                                        Spacer(Modifier.width(4.dp))
                                        OutgoingStatusIcon(status = message.status, size = 14.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ConversationInputBar(
            conversation = conversation,
            viewModel = viewModel,
            modifier = Modifier.imePadding()
        )
    }

    if (showContactInfo) {
        ContactInfoDialog(
            conversation = conversation,
            onDismiss = { showContactInfo = false },
            onBack = onBack,
            onDelete = onDelete,
            onCall = onCall,
            onOpenCalls = onOpenCalls,
            onSearchChat = {
                showContactInfo = false
                filtering = true
                query = ""
            }
        )
    }
}

@Composable
private fun ConversationAvatar(name: String, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ContactInfoDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onCall: (String, String) -> Unit,
    onOpenCalls: (String, String) -> Unit,
    onSearchChat: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Contacto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        onDismiss()
                        onDelete()
                    }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            conversation.name.take(1).uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        conversation.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        conversation.address,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ContactQuickAction(Icons.Default.Call, "Llamar") {
                            onDismiss()
                            onCall(conversation.name, conversation.address)
                        }
                        ContactQuickAction(Icons.Default.Search, "Buscar") {
                            onDismiss()
                            onSearchChat()
                        }
                        ContactQuickAction(Icons.Default.Info, "Llamadas") {
                            onDismiss()
                            onOpenCalls(conversation.name, conversation.address)
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                            ContactActionRow("Llamar") {
                                onDismiss()
                                onCall(conversation.name, conversation.address)
                            }
                            ContactActionRow("Ver llamadas") {
                                onDismiss()
                                onOpenCalls(conversation.name, conversation.address)
                            }
                            ContactActionRow("WhatsApp") {
                                try {
                                    val wa = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://wa.me/${conversation.address.replace(Regex("[^0-9+]"), "")}")
                                    )
                                    context.startActivity(wa)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "WhatsApp no disponible", Toast.LENGTH_SHORT).show()
                                }
                            }
                            ContactActionRow("Compartir contacto") {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Contacto",
                                        "${conversation.name} - ${conversation.address}"
                                    )
                                )
                                Toast.makeText(context, "Contacto copiado", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ContactQuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun highlightedBody(body: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(body)
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(body)
    return buildAnnotatedString {
        val lowerBody = body.lowercase()
        val lowerQ = q.lowercase()
        var start = 0
        while (true) {
            val i = lowerBody.indexOf(lowerQ, start)
            if (i < 0) {
                append(body.substring(start))
                break
            }
            if (i > start) append(body.substring(start, i))
            withStyle(
                SpanStyle(
                    background = Color(0xFFFFE082),
                    color = Color(0xFF000000),
                    fontWeight = FontWeight.Bold
                )
            ) { append(body.substring(i, i + q.length)) }
            start = i + q.length
        }
    }
}

@Composable
private fun ContactActionRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConversationInputBar(
    conversation: Conversation,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }

    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var showCameraMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showCameraMenu = true }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camara")
                }
                DropdownMenu(expanded = showCameraMenu, onDismissRequest = { showCameraMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Camara") },
                        onClick = {
                            showCameraMenu = false
                            openCamera(context)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Galeria") },
                        onClick = {
                            showCameraMenu = false
                            openGallery(context)
                        }
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                Modifier.weight(1f),
                maxLines = 4,
                placeholder = { Text("Escribe un mensaje") }
            )
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = {
                    val body = text.trim()
                    if (body.isBlank()) return@FilledIconButton
                    scope.launch {
                        viewModel.sendSingleMessage(conversation.address, body)
                        Toast.makeText(context, "Mensaje enviado", Toast.LENGTH_SHORT).show()
                    }
                    text = ""
                },
                enabled = text.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            }
        }
    }
}

private fun openCamera(context: Context) {
    val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No hay camara disponible", Toast.LENGTH_SHORT).show()
    }
}

private fun openGallery(context: Context) {
    val intent = Intent(
        Intent.ACTION_PICK,
        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    )
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No hay galeria disponible", Toast.LENGTH_SHORT).show()
    }
}
