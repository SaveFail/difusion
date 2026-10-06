package com.difusion.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.difusion.app.data.Contact
import com.difusion.app.data.MessageTemplate
import com.difusion.app.service.EmailSyncService
import com.difusion.app.service.GmailApiService
import com.difusion.app.service.GmailAuth
import com.difusion.app.service.MailClient
import com.difusion.app.storage.EmailSyncPrefs
import com.difusion.app.storage.RecipientsPrefs
import com.difusion.app.storage.SmtpPrefs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Correo dentro de la sección Mensaje.
 * - Con SESIÓN de Google: lee y envía DIRECTO por la API de Gmail.
 * - Sin sesión: cae al puente de Apps Script si está configurado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorreoInboxScreen(
    contacts: List<Contact> = emptyList(),
    selectedIds: Set<Long> = emptySet(),
    viewModel: MainViewModel? = null
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Masivo") }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Bandeja") }
            )
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0 && viewModel != null) {
                CorreoMasivoTab(contacts = contacts, selectedIds = selectedIds, viewModel = viewModel)
            } else {
                CorreoBandejaScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorreoBandejaScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var signedIn by remember { mutableStateOf(GmailAuth.isSignedIn(context)) }
    var smtpConfigured by remember { mutableStateOf(MailClient.isConfigured(context)) }
    var url by rememberSaveable { mutableStateOf(EmailSyncPrefs.getUrl(context)) }
    var token by rememberSaveable { mutableStateOf(EmailSyncPrefs.getToken(context)) }
    var showBridge by rememberSaveable { mutableStateOf(false) }
    var showConfig by rememberSaveable { mutableStateOf(false) }
    // Carpeta: inbox, sent, spam, trash.
    var folder by rememberSaveable { mutableStateOf("inbox") }

    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var inbox by remember { mutableStateOf<List<EmailSyncService.MailSummary>>(emptyList()) }
    var unread by remember { mutableStateOf(0) }
    var openThread by remember { mutableStateOf<EmailSyncService.MailThread?>(null) }
    var openThreadId by remember { mutableStateOf<String?>(null) }
    var replyText by rememberSaveable { mutableStateOf("") }
    var showCompose by remember { mutableStateOf(false) }

    val bridge = EmailSyncService.EmailConfig(url.trim(), token.trim())
    fun nativeToken(): String? = GmailAuth.token(context)
    fun canUseMailbox() = nativeToken() != null || smtpConfigured || url.isNotBlank()

    fun loadInbox() {
        if (!canUseMailbox()) return
        loading = true
        val nt = nativeToken()
        val useSmtp = nt == null && smtpConfigured
        val label = when (folder) {
            "sent" -> "SENT"
            "spam" -> "SPAM"
            "trash" -> "TRASH"
            "all" -> "ALL"
            else -> "INBOX"
        }
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                when {
                    nt != null -> GmailApiService.listFolder(nt, label, 15)
                    useSmtp -> MailClient.listFolder(context, folder, 15)
                    else -> EmailSyncService.listInbox(context, bridge, 25)
                }
            }
            loading = false
            if (res.success) {
                inbox = res.messages
                unread = res.unread
                errorMsg = ""
            } else {
                errorMsg = res.error
                Toast.makeText(context, res.error, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun testBridge() {
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                EmailSyncService.ping(EmailSyncService.EmailConfig(url.trim(), token.trim()))
            }
            Toast.makeText(
                context,
                if (res.success) "Conectado: " + res.email.ifBlank { "Gmail" }
                else "No se pudo conectar: ${res.error}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openThreadById(id: String) {
        openThreadId = id
        loading = true
        val nt = nativeToken()
        val useSmtp = nt == null && smtpConfigured
        scope.launch {
            openThread = withContext(Dispatchers.IO) {
                when {
                    nt != null -> GmailApiService.readThread(nt, id)
                    useSmtp -> MailClient.readMessage(context, folder, id)
                    else -> EmailSyncService.readThread(bridge, id)
                }
            }
            loading = false
        }
    }

    LaunchedEffect(signedIn, url, smtpConfigured, folder) {
        if (canUseMailbox()) loadInbox()
    }
    val folders = listOf(
        "inbox" to "Recibidos",
        "all" to "Todos",
        "sent" to "Enviados",
        "spam" to "Spam",
        "trash" to "Papelera"
    )

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Correo",
            subtitle = if (signedIn) "Gmail conectado (envío directo)"
            else "Tu Gmail: ver, responder y enviar",
            icon = Icons.Default.Email
        )

        if (canUseMailbox() && openThread == null) {
            ScrollableTabRow(
                selectedTabIndex = folders.indexOfFirst { it.first == folder }.coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                folders.forEach { (key, label) ->
                    Tab(
                        selected = folder == key,
                        onClick = {
                            folder = key
                            loadInbox()
                        },
                        text = { Text(label) }
                    )
                }
            }
        }

        if (!canUseMailbox()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (GmailAuth.hasBakedClientId()) {
                    GmailSignInSection { signedIn = true; loadInbox() }
                    Spacer(Modifier.height(12.dp))
                }
                SmtpConfigCard(onSaved = { smtpConfigured = true; loadInbox() })
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { showBridge = !showBridge }) {
                    Text(if (showBridge) "Ocultar Apps Script" else "Usar Apps Script en su lugar")
                }
                if (showBridge) {
                    BridgeConfigCard(
                        url = url,
                        token = token,
                        onUrlChange = { url = it },
                        onTokenChange = { token = it },
                        onSave = {
                            EmailSyncPrefs.setUrl(context, url.trim())
                            EmailSyncPrefs.setToken(context, token.trim())
                            EmailSyncPrefs.setEnabled(context, true)
                            Toast.makeText(context, "Puente configurado", Toast.LENGTH_SHORT).show()
                            loadInbox()
                        },
                        onTest = { testBridge() }
                    )
                }
            }
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
                            val nt = nativeToken()
                            val useSmtp = nt == null && smtpConfigured
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    when {
                                        nt != null -> GmailApiService.reply(nt, id, body)
                                        useSmtp -> MailClient.reply(context, folder, id, body)
                                        else -> EmailSyncService.reply(bridge, id, body)
                                    }
                                }
                                Toast.makeText(
                                    context,
                                    if (ok) "Respuesta enviada" else "No se pudo responder",
                                    Toast.LENGTH_SHORT
                                ).show()
                                replyText = ""
                                openThread = withContext(Dispatchers.IO) {
                                    when {
                                        nt != null -> GmailApiService.readThread(nt, id)
                                        useSmtp -> MailClient.readMessage(context, folder, id)
                                        else -> EmailSyncService.readThread(bridge, id)
                                    }
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
                if (unread > 0) "Bandeja · $unread sin leer" else "Bandeja de entrada",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { loadInbox() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
            IconButton(onClick = { showConfig = !showConfig }) {
                Icon(Icons.Default.Settings, contentDescription = "Configurar")
            }
        }

        if (errorMsg.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { loadInbox() }) { Text("Reintentar") }
                }
            }
        }

        if (showConfig) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                SmtpConfigCard(onSaved = { smtpConfigured = true; showConfig = false; loadInbox() })
                Spacer(Modifier.height(12.dp))
            }
        }

        if (showBridge) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                BridgeConfigCard(
                    url = url,
                    token = token,
                    onUrlChange = { url = it },
                    onTokenChange = { token = it },
                    onSave = {
                        EmailSyncPrefs.setUrl(context, url.trim())
                        EmailSyncPrefs.setToken(context, token.trim())
                        EmailSyncPrefs.setEnabled(context, true)
                        showBridge = false
                        Toast.makeText(context, "Puente guardado", Toast.LENGTH_SHORT).show()
                    },
                    onTest = {
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                EmailSyncService.ping(EmailSyncService.EmailConfig(url.trim(), token.trim()))
                            }
                            Toast.makeText(
                                context,
                                if (res.success) "Conectado: " + res.email.ifBlank { "Gmail" }
                                else "No se pudo conectar: ${res.error}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (loading && inbox.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (inbox.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Sin mensajes. Toca actualizar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            .clickable { openThreadById(m.threadId) }
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
                        EmailSyncService.sendSmart(context, listOf(to), subject, body)
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
private fun BridgeConfigCard(
    url: String,
    token: String,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Respaldo con Apps Script (opcional)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Permite enviar/leer sin sesión de Google. Límite ~100 correos/día. " +
                    "Con sesión de Google no lo necesitas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("URL del Web App") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                label = { Text("Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = onSave, enabled = url.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text("Guardar")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onTest, enabled = url.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text("Probar")
                }
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

@Composable
fun SmtpConfigCard(onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf(SmtpPrefs.getEmail(context)) }
    var pass by rememberSaveable { mutableStateOf(SmtpPrefs.getPassword(context)) }
    var testError by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Tu Gmail (correo y contraseña de aplicación)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "No necesitas Google Cloud. Solo:\n" +
                    "1. En tu cuenta de Google activa la verificación en 2 pasos.\n" +
                    "2. Entra a myaccount.google.com/apppasswords y crea una " +
                    "\"Contraseña de aplicación\".\n" +
                    "3. Escribe aquí tu correo y esa contraseña de 16 letras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo de Gmail") },
                placeholder = { Text("tucorreo@gmail.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Contraseña de aplicación") },
                placeholder = { Text("abcd efgh ijkl mnop") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Button(
                    onClick = {
                        SmtpPrefs.setEmail(context, email)
                        SmtpPrefs.setPassword(context, pass)
                        scope.launch {
                            val (ok, msg) = withContext(Dispatchers.IO) { MailClient.testConnection(context) }
                            if (ok) {
                                testError = ""
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                onSaved()
                            } else {
                                testError = smtpHint(msg)
                                Toast.makeText(context, testError, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = email.isNotBlank() && pass.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Guardar y cargar") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        SmtpPrefs.setEmail(context, email)
                        SmtpPrefs.setPassword(context, pass)
                        scope.launch {
                            val (ok, msg) = withContext(Dispatchers.IO) { MailClient.testConnection(context) }
                            testError = if (ok) "" else smtpHint(msg)
                            Toast.makeText(
                                context,
                                if (ok) msg else testError,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = email.isNotBlank() && pass.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Probar") }
            }
            if (testError.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        testError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { openUrl(context, "https://myaccount.google.com/apppasswords") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Paso 1: Crear contraseña de aplicación") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { openUrl(context, "https://mail.google.com/mail/u/0/#settings/fwdandpop") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Paso 2: Activar IMAP en Gmail") }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun smtpHint(msg: String): String {
    val m = msg.lowercase()
    return when {
        m.contains("application-specific password") || m.contains("application specific") ->
            "Google exige una CONTRASEÑA DE APLICACIÓN.\n\n" +
                "1. Activa la verificación en 2 pasos en tu cuenta Google.\n" +
                "2. Abre myaccount.google.com/apppasswords y crea una (nombre: Difusión).\n" +
                "3. Copia las 16 letras y pégalas aquí (NO tu contraseña normal)."
        m.contains("authentication") || m.contains("535") ||
            m.contains("username and password") || m.contains("not accepted") ||
            m.contains("credential") || m.contains("login") ->
            "No se pudo conectar.\n\nUsa una CONTRASEÑA DE APLICACIÓN de 16 letras " +
                "(NO tu contraseña normal de Gmail) y activa la verificación en 2 pasos.\n\n" +
                "Detalle: $msg"
        m.contains("imap") || m.contains("not enabled") || m.contains("disabled") ->
            "No se pudo conectar.\n\nActiva IMAP en Gmail ▸ Configuración ▸ " +
                "Reenvío y POP/IMAP.\n\nDetalle: $msg"
        else -> "No se pudo conectar: $msg"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorreoMasivoTab(
    contacts: List<Contact>,
    selectedIds: Set<Long>,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val emailProgress by viewModel.emailProgress.collectAsStateWithLifecycle()

    var mode by rememberSaveable { mutableStateOf(0) } // 0 sel, 1 lista, 2 individual
    var emailList by rememberSaveable { mutableStateOf(RecipientsPrefs.get(context)) }
    var singleEmail by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var scheduledAt by remember { mutableStateOf<Long?>(null) }

    val selectedContacts = remember(contacts, selectedIds) {
        contacts.filter { it.id in selectedIds }
    }
    val selectedEmails = remember(selectedContacts) {
        selectedContacts.mapNotNull { it.email.trim().takeIf { e -> e.isNotBlank() } }
    }

    fun recipients(): List<String> = when (mode) {
        0 -> selectedEmails
        1 -> RecipientsPrefs.parse(emailList)
        else -> listOf(singleEmail.trim()).filter { it.isNotBlank() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Envío masivo de correo",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Igual que el envío de mensajes, pero por correo. Se envía uno por uno " +
                "(sin CC ni CCO) y corre en segundo plano.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (templates.isNotEmpty()) {
            Text(
                "Plantillas",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(templates) { t: MessageTemplate ->
                    InputChip(
                        selected = false,
                        onClick = {
                            body = t.body
                            if (t.subject.isNotBlank()) subject = t.subject
                        },
                        label = { Text(t.name) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            "Destinatarios",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == 0,
                onClick = { mode = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                label = { Text("Seleccionados") }
            )
            SegmentedButton(
                selected = mode == 1,
                onClick = { mode = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                label = { Text("Lista") }
            )
            SegmentedButton(
                selected = mode == 2,
                onClick = { mode = 2 },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                label = { Text("Individual") }
            )
        }
        Spacer(Modifier.height(8.dp))
        when (mode) {
            0 -> Text(
                "${selectedEmails.size} contactos seleccionados con correo (marca contactos en la pestaña Contactos).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            1 -> {
                OutlinedTextField(
                    value = emailList,
                    onValueChange = {
                        emailList = it
                        RecipientsPrefs.set(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp),
                    label = { Text("Correos precargados (coma o línea)") },
                    placeholder = { Text("cliente1@correo.com, cliente2@correo.com") },
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${RecipientsPrefs.parse(emailList).size} correos válidos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> OutlinedTextField(
                value = singleEmail,
                onValueChange = { singleEmail = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Correo") },
                placeholder = { Text("persona@correo.com") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Asunto") },
            placeholder = { Text("Ej: Aviso de pago") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 320.dp),
            label = { Text("Mensaje") },
            placeholder = { Text("Hola {nombre}, le informamos...") },
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(12.dp))

        ScheduleControls(onAtMillis = { scheduledAt = it })
        Spacer(Modifier.height(12.dp))

        emailProgress?.let { p ->
            Text(
                "Enviando ${p.first} de ${p.second}…",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
        }

        val r = recipients()
        Button(
            enabled = subject.isNotBlank() && body.isNotBlank() && r.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (scheduledAt != null) {
                    if (scheduledAt!! <= System.currentTimeMillis()) {
                        Toast.makeText(context, "Elige una fecha y hora futuras", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.scheduleSend(
                            if (mode == 0) selectedContacts else emptyList(),
                            body, subject, 1, scheduledAt!!,
                            if (mode != 0) r else emptyList()
                        )
                        Toast.makeText(context, "Envío de correo programado", Toast.LENGTH_LONG).show()
                    }
                } else {
                    viewModel.sendBulkEmailBackground(r, subject, body)
                }
            }
        ) {
            Icon(Icons.Default.Email, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (scheduledAt != null) "Programar envío (${r.size})" else "Enviar a ${r.size}")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Puedes dejar la app en segundo plano; el envío continúa y verás el avance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
    }
}
