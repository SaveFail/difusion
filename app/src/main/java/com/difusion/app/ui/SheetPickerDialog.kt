package com.difusion.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Diálogo (dentro de la app) para elegir cuál hoja del libro de Drive importar.
 * Reemplaza a la ventana flotante del sistema para no depender del permiso de
 * superposición.
 */
@Composable
fun SheetPickerDialog(
    sheets: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    onFinalize: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Elige la hoja del Drive") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (sheets.size == 1) "El libro tiene 1 hoja. Toca Finalizar."
                    else "El libro tiene ${sheets.size} hojas. Elige una y toca Finalizar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                sheets.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onPick(index) }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selectedIndex in sheets.indices) {
                    onFinalize()
                } else {
                    Toast.makeText(context, "Elige primero una hoja", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Finalizar") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
