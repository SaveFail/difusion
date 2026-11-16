package com.difusion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.difusion.app.service.BotFlowStore

// ============================================================
//  Editor de flujos: mapa conceptual de nodos del bot.
// ============================================================

@Composable
fun BotFlowEditorDialog(
    initialFlow: BotFlowStore.Flow,
    onSave: (BotFlowStore.Flow) -> Unit,
    onDismiss: () -> Unit
) {
    var flow by remember { mutableStateOf(initialFlow) }
    var editingNode by remember { mutableStateOf<BotFlowStore.Node?>(null) }
    var addingBranchFor by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // --- Barra superior ---
                Surface(color = MaterialTheme.colorScheme.primary, tonalElevation = 2.dp) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                AppIcon(Icons.Default.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Text(
                                "Mapa del bot",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onSave(flow) }) {
                                Text("Guardar", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedTextField(
                            value = flow.name,
                            onValueChange = { flow = flow.copy(name = it) },
                            label = { Text("Nombre del flujo") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                focusedLabelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                unfocusedLabelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = flow.trigger,
                            onValueChange = { flow = flow.copy(trigger = it) },
                            label = { Text("Palabra clave de inicio (opcional)") },
                            placeholder = { Text("Ej. hola, quiero pagar…") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                focusedLabelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                unfocusedLabelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (flow.enabled) "Flujo activo" else "Flujo pausado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = flow.enabled, onCheckedChange = { flow = flow.copy(enabled = it) })
                        }
                    }
                }

                // --- Mapa conceptual ---
                val startId = BotFlowStore.defaultStart(flow)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (flow.nodes.isEmpty()) {
                        EmptyState(
                            Icons.Default.Layers,
                            "El flujo está vacío",
                            "Agrega el primer paso con los botones de abajo."
                        )
                    } else {
                        Text(
                            "Toque un paso para editarlo. El botón + agrega una respuesta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        FlowMap(
                            flow = flow,
                            nodeId = startId,
                            depth = 0,
                            onEdit = { editingNode = it },
                            onAddBranch = { addingBranchFor = it }
                        )
                    }
                }

                // --- Barra inferior: agregar pasos ---
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AddStepButton("💬 Paso mensaje", Icons.Default.Chat) {
                            val n = BotFlowStore.Node(BotFlowStore.newId(), "message", text = "Escribe tu mensaje…")
                            flow = flow.copy(nodes = linkNew(flow, n))
                        }
                        AddStepButton("⚙️ Acción", Icons.Default.Tune) {
                            val n = BotFlowStore.Node(BotFlowStore.newId(), "action", action = "gestion", text = "")
                            flow = flow.copy(nodes = linkNew(flow, n))
                        }
                        AddStepButton("✅ Fin", Icons.Default.TaskAlt) {
                            val n = BotFlowStore.Node(BotFlowStore.newId(), "end")
                            flow = flow.copy(nodes = linkNew(flow, n))
                        }
                    }
                }
            }
        }
    }

    // Diálogo de edición de nodo
    editingNode?.let { node ->
        NodeEditDialog(
            node = node,
            flow = flow,
            onSave = { updated ->
                flow = flow.copy(nodes = flow.nodes.map { if (it.id == updated.id) updated else it })
                editingNode = null
            },
            onDelete = {
                flow = flow.copy(nodes = unlinkNode(flow, node.id))
                editingNode = null
            },
            onDismiss = { editingNode = null }
        )
    }

    // Diálogo de agregar rama
    addingBranchFor?.let { parentId ->
        AddBranchDialog(
            flow = flow,
            parentId = parentId,
            onAdd = { keyword, targetId ->
                val parent = BotFlowStore.nodeById(flow, parentId)
                if (parent != null) {
                    val updated = parent.copy(branches = parent.branches + BotFlowStore.Branch(keyword, targetId))
                    flow = flow.copy(nodes = flow.nodes.map { if (it.id == parent.id) updated else it })
                }
                addingBranchFor = null
            },
            onDismiss = { addingBranchFor = null }
        )
    }
}

// Crea un nodo nuevo y lo enlaza como siguiente del último nodo sin salida.
private fun linkNew(flow: BotFlowStore.Flow, node: BotFlowStore.Node): List<BotFlowStore.Node> {
    if (flow.nodes.isEmpty()) {
        return listOf(node).also { }
    }
    // Busca el primer nodo "message" sin nextId ni ramas (candidato a enlazar)
    val target = flow.nodes.firstOrNull { it.kind == "message" && it.nextId.isBlank() && it.branches.isEmpty() }
        ?: flow.nodes.lastOrNull { it.kind != "end" }
    val nodes = flow.nodes.toMutableList()
    nodes.add(node)
    if (target != null) {
        val idx = nodes.indexOfFirst { it.id == target.id }
        nodes[idx] = target.copy(nextId = node.id)
    }
    return nodes
}

// Quita un nodo y desenlaza cualquier referencia hacia él.
private fun unlinkNode(flow: BotFlowStore.Flow, id: String): List<BotFlowStore.Node> {
    return flow.nodes
        .filter { it.id != id }
        .map { n ->
            n.copy(
                nextId = if (n.nextId == id) "" else n.nextId,
                branches = n.branches.filter { it.targetId != id }
            )
        }
}

@Composable
private fun AddStepButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        AppIcon(icon, null, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

// ============================================================
//  Render del mapa (recursivo, con conectores y etiquetas)
// ============================================================

@Composable
private fun FlowMap(
    flow: BotFlowStore.Flow,
    nodeId: String,
    depth: Int,
    onEdit: (BotFlowStore.Node) -> Unit,
    onAddBranch: (String) -> Unit
) {
    if (nodeId.isBlank() || depth > 20) return  // límite de profundidad evita ciclos
    val node = BotFlowStore.nodeById(flow, nodeId) ?: return

    Column(Modifier.fillMaxWidth()) {
        FlowNodeCard(
            node = node,
            isStart = nodeId == BotFlowStore.defaultStart(flow),
            onEdit = { onEdit(node) },
            onAddBranch = { if (node.kind == "message") onAddBranch(node.id) }
        )

        val indent = (depth * 18).dp
        Column(Modifier.padding(start = indent)) {
            if (node.kind == "message") {
                node.branches.forEach { b ->
                    ConnectorLabel("si responde \"${b.keyword}\"")
                    FlowMap(flow, b.targetId, depth + 1, onEdit, onAddBranch)
                }
                if (node.nextId.isNotBlank()) {
                    ConnectorLabel("en cualquier otro caso")
                    FlowMap(flow, node.nextId, depth + 1, onEdit, onAddBranch)
                } else if (node.branches.isNotEmpty()) {
                    ConnectorLabel("(sin salida por defecto)")
                }
            } else if (node.nextId.isNotBlank()) {
                ConnectorLabel(null)
                FlowMap(flow, node.nextId, depth, onEdit, onAddBranch)
            }
        }
    }
}

@Composable
private fun ConnectorLabel(label: String?) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .width(2.dp)
                .height(16.dp)
                .background(colors.primary.copy(alpha = 0.5f))
        )
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FlowNodeCard(
    node: BotFlowStore.Node,
    isStart: Boolean,
    onEdit: () -> Unit,
    onAddBranch: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val (icon, tint, typeLabel) = when (node.kind) {
        "action" -> Triple(Icons.Default.Tune, Color(0xFFF9A825), "Acción")
        "end" -> Triple(Icons.Default.TaskAlt, Color(0xFF2E7D46), "Fin")
        else -> Triple(Icons.Default.Chat, colors.primary, "Mensaje")
    }
    val summary = when (node.kind) {
        "action" -> BotFlowStore.ACTION_LABELS[node.action] ?: "Acción"
        "end" -> "Termina la conversación"
        else -> node.text.ifBlank { "Mensaje sin texto" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = LocalAppShape.current,
        colors = CardDefaults.cardColors(
            containerColor = if (isStart) colors.primaryContainer.copy(alpha = 0.5f) else colors.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(icon, null, tint = tint, size = 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                    if (isStart) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "INICIO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onSurface
                )
                if (node.kind == "message" && node.branches.isNotEmpty()) {
                    Text(
                        "${node.branches.size} respuesta(s) configurada(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            if (node.kind == "message") {
                IconButton(onClick = onAddBranch) {
                    AppIcon(Icons.Default.Add, "Agregar respuesta", tint = colors.primary)
                }
            }
        }
    }
}

// ============================================================
//  Diálogo: editar nodo
// ============================================================

@Composable
private fun NodeEditDialog(
    node: BotFlowStore.Node,
    flow: BotFlowStore.Flow,
    onSave: (BotFlowStore.Node) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(node.kind) }
    var text by remember { mutableStateOf(node.text) }
    var action by remember { mutableStateOf(node.action.ifBlank { "gestion" }) }
    var minutes by remember { mutableStateOf(if (node.minutes > 0) node.minutes.toString() else "") }
    var nextId by remember { mutableStateOf(node.nextId) }
    var branches by remember { mutableStateOf(node.branches) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paso del flujo") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // tipo
                Text("Tipo de paso", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindChip("Mensaje", kind == "message") { kind = "message" }
                    KindChip("Acción", kind == "action") { kind = "action" }
                    KindChip("Fin", kind == "end") { kind = "end" }
                }
                Spacer(Modifier.height(12.dp))

                when (kind) {
                    "message" -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Mensaje a enviar") },
                            placeholder = { Text("Hola {nombre}, …") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Respuestas esperadas", style = MaterialTheme.typography.labelMedium)
                        branches.forEachIndexed { i, b ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "“${b.keyword}”",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { branches = branches.filterIndexed { j, _ -> j != i } }) {
                                    AppIcon(Icons.Default.Delete, "Quitar", tint = MaterialTheme.colorScheme.error, size = 18.dp)
                                }
                            }
                        }
                        TextButton(onClick = {
                            branches = branches + BotFlowStore.Branch("nueva respuesta", "")
                        }) { Text("+ Agregar respuesta") }

                        Spacer(Modifier.height(8.dp))
                        TargetPicker(
                            label = "Si no coincide ninguna respuesta",
                            value = nextId,
                            flow = flow,
                            exclude = node.id,
                            onSelect = { nextId = it }
                        )
                    }
                    "action" -> {
                        // selector de acción
                        Column {
                            BotFlowStore.ACTION_LABELS.forEach { (key, label) ->
                                val selected = action == key
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { action = key }
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selected) AppIcon(Icons.Default.Check, null, size = 16.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (action == "schedule") {
                            OutlinedTextField(
                                value = minutes,
                                onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                                label = { Text("En X minutos") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = {
                                Text(
                                    when (action) {
                                        "schedule" -> "Mensaje a programar"
                                        "tag" -> "Etiqueta"
                                        else -> "Valor"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        TargetPicker(
                            label = "Continuar con",
                            value = nextId,
                            flow = flow,
                            exclude = node.id,
                            onSelect = { nextId = it }
                        )
                    }
                    "end" -> {
                        Text(
                            "Este paso termina la conversación y olvida el estado del cliente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
                TextButton(
                    enabled = kind != "message" || text.isNotBlank(),
                    onClick = {
                        onSave(
                            node.copy(
                                kind = kind,
                                text = text,
                                action = if (kind == "action") action else "",
                                minutes = minutes.toIntOrNull() ?: 0,
                                nextId = nextId,
                                branches = if (kind == "message") branches.filter { it.keyword.isNotBlank() } else emptyList()
                            )
                        )
                    }
                ) { Text("Guardar") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun TargetPicker(
    label: String,
    value: String,
    flow: BotFlowStore.Flow,
    exclude: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("" to "— Terminar el flujo —") +
        flow.nodes.filter { it.id != exclude && it.kind != "end" }
            .map { it.id to nodeLabel(it) }
    val current = options.firstOrNull { it.first == value }?.second ?: "— Terminar el flujo —"

    Text(label, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(6.dp))
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            AppIcon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, lbl) ->
                DropdownMenuItem(
                    text = { Text(lbl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(id); expanded = false }
                )
            }
        }
    }
}

private fun nodeLabel(n: BotFlowStore.Node): String = when (n.kind) {
    "message" -> "💬 " + (n.text.ifBlank { "Mensaje" }).take(28)
    "action" -> "⚙️ " + (BotFlowStore.ACTION_LABELS[n.action] ?: "Acción")
    else -> "✅ Fin"
}

// ============================================================
//  Diálogo: agregar rama
// ============================================================

@Composable
private fun AddBranchDialog(
    flow: BotFlowStore.Flow,
    parentId: String,
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva respuesta") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Palabra clave (ej. sí, mañana)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                TargetPicker(
                    label = "Cuando el cliente responda esto, ir a",
                    value = targetId,
                    flow = flow,
                    exclude = parentId,
                    onSelect = { targetId = it }
                )
            }
        },
        confirmButton = {
            TextButton(enabled = keyword.isNotBlank(), onClick = { onAdd(keyword.trim(), targetId) }) {
                Text("Agregar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
