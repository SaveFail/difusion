package com.difusion.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.difusion.app.service.EmailSyncService
import com.difusion.app.storage.EmailSyncPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Correo (Gmail) dentro de la sección Mensaje.
 * El acceso es real: usa un Web App de Google Apps Script que se ejecuta COMO
 * tu cuenta de Google (al desplegarlo autorizas con tu cuenta = "iniciar sesión").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorreoInboxScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by rememberSaveable { mutableStateOf(EmailSyncPrefs.getUrl(context)) }
    var token by rememberSaveable { mutableStateOf(EmailSyncPrefs.getToken(context)) }
    var showConfig by rememberSaveable { mutableStateOf(EmailSyncPrefs.getUrl(context).isBlank()) }

    var loading by remember { mutableStateOf(false) }
    var inbox by remember { mutableStateOf<List<EmailSyncService.MailSummary>>(emptyList()) }
    var unread by remember { mutableStateOf(0) }
    var openThread by remember { mutableStateOf<EmailSyncService.MailThread?>(null) }
    var openThreadId by remember { mutableStateOf<String?>(null) }
    var replyText by rememberSaveable { mutableStateOf("") }
    var showCompose by remember { mutableStateOf(false) }

    fun config() = EmailSyncService.EmailConfig(url.trim(), token.trim())

    fun loadInbox() {
        if (url.isBlank()) return
        loading = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                EmailSyncService.listInbox(context, config(), 25)
            }
            loading = false
            if (res.success) {
                inbox = res.messages
                unread = res.unread
            } else {
                Toast.makeText(context, res.error, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(url) {
        if (url.isNotBlank()) loadInbox()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Correo",
            subtitle = "Tu Gmail real: ver, responder y enviar",
            icon = Icons.Default.Email
        )

        if (showConfig || url.isBlank()) {
            ConfigCard(
                url = url,
                token = token,
                onUrlChange = { url = it },
                onTokenChange = { token = it },
                onSave = {
                    EmailSyncPrefs.setUrl(context, url.trim())
                    EmailSyncPrefs.setToken(context, token.trim())
                    EmailSyncPrefs.setEnabled(context, true)
                    showConfig = false
                    Toast.makeText(context, "Correo configurado", Toast.LENGTH_SHORT).show()
                    loadInbox()
                }
            )
            return@Column
        }

        // --- Conversación abierta ---
        val thread = openThread
        if (thread != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { openThread = null; openThreadId = null; replyText = "" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    thread.subject.ifBlank { "(sin asunto)" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(thread.messages) { msg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (msg.isFromMe) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                if (msg.isFromMe) "Yo" else msg.from,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(msg.body.trim(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        placeholder = { Text("Responder...") }
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(
                        enabled = replyText.isNotBlank(),
                        onClick = {
                            val body = replyText.trim()
                            val id = openThreadId ?: return@FilledIconButton
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    EmailSyncService.reply(config(), id, body)
                                }
                                Toast.makeText(
                                    context,
                                    if (ok) "Respuesta enviada" else "No se pudo responder",
                                    Toast.LENGTH_SHORT
                                ).show()
                                replyText = ""
                                openThread = withContext(Dispatchers.IO) {
                                    EmailSyncService.readThread(config(), id)
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Responder")
                    }
                }
            }
            return@Column
        }

        // --- Bandeja ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (unread > 0) "Bandeja de entrada · $unread sin leer" else "Bandeja de entrada",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { loadInbox() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
            IconButton(onClick = { showConfig = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Configurar")
            }
        }

        if (loading && inbox.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (inbox.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Sin mensajes. Toca actualizar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(inbox, key = { it.threadId }) { m ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (m.unread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openThreadId = m.threadId
                                scope.launch {
                                    loading = true
                                    openThread = withContext(Dispatchers.IO) {
                                        EmailSyncService.readThread(config(), m.threadId)
                                    }
                                    loading = false
                                }
                            }
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                m.from,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (m.unread) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                m.subject.ifBlank { "(sin asunto)" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                m.snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { showCompose = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Icon(Icons.Default.Email, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Redactar correo")
        }
    }

    if (showCompose) {
        ComposeDialog(
            onDismiss = { showCompose = false },
            onSend = { to, subject, body ->
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        EmailSyncService.sendOne(context, config(), to, subject, body)
                    }
                    Toast.makeText(
                        context,
                        if (res.success) "Correo enviado" else "Error: ${res.error}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                showCompose = false
            }
        )
    }
}

@Composable
private fun ConfigCard(
    url: String,
    token: String,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Conecta tu Gmail",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "El acceso es real y seguro: publica el script EmailSyncAppsScript.gs " +
                        "como Aplicación web desde tu cuenta de Google (al hacerlo, Google te " +
                        "pide autorizar = iniciar sesión). Luego pega aquí la URL .../exec.\n\n" +
                        "Puedes ver la bandeja, leer, responder, enviar y programar correos. " +
                        "Ninguna contraseña viaja a la app.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("URL del Web App (Gmail)") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = onTokenChange,
                    label = { Text("Token (el mismo del script)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onSave,
                    enabled = url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar y conectar") }
            }
        }
    }
}

@Composable
private fun ComposeDialog(
    onDismiss: () -> Unit,
    onSend: (to: String, subject: String, body: String) -> Unit
) {
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Nuevo correo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("Para (correo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Asunto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Mensaje") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSend(to.trim(), subject.trim(), body) },
                        enabled = to.isNotBlank() && subject.isNotBlank() && body.isNotBlank()
                    ) { Text("Enviar") }
                }
            }
        }
    }
}
