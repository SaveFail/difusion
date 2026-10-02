package com.difusion.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.difusion.app.import.ParsedRow

private const val RF_ALL = "\u0000ALL"
private const val RF_BLANK = "\u0000BLANK"
private const val REVIEW_MAX_ROWS = 300

/**
 * Diálogo (dentro de la app) de revisión previa a importar desde Drive. Muestra
 * filtros por categoría de gestión (tipificación, estado, medio) y las filas con
 * todas las columnas y una casilla. Reemplaza a la ventana flotante del sistema.
 */
@Composable
fun ImportReviewDialog(
    rows: List<ParsedRow>,
    onImport: (List<ParsedRow>) -> Unit,
    onCancel: () -> Unit
) {
    val selected = remember { mutableStateListOf<Int>().apply { addAll(rows.indices) } }
    var gestionFilter by remember { mutableStateOf(RF_ALL) }
    var estadoFilter by remember { mutableStateOf(RF_ALL) }
    var medioFilter by remember { mutableStateOf(RF_ALL) }

    fun distinct(selector: (ParsedRow) -> String) =
        rows.map { selector(it).trim() }.filter { it.isNotBlank() }.distinct().sorted()

    fun hasBlank(selector: (ParsedRow) -> String) = rows.any { selector(it).isBlank() }

    // Cantidad de contactos por cada opción (para mostrarla en los chips).
    fun countFor(selector: (ParsedRow) -> String, value: String): Int = when (value) {
        RF_ALL -> rows.size
        RF_BLANK -> rows.count { selector(it).isBlank() }
        else -> rows.count { selector(it).trim() == value }
    }

    fun match(value: String, filter: String) = when (filter) {
        RF_ALL -> true
        RF_BLANK -> value.isBlank()
        else -> value.trim() == filter
    }

    val visible = rows.withIndex().filter { (_, r) ->
        match(r.gestion, gestionFilter) && match(r.estado, estadoFilter) && match(r.medio, medioFilter)
    }.map { it.index to it.value }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.9f)
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                // Cabecera
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Revisar importación",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCancel) {
                        AppIcon(Icons.Default.Close, contentDescription = "Cancelar")
                    }
                }
                Text(
                    "Solo asignados a ti · ${rows.size} disponibles · ${selected.size} seleccionados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // Filtros por dimensión
                ReviewFilterRow(
                    label = "Tipificación",
                    values = distinct { it.gestion },
                    hasBlank = hasBlank { it.gestion },
                    blankLabel = "Sin gestionar",
                    selected = gestionFilter,
                    countFor = { value -> countFor({ r -> r.gestion }, value) },
                    onSelect = { gestionFilter = it }
                )
                ReviewFilterRow(
                    label = "Estado",
                    values = distinct { it.estado },
                    hasBlank = hasBlank { it.estado },
                    blankLabel = "Sin estado",
                    selected = estadoFilter,
                    countFor = { value -> countFor({ r -> r.estado }, value) },
                    onSelect = { estadoFilter = it }
                )
                ReviewFilterRow(
                    label = "Medio de contacto",
                    values = distinct { it.medio },
                    hasBlank = hasBlank { it.medio },
                    blankLabel = "Sin medio",
                    selected = medioFilter,
                    countFor = { value -> countFor({ r -> r.medio }, value) },
                    onSelect = { medioFilter = it }
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    "Nombre · Cédula · Teléfono · Asignado a",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Lista de filas
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visible.take(REVIEW_MAX_ROWS), key = { it.first }) { (index, row) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(index),
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(index) else selected.remove(index)
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val detail = buildString {
                                    val c = row.cedula.trim()
                                    if (c.isNotEmpty()) append("C.I. $c")
                                    val p = row.phone.trim()
                                    if (p.isNotEmpty()) {
                                        if (isNotEmpty()) append(" · ")
                                        append(p)
                                    }
                                }
                                if (detail.isNotEmpty()) {
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val cats = listOf(row.gestion, row.estado, row.medio)
                                    .map { it.trim() }.filter { it.isNotBlank() }
                                if (cats.isNotEmpty()) {
                                    Text(
                                        cats.joinToString("  ·  "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (row.assignment.isNotBlank()) {
                                    Text(
                                        "Asignado a: ${row.assignment}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (visible.size > REVIEW_MAX_ROWS) {
                        item {
                            Text(
                                "… y ${visible.size - REVIEW_MAX_ROWS} más. Usa los filtros para acotar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Pie
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { visible.forEach { (i, _) -> if (!selected.contains(i)) selected.add(i) } }) {
                        Text("Marcar todo")
                    }
                    TextButton(onClick = { visible.forEach { (i, _) -> selected.remove(i) } }) {
                        Text("Nada")
                    }
                    Spacer(Modifier.weight(1f))
                    PrimaryActionButton(
                        text = "Importar (${selected.size})",
                        onClick = { onImport(rows.filterIndexed { i, _ -> selected.contains(i) }) },
                        compact = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewFilterRow(
    label: String,
    values: List<String>,
    hasBlank: Boolean,
    blankLabel: String,
    selected: String,
    countFor: (String) -> Int,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selected == RF_ALL,
                onClick = { onSelect(RF_ALL) },
                label = { Text("Todos (${countFor(RF_ALL)})") }
            )
            values.forEach { v ->
                FilterChip(
                    selected = selected == v,
                    onClick = { onSelect(v) },
                    label = { Text("$v (${countFor(v)})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
            if (hasBlank) {
                FilterChip(
                    selected = selected == RF_BLANK,
                    onClick = { onSelect(RF_BLANK) },
                    label = { Text("$blankLabel (${countFor(RF_BLANK)})", maxLines = 1) }
                )
            }
        }
    }
}
