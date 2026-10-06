package com.difusion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.difusion.app.service.VoiceMessageStore

@Composable
fun SettingsScreen(
    delaySeconds: Int,
    onDelayChange: (Long) -> Unit,
    safeMode: Boolean,
    onSafeModeChange: (Boolean) -> Unit,
    callDelaySeconds: Int,
    onCallDelayChange: (Long) -> Unit,
    ringDurationSeconds: Int,
    onRingDurationChange: (Long) -> Unit,
    maxCallSeconds: Int,
    onMaxCallChange: (Long) -> Unit,
    repeatTimes: Int,
    onRepeatTimesChange: (Int) -> Unit,
    repeatMode: com.difusion.app.service.RepeatMode,
    onRepeatModeChange: (com.difusion.app.service.RepeatMode) -> Unit,
    voiceEnabled: Boolean,
    onVoiceEnabledChange: (Boolean) -> Unit,
    smsGranted: Boolean,
    onRequestSmsPermission: () -> Unit,
    onMakeDefaultSms: (() -> Unit)?,
    onRequestAllPermissions: () -> Unit,
    onRequestSpecialPermissions: () -> Unit,
    onOpenSystemPermissions: () -> Unit,
    fullScreenGranted: Boolean,
    onRepairPhones: (() -> Unit)?,
    appVersion: String,
    repoVersion: String,
    updateStatus: String,
    onCheckUpdates: () -> Unit,
    onOpenAppearance: () -> Unit,
    onResetAppearance: () -> Unit,
    userName: String,
    onUserChange: (String) -> Unit,
    isDefaultSms: Boolean,
    isDefaultDialer: Boolean,
    onMakeDefaultMessagingAndDialer: () -> Unit,
    bluetoothPrefer: Boolean,
    onBluetoothPreferChange: (Boolean) -> Unit,
    bluetoothDevices: String,
    overlayGranted: Boolean,
    onRequestOverlay: () -> Unit,
    recordingEnabled: Boolean,
    onRecordingEnabledChange: (Boolean) -> Unit,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    driveUrl: String,
    onDriveUrlChange: (String) -> Unit,
    onScanQr: () -> Unit,
    driveSyncStatus: String,
    onSyncFromDrive: () -> Unit,
    // Hojas visibles del libro (picker) + hoja elegida + finaliza la importación.
    driveSheets: List<String>,
    driveSelectedSheetIndex: Int,
    onDriveSheetSelect: (Int) -> Unit,
    onFinalizeSyncFromDrive: () -> Unit,
    driveQuick: Boolean,
    onDriveQuickChange: (Boolean) -> Unit,
    onQuickSyncFromDrive: () -> Unit,
    // Sincronización de gestiones DE VUELTA a la hoja (escritura vía Apps Script).
    driveSyncUrl: String,
    onDriveSyncUrlChange: (String) -> Unit,
    driveSyncToken: String,
    onDriveSyncTokenChange: (String) -> Unit,
    driveSyncEnabled: Boolean,
    onDriveSyncEnabledChange: (Boolean) -> Unit,
    onTestDriveSync: () -> Unit,
    // Envío masivo de correo (Apps Script).
    emailSyncUrl: String,
    onEmailSyncUrlChange: (String) -> Unit,
    emailSyncToken: String,
    onEmailSyncTokenChange: (String) -> Unit,
    emailSyncEnabled: Boolean,
    onEmailSyncEnabledChange: (Boolean) -> Unit,
    onTestEmailConnection: () -> Unit
) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showDonate by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
        ScreenHeader(
            title = "Ajustes",
            subtitle = "Permisos y configuración de envío",
            icon = Icons.Default.Settings
        )

        // Pestañas para ordenar la configuración por temas.
        val settingsTabs = listOf("Permisos", "Actualizaciones", "Drive", "Llamadas", "Apariencia", "Envío", "General", "Correo")
        var settingsTab by remember { mutableStateOf(0) }
        ScrollableTabRow(
            selectedTabIndex = settingsTab,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            settingsTabs.forEachIndexed { index, title ->
                Tab(
                    selected = settingsTab == index,
                    onClick = { settingsTab = index },
                    text = { Text(title, maxLines = 1, fontSize = 13.sp) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (settingsTab == 0) {
            SectionCard {
                Text(
                    "Permisos de la app",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Concede de una vez todos los permisos normales (SMS, llamadas, estado del teléfono, micrófono y notificaciones) y luego los permisos especiales (mostrar sobre otras apps y pantalla completa para las llamadas).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Dar todos los permisos",
                    onClick = onRequestAllPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.CheckCircle
                )
                Spacer(modifier = Modifier.height(8.dp))
                SecondaryActionButton(
                    text = if (overlayGranted && fullScreenGranted) {
                        "Permisos especiales activados"
                    } else {
                        "Dar permisos especiales"
                    },
                    onClick = onRequestSpecialPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Layers
                )
                Spacer(modifier = Modifier.height(8.dp))
                SecondaryActionButton(
                    text = "Abrir permisos del sistema (restringidos)",
                    onClick = onOpenSystemPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Settings
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Si el equipo bloquea permisos (p. ej. \"Permisos restringidos\" en MIUI o \"Permitir ajustes restringidos\" en Android 13+), este botón abre la pantalla donde se activan. Android no permite activarlos desde la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            } // fin Permisos (parte 1)
            if (settingsTab == 1) {

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "Actualizaciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Versión instalada: $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (repoVersion.isNotBlank())
                        "Última versión en el repositorio: $repoVersion"
                    else
                        "Toca \"Buscar actualizaciones\" para ver la última versión publicada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (updateStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        updateStatus,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Buscar actualizaciones",
                    onClick = onCheckUpdates,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.SystemUpdate
                )
            }
            } // fin Actualizaciones
            if (settingsTab == 0) {

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = if (smsGranted) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(
                            if (smsGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (smsGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (smsGranted) "Permiso de SMS activado" else "Falta el permiso para enviar SMS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (smsGranted) {
                            "La app puede enviar mensajes directamente, sin ser la app de SMS predeterminada."
                        } else {
                            "Toca el botón para pedir el permiso. Si tu teléfono pide 'Permisos restringidos', actívalo y vuelve a tocar el botón."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!smsGranted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PrimaryActionButton(
                            text = "Permitir enviar SMS",
                            onClick = onRequestSmsPermission,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.Sms
                        )
                        if (onMakeDefaultSms != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SecondaryActionButton(
                                text = "O convertir en app de SMS predeterminada",
                                onClick = onMakeDefaultSms,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Default.Sms
                            )
                        }
                    }
                }
            }
            } // fin Permisos
            if (settingsTab == 2) {

        Spacer(modifier = Modifier.height(16.dp))

        SectionCard {
            Text(
                "Contactos a llamar desde Google Drive",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Crea en Google Sheets una hoja con columnas: Nombre, Cédula, Teléfono, " +
                    "\"Asignado a\" (o EJECUTIVO), y las categorías \"SEGUIMIENTO\" " +
                    "(tipificación), \"STATUS\" (estado) y \"MEDIO DE CONTACTO\". Luego " +
                    "compártela desde Drive con \"Cualquier persona con el enlace → Lector\" " +
                    "y pega aquí el enlace.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = driveUrl,
                onValueChange = onDriveUrlChange,
                label = { Text("Enlace de tu hoja (usuario: Lector)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingIcon = {
                    Icon(Icons.Default.Cloud, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = onScanQr) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Escanear código QR del Drive"
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Toca el ícono de QR para abrir la cámara y escanear el código que te da Drive en el navegador.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Al sincronizar se IMPORTAN los contactos de la hoja que elijas cuyo " +
                    "\"Asignado a\" coincida con tu Nombre de usuario (sin tildes ni mayúsculas). " +
                    "Si la hoja no tiene esa columna, se importan todos. Se descartan duplicados " +
                    "por cédula y esa lista REEMPLAZA la lista de contactos a llamar.\n\n" +
                    "Las columnas de categorías se detectan por su nombre: \"SEGUIMIENTO\" o " +
                    "\"TIPIFICACIÓN\" (ej. NO CONTESTA), \"STATUS\" o \"ESTADO\" (ej. PROMESA DE " +
                    "PAGO) y \"MEDIO DE CONTACTO\" (ej. WHATSAPP). En Mis Contactos verás un " +
                    "filtro por cada una con los nombres reales de la hoja; los vacíos aparecen " +
                    "como \"Sin gestionar\", \"Sin estado\" o \"Sin medio\", y al elegir una " +
                    "categoría \"Todos\" selecciona solo los visibles.\n\n" +
                    "Antes de reemplazar la lista, se abre una VENTANA FLOTANTE de revisión: " +
                    "arriba los filtros de cada categoría y abajo las filas con todas las " +
                    "columnas y una casilla (todas marcadas). Marca lo que quieras e \"Importar\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = driveQuick,
                    onCheckedChange = onDriveQuickChange
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sincronización rápida (1 toque, sin ventana de revisión)")
            }
            Spacer(modifier = Modifier.height(6.dp))
            PrimaryActionButton(
                text = "Sincronizar rápido (1 toque)",
                onClick = onQuickSyncFromDrive,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Cloud
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Usa el enlace guardado y la última hoja. Ideal cuando siempre importas la misma hoja.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryActionButton(
                text = "Sincronizar desde Drive (elegir hoja)",
                onClick = onSyncFromDrive,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Cloud
            )
            if (driveSyncStatus.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    driveSyncStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Picker de hojas (ventana flotante): se muestra SOLO cuando el libro
            // tiene varias pestañas. El usuario elige una y pulsa "Finalizar
            // sincronización" para importar SOLO esa hoja.
        }

    Spacer(modifier = Modifier.height(16.dp))

        SectionCard {
            Text(
                "Contactos de la lista a llamar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Es la persona que realiza los procesos. Este nombre queda registrado en cada envío masivo, cada llamada y cada mensaje como el responsable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                var userText by remember { mutableStateOf(userName) }
                OutlinedTextField(
                    value = userText,
                    onValueChange = { userText = it },
                    label = { Text("Nombre del usuario") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Guardar usuario",
                    onClick = {
                        val n = userText.trim()
                        if (n.isEmpty()) {
                            Toast.makeText(context, "Escribe el nombre del usuario.", Toast.LENGTH_LONG).show()
                        } else {
                            onUserChange(n)
                            Toast.makeText(context, "Usuario guardado: $n. Quedará registrado en cada envío, llamada y mensaje.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Person
                )
            }

        Spacer(modifier = Modifier.height(16.dp))

        SectionCard {
            Text(
                "Sincronizar gestiones a la hoja (escritura)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Al llamar, enviar SMS o guardar una gestión, los cambios (SEGUIMIENTO, " +
                    "STATUS, MEDIO DE CONTACTO y FECHA DE GESTION) se escriben de vuelta en la " +
                    "hoja. Se usa un Web App de Google Apps Script (DriveSyncAppsScript.gs). " +
                    "Cada fila se identifica por ID CUOTA o Cédula+Teléfono, así varias " +
                    "personas pueden trabajar a la vez sin interferir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = driveSyncUrl,
                onValueChange = onDriveSyncUrlChange,
                label = { Text("URL del Web App (Apps Script)") },
                placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = driveSyncToken,
                onValueChange = onDriveSyncTokenChange,
                label = { Text("Token (opcional)") },
                placeholder = { Text("Solo si configuraste API_TOKEN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = driveSyncEnabled,
                    onCheckedChange = onDriveSyncEnabledChange
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Activar sincronización automática a Drive")
            }
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryActionButton(
                text = "Probar conexión",
                onClick = onTestDriveSync,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Cloud
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Con solo pegar la URL ya queda activa. Cada tipificación, status, " +
                    "cambio de medio y fecha de gestión se escriben solos en la hoja.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionCard {
            Text(
                "Correo (Gmail)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "La configuración y el paso a paso para conectar tu Gmail están en la " +
                    "pestaña \"Correo\" de Ajustes (tap arriba). Ese mismo correo se usa " +
                    "para el envío masivo y programado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
            } // fin Drive
            if (settingsTab == 0) {

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "Mensajes y llamadas predeterminadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Convierte esta app en la app de Mensajes (SMS) y de Llamadas predeterminada a la vez. Así podrás enviar y recibir SMS, y ver la pantalla completa de la llamada con sus controles durante las campañas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDefaultSms && isDefaultDialer) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = if (isDefaultSms && isDefaultDialer) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 18.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isDefaultSms) "Mensajes: predeterminada"
                                else "Mensajes: no predeterminada",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDefaultSms) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (isDefaultDialer) "Llamadas: predeterminada"
                                else "Llamadas: no predeterminada",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDefaultDialer) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Siempre permite volver a establecerlas como predeterminadas
                // (aunque ya lo sean), para que el botón no cambie de función.
                PrimaryActionButton(
                    text = "Hacer predeterminadas Mensajes y Llamadas",
                    onClick = onMakeDefaultMessagingAndDialer,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Phone
                )
            }
            } // fin Roles
            if (settingsTab == 3) {

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "Llamadas Bluetooth",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Si hay un dispositivo de audio Bluetooth conectado (audífonos, manos libres, bocina), las llamadas se hacen por ese medio por defecto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (bluetoothDevices.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            size = 20.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (bluetoothDevices.isBlank()) "Sin dispositivos Bluetooth conectados"
                            else "Conectado: $bluetoothDevices",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Usar Bluetooth por defecto",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = bluetoothPrefer,
                        onCheckedChange = onBluetoothPreferChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "Barra de llamada flotante",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Mientras haya una llamada en curso, muestra una barra en la parte superior sobre cualquier app para volver a la llamada, alternar el altavoz o colgar sin salir de lo que estés haciendo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (overlayGranted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (overlayGranted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 20.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (overlayGranted) "Activada: aparece sobre cualquier app"
                            else "Sin permiso de superposición",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (overlayGranted) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryActionButton(
                    text = if (overlayGranted) "Abrir opciones de superposición"
                    else "Activar (mostrar sobre otras apps)",
                    onClick = { onRequestOverlay() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Layers
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Grabación de llamadas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Cuando la llamada se conecta, empieza a grabarse automáticamente en formato .amr en Descargas/llamadas de troncal (una carpeta por día).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = recordingEnabled,
                        onCheckedChange = onRecordingEnabledChange
                    )
                }

                if (recordingEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (!micGranted) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(
                                    Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    size = 20.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Sin permiso de micrófono",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        PrimaryActionButton(
                            text = "Conceder permiso de micrófono",
                            onClick = onRequestMic,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.Mic
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    size = 20.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Activado: se grabarán las llamadas conectadas",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "¿Teléfono no habilita el permiso?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "En algunos equipos (Huawei/Honor, ej. Honor Play 10) la opción de SMS está deshabilitada para apps instaladas por APK. En ese caso usa la app de SMS predeterminada: Android otorga el permiso de envío automáticamente. Luego puedes devolver tu app de mensajes a predeterminada cuando quieras.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            } // fin Llamadas
            if (settingsTab == 4) {

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "Apariencia y distribución",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Abre un menú aparte para cambiar el color de la app, de las letras, el fondo y el tipo de fuente, con vista previa de cómo se distribuye la pantalla. Evita cambios directos aquí para no alterar la vista de Ajustes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Personalizar colores y apariencia",
                    onClick = onOpenAppearance,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Palette
                )
            }
            } // fin Apariencia
            if (settingsTab == 5) {

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Modo de envío",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (safeMode) "MODO SEGURO" else "MODO DESATENDIDO",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (safeMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = safeMode,
                        onCheckedChange = onSafeModeChange
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (safeMode) {
                        "Envía por bloques de 70-90 mensajes y descansa 5 minutos entre bloques (usa el contador). Recomendado para no bloquear tu línea."
                    } else {
                        "Envía todo consecutivo, sin bloques ni contador. Solo respeta la pausa entre mensajes. Úsalo solo si tu operador lo permite."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "Pausa entre mensajes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Recomendado: 2-5 segundos para evitar bloqueos del operador.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                var delayText by remember { mutableStateOf(delaySeconds.toString()) }
                OutlinedTextField(
                    value = delayText,
                    onValueChange = { delayText = it.filter { c -> c.isDigit() } },
                    label = { Text("Segundos") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Guardar",
                    onClick = {
                        val seconds = delayText.toLongOrNull()
                        if (seconds == null) {
                            Toast.makeText(context, "Escribe un número válido de segundos.", Toast.LENGTH_LONG).show()
                        } else if (seconds < 0L) {
                            Toast.makeText(context, "El intervalo no puede ser negativo.", Toast.LENGTH_LONG).show()
                        } else if (seconds > 60L) {
                            Toast.makeText(context, "El intervalo supera el tiempo permitido para este proceso (máximo 60 segundos).", Toast.LENGTH_LONG).show()
                        } else {
                            onDelayChange(seconds * 1000)
                            Toast.makeText(context, "Pausa entre mensajes guardada: $seconds segundos.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Save
                )
            }
            } // fin Envío
            if (settingsTab == 3) {

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "Pausa entre llamadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tiempo de espera entre llamadas consecutivas de la secuencia. Máximo 60 segundos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                var callDelayText by remember { mutableStateOf(callDelaySeconds.toString()) }
                OutlinedTextField(
                    value = callDelayText,
                    onValueChange = { callDelayText = it.filter { c -> c.isDigit() } },
                    label = { Text("Segundos (0-60)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Guardar pausa de llamadas",
                    onClick = {
                        val seconds = callDelayText.toLongOrNull()
                        if (seconds == null) {
                            Toast.makeText(context, "Escribe un número válido de segundos.", Toast.LENGTH_LONG).show()
                        } else if (seconds < 0L) {
                            Toast.makeText(context, "El intervalo no puede ser negativo.", Toast.LENGTH_LONG).show()
                        } else if (seconds > 60L) {
                            Toast.makeText(context, "Supera el tiempo permitido para este proceso (máximo 60 segundos).", Toast.LENGTH_LONG).show()
                        } else {
                            onCallDelayChange(seconds * 1000)
                            Toast.makeText(context, "Pausa entre llamadas guardada: $seconds segundos.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Save
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "Espera de timbre",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Cuánto espera la secuencia sonando antes de dar por 'sin respuesta' y pasar al siguiente cliente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                var ringText by remember { mutableStateOf(ringDurationSeconds.toString()) }
                OutlinedTextField(
                    value = ringText,
                    onValueChange = { ringText = it.filter { c -> c.isDigit() } },
                    label = { Text("Segundos (0-300)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Guardar espera de timbre",
                    onClick = {
                        val seconds = ringText.toLongOrNull()
                        if (seconds == null) {
                            Toast.makeText(context, "Escribe un número válido de segundos.", Toast.LENGTH_LONG).show()
                        } else if (seconds < 0L) {
                            Toast.makeText(context, "El valor no puede ser negativo.", Toast.LENGTH_LONG).show()
                        } else if (seconds > 300L) {
                            Toast.makeText(context, "Máximo 300 segundos.", Toast.LENGTH_LONG).show()
                        } else {
                            onRingDurationChange(seconds * 1000)
                            Toast.makeText(context, "Espera de timbre guardada: $seconds segundos.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Save
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "Límite de llamada contestada",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Si el cliente mantiene la llamada más de este tiempo, la secuencia cuelga y pasa al siguiente. Escribe 0 para sin límite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                var maxCallText by remember { mutableStateOf(maxCallSeconds.toString()) }
                OutlinedTextField(
                    value = maxCallText,
                    onValueChange = { maxCallText = it.filter { c -> c.isDigit() } },
                    label = { Text("Segundos (0 = sin límite)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Guardar límite de llamada",
                    onClick = {
                        val seconds = maxCallText.toLongOrNull()
                        if (seconds == null) {
                            Toast.makeText(context, "Escribe un número válido de segundos.", Toast.LENGTH_LONG).show()
                        } else if (seconds < 0L) {
                            Toast.makeText(context, "El valor no puede ser negativo.", Toast.LENGTH_LONG).show()
                        } else {
                            onMaxCallChange(seconds * 1000)
                            Toast.makeText(
                                context,
                                if (seconds == 0L) "Sin límite de duración guardado."
                                else "Límite de llamada guardado: $seconds segundos.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Save
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard {
                Text(
                    "Repetición de llamadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Cuántas veces se repite la secuencia (1-10). 'Por cola' termina la ronda completa y vuelve desde el principio; 'Por usuario' repite cada número antes de pasar al siguiente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { onRepeatTimesChange((repeatTimes - 1).coerceAtLeast(1)) },
                        enabled = repeatTimes > 1
                    ) {
                        AppIcon(Icons.Default.Remove, contentDescription = "Disminuir", size = 20.dp)
                    }
                    Text(
                        "$repeatTimes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.width(56.dp)
                    )
                    FilledTonalIconButton(
                        onClick = { onRepeatTimesChange((repeatTimes + 1).coerceAtMost(10)) },
                        enabled = repeatTimes < 10
                    ) {
                        AppIcon(Icons.Default.Add, contentDescription = "Aumentar", size = 20.dp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (repeatTimes == 1) {
                        Text(
                            "Una sola vuelta",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "${repeatTimes} llamadas a cada cliente",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = repeatMode == com.difusion.app.service.RepeatMode.QUEUE,
                        onClick = { onRepeatModeChange(com.difusion.app.service.RepeatMode.QUEUE) },
                        label = { Text("Por cola") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = repeatMode == com.difusion.app.service.RepeatMode.USER,
                        onClick = { onRepeatModeChange(com.difusion.app.service.RepeatMode.USER) },
                        label = { Text("Por usuario") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    when (repeatMode) {
                        com.difusion.app.service.RepeatMode.QUEUE ->
                            "La lista completa se vuelve a llamar desde el 1º."
                        com.difusion.app.service.RepeatMode.USER ->
                            "Cada cliente se llama $repeatTimes veces seguidas."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CallVoiceMessageCard(
                voiceEnabled = voiceEnabled,
                onVoiceEnabledChange = onVoiceEnabledChange,
                micGranted = micGranted,
                onRequestMic = onRequestMic
            )
            } // fin Llamadas (retardos y mensaje)
            if (settingsTab == 6) {

            if (onRepairPhones != null) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionCard {
                    Text(
                        "Corregir números de teléfono",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Si algunos números cargados desde Excel quedaron sin el 0 inicial (4122873438 en vez de 04122873438), toca el botón para corregirlos automáticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrimaryActionButton(
                        text = "Corregir ahora (recargar)",
                        onClick = onRepairPhones,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Refresh
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionCard {
                Text(
                    "Restablecer apariencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Devuelve los colores, la fuente, el espaciado y las burbujas del chat a los valores por defecto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryActionButton(
                    text = "Restablecer apariencia",
                    onClick = onResetAppearance,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.RestartAlt
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionCard {
                Text(
                    "Acerca de Difusión",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "App de SMS y llamadas masivas para gestión de cobranza: envía mensajes y campañas de llamadas a tu lista de contactos, sincronizada desde Google Sheets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Versión instalada: $appVersion" +
                        if (repoVersion.isNotBlank()) " · Última en el repositorio: $repoVersion" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryActionButton(
                        text = "Ver descripción",
                        onClick = { showAbout = true },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Info
                    )
                    SecondaryActionButton(
                        text = "Personalizar",
                        onClick = onOpenAppearance,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Palette
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                SecondaryActionButton(
                    text = "Ver términos y condiciones",
                    onClick = { showTerms = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Gavel
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryActionButton(
                    text = "Apoyar el proyecto",
                    onClick = { showDonate = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Favorite
                )
            }
            } // fin General
            if (settingsTab == 7) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Opción sin Google Cloud (recomendada): Gmail con contraseña de aplicación",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                SmtpConfigCard(onSaved = {
                    Toast.makeText(
                        context,
                        "Correo configurado. Ve a la pestaña Mensaje ▸ Correo para ver la bandeja.",
                        Toast.LENGTH_LONG
                    ).show()
                })

                Spacer(modifier = Modifier.height(20.dp))

                GmailSignInSection()

                Spacer(modifier = Modifier.height(16.dp))

                SectionCard {
                    Text(
                        "Iniciar sesión con Google — paso a paso",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "USUARIO FINAL (lo que hace cada persona): abrir Mensaje ▸ Correo, " +
                            "tocar \"Iniciar sesión con Google\" y aceptar. Nada más.\n\n" +
                            "CONFIGURADOR (solo tú, UNA vez): los pasos de abajo. Se hace una " +
                            "sola vez y queda para todos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Solo el configurador (una vez):\n" +
                            "1. Entra a console.cloud.google.com y crea un proyecto.\n" +
                            "2. Configura la pantalla de consentimiento (tipo \"Externo\") y " +
                            "agrega tu correo como \"usuario de prueba\".\n" +
                            "3. En Credenciales, crea un ID de cliente OAuth tipo " +
                            "\"Android\" con:\n" +
                            "     • Nombre del paquete: com.difusion.app\n" +
                            "     • SHA-1: C0:F2:7D:36:C0:2D:94:A4:AB:06:26:55:55:83:F7:27:" +
                            "F4:F3:D4:BE\n" +
                            "   (y otro igual con el SHA-1 de depuración si pruebas en USB: " +
                            "A4:8C:EA:70:00:BF:50:9A:73:12:45:B3:2C:B6:1F:87:DA:8E:23:92)\n" +
                            "4. Crea además un ID de cliente OAuth tipo \"Aplicación web\" " +
                            "(no hace falta configurar URLs).\n" +
                            "5. Copia el Client ID de tipo \"Aplicación web\".\n" +
                            "6. Pégalo arriba (\"Client ID de Google\") y toca " +
                            "\"Iniciar sesión con Google\".\n" +
                            "7. Elige tu cuenta y acepta el permiso de Gmail.\n" +
                            "8. Listo: verás \"Conectado como: tu correo\".",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Nota: los permisos de Gmail son \"restringidos\"; en modo Prueba " +
                            "funcionan con tus usuarios de prueba, pero el token dura ~1 hora y " +
                            "se renueva al volver a tocar el botón. Para distribución pública " +
                            "Google pide verificación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Importante: para el envío masivo/programado, los contactos deben " +
                            "tener correo. Agrega una columna \"CORREO\" o \"EMAIL\" en tu hoja " +
                            "(se detecta al importar) o escríbelo al crear el contacto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionCard {
                    Text(
                        "Respaldo con Apps Script (opcional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emailSyncUrl,
                        onValueChange = onEmailSyncUrlChange,
                        label = { Text("URL del Web App (Gmail)") },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emailSyncToken,
                        onValueChange = onEmailSyncTokenChange,
                        label = { Text("Token (el mismo del script)") },
                        placeholder = { Text("El API_TOKEN que pusiste en el script") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = emailSyncEnabled,
                            onCheckedChange = onEmailSyncEnabledChange
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Activar correo (ver bandeja y envío masivo)")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    PrimaryActionButton(
                        text = "Probar conexión",
                        onClick = onTestEmailConnection,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Email
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Al probar, la app consulta tu Gmail y te dice con qué cuenta quedó " +
                            "conectada. Si falla, revisa la URL (/exec) y el token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            } // fin Correo
            } // fin contenido de pestaña
        }

        if (showAbout) {
            AboutDialog(
                appVersion = appVersion,
                repoVersion = repoVersion,
                onDismiss = { showAbout = false }
            )
        }

        if (showTerms) {
            TermsDialog(onDismiss = { showTerms = false })
        }

        if (showDonate) {
            DonateDialog(onDismiss = { showDonate = false })
        }
    }

@Composable
private fun AboutDialog(appVersion: String, repoVersion: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Difusión") },
        text = {
            Column {
                Text(
                    "App de SMS y llamadas masivas para gestión de cobranza.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• Importa contactos desde Excel/CSV o Google Sheets.\n" +
                        "• Envía SMS masivos con plantillas y variables ({nombre}, {telefono}).\n" +
                        "• Realiza campañas de llamadas con grabación y mensaje pregrabado.\n" +
                        "• Organiza los contactos por categorías de gestión (tipificación, estado, medio).\n" +
                        "• Funciona como app de SMS y de llamadas predeterminada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Versión instalada: $appVersion" +
                        if (repoVersion.isNotBlank()) " · Repositorio: $repoVersion" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
@Composable
private fun CallVoiceMessageCard(
    voiceEnabled: Boolean,
    onVoiceEnabledChange: (Boolean) -> Unit,
    micGranted: Boolean,
    onRequestMic: () -> Unit
) {
    val context = LocalContext.current
    val rec by VoiceMessageStore.recording.collectAsState()
    val previewing by VoiceMessageStore.previewing.collectAsState()
    val revision by VoiceMessageStore.revision.collectAsState()
    val messages = remember(revision) { VoiceMessageStore.list(context) }
    val selected = remember(revision) { VoiceMessageStore.selected(context) }
    val hasMsg = messages.isNotEmpty()
    // Estado local para que el interruptor responda al instante.
    var enabled by remember { mutableStateOf(voiceEnabled) }

    DisposableEffect(Unit) {
        onDispose { VoiceMessageStore.stop() }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val f = VoiceMessageStore.importAudio(context, uri)
            Toast.makeText(
                context,
                if (f != null) "Audio agregado a la lista" else "No se pudo leer el archivo",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    SectionCard {
        Text(
            "Mensaje automático al contestar",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Graba o importa uno o varios audios. El SELECCIONADO se reproduce " +
                "automáticamente cuando el cliente contesta la llamada y, al terminar " +
                "el audio, la llamada se cuelga sola. Mientras suena, la app activa el " +
                "altavoz para que el interlocutor lo escuche.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    onVoiceEnabledChange(it)
                    VoiceMessageStore.setEnabled(context, it)
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "Usar mensaje automático",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (hasMsg) "Activado: al contestar suena y luego cuelga."
                    else "Aviso: aún no hay audio. Graba o importa uno abajo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasMsg) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!rec) {
            Button(
                onClick = {
                    if (!micGranted) {
                        onRequestMic()
                    } else {
                        VoiceMessageStore.startRecording(context)
                        if (!VoiceMessageStore.recording.value) {
                            Toast.makeText(
                                context,
                                "No se pudo iniciar la grabación. Revisa el permiso de micrófono.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                AppIcon(Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Grabar nuevo mensaje")
            }
        } else {
            Button(
                onClick = { VoiceMessageStore.stopRecording(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                AppIcon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Detener grabación")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Habla ahora. El audio se guardará y quedará seleccionado.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { audioPicker.launch("audio/*") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            AppIcon(Icons.Default.AudioFile, contentDescription = null, size = 18.dp)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Importar audio")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (messages.isEmpty()) {
            Text(
                "Aún no hay mensajes. Graba o importa uno.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Mensajes (toca para elegir el que se usará):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            messages.forEach { f ->
                val isSel = selected?.absolutePath == f.absolutePath
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { VoiceMessageStore.setSelected(context, f) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSel,
                            onClick = { VoiceMessageStore.setSelected(context, f) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                f.nameWithoutExtension,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                "${f.length() / 1024} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                if (previewing == f.name) VoiceMessageStore.stopPreview()
                                else VoiceMessageStore.playPreview(context, f)
                            }
                        ) {
                            Icon(
                                if (previewing == f.name) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Reproducir"
                            )
                        }
                        IconButton(onClick = {
                            if (previewing == f.name) VoiceMessageStore.stopPreview()
                            VoiceMessageStore.delete(context, f)
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Borrar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
