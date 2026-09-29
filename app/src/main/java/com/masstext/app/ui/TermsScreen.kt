package com.masstext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Texto de los términos y condiciones (versión 1). */
const val TERMS_VERSION = 1

/**
 * Pantalla de Términos y Condiciones que se muestra la PRIMERA vez que se
 * instala/abre la app. El usuario debe aceptar para poder usarla.
 */
@Composable
fun TermsScreen(onAccept: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ScreenHeader(
            title = "Términos y Condiciones",
            subtitle = "Léelos y acepta para continuar",
            icon = Icons.Default.Gavel
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionCard {
                TermsText()
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { accepted = !accepted },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "He leído y acepto los Términos y Condiciones.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                PrimaryActionButton(
                    text = "Aceptar y continuar",
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Gavel,
                    enabled = accepted
                )
            }
        }
    }
}

/** Diálogo para consultar los términos y condiciones después del primer arranque. */
@Composable
fun TermsDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Términos y Condiciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TermsText()
                }
                Spacer(Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Cerrar",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TermsText() {
    Column {
        Text(
            "LEX RECOVER — Términos y Condiciones de uso",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Term("1. Objeto.",
            "LEX RECOVER es una herramienta que facilita el envío de mensajes y la " +
                "realización de llamadas a contactos, como medio o facilidad de apoyo " +
                "para comunicarse con clientes o personas que hayan otorgado su " +
                "consentimiento.")
        Term("2. Responsabilidad del usuario.",
            "El usuario es el ÚNICO responsable del uso que dé a la aplicación y de:")
        Bullet("Contar con el consentimiento previo de las personas a contactar.")
        Bullet("Cumplir las leyes de telecomunicaciones, protección de datos, " +
            "protección al consumidor y cobranza que le apliquen.")
        Bullet("No enviar mensajes ni realizar llamadas no solicitadas (spam) ni de " +
            "forma abusiva, reiterada o acosadora.")
        Bullet("Identificarse en sus mensajes y llamadas y ofrecer una forma de no " +
            "recibir más comunicaciones (opt-out).")
        Bullet("Informar y obtener el consentimiento antes de grabar una llamada o " +
            "reproducir un mensaje automático.")
        Term("3. Grabación y datos.",
            "Si usa la grabación de llamadas o el mensaje pregrabado, el usuario se " +
                "obliga a avisar al interlocutor y obtener su consentimiento. El " +
                "usuario es responsable del tratamiento y resguardo de los datos " +
                "personales que maneje con la aplicación.")
        Term("4. Exención de responsabilidad.",
            "La aplicación se distribuye “tal cual”, como una facilidad o medio de " +
                "contacto. No se garantiza su correcto funcionamiento ni la " +
                "disponibilidad del servicio de telefonía, mensajería o internet. " +
                "Los autores y desarrolladores NO se hacen responsables por daños, " +
                "perjuicios, sanciones, pérdida de datos o cualquier inconveniente " +
                "derivado del uso o mal uso de la aplicación, ni por el contenido de " +
                "los mensajes o llamadas que realice el usuario.")
        Term("5. Aceptación.",
            "Al pulsar “Aceptar y continuar”, el usuario declara haber leído y " +
                "aceptado estos términos y asume toda la responsabilidad por el uso " +
                "de la aplicación.")
        Spacer(Modifier.height(8.dp))
        Text(
            "Versión de los términos: $TERMS_VERSION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Term(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(2.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
