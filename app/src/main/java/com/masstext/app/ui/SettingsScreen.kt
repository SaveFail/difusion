package com.masstext.app.ui

import androidx.compose.foundation.background
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
import com.masstext.app.service.VoiceMessageStore

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
    repeatMode: com.masstext.app.service.RepeatMode,
    onRepeatModeChange: (com.masstext.app.service.RepeatMode) -> Unit,
    voiceEnabled: Boolean,
    onVoiceEnabledChange: (Boolean) -> Unit,
    smsGranted: Boolean,
    onRequestSmsPermission: () -> Unit,
    onMakeDefaultSms: (() -> Unit)?,
    onRequestAllPermissions: () -> Unit,
    onRequestSpecialPermissions: () -> Unit,
    fullScreenGranted: Boolean,
    onRepairPhones: (() -> Unit)?,
    appVersion: String,
    repoVersion: String,
    updateStatus: String,
    onCheckUpdates: () -> Unit,
    onOpenAppearance: () -> Unit,
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
    onFinalizeSyncFromDrive: () -> Unit
) {
    val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
        ScreenHeader(
            title = "Ajustes",
            subtitle = "Permisos y configuración de envío",
            icon = Icons.Default.Settings
        )

        // Pestañas para ordenar la configuración por temas.
        val settingsTabs = listOf("Permisos", "Drive", "Roles", "Llamadas", "Apariencia", "Envío", "General")
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
            }

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
            if (settingsTab == 1) {

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
            PrimaryActionButton(
                text = "Sincronizar desde Drive",
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
            } // fin Drive
            if (settingsTab == 2) {

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
                        selected = repeatMode == com.masstext.app.service.RepeatMode.QUEUE,
                        onClick = { onRepeatModeChange(com.masstext.app.service.RepeatMode.QUEUE) },
                        label = { Text("Por cola") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = repeatMode == com.masstext.app.service.RepeatMode.USER,
                        onClick = { onRepeatModeChange(com.masstext.app.service.RepeatMode.USER) },
                        label = { Text("Por usuario") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    when (repeatMode) {
                        com.masstext.app.service.RepeatMode.QUEUE ->
                            "La lista completa se vuelve a llamar desde el 1º."
                        com.masstext.app.service.RepeatMode.USER ->
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
            } // fin Envío
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
            } // fin General
        }
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
    val playing by VoiceMessageStore.playing.collectAsState()
    val hasMsg by VoiceMessageStore.hasMessage.collectAsState()

    LaunchedEffect(Unit) { VoiceMessageStore.init(context) }
    DisposableEffect(Unit) {
        onDispose { VoiceMessageStore.stop() }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val ok = VoiceMessageStore.importAudio(context, uri)
            Toast.makeText(
                context,
                if (ok) "Mensaje de audio importado" else "No se pudo leer el archivo",
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
            "Graba o elige un audio que se reproducirá a los clientes automáticamente cuando respondan la llamada (una sola vez por llamada). Mientras el mensaje está activo, tu micrófono se silencia toda la llamada para que el cliente solo escuche el audio y nada del ambiente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = voiceEnabled,
                onCheckedChange = onVoiceEnabledChange,
                enabled = hasMsg
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "Usar mensaje automático",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (!hasMsg) {
                    Text(
                        "Graba o elige un audio primero",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!rec) {
            Button(
                onClick = {
                    if (micGranted) VoiceMessageStore.startRecording(context)
                    else onRequestMic()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                AppIcon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Grabar mensaje")
            }
        } else {
            Button(
                onClick = { VoiceMessageStore.stopRecording() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                AppIcon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Detener grabación")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Habla ahora. El audio quedará listo al detener.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { audioPicker.launch("audio/*") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                AppIcon(Icons.Default.AudioFile, contentDescription = null, size = 18.dp)
                Spacer(Modifier.width(4.dp))
                Text("Elegir audio")
            }
            OutlinedButton(
                onClick = {
                    if (playing) VoiceMessageStore.stopPreview()
                    else VoiceMessageStore.playPreview(context)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                enabled = hasMsg && !rec
            ) {
                AppIcon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    size = 18.dp
                )
                Spacer(Modifier.width(4.dp))
                Text(if (playing) "Detener" else "Reproducir")
            }
        }

        if (hasMsg) {
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = { VoiceMessageStore.delete(context) },
                modifier = Modifier.align(Alignment.Start)
            ) {
                AppIcon(Icons.Default.Delete, contentDescription = null, size = 16.dp)
                Spacer(Modifier.width(4.dp))
                Text("Borrar mensaje")
            }
        }
    }
}