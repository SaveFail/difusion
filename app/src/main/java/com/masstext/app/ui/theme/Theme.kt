package com.masstext.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

@Immutable
data class ThemeConfig(
    val accent: Long = 0xFF0000FF,
    val text: Long = 0xFF050A2E,
    val background: Long = 0xFFF3FCFB,
    val font: String = "lex",
    val textScale: Float = 1f,
    val iconScale: Float = 1f,
    val darkMode: Boolean = false,
    val useGradient: Boolean = true,
    val cornerStyle: String = "rounded",
    val spacing: String = "comfortable",
    val statusBarTint: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val notificationSound: String = "",
    val preset: String = "lexrecover",
    // Colores del estado de los mensajes en el chat.
    val statusSent: Long = 0xFF2E7D32,
    val statusSending: Long = 0xFFF9A825,
    val statusFailed: Long = 0xFFC62828,
    // Personalización ampliada del chat y del editor. 0 = usar el color de la app.
    val outgoingBubble: Long = 0,
    val incomingBubble: Long = 0,
    val bubbleShape: String = "rounded", // rounded | pill | sharp
    val showMessageStatus: Boolean = true,
    val showMessageTime: Boolean = true,
    val showSmsCounter: Boolean = true
)

object ThemePrefs {

    private const val PREFS = "masstext_prefs"
    private const val KEY_ACCENT = "theme_accent"
    private const val KEY_TEXT = "theme_text"
    private const val KEY_BG = "theme_background"
    private const val KEY_FONT = "theme_font"
    private const val KEY_TEXT_SCALE = "theme_text_scale"
    private const val KEY_ICON_SCALE = "theme_icon_scale"
    private const val KEY_DARK = "theme_dark"
    private const val KEY_GRADIENT = "theme_gradient"
    private const val KEY_CORNER = "theme_corner"
    private const val KEY_SPACING = "theme_spacing"
    private const val KEY_STATUS_BAR = "theme_status_bar"
    private const val KEY_SOUND = "theme_sound"
    private const val KEY_VIBRATION = "theme_vibration"
    private const val KEY_NOTIFICATION_SOUND = "theme_notification_sound"
    private const val KEY_PRESET = "theme_preset"
    private const val KEY_STATUS_SENT = "theme_status_sent"
    private const val KEY_STATUS_SENDING = "theme_status_sending"
    private const val KEY_STATUS_FAILED = "theme_status_failed"
    private const val KEY_OUT_BUBBLE = "theme_out_bubble"
    private const val KEY_IN_BUBBLE = "theme_in_bubble"
    private const val KEY_BUBBLE_SHAPE = "theme_bubble_shape"
    private const val KEY_SHOW_STATUS = "theme_show_status"
    private const val KEY_SHOW_TIME = "theme_show_time"
    private const val KEY_SHOW_SMS_COUNTER = "theme_show_sms_counter"
    private const val KEY_VERSION = "theme_version"
    private const val VERSION = 2

    fun read(context: Context): ThemeConfig {
        val prefs = prefs(context)
        val defaults = ThemeConfig()
        if (prefs.getInt(KEY_VERSION, -1) != VERSION) {
            prefs.edit().putInt(KEY_VERSION, VERSION).apply()
            return defaults
        }
        return ThemeConfig(
            accent = prefs.getLong(KEY_ACCENT, defaults.accent),
            text = prefs.getLong(KEY_TEXT, defaults.text),
            background = prefs.getLong(KEY_BG, defaults.background),
            font = prefs.getString(KEY_FONT, defaults.font) ?: defaults.font,
            textScale = prefs.getFloat(KEY_TEXT_SCALE, defaults.textScale),
            iconScale = prefs.getFloat(KEY_ICON_SCALE, defaults.iconScale),
            darkMode = prefs.getBoolean(KEY_DARK, defaults.darkMode),
            useGradient = prefs.getBoolean(KEY_GRADIENT, defaults.useGradient),
            cornerStyle = prefs.getString(KEY_CORNER, defaults.cornerStyle) ?: defaults.cornerStyle,
            spacing = prefs.getString(KEY_SPACING, defaults.spacing) ?: defaults.spacing,
            statusBarTint = prefs.getBoolean(KEY_STATUS_BAR, defaults.statusBarTint),
            soundEnabled = prefs.getBoolean(KEY_SOUND, defaults.soundEnabled),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, defaults.vibrationEnabled),
            notificationSound = prefs.getString(KEY_NOTIFICATION_SOUND, defaults.notificationSound) ?: defaults.notificationSound,
            preset = prefs.getString(KEY_PRESET, defaults.preset) ?: defaults.preset,
            statusSent = prefs.getLong(KEY_STATUS_SENT, defaults.statusSent),
            statusSending = prefs.getLong(KEY_STATUS_SENDING, defaults.statusSending),
            statusFailed = prefs.getLong(KEY_STATUS_FAILED, defaults.statusFailed),
            outgoingBubble = prefs.getLong(KEY_OUT_BUBBLE, defaults.outgoingBubble),
            incomingBubble = prefs.getLong(KEY_IN_BUBBLE, defaults.incomingBubble),
            bubbleShape = prefs.getString(KEY_BUBBLE_SHAPE, defaults.bubbleShape) ?: defaults.bubbleShape,
            showMessageStatus = prefs.getBoolean(KEY_SHOW_STATUS, defaults.showMessageStatus),
            showMessageTime = prefs.getBoolean(KEY_SHOW_TIME, defaults.showMessageTime),
            showSmsCounter = prefs.getBoolean(KEY_SHOW_SMS_COUNTER, defaults.showSmsCounter)
        )
    }

    fun write(context: Context, config: ThemeConfig) {
        prefs(context).edit()
            .putInt(KEY_VERSION, VERSION)
            .putLong(KEY_ACCENT, config.accent)
            .putLong(KEY_TEXT, config.text)
            .putLong(KEY_BG, config.background)
            .putString(KEY_FONT, config.font)
            .putFloat(KEY_TEXT_SCALE, config.textScale)
            .putFloat(KEY_ICON_SCALE, config.iconScale)
            .putBoolean(KEY_DARK, config.darkMode)
            .putBoolean(KEY_GRADIENT, config.useGradient)
            .putString(KEY_CORNER, config.cornerStyle)
            .putString(KEY_SPACING, config.spacing)
            .putBoolean(KEY_STATUS_BAR, config.statusBarTint)
            .putBoolean(KEY_SOUND, config.soundEnabled)
            .putBoolean(KEY_VIBRATION, config.vibrationEnabled)
            .putString(KEY_NOTIFICATION_SOUND, config.notificationSound)
            .putString(KEY_PRESET, config.preset)
            .putLong(KEY_STATUS_SENT, config.statusSent)
            .putLong(KEY_STATUS_SENDING, config.statusSending)
            .putLong(KEY_STATUS_FAILED, config.statusFailed)
            .putLong(KEY_OUT_BUBBLE, config.outgoingBubble)
            .putLong(KEY_IN_BUBBLE, config.incomingBubble)
            .putString(KEY_BUBBLE_SHAPE, config.bubbleShape)
            .putBoolean(KEY_SHOW_STATUS, config.showMessageStatus)
            .putBoolean(KEY_SHOW_TIME, config.showMessageTime)
            .putBoolean(KEY_SHOW_SMS_COUNTER, config.showSmsCounter)
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// ---------- Fuente personalizada desde archivo ----------

private const val FONT_DIR = "fonts"
private const val FONT_FILE = "custom.ttf"

fun customFontFile(context: Context): File = File(context.filesDir, "$FONT_DIR/$FONT_FILE")

fun installCustomFont(context: Context, uri: Uri): Boolean {
    return try {
        val file = customFontFile(context)
        file.parentFile?.mkdirs()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        FileOutputStream(file).use { it.write(bytes) }
        true
    } catch (_: Exception) {
        false
    }
}

fun fontFamilyFor(name: String, context: Context): FontFamily = when (name) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    "sans" -> FontFamily.SansSerif
    "custom" -> customFontFamily(context) ?: FontFamily.Default
    "lato" -> resFontFamily(context, "lato") ?: FontFamily.Default
    "motigen" -> resFontFamily(context, "motigen") ?: FontFamily.Default
    "lex" -> resFontFamily(context, "lato") ?: FontFamily.Default
    else -> FontFamily.Default
}

fun customFontFamily(context: Context): FontFamily? {
    val file = customFontFile(context)
    if (!file.exists()) return null
    return try {
        FontFamily(Font(file, FontWeight.Normal, FontStyle.Normal))
    } catch (_: Exception) {
        null
    }
}

fun resFontFamily(context: Context, name: String): FontFamily? {
    val id = try {
        context.resources.getIdentifier("font/$name", "font", context.packageName)
    } catch (_: Exception) {
        0
    }
    return if (id != 0) {
        runCatching { FontFamily(Font(id, FontWeight.Normal, FontStyle.Normal)) }.getOrNull()
    } else {
        null
    }
}

fun fontNameLabel(name: String): String = when (name) {
    "serif" -> "Serif"
    "mono" -> "Monoespaciada"
    "cursive" -> "Cursiva"
    "sans" -> "Sans"
    "custom" -> "Archivo propio"
    "lato" -> "Lato"
    "motigen" -> "Motigen"
    "lex" -> "Difusión"
    else -> "Predeterminada"
}

// ---------- Formas y densidades ----------

fun cornerLabel(cornerStyle: String): String = when (cornerStyle) {
    "pill" -> "Píldora"
    "sharp" -> "Cuadrado"
    else -> "Redondeado"
}

fun spacingLabel(spacing: String): String = when (spacing) {
    "compact" -> "Compacto"
    "spacious" -> "Espacioso"
    else -> "Cómodo"
}

fun appRadius(cornerStyle: String): Dp = when (cornerStyle) {
    "pill" -> 26.dp
    "sharp" -> 6.dp
    else -> 16.dp
}

fun appShape(cornerStyle: String): Shape = when (cornerStyle) {
    "sharp" -> RoundedCornerShape(6.dp)
    else -> RoundedCornerShape(if (cornerStyle == "pill") 24.dp else 16.dp)
}

fun appPadding(spacing: String): Dp = when (spacing) {
    "compact" -> 12.dp
    "spacious" -> 20.dp
    else -> 16.dp
}

// ---------- Presets ----------

data class ThemePreset(
    val id: String,
    val label: String,
    val accent: Long,
    val text: Long,
    val background: Long,
    val darkMode: Boolean = false,
    val cornerStyle: String = "rounded",
    val gradient: Boolean = true
)

val THEME_PRESETS: List<ThemePreset> = listOf(
    ThemePreset("lexrecover", "Difusión", 0xFF0000FF, 0xFF050A2E, 0xFFF3FCFB),
    ThemePreset("violeta", "Violeta", 0xFF7C3AED, 0xFF1B1023, 0xFFF7F4FF),
    ThemePreset("oceano", "Océano", 0xFF0E7C86, 0xFF06262B, 0xFFEAF7F6),
    ThemePreset("cielo", "Cielo", 0xFF2F6FED, 0xFF0B1E4B, 0xFFEEF4FF, cornerStyle = "pill"),
    ThemePreset("rosa", "Rosa", 0xFFD4507A, 0xFF3A0F21, 0xFFFFF3F7, cornerStyle = "pill"),
    ThemePreset("bosque", "Bosque", 0xFF2E7D46, 0xFF0E2A16, 0xFFEFF7F0),
    ThemePreset("cafe", "Café", 0xFF8D5B3C, 0xFF2B1A10, 0xFFF7F0E8),
    ThemePreset("medianoche", "Medianoche", 0xFF8A9BFF, 0xFFE8E9FF, 0xFF14152A, darkMode = true, cornerStyle = "sharp"),
    ThemePreset("solar", "Solar", 0xFFE8930C, 0xFF3A2600, 0xFFFFF8E6, cornerStyle = "pill"),
    ThemePreset("vino", "Vino", 0xFFB03052, 0xFF33050F, 0xFFFDEEF1),
    ThemePreset("lima", "Lima", 0xFF5F9E1A, 0xFF1C3005, 0xFFF2FBEA)
)

fun applyPreset(config: ThemeConfig, presetId: String): ThemeConfig {
    val p = THEME_PRESETS.firstOrNull { it.id == presetId }
        ?: return config.copy(preset = presetId)
    return config.copy(
        accent = p.accent,
        text = p.text,
        background = p.background,
        darkMode = p.darkMode,
        cornerStyle = p.cornerStyle,
        useGradient = p.gradient,
        preset = presetId
    )
}

fun presetLabel(config: ThemeConfig): String =
    THEME_PRESETS.firstOrNull { it.id == config.preset }?.label ?: "Personalizado"

// ---------- Exportar / importar configuración ----------

private fun configToJson(config: ThemeConfig): JSONObject = JSONObject().apply {
    put("version", 1)
    put("accent", config.accent)
    put("text", config.text)
    put("background", config.background)
    put("font", config.font)
    put("textScale", config.textScale.toDouble())
    put("iconScale", config.iconScale.toDouble())
    put("darkMode", config.darkMode)
    put("useGradient", config.useGradient)
    put("cornerStyle", config.cornerStyle)
    put("spacing", config.spacing)
    put("statusBarTint", config.statusBarTint)
    put("soundEnabled", config.soundEnabled)
    put("vibrationEnabled", config.vibrationEnabled)
    put("notificationSound", config.notificationSound)
    put("statusSent", config.statusSent)
    put("statusSending", config.statusSending)
    put("statusFailed", config.statusFailed)
    put("outgoingBubble", config.outgoingBubble)
    put("incomingBubble", config.incomingBubble)
    put("bubbleShape", config.bubbleShape)
    put("showMessageStatus", config.showMessageStatus)
    put("showMessageTime", config.showMessageTime)
    put("showSmsCounter", config.showSmsCounter)
}

fun configFromJson(json: String): ThemeConfig? {
    return try {
        val o = JSONObject(json)
        ThemeConfig(
            accent = o.optLong("accent", 0xFF0000FF),
            text = o.optLong("text", 0xFF050A2E),
            background = o.optLong("background", 0xFFF3FCFB),
            font = o.optString("font", "lex"),
            textScale = o.optDouble("textScale", 1.0).toFloat(),
            iconScale = o.optDouble("iconScale", 1.0).toFloat(),
            darkMode = o.optBoolean("darkMode", false),
            useGradient = o.optBoolean("useGradient", true),
            cornerStyle = o.optString("cornerStyle", "rounded"),
            spacing = o.optString("spacing", "comfortable"),
            statusBarTint = o.optBoolean("statusBarTint", true),
            soundEnabled = o.optBoolean("soundEnabled", true),
            vibrationEnabled = o.optBoolean("vibrationEnabled", true),
            notificationSound = o.optString("notificationSound", ""),
            statusSent = o.optLong("statusSent", 0xFF2E7D32),
            statusSending = o.optLong("statusSending", 0xFFF9A825),
            statusFailed = o.optLong("statusFailed", 0xFFC62828),
            outgoingBubble = o.optLong("outgoingBubble", 0),
            incomingBubble = o.optLong("incomingBubble", 0),
            bubbleShape = o.optString("bubbleShape", "rounded"),
            showMessageStatus = o.optBoolean("showMessageStatus", true),
            showMessageTime = o.optBoolean("showMessageTime", true),
            showSmsCounter = o.optBoolean("showSmsCounter", true)
        )
    } catch (_: Exception) {
        null
    }
}

fun readConfigFromUri(context: Context, uri: Uri): ThemeConfig? {
    return try {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8)
            ?: return null
        configFromJson(text)
    } catch (_: Exception) {
        null
    }
}

fun exportConfig(context: Context, config: ThemeConfig): String {
    val bytes = configToJson(config).toString(4).toByteArray(Charsets.UTF_8)
    val fileName = "apariencia_lexrecover.json"
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Apariencia guardada en Descargas"
            } else {
                "No se pudo guardar en Descargas"
            }
        } else {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).writeBytes(bytes)
            "Apariencia guardada en Descargas"
        }
    } catch (e: Exception) {
        "Error al exportar: ${e.message}"
    }
}

// ---------- Esquema de color ----------

private fun Color.withBg(bg: Color, amount: Float): Color = lerp(bg, this, amount)

private fun buildScheme(config: ThemeConfig): ColorScheme {
    val dark = config.darkMode
    val accent = Color(config.accent)
    var text = Color(config.text)
    var bg = Color(config.background)
    if (dark) {
        text = if (text.luminance() > 0.5f) Color(0xFFECEAF4) else lerp(text, Color(0xFFECEAF4), 0.7f)
        bg = if (bg.luminance() > 0.5f) Color(0xFF11121A) else lerp(bg, Color(0xFF11121A), 0.55f)
    }
    val darkBg = bg.luminance() < 0.5f

    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
    val surface = if (darkBg) lerp(bg, Color.White, 0.07f) else lerp(bg, Color.White, 0.5f)
    val surfaceVariant = if (darkBg) lerp(bg, Color.White, 0.11f) else lerp(bg, accent, 0.08f)
    val onSurfaceVariant = lerp(text, bg, 0.4f)
    val secondary = lerp(text, accent, 0.55f)
    val tertiary = lerp(accent, text, 0.42f)
    val containerAmount = if (darkBg) 0.30f else 0.82f

    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.withBg(bg, 0.30f),
            onPrimaryContainer = lerp(text, accent, 0.2f),
            secondary = secondary,
            onSecondary = if (secondary.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF),
            secondaryContainer = secondary.withBg(bg, 0.30f),
            onSecondaryContainer = text,
            tertiary = tertiary,
            onTertiary = if (tertiary.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF),
            tertiaryContainer = tertiary.withBg(bg, 0.30f),
            onTertiaryContainer = text,
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = onSurfaceVariant,
            outlineVariant = lerp(bg, Color.White, 0.22f)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.withBg(bg, containerAmount),
            onPrimaryContainer = lerp(text, accent, 0.30f),
            secondary = secondary,
            onSecondary = if (secondary.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF),
            secondaryContainer = secondary.withBg(bg, containerAmount),
            onSecondaryContainer = text,
            tertiary = tertiary,
            onTertiary = if (tertiary.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF),
            tertiaryContainer = tertiary.withBg(bg, containerAmount),
            onTertiaryContainer = text,
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = onSurfaceVariant,
            outlineVariant = if (darkBg) lerp(bg, Color.White, 0.18f) else lerp(bg, Color.Black, 0.12f)
        )
    }
}

private fun buildTypography(fontName: String, context: Context): Typography {
    val headings = when (fontName) {
        "lex" -> resFontFamily(context, "motigen")
            ?: resFontFamily(context, "lato")
            ?: FontFamily.Default
        else -> fontFamilyFor(fontName, context)
    }
    val body = when (fontName) {
        "lex" -> resFontFamily(context, "lato") ?: FontFamily.Default
        else -> headings
    }
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = headings),
        displayMedium = base.displayMedium.copy(fontFamily = headings),
        displaySmall = base.displaySmall.copy(fontFamily = headings),
        headlineLarge = base.headlineLarge.copy(fontFamily = headings),
        headlineMedium = base.headlineMedium.copy(fontFamily = headings),
        headlineSmall = base.headlineSmall.copy(fontFamily = headings),
        titleLarge = base.titleLarge.copy(fontFamily = headings),
        titleMedium = base.titleMedium.copy(fontFamily = headings),
        titleSmall = base.titleSmall.copy(fontFamily = headings),
        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body),
        labelMedium = base.labelMedium.copy(fontFamily = body),
        labelSmall = base.labelSmall.copy(fontFamily = body)
    )
}

@Composable
fun MassTextTheme(
    config: ThemeConfig,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val typography = remember(config.font) { buildTypography(config.font, context) }
    val shapes = remember(config.cornerStyle) {
        val r = when (config.cornerStyle) {
            "sharp" -> 6.dp
            "pill" -> 24.dp
            else -> 16.dp
        }
        Shapes(
            small = RoundedCornerShape(r * 0.65f),
            medium = RoundedCornerShape(r),
            large = RoundedCornerShape(r * 1.4f)
        )
    }
    MaterialTheme(
        colorScheme = buildScheme(config),
        typography = typography,
        shapes = shapes,
        content = content
    )
}