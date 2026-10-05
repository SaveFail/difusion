package com.difusion.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.difusion.app.service.GmailAuth
import com.difusion.app.storage.GmailAuthPrefs
import kotlinx.coroutines.launch

internal fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Sección de inicio de sesión con Google (permiso directo a Gmail).
 * Al autorizar, la app guarda el token y desde ahí puede leer y enviar correos
 * por la API de Gmail sin intermediarios.
 */
@Composable
fun GmailSignInSection(onSignedIn: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baked = GmailAuth.hasBakedClientId()
    var clientId by rememberSaveable { mutableStateOf(GmailAuth.clientId(context)) }
    var signedIn by remember { mutableStateOf(GmailAuth.isSignedIn(context)) }
    var account by remember { mutableStateOf(GmailAuth.account(context)) }
    val activity = remember(context) { context.findActivity() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        try {
            val result = GmailAuth.resultFromIntent(context, res.data)
            scope.launch {
                GmailAuth.applyResult(context, result)
                signedIn = GmailAuth.isSignedIn(context)
                account = GmailAuth.account(context)
                Toast.makeText(
                    context,
                    "Sesión iniciada: " + account.ifBlank { "Google" },
                    Toast.LENGTH_LONG
                ).show()
                onSignedIn()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo autorizar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun signIn() {
        val act = activity
        if (act == null) {
            Toast.makeText(context, "No se pudo abrir el inicio de sesión", Toast.LENGTH_LONG).show()
            return
        }
        val id = if (baked) GmailAuth.clientId(context) else clientId.trim()
        if (id.isBlank()) {
            Toast.makeText(context, "La app aún no tiene el Client ID configurado", Toast.LENGTH_LONG).show()
            return
        }
        if (!baked) GmailAuthPrefs.setClientId(context, id)
        GmailAuth.request(act, id)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    result.pendingIntent?.let {
                        launcher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                    }
                } else {
                    scope.launch {
                        GmailAuth.applyResult(context, result)
                        signedIn = GmailAuth.isSignedIn(context)
                        account = GmailAuth.account(context)
                        Toast.makeText(context, "Sesión lista", Toast.LENGTH_SHORT).show()
                        onSignedIn()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error de Google: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Iniciar sesión con Google",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (signedIn) "Conectado como: " + account.ifBlank { "(cuenta de Google)" }
                else "Toca el botón, elige tu cuenta de Google y acepta el permiso. " +
                    "Así la app podrá ver tu bandeja y enviar correos directamente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!baked) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID de Google (solo configurador)") },
                    placeholder = { Text("xxxxxx.apps.googleusercontent.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { signIn() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (signedIn) "Reiniciar sesión" else "Iniciar sesión con Google")
                }
                if (signedIn) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        GmailAuth.signOut(context)
                        signedIn = false
                        account = ""
                        Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                    }) { Text("Salir") }
                }
            }
        }
    }
}
