package com.difusion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

val LocalIconScale = staticCompositionLocalOf { 1f }
val LocalAppShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(16.dp) }
val LocalAppRadius = staticCompositionLocalOf { 16.dp }
val LocalAppPadding = staticCompositionLocalOf { 16.dp }
val LocalUseGradient = staticCompositionLocalOf { true }

@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size * LocalIconScale.current)
    )
}

fun formatClockTime(date: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(date))

fun formatConversationTime(date: Long): String {
    val calendar = Calendar.getInstance()
    val today = calendar.get(Calendar.YEAR) to calendar.get(Calendar.DAY_OF_YEAR)
    val d = Calendar.getInstance().apply { timeInMillis = date }
    val day = d.get(Calendar.YEAR) to d.get(Calendar.DAY_OF_YEAR)
    return when {
        day == today -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(date))
        TimeUnit.DAYS.convert(System.currentTimeMillis() - date, TimeUnit.MILLISECONDS) in 1..6 ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(date))
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(date))
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    val useGradient = LocalUseGradient.current
    val bottomRadius = LocalAppRadius.current
    val background: androidx.compose.ui.graphics.Brush = if (useGradient) {
        Brush.verticalGradient(
            listOf(
                colors.primary,
                colors.tertiary.copy(alpha = 0.92f)
            )
        )
    } else {
        Brush.verticalGradient(listOf(colors.primary, colors.primary))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = bottomRadius * 1.5f, bottomEnd = bottomRadius * 1.5f))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                AppIcon(
                    icon,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    size = 26.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimary
            )
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                color = colors.onPrimary.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = LocalAppShape.current,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LocalAppPadding.current),
            content = content
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIcon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            size = 72.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color? = null,
    compact: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    if (LocalUseGradient.current && containerColor == null) {
        val shape = LocalAppShape.current
        val height = if (compact) 36.dp else 46.dp
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    Brush.horizontalGradient(listOf(colors.primary, colors.tertiary))
                )
                .clickable(enabled = enabled, onClick = onClick)
                .then(if (enabled) Modifier else Modifier.alpha(0.4f))
                .height(height)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    AppIcon(icon, contentDescription = null, size = if (compact) 17.dp else 20.dp, tint = colors.onPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimary
                )
            }
        }
    } else {
        Button(
            onClick = onClick,
            modifier = if (compact) modifier.height(36.dp) else modifier,
            enabled = enabled,
            shape = RoundedCornerShape(if (compact) 10.dp else 14.dp),
            colors = if (containerColor != null) {
                ButtonDefaults.buttonColors(containerColor = containerColor)
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            if (icon != null) {
                AppIcon(icon, contentDescription = null, size = if (compact) 17.dp else 20.dp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color? = null,
    compact: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = if (compact) modifier.height(36.dp) else modifier,
        enabled = enabled,
        shape = RoundedCornerShape(if (compact) 10.dp else 14.dp),
        colors = if (containerColor != null) {
            ButtonDefaults.outlinedButtonColors(containerColor = containerColor)
        } else {
            ButtonDefaults.outlinedButtonColors()
        }
    ) {
        if (icon != null) {
            AppIcon(icon, contentDescription = null, size = if (compact) 17.dp else 20.dp)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun initialsFor(name: String): String = name.trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
    .joinToString("")
    .ifBlank { "?" }

@Composable
fun InitialsAvatar(
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val initials = initialsFor(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            color = colors.primary,
            fontWeight = FontWeight.Bold,
            fontSize = with(LocalDensity.current) { (size.value * 0.34f).sp }
        )
    }
}