package com.difusion.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.difusion.app.service.WhatsAppBridge
import com.difusion.app.storage.WaPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bot de WhatsApp (worker Baileys open source) + reglas de auto-respuesta.
 * El worker corre en un PC/servidor; aquí solo se configura la conexión y las
 * reglas (que se envían al worker para responder en tiempo real).
 */
@Composable
fun BotScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(WaPrefs.getUrl(context)) }
    var token by remember { mutableStateOf(WaPrefs.getToken(context)) }
    var statusText by remember { mutableStateOf("Sin probar") }
    var botEnabled by remember { mutableStateOf(WaPrefs.isBotEnabled(context)) }
    var rules by remember {
        mutableStateOf(WhatsAppBridge.parseRules(WaPrefs.getRulesJson(context)))
    }
    var pairPhone by remember { mutableStateOf("") }
    var newKeyword by remember { mutableStateOf("") }
    var newReply by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun saveConfig() {
        WaPrefs.setUrl(context, url)
        WaPrefs.setToken(context, token)
    }

    fun persistAndPush() {
        WaPrefs.setBotEnabled(context, botEnabled)
        WaPrefs.setRulesJson(context, WhatsAppBridge.rulesToJson(rules))
        if (!WaPrefs.isConfigured(context)) return
        scope.launch {
            val (ok, err) = withContext(Dispatchers.IO) {
                WhatsAppBridge.pushBot(context, botEnabled, rules)
            }
            statusText = if (ok) "Bot sincronizado con el worker" else "No se pudo sincronizar: $err"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Bot de WhatsApp",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Conecta la app a un worker de WhatsApp (Baileys, open source) que corre en " +
                "un PC/servidor. Desde aquí programas el bot: responde solo por palabras " +
                "clave y atiende los chats.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        SectionCard {
            Text("Conexión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL del worker") },
                placeholder = { Text("http://192.168.1.10:3001") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token (WA_TOKEN del worker)") },
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = botEnabled,
                    onCheckedChange = {
                        botEnabled = it
                        persistAndPush()
                    }
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bot activo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Responde automáticamente según las reglas de abajo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard {
            Text("Reglas (palabra clave → respuesta)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Cuando el cliente escriba la palabra clave, el bot responde. Sin límite de reglas.",
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
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "“${rule.keyword}”",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                rule.reply,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            val l = rules.toMutableList()
                            l.removeAt(index)
                            rules = l
                            persistAndPush()
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
            OutlinedTextField(
                value = newKeyword,
                onValueChange = { newKeyword = it },
                label = { Text("Palabra clave") },
                placeholder = { Text("pago, precio, info…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = newReply,
                onValueChange = { newReply = it },
                label = { Text("Respuesta del bot") },
                placeholder = { Text("Hola {nombre}, con gusto te ayudamos…") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = {
                        val k = newKeyword.trim()
                        val r = newReply.trim()
                        if (k.isNotBlank() && r.isNotBlank()) {
                            rules = rules + WhatsAppBridge.Rule(k, r)
                            newKeyword = ""
                            newReply = ""
                            persistAndPush()
                        }
                    },
                    enabled = newKeyword.isNotBlank() && newReply.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    AppIcon(Icons.Default.Add, contentDescription = null, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar regla")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { persistAndPush() },
                    enabled = url.isNotBlank()
                ) { Text("Sincronizar bot") }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
