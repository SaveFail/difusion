package com.difusion.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.difusion.app.service.SimManager

/**
 * Botón pequeño con forma de SIM para elegir con qué SIM enviar los mensajes.
 * Aparece aunque solo haya una SIM activa (muestra cuál es). La selección se
 * guarda y la usan el envío masivo y el chat.
 */
@Composable
fun SimPickerButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sims = remember { SimManager.getSims(context) }
    if (sims.isEmpty()) return
    var selectedId by remember { mutableStateOf(SimManager.getSmsSim(context)?.handle?.id) }
    var expanded by remember { mutableStateOf(false) }
    val selected = sims.find { it.handle.id == selectedId } ?: sims.first()

    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    Icons.Default.SimCard,
                    contentDescription = "Elegir SIM para enviar",
                    tint = MaterialTheme.colorScheme.primary,
                    size = 16.dp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    selected.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sims.forEach { sim ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (sim.number != null) "${sim.label} · ${sim.number}" else sim.label
                        )
                    },
                    leadingIcon = {
                        AppIcon(
                            Icons.Default.SimCard,
                            contentDescription = null,
                            size = 18.dp,
                            tint = if (sim.handle.id == selectedId) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        selectedId = sim.handle.id
                        SimManager.setSmsId(context, sim.handle.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
