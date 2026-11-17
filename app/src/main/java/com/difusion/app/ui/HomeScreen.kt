package com.difusion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Panel de Inicio: la puerta de entrada amigable a todo lo que hace la app.
 * Accesos rápidos a las tareas principales + un resumen en vivo.
 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigate: (String) -> Unit
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Cabecera
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column {
                Text(
                    "Inicio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Todo lo que necesitas para comunicarte con tus clientes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatPill("${contacts.size}", "contactos")
                    StatPill("${conversations.size}", "chats")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Acciones principales
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "Acciones rápidas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(
                    Modifier.weight(1f),
                    Icons.Default.Edit,
                    "Enviar",
                    "SMS masivo",
                    MaterialTheme.colorScheme.primary
                ) { onNavigate("editor") }
                QuickAction(
                    Modifier.weight(1f),
                    Icons.Default.Phone,
                    "Llamar",
                    "en secuencia",
                    Color(0xFF0E7C86)
                ) { onNavigate("calls") }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(
                    Modifier.weight(1f),
                    Icons.Default.Chat,
                    "WhatsApp",
                    "bot y envíos",
                    Color(0xFF2E7D46)
                ) { onNavigate("settings") }
                QuickAction(
                    Modifier.weight(1f),
                    Icons.Default.Contacts,
                    "Contactos",
                    "importar y filtrar",
                    Color(0xFF8D5B3C)
                ) { onNavigate("contacts") }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(
                    Modifier.weight(1f),
                    Icons.Default.History,
                    "Historial",
                    "de envíos",
                    Color(0xFF7C3AED)
                ) { onNavigate("history") }
                QuickAction(
                    Modifier.weight(1f),
                    Icons.Default.Settings,
                    "Ajustes",
                    "y personalización",
                    MaterialTheme.colorScheme.onSurfaceVariant
                ) { onNavigate("settings") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(icon, null, tint = color, size = 24.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
