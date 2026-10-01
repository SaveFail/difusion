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

/** Versión vigente de los términos. Al subirla, la app los vuelve a pedir. */
const val TERMS_VERSION = 2

/**
 * Pantalla de Términos y Condiciones que se muestra la PRIMERA vez que se
 * instala/abre la app (o cuando cambia la versión de los términos). El usuario
 * debe aceptar para poder usarla.
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
            SectionCard { TermsText() }
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
                        "He leído y acepto los Términos y Condiciones y la exención de responsabilidad.",
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

/** Diálogo para consultar los términos después del primer arranque. */
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
            "Difusión — Términos y Condiciones de uso",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))

        Term(
            "1. Naturaleza de la aplicación.",
            "Difusión es una herramienta de software que facilita la gestión de " +
                "contactos y el envío de mensajes y llamadas, como MEDIO O FACILIDAD de " +
                "apoyo para comunicarse con clientes o personas que hayan otorgado su " +
                "consentimiento. La app NO presta servicios de telecomunicaciones (los " +
                "presta su operador), NO genera ni controla el contenido de los mensajes " +
                "o llamadas, y NO es responsable de dicho contenido ni de la red."
        )
        Term(
            "2. Aceptación.",
            "Al pulsar “Aceptar y continuar” el usuario declara haber leído y aceptado " +
                "estos términos y asume toda la responsabilidad por el uso de la " +
                "aplicación."
        )
        Term(
            "3. Responsabilidad exclusiva del usuario.",
            "El usuario es el ÚNICO y EXCLUSIVO responsable de:"
        )
        Bullet("Contar con el consentimiento previo, expreso e informado de las personas a contactar.")
        Bullet("Cumplir TODAS las leyes y normas aplicables en su país o región (telecomunicaciones, comunicaciones electrónicas, protección de datos personales, protección al consumidor, prácticas de cobranza, publicidad, competencia y prevención de spam, entre otras).")
        Bullet("No enviar comunicaciones no solicitadas (spam), masivas sin consentimiento, engañosas, fraudulentas, amenazantes, discriminatorias o que constituyan acoso.")
        Bullet("Identificarse en sus comunicaciones y ofrecer una forma de baja (opt-out).")
        Bullet("Avisar y obtener el consentimiento antes de grabar una llamada o reproducir un mensaje automático, conforme a la ley aplicable (algunas regiones exigen el consentimiento de TODAS las partes).")
        Bullet("Tratar los datos personales conforme a la ley (base legal, seguridad, confidencialidad y retención).")
        Bullet("No usar la aplicación para fines ilícitos.")
        Term(
            "4. Regiones y normativas aplicables.",
            "Dependiendo del país o región pueden aplicar, entre otras, normas de " +
                "protección de datos y privacidad (p. ej. RGPD en la Unión Europea, LGPD " +
                "en Brasil y leyes equivalentes en otras regiones), normas anti-spam y de " +
                "comunicaciones (p. ej. CAN-SPAM/TCPA en EE. UU., CASL en Canadá y " +
                "similares), y regulación de telecomunicaciones y de cobranza. Es " +
                "responsabilidad del usuario conocer y cumplir las que le apliquen. La " +
                "aplicación se ofrece con carácter general y NO garantiza el cumplimiento " +
                "normativo en ninguna jurisdicción."
        )
        Term(
            "5. Grabación y mensajes automáticos.",
            "El usuario se obliga a informar al interlocutor y a obtener su " +
                "consentimiento antes de grabar la llamada o de reproducir un mensaje " +
                "automático. La app solo pone a disposición la función técnica; su uso " +
                "legal es responsabilidad del usuario."
        )
        Term(
            "6. Datos personales.",
            "El usuario es el responsable del tratamiento de los datos que cargue o " +
                "gestione con la app (nombres, teléfonos, cédulas, información de deuda, " +
                "etc.). La app los procesa localmente en el dispositivo del usuario y no " +
                "los recopila ni comparte con los autores."
        )
        Term(
            "7. Sin garantías (“tal cual”).",
            "La aplicación se distribuye “TAL CUAL” y “SEGÚN DISPONIBILIDAD”, sin " +
                "garantía de ningún tipo, expresa o implícita, incluyendo " +
                "funcionamiento, idoneidad, disponibilidad, exactitud o no interrupción. " +
                "No se garantiza el envío/recepción de mensajes ni el establecimiento de " +
                "llamadas, ya que dependen de terceros (operador, red, servicios de " +
                "Google, etc.)."
        )
        Term(
            "8. Limitación de responsabilidad.",
            "En la máxima medida permitida por la ley, los autores, desarrolladores y " +
                "distribuidores NO serán responsables por daños directos, indirectos, " +
                "incidentales, especiales o consecuentes, lucro cesante, pérdida de " +
                "datos, sanciones, multas, bloqueos de línea o reclamaciones de terceros, " +
                "derivados del uso o de la imposibilidad de uso de la aplicación, aunque " +
                "se les hubiera advertido. El uso de la app es bajo cuenta y riesgo del " +
                "usuario."
        )
        Term(
            "9. Indemnización.",
            "El usuario se obliga a mantener indemnes y a defender a los autores, " +
                "desarrolladores y distribuidores frente a cualquier reclamación, " +
                "demanda, sanción o gasto (incluidos honorarios razonables) que surja del " +
                "uso que el usuario haga de la aplicación o del incumplimiento de estos " +
                "términos o de la ley."
        )
        Term(
            "10. Servicios de terceros.",
            "La app se apoya en servicios de terceros (operador de telefonía, Google " +
                "Sheets/Drive, servicios de Google, repositorios de distribución) cuyos " +
                "términos y políticas son ajenos a los autores y deben ser respetados por " +
                "el usuario."
        )
        Term(
            "11. Propiedad intelectual.",
            "La aplicación y su código se ofrecen según los términos del repositorio. " +
                "Las marcas y servicios de terceros pertenecen a sus respectivos dueños."
        )
        Term(
            "12. Cambios.",
            "Estos términos pueden actualizarse. La versión vigente se muestra en la " +
                "app y su aceptación puede volver a solicitarse."
        )
        Term(
            "13. Divisibilidad.",
            "Si alguna cláusula se declara inválida, el resto permanece vigente."
        )
        Term(
            "14. Ley aplicable.",
            "Estos términos se interpretan conforme a las leyes del domicilio de los " +
                "autores, sin perjuicio de las normas imperativas de protección al " +
                "consumidor u orden público que resulten aplicables en la jurisdicción " +
                "del usuario."
        )
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
