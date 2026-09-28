package com.masstext.app.ui

import android.content.Context
import android.media.RingtoneManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import com.masstext.app.service.AppFeedback
import com.masstext.app.service.NotificationTones
import com.masstext.app.ui.theme.ThemeConfig
import com.masstext.app.ui.theme.THEME_PRESETS
import com.masstext.app.ui.theme.ThemePreset
import com.masstext.app.ui.theme.cornerLabel
import com.masstext.app.ui.theme.applyPreset
import com.masstext.app.ui.theme.fontNameLabel
import com.masstext.app.ui.theme.presetLabel
import com.masstext.app.ui.theme.spacingLabel
import kotlin.math.abs

val accentOptions: List<Pair<String, Long>> = listOf(
    "Violeta" to 0xFF7C3AED,
    "Azul" to 0xFF3F6ABE,
    "Verde" to 0xFF2E7D32,
    "Turquesa" to 0xFF00897B,
    "Morado" to 0xFF7B1FA2,
    "Naranja" to 0xFFEF6C00,
    "Rojo" to 0xFFC62828,
    "Rosa" to 0xFFAD1457,
    "Gris" to 0xFF37474F
)

val textOptions: List<Pair<String, Long>> = listOf(
    "Oscuro" to 0xFF1B1B22,
    "Negro" to 0xFF000000,
    "Azul noche" to 0xFF0D1B3E,
    "Blanco" to 0xFFFFFFFF,
    "Gris claro" to 0xFFE0E0E0
)

val bgOptions: List<Pair<String, Long>> = listOf(
    "Blanco" to 0xFFF9F9FF,
    "Blanco puro" to 0xFFFFFFFF,
    "Crema" to 0xFFFFF8E1,
    "Gris claro" to 0xFFECEFF1,
    "Azul claro" to 0xFFE3F2FD,
    "Gris oscuro" to 0xFF263238,
    "Negro" to 0xFF121212
)

// Colores para el estado de los mensajes en el chat.
val statusOptions: List<Pair<String, Long>> = listOf(
    "Verde" to 0xFF2E7D32,
    "Rojo" to 0xFFC62828,
    "Ámbar" to 0xFFF9A825,
    "Azul" to 0xFF1565C0,
    "Morado" to 0xFF6A1B9A,
    "Naranja" to 0xFFEF6C00,
    "Gris" to 0xFF546E7A,
    "Negro" to 0xFF000000,
    "Blanco" to 0xFFFFFFFF
)

private val textScaleOptions = listOf(
    "Pequeñas" to 0.85f,
    "Normal" to 1f,
    "Grandes" to 1.2f,
    "Extra" to 1.4f
)

private val iconScaleOptions = listOf(
    "Pequeños" to 0.8f,
    "Normal" to 1f,
    "Grandes" to 1.25f
)

private val cornerOptions = listOf("pill", "rounded", "sharp")
private val spacingOptions = listOf("compact", "comfortable", "spacious")

private fun near(a: Float, b: Float): Boolean = abs(a - b) < 0.01f

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ToggleRow(
    label: String,
    helper: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (helper != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AppearanceScreen(
    config: ThemeConfig,
    onConfigChange: (ThemeConfig) -> Unit,
    onBack: () -> Unit,
    onPickFont: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit
) {
    val context = LocalContext.current
    var showTonePicker by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        AppIcon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver a Ajustes",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Apariencia y colores",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Pestañas para dividir la personalización y evitar confusiones.
            val tabs = listOf("Tema", "Marca", "Colores", "Texto", "Fuente", "Sonido", "Vista", "Guardar")
            var tab by remember { mutableStateOf(0) }
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
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
                if (tab == 0) {

                SectionLabel("Temas preestablecidos")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    THEME_PRESETS.forEach { preset ->
                        PresetCard(
                            preset = preset,
                            selected = config.preset == preset.id,
                            onSelected = { onConfigChange(applyPreset(config, preset.id)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tema actual: ${presetLabel(config)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                SectionCard {
                    ToggleRow(
                        label = "Modo oscuro",
                        helper = "Colores definidos para pantallas oscuras, sin aplicar el modo del sistema.",
                        checked = config.darkMode,
                        onCheckedChange = { onConfigChange(config.copy(darkMode = it, preset = "")) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ToggleRow(
                        label = "Gradiente en botones y cabeceras",
                        checked = config.useGradient,
                        onCheckedChange = { onConfigChange(config.copy(useGradient = it, preset = "")) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                SectionCard {
                    Text("Forma de las esquinas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cornerOptions.forEach { c ->
                            FilterChip(
                                selected = config.cornerStyle == c,
                                onClick = { onConfigChange(config.copy(cornerStyle = c, preset = "")) },
                                label = { Text(cornerLabel(c), fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Espaciado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        spacingOptions.forEach { s ->
                            FilterChip(
                                selected = config.spacing == s,
                                onClick = { onConfigChange(config.copy(spacing = s, preset = "")) },
                                label = { Text(spacingLabel(s), fontSize = 12.sp) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                SectionCard {
                    ToggleRow(
                        label = "Colorear barra de estado",
                        helper = "La barra superior del sistema usa el color de la app.",
                        checked = config.statusBarTint,
                        onCheckedChange = { onConfigChange(config.copy(statusBarTint = it)) }
                    )
                }
                } // fin Tema
                if (tab == 1) {
                    MarcaTab(config = config, onConfigChange = onConfigChange)
                }
                if (tab == 5) {

                SectionCard {
                    Text("Sonido y avisos del teléfono", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Elige el timbre del sistema que avisará al llegar un mensaje, al terminar un envío y al terminar la secuencia de llamadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ToggleRow(
                        label = "Sonido de notificación",
                        checked = config.soundEnabled,
                        onCheckedChange = { onConfigChange(config.copy(soundEnabled = it)) }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ToggleRow(
                        label = "Vibración",
                        helper = "Ya implementada: hace vibrar el teléfono (sujeto a la vibración activa del equipo).",
                        checked = config.vibrationEnabled,
                        onCheckedChange = { onConfigChange(config.copy(vibrationEnabled = it)) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Timbre actual: ${currentToneLabel(config, context)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryActionButton(
                            text = "Escoger timbre",
                            onClick = { showTonePicker = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Notifications
                        )
                        SecondaryActionButton(
                            text = "Probar",
                            onClick = {
                                val ctx = context
                                AppFeedback.notify(ctx, config.copy(soundEnabled = true, vibrationEnabled = true))
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.VolumeUp
                        )
                    }
                }
                if (showTonePicker) {
                    TonePickerDialog(
                        currentUri = config.notificationSound,
                        onSelect = { uri ->
                            onConfigChange(config.copy(notificationSound = uri, soundEnabled = true))
                            showTonePicker = false
                        },
                        onDismiss = { showTonePicker = false }
                    )
                }
                } // fin Sonido
                if (tab == 6) {

                Text(
                    "Vista previa de la pantalla",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Así se verá la distribución de la app con los colores, tamaño de letras e iconos elegidos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreviewCard(config)
                } // fin Vista
                if (tab == 3) {

                SectionLabel("Tamaño de las letras")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    textScaleOptions.forEach { (label, v) ->
                        FilterChip(
                            selected = near(config.textScale, v),
                            onClick = { onConfigChange(config.copy(textScale = v)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                SectionLabel("Tamaño de los iconos")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    iconScaleOptions.forEach { (label, v) ->
                        FilterChip(
                            selected = near(config.iconScale, v),
                            onClick = { onConfigChange(config.copy(iconScale = v)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
                } // fin Texto
                if (tab == 2) {

                SectionLabel("Color de la aplicación")
                Spacer(modifier = Modifier.height(6.dp))
                ColorSwatchRow(accentOptions, selected = config.accent, onSelected = {
                    onConfigChange(config.copy(accent = it, preset = ""))
                })
                Spacer(modifier = Modifier.height(8.dp))
                var showCustomAccent by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showCustomAccent = !showCustomAccent }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        Icons.Default.ColorLens,
                        contentDescription = null,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (showCustomAccent) "Ocultar selector de color" else "Elegir cualquier color de la paleta",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (showCustomAccent) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HsvColorPicker(
                        initial = config.accent,
                        onColor = { onConfigChange(config.copy(accent = it, preset = "")) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                SectionLabel("Color de las letras")
                Spacer(modifier = Modifier.height(6.dp))
                ColorSwatchRow(textOptions, selected = config.text, onSelected = {
                    onConfigChange(config.copy(text = it, preset = ""))
                })
                Spacer(modifier = Modifier.height(12.dp))

                SectionLabel("Color de fondo")
                Spacer(modifier = Modifier.height(6.dp))
                ColorSwatchRow(bgOptions, selected = config.background, onSelected = {
                    onConfigChange(config.copy(background = it, preset = ""))
                })
                Spacer(modifier = Modifier.height(12.dp))

                SectionLabel("Colores del estado de los mensajes")
                Spacer(modifier = Modifier.height(6.dp))
                Text("Enviado", style = MaterialTheme.typography.bodySmall)
                ColorSwatchRow(statusOptions, selected = config.statusSent, onSelected = {
                    onConfigChange(config.copy(statusSent = it, preset = ""))
                })
                Spacer(modifier = Modifier.height(8.dp))
                Text("Enviando", style = MaterialTheme.typography.bodySmall)
                ColorSwatchRow(statusOptions, selected = config.statusSending, onSelected = {
                    onConfigChange(config.copy(statusSending = it, preset = ""))
                })
                Spacer(modifier = Modifier.height(8.dp))
                Text("No enviado", style = MaterialTheme.typography.bodySmall)
                ColorSwatchRow(statusOptions, selected = config.statusFailed, onSelected = {
                    onConfigChange(config.copy(statusFailed = it, preset = ""))
                })
                } // fin Colores
                if (tab == 4) {

                SectionLabel("Tipo de fuente")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("lex", "default", "sans", "serif", "mono", "cursive", "lato", "motigen").forEach { f ->
                        FilterChip(
                            selected = config.font == f,
                            onClick = { onConfigChange(config.copy(font = f)) },
                            label = { Text(fontNameLabel(f), fontSize = 12.sp) }
                        )
                    }
                }
                if (config.font == "custom") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Usando la fuente instalada de tu dispositivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                SecondaryActionButton(
                    text = "Instalar una fuente (.ttf)",
                    onClick = onPickFont,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.FileOpen
                )
                } // fin Fuente
                if (tab == 7) {

                SectionLabel("Guardar / compartir tu apariencia")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryActionButton(
                        text = "Exportar",
                        onClick = onExportConfig,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Save
                    )
                    SecondaryActionButton(
                        text = "Importar",
                        onClick = onImportConfig,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.FileUpload
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                SecondaryActionButton(
                    text = "Restablecer colores y apariencia",
                    onClick = { onConfigChange(ThemeConfig()) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.RestartAlt
                )
                } // fin Guardar
            }
        }
    }
}

// Emojis disponibles como logo de la app.
val logoOptions: List<String> = listOf(
    "📨", "💬", "📞", "📣", "🚀", "⭐", "🔵", "🟢", "🟣", "🔥", "🛡️", "💼", "📊", "❤️", "✅", "⚡"
)

@Composable
private fun MarcaTab(config: ThemeConfig, onConfigChange: (ThemeConfig) -> Unit) {
    SectionCard {
        Text("Nombre de la app", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = config.appName,
            onValueChange = { onConfigChange(config.copy(appName = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre a mostrar") },
            placeholder = { Text("LEX RECOVER") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Icono de la app (logo)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            logoOptions.forEach { emoji ->
                FilterChip(
                    selected = config.appLogo == emoji,
                    onClick = { onConfigChange(config.copy(appLogo = emoji)) },
                    label = { Text(emoji, fontSize = 18.sp) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        BrandPreview(config)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Android no permite cambiar el nombre ni el icono del lanzador a valores libres. " +
                "Este nombre y logo se muestran dentro de la app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BrandPreview(config: ThemeConfig) {
    val name = config.appName.ifBlank { "LEX RECOVER" }
    val logo = config.appLogo.ifBlank { "📨" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(logo, fontSize = 24.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Así se verá tu marca dentro de la app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: ThemePreset,
    selected: Boolean,
    onSelected: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onSelected)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(preset.background))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f),
                    RoundedCornerShape(16.dp)
                )
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(preset.accent))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(preset.text))
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            preset.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreviewCard(config: ThemeConfig) {
    val accent = Color(config.accent)
    val text = Color(config.text)
    val bg = Color(config.background)
    val darkBg = bg.luminance() < 0.5f
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
    val surface = if (darkBg) lerp(bg, Color.White, 0.08f) else lerp(bg, Color.White, 0.5f)
    val onSbg = if (darkBg) lerp(text, Color.White, 0.35f) else lerp(text, bg, 0.4f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, text.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (config.useGradient) {
                        Brush.verticalGradient(listOf(accent, lerp(accent, text, 0.3f)))
                    } else {
                        Brush.verticalGradient(listOf(accent, accent))
                    }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(onAccent.copy(alpha = 0.25f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(onAccent.copy(alpha = 0.92f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(74.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(onAccent.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(54.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(onAccent.copy(alpha = 0.9f))
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(surface),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(54.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(onSbg.copy(alpha = 0.6f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(surface)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.35f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(9.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(text.copy(alpha = 0.92f))
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .width(190.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(text.copy(alpha = 0.4f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(text.copy(alpha = 0.2f))
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(surface)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Vista previa del texto con los tamaños elegidos",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
    }
}

@Composable
fun ColorSwatchRow(
    options: List<Pair<String, Long>>,
    selected: Long,
    onSelected: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { (label, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(color))
                        .then(
                            if (selected == color) {
                                Modifier.border(
                                    3.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(10.dp)
                                )
                            } else {
                                Modifier.border(
                                    1.dp,
                                    Color.Black.copy(alpha = 0.2f),
                                    RoundedCornerShape(10.dp)
                                )
                            }
                        )
                        .clickable { onSelected(color) }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp
                )
            }
        }
    }
}

private fun rgbToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> (60f * (((g - b) / d) % 6f) + 360f) % 360f
        max == g -> (60f * (((b - r) / d) + 2f) + 360f) % 360f
        else -> (60f * (((r - g) / d) + 4f) + 360f) % 360f
    }
    val s = if (max == 0f) 0f else d / max
    return Triple(h, s, max)
}

private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val hh = ((h % 360f) + 360f) % 360f / 60f
    val i = hh.toInt()
    val f = hh - i
    val p = v * (1 - s)
    val q = v * (1 - f * s)
    val t = v * (1 - (1 - f) * s)
    val (r, gr, b) = when (i % 6) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    return Color(r, gr, b)
}

@Composable
private fun HsvColorPicker(
    initial: Long,
    onColor: (Long) -> Unit
) {
    var hsv by remember { mutableStateOf(rgbToHsv(Color(initial))) }
    val hueColor = remember(hsv.first) { hsvToColor(hsv.first, 1f, 1f) }
    val selected = hsvToColor(hsv.first, hsv.second, hsv.third)

    fun updateSaturationValue(pos: Offset, size: IntSize) {
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        val s = (pos.x / w).coerceIn(0f, 1f)
        val v = (1f - pos.y / h).coerceIn(0f, 1f)
        hsv = Triple(hsv.first, s, v)
        onColor(hsvToColor(hsv.first, s, v).value.toLong())
    }

    fun updateHue(pos: Offset, width: Int) {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = (pos.x / w).coerceIn(0f, 1f) * 360f
        hsv = Triple(h, hsv.second, hsv.third)
        onColor(hsvToColor(h, hsv.second, hsv.third).value.toLong())
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures { updateSaturationValue(it, size) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        updateSaturationValue(change.position, size)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.White, hueColor)))
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        val hueColors = remember {
            List(13) { i -> hsvToColor(i * 30f, 1f, 1f) }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.horizontalGradient(hueColors))
                .pointerInput(Unit) {
                    detectTapGestures { updateHue(it, size.width) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        updateHue(change.position, size.width)
                    }
                }
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(selected)
                    .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                String.format("#%06X", 0xFFFFFFL and selected.value.toLong()),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun currentToneLabel(config: ThemeConfig, context: Context): String {
    if (config.notificationSound.isBlank()) return "Predeterminado (del sistema)"
    return NotificationTones.list(context)
        .firstOrNull { it.uri.toString() == config.notificationSound }
        ?.title ?: "Personalizado"
}

@Composable
private fun TonePickerDialog(
    currentUri: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tones = remember { NotificationTones.list(context) }
    var previewedUri by remember { mutableStateOf(currentUri) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timbre de notificación") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Se reproducirá al llegar un mensaje, al terminar un envío y al terminar la secuencia de llamadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                ToneRow(
                    title = "Predeterminado (del sistema)",
                    uri = "",
                    selected = previewedUri.isBlank(),
                    onClick = {
                        previewedUri = ""
                        AppFeedback.playTone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    }
                )
                tones.forEach { tone ->
                    val uri = tone.uri.toString()
                    ToneRow(
                        title = tone.title,
                        uri = uri,
                        selected = previewedUri == uri,
                        onClick = {
                            previewedUri = uri
                            AppFeedback.playTone(context, tone.uri)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(previewedUri) }) { Text("Usar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ToneRow(title: String, uri: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}