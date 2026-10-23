package com.difusion.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.difusion.app.service.BotAutomation
import com.difusion.app.service.WhatsAppBridge
import com.difusion.app.storage.RecipientsPrefs
import com.difusion.app.storage.WaPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bot de WhatsApp (worker Baileys open source): conexión, vinculación,
 * reglas con varias acciones (respuestas, esperas, webhooks), ayuda y envío.
 */
@Composable
fun BotScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(WaPrefs.getUrl(context)) }
    var token by remember { mutableStateOf(WaPrefs.getToken(context)) }
    var statusText by remember { mutableStateOf("Sin probar") }
    var botEnabled by remember { mutableStateOf(WaPrefs.isBotEnabled(context)) }
    var rules by remember { mutableStateOf(WhatsAppBridge.parseRules(WaPrefs.getRulesJson(context))) }
    var pairPhone by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showPhoneHelp by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    var sendPhone by remember { mutableStateOf("") }
    var sendText by remember { mutableStateOf("") }

    // Automatizaciones de la app (acceso completo a procesos).
    var autoRules by remember {
        mutableStateOf(BotAutomation.parseRules(WaPrefs.getAutoRulesJson(context)))
    }
    var editingAuto by remember { mutableStateOf<Int?>(null) }

    // Mensajes programados (varios).
    val vm: MainViewModel = viewModel()
    val scheduled by vm.scheduledSends.collectAsStateWithLifecycle()
    val pendingWa = scheduled.filter { it.channel == 2 && it.status == 0 }
    var schNumbers by remember { mutableStateOf("") }
    var schText by remember { mutableStateOf("") }
    var schAt by remember { mutableStateOf<Long?>(null) }

    fun saveAuto(newRules: List<BotAutomation.Rule>) {
        autoRules = newRules
        WaPrefs.setAutoRulesJson(context, BotAutomation.rulesToJson(newRules))
    }

    // Al abrir la pantalla, empuja al worker las reglas guardadas (por si el
    // servidor se reinició) y activa el sondeo de automatizaciones.
    LaunchedEffect(Unit) {
        if (WaPrefs.isConfigured(context)) {
            val saved = WhatsAppBridge.parseRules(WaPrefs.getRulesJson(context))
            withContext(Dispatchers.IO) {
                WhatsAppBridge.pushBot(context, WaPrefs.isBotEnabled(context), saved)
            }
            if (WaPrefs.isBotEnabled(context)) BotAutomation.ensurePolling(context)
        }
    }

    fun saveConfig() {
        WaPrefs.setUrl(context, url)
        WaPrefs.setToken(context, token)
    }

    fun persistAndPush(newRules: List<WhatsAppBridge.Rule> = rules) {
        rules = newRules
        WaPrefs.setBotEnabled(context, botEnabled)
        WaPrefs.setRulesJson(context, WhatsAppBridge.rulesToJson(newRules))
        if (!WaPrefs.isConfigured(context)) return
        scope.launch {
            val (ok, err) = withContext(Dispatchers.IO) {
                WhatsAppBridge.pushBot(context, botEnabled, newRules)
            }
            statusText = if (ok) "Bot sincronizado con el worker" else "No se pudo sincronizar: $err"
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
            Text(
                "Bot de WhatsApp",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Conecta la app a un worker de WhatsApp (Baileys, open source) y programa " +
                    "reglas con acciones: respuestas, esperas y webhooks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { showHelp = true }) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("¿Cómo funciona el bot?")
            }
            Spacer(Modifier.height(12.dp))

            // --- Conexión ---
            SectionCard {
                Text("Conexión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        // Se guarda al escribir, sin depender de tocar "Probar".
                        WaPrefs.setUrl(context, it)
                    },
                    label = { Text("URL del worker") },
                    placeholder = { Text("http://192.168.1.10:3001") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        WaPrefs.setToken(context, it)
                    },
                    label = { Text("Token (WA_TOKEN)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(
                        onClick = {
                            saveConfig()
                            busy = true
                            scope.launch {
                                val (ok, msg) = withContext(Dispatchers.IO) { WhatsAppBridge.ping(context) }
                                busy = false
                                statusText = if (ok) msg else "Error: $msg"
                            }
                        },
                        enabled = url.isNotBlank() && !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Probar conexión") }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pairPhone,
                        onValueChange = { pairPhone = it },
                        label = { Text("Tu número para vincular") },
                        placeholder = { Text("584241234567") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            saveConfig()
                            busy = true
                            scope.launch {
                                val (_, err) = withContext(Dispatchers.IO) {
                                    WhatsAppBridge.pair(context, pairPhone)
                                }
                                val st = withContext(Dispatchers.IO) { WhatsAppBridge.status(context) }
                                busy = false
                                statusText = when {
                                    st.pairing != null -> "Código de vinculación: ${st.pairing}"
                                    st.state == "open" -> "WhatsApp ya está vinculado"
                                    err.isNotBlank() -> "Error: $err"
                                    else -> "Solicita el código en WhatsApp ▸ Dispositivos vinculados"
                                }
                            }
                        },
                        enabled = pairPhone.isNotBlank() && url.isNotBlank() && !busy
                    ) { Text("Vincular") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusText.startsWith("Error") || statusText.startsWith("No se pudo"))
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Si el worker está en un PC, usa la IP del PC (no 127.0.0.1). " +
                        "Si está en el teléfono (Termux), usa 127.0.0.1.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            url = "http://127.0.0.1:3001"
                            saveConfig()
                            statusText = "URL local lista. Toca Probar conexión."
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Usar 127.0.0.1") }
                    OutlinedButton(
                        onClick = { showPhoneHelp = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("¿Sin PC?") }
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Bot ---
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = botEnabled,
                        onCheckedChange = {
                            botEnabled = it
                            persistAndPush(rules)
                            if (it) {
                                BotAutomation.ensurePolling(context)
                                BotAutomation.pollNow(context)
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bot activo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Responde y ejecuta las reglas automáticamente.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Enviar WhatsApp ---
            SectionCard {
                Text("Enviar WhatsApp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = sendPhone,
                    onValueChange = { sendPhone = it },
                    label = { Text("Número (con código de país)") },
                    placeholder = { Text("584241234567") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = sendText,
                    onValueChange = { sendText = it },
                    label = { Text("Mensaje") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (!openWhatsAppApp(context)) {
                                Toast.makeText(context, "WhatsApp no está instalado", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(Icons.Default.Send, contentDescription = null, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Abrir WhatsApp")
                    }
                    Button(
                        enabled = sendPhone.isNotBlank() && sendText.isNotBlank() && url.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            saveConfig() // asegura URL/token guardados antes de enviar
                            scope.launch {
                                val (ok, err) = withContext(Dispatchers.IO) {
                                    WhatsAppBridge.send(context, sendPhone, sendText)
                                }
                                statusText = if (ok) "WhatsApp enviado a $sendPhone"
                                else "No se pudo enviar: " + (err.ifBlank { "sin respuesta del worker" })
                                if (ok) sendText = ""
                            }
                        }
                    ) {
                        AppIcon(Icons.Default.Send, contentDescription = null, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Enviar")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Reglas ---
            SectionCard {
                Text("Reglas del bot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Palabra clave → serie de acciones. Sin límite de reglas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (rules.isEmpty()) {
                    Text(
                        "Aún no hay reglas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    rules.forEachIndexed { index, rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "“${rule.keyword}” · ${matchLabel(rule.match)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    actionsSummary(rule.actions),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { editingIndex = index }) {
                                AppIcon(Icons.Default.Edit, contentDescription = "Editar", size = 20.dp)
                            }
                            IconButton(onClick = {
                                val l = rules.toMutableList()
                                l.removeAt(index)
                                persistAndPush(l)
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
                Button(
                    onClick = { editingIndex = -1 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppIcon(Icons.Default.Add, contentDescription = null, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar regla")
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Automatizaciones de la app (acceso completo) ---
            SectionCard {
                Text(
                    "Automatizaciones de la app (acceso completo)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cuando el cliente escriba una palabra clave, la app puede: responder, " +
                        "cambiar Seguimiento/Status/Medio del contacto, etiquetarlo y programar " +
                        "un mensaje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (autoRules.isEmpty()) {
                    Text(
                        "Aún no hay automatizaciones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    autoRules.forEachIndexed { index, r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "“${r.keyword}”",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    autoSummary(r),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { editingAuto = index }) {
                                AppIcon(Icons.Default.Edit, contentDescription = "Editar", size = 20.dp)
                            }
                            IconButton(onClick = {
                                saveAuto(autoRules.filterIndexed { i, _ -> i != index })
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { editingAuto = -1 },
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(Icons.Default.Add, contentDescription = null, size = 18.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar")
                    }
                    OutlinedButton(
                        onClick = {
                            BotAutomation.ensurePolling(context)
                            BotAutomation.pollNow(context)
                            statusText = "Procesando mensajes entrantes…"
                        }
                    ) {
                        AppIcon(Icons.Default.Refresh, contentDescription = null, size = 18.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("Procesar")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Mensajes programados de WhatsApp (varios) ---
            SectionCard {
                Text(
                    "Mensajes programados de WhatsApp",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Programa varios mensajes. Quedan registrados en Historial ▸ Programados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = schNumbers,
                    onValueChange = { schNumbers = it },
                    label = { Text("Números (coma o línea)") },
                    placeholder = { Text("584241234567, 584149876543") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = schText,
                    onValueChange = { schText = it },
                    label = { Text("Mensaje a programar") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ScheduleControls(onAtMillis = { schAt = it })
                Spacer(Modifier.height(8.dp))
                val parsedPhones = RecipientsPrefs.parsePhones(schNumbers)
                Button(
                    enabled = parsedPhones.isNotEmpty() && schText.isNotBlank() && schAt != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val at = schAt ?: return@Button
                        if (at <= System.currentTimeMillis()) {
                            Toast.makeText(context, "Elige una fecha y hora futuras", Toast.LENGTH_LONG).show()
                        } else {
                            vm.scheduleSend(emptyList(), schText.trim(), "", 2, at, parsedPhones)
                            Toast.makeText(
                                context,
                                "Programado para ${parsedPhones.size} número(s)",
                                Toast.LENGTH_LONG
                            ).show()
                            schText = ""
                        }
                    }
                ) { Text("Programar mensaje (${parsedPhones.size})") }

                if (pendingWa.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Pendientes (${pendingWa.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    pendingWa.take(10).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sdf.format(Date(item.scheduledAt)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    item.message.take(50),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = {
                                vm.cancelScheduledSend(item)
                                Toast.makeText(context, "Cancelado", Toast.LENGTH_SHORT).show()
                            }) { Text("Cancelar") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

    if (showHelp) {
        BotHelpDialog(onDismiss = { showHelp = false })
    }

    if (showPhoneHelp) {
        val command = "pkg install -y git && git clone --depth 1 " +
            "https://github.com/SaveFail/difusion.git ~/difusion-src && " +
            "bash ~/difusion-src/wa-bridge/termux-install.sh"
        AlertDialog(
            onDismissRequest = { showPhoneHelp = false },
            title = { Text("Ejecutar en el teléfono (sin PC)") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "El worker (Node + Baileys) puede correr EN EL TELÉFONO con Termux. " +
                            "Así no necesitas PC.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    HelpItem("1) Instala Termux", "Desde F-Droid: https://f-droid.org/packages/com.termux/")
                    HelpItem("2) Abre Termux y pega este comando", command)
                    HelpItem(
                        "3) Vuelve a esta pantalla",
                        "Toca \"Usar 127.0.0.1\" y luego \"Probar conexión\"."
                    )
                    HelpItem(
                        "4) Para que siga en segundo plano",
                        "Instala Termux:Boot (F-Droid) y copia el arranque a ~/.termux/boot/."
                    )
                    HelpItem(
                        "Nota",
                        "El worker corre en el teléfono; la app se conecta a 127.0.0.1. " +
                            "Mantén Termux con permiso de batería sin restricciones."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("comando", command))
                    Toast.makeText(context, "Comando copiado", Toast.LENGTH_SHORT).show()
                }) { Text("Copiar comando") }
            },
            dismissButton = {
                TextButton(onClick = { showPhoneHelp = false }) { Text("Cerrar") }
            }
        )
    }

    val idx = editingIndex
    if (idx != null) {
        val current = if (idx >= 0 && idx < rules.size) rules[idx] else null
        RuleEditorDialog(
            initial = current,
            onDismiss = { editingIndex = null },
            onSave = { rule ->
                val l = rules.toMutableList()
                if (idx in l.indices) l[idx] = rule else l.add(rule)
                persistAndPush(l)
                editingIndex = null
            }
        )
    }

    val autoIdx = editingAuto
    if (autoIdx != null) {
        val current = if (autoIdx >= 0 && autoIdx < autoRules.size) autoRules[autoIdx] else null
        AutoRuleDialog(
            initial = current,
            onDismiss = { editingAuto = null },
            onSave = { rule ->
                val l = autoRules.toMutableList()
                if (autoIdx in l.indices) l[autoIdx] = rule else l.add(rule)
                saveAuto(l)
                editingAuto = null
            }
        )
    }
}

private fun autoSummary(r: BotAutomation.Rule): String {
    val parts = ArrayList<String>()
    if (r.reply.isNotBlank()) parts.add("responde")
    if (r.setGestion.isNotBlank()) parts.add("seguimiento=${r.setGestion}")
    if (r.setEstado.isNotBlank()) parts.add("status=${r.setEstado}")
    if (r.setMedio.isNotBlank()) parts.add("medio=${r.setMedio}")
    if (r.addTag.isNotBlank()) parts.add("etiqueta=${r.addTag}")
    if (r.scheduleMinutes > 0) parts.add("programa en ${r.scheduleMinutes} min")
    return if (parts.isEmpty()) "sin acciones" else parts.joinToString(" · ")
}

@Composable
private fun AutoRuleDialog(
    initial: BotAutomation.Rule?,
    onDismiss: () -> Unit,
    onSave: (BotAutomation.Rule) -> Unit
) {
    var keyword by remember { mutableStateOf(initial?.keyword ?: "") }
    var reply by remember { mutableStateOf(initial?.reply ?: "") }
    var setGestion by remember { mutableStateOf(initial?.setGestion ?: "") }
    var setEstado by remember { mutableStateOf(initial?.setEstado ?: "") }
    var setMedio by remember { mutableStateOf(initial?.setMedio ?: "") }
    var addTag by remember { mutableStateOf(initial?.addTag ?: "") }
    var minutes by remember { mutableStateOf((initial?.scheduleMinutes ?: 0).toString()) }
    var scheduleText by remember { mutableStateOf(initial?.scheduleText ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva automatización" else "Editar automatización") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Cuando el mensaje entrante contenga la palabra clave, se ejecutan las acciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Palabra clave") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Responder por WhatsApp (opcional)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = reply,
                    onValueChange = { reply = it },
                    label = { Text("Respuesta") },
                    placeholder = { Text("Hola {nombre}, …") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Actualizar el contacto (opcional)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = setGestion,
                    onValueChange = { setGestion = it },
                    label = { Text("Seguimiento (ej. CONTESTA)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = setEstado,
                    onValueChange = { setEstado = it },
                    label = { Text("Status (ej. PROMESA DE PAGO)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = setMedio,
                    onValueChange = { setMedio = it },
                    label = { Text("Medio (ej. WHATSAPP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = addTag,
                    onValueChange = { addTag = it },
                    label = { Text("Agregar etiqueta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Programar un mensaje (opcional)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                    label = { Text("En X minutos") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = scheduleText,
                    onValueChange = { scheduleText = it },
                    label = { Text("Texto a programar") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = keyword.isNotBlank(),
                onClick = {
                    onSave(
                        BotAutomation.Rule(
                            keyword = keyword.trim(),
                            reply = reply.trim(),
                            setGestion = setGestion.trim(),
                            setEstado = setEstado.trim(),
                            setMedio = setMedio.trim(),
                            addTag = addTag.trim(),
                            scheduleMinutes = minutes.toIntOrNull() ?: 0,
                            scheduleText = scheduleText.trim()
                        )
                    )
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun matchLabel(match: String): String = when (match) {
    "equals" -> "es igual a"
    "startsWith" -> "empieza con"
    else -> "contiene"
}

private fun actionsSummary(actions: List<WhatsAppBridge.Action>): String {
    if (actions.isEmpty()) return "(sin acciones)"
    return actions.joinToString("  →  ") { a ->
        when (a) {
            is WhatsAppBridge.Action.Reply -> "💬 ${a.text.take(30)}"
            is WhatsAppBridge.Action.Wait -> "⏱ ${a.seconds}s"
            is WhatsAppBridge.Action.Http -> "🌐 ${a.url.take(30)}"
        }
    }
}

@Composable
private fun RuleEditorDialog(
    initial: WhatsAppBridge.Rule?,
    onDismiss: () -> Unit,
    onSave: (WhatsAppBridge.Rule) -> Unit
) {
    var keyword by remember { mutableStateOf(initial?.keyword ?: "") }
    var match by remember { mutableStateOf(initial?.match ?: "contains") }
    var caseSensitive by remember { mutableStateOf(initial?.caseSensitive ?: false) }
    var stop by remember { mutableStateOf(initial?.stop ?: true) }
    val actions = remember {
        mutableStateListOf<WhatsAppBridge.Action>().apply { addAll(initial?.actions ?: emptyList()) }
    }

    var type by remember { mutableStateOf("reply") }
    var text by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("5") }
    var httpUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva regla" else "Editar regla") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Palabra clave") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Coincidencia", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("contains" to "Contiene", "equals" to "Igual", "startsWith" to "Empieza").forEach {
                        FilterChip(
                            selected = match == it.first,
                            onClick = { match = it.first },
                            label = { Text(it.second) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
                    Text("Distinguir mayúsculas")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = stop, onCheckedChange = { stop = it })
                    Text("Detener al coincidir (no evaluar más reglas)")
                }

                Spacer(Modifier.height(10.dp))
                Text("Acciones (en orden)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                if (actions.isEmpty()) {
                    Text(
                        "Agrega al menos una acción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    actions.forEachIndexed { i, a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${i + 1}. " + when (a) {
                                    is WhatsAppBridge.Action.Reply -> "Responder: ${a.text.take(40)}"
                                    is WhatsAppBridge.Action.Wait -> "Esperar ${a.seconds}s"
                                    is WhatsAppBridge.Action.Http -> "Webhook: ${a.url.take(40)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { actions.removeAt(i) }) {
                                AppIcon(
                                    Icons.Default.Delete,
                                    contentDescription = "Quitar",
                                    tint = MaterialTheme.colorScheme.error,
                                    size = 18.dp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Agregar acción", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("reply" to "Respuesta", "wait" to "Espera", "http" to "Webhook").forEach {
                        FilterChip(
                            selected = type == it.first,
                            onClick = { type = it.first },
                            label = { Text(it.second) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                when (type) {
                    "reply" -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Texto de la respuesta") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    "wait" -> OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it.filter { c -> c.isDigit() } },
                        label = { Text("Segundos de espera") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    else -> OutlinedTextField(
                        value = httpUrl,
                        onValueChange = { httpUrl = it },
                        label = { Text("URL del webhook (POST)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        when (type) {
                            "reply" -> if (text.isNotBlank()) {
                                actions.add(WhatsAppBridge.Action.Reply(text.trim())); text = ""
                            }
                            "wait" -> {
                                val s = seconds.toIntOrNull() ?: 5
                                actions.add(WhatsAppBridge.Action.Wait(s.coerceIn(1, 3600)))
                            }
                            else -> if (httpUrl.isNotBlank()) {
                                actions.add(WhatsAppBridge.Action.Http(httpUrl.trim())); httpUrl = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppIcon(Icons.Default.Add, contentDescription = null, size = 18.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Agregar acción")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = keyword.isNotBlank() && actions.isNotEmpty(),
                onClick = {
                    onSave(
                        WhatsAppBridge.Rule(
                            keyword = keyword.trim(),
                            match = match,
                            caseSensitive = caseSensitive,
                            stop = stop,
                            actions = actions.toList()
                        )
                    )
                }
            ) { Text("Guardar regla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun BotHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Cómo funciona el bot?") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                HelpItem("Conexión", "URL y token del worker (wa-bridge). Ejemplo: http://IP-DEL-PC:3001")
                HelpItem("Vincular", "Pon tu número con código de país y toca Vincular. Copia el código de 8 dígitos en WhatsApp ▸ Dispositivos vinculados.")
                HelpItem("Bot activo", "Si está apagado, solo se envían mensajes; no responde solo.")
                HelpItem("Palabra clave", "Texto que el cliente escribe. La coincidencia puede ser: contiene, es igual o empieza con.")
                HelpItem("Acciones", "Se ejecutan en orden: Respuesta (envía un texto), Espera (pausa N segundos) y Webhook (llama una URL).")
                HelpItem("Detener al coincidir", "Si está marcado, al cumplirse la regla no se evalúan las siguientes.")
                HelpItem("Plantillas", "Puedes usar {nombre} y {telefono} en las respuestas.")
                HelpItem("Enviar WhatsApp", "Escribe número (con código de país) y mensaje para enviar al instante.")
                HelpItem("Programados", "Para programar muchos mensajes usa Mensaje ▸ Redactar ▸ canal WhatsApp.")
                HelpItem("Importante", "El worker debe estar encendido y vinculado para enviar o responder.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } }
    )
}

@Composable
private fun HelpItem(title: String, text: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
