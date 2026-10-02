package com.difusion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.difusion.app.data.Contact

// Filtros especiales de las categorías. Se usan caracteres nulos como
// centinelas para no chocar con un valor real de categoría.
private const val FILTER_ALL = "\u0000ALL"
private const val FILTER_UNMANAGED = "\u0000UNMANAGED"
private const val LABEL_SIN_TIPIF = "Sin gestionar"
private const val LABEL_SIN_ESTADO = "Sin estado"
private const val LABEL_SIN_MEDIO = "Sin medio"

@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    selected: Set<Long>,
    users: List<String>,
    onToggle: (Long) -> Unit,
    onSelectAll: (List<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onAddManual: () -> Unit,
    onImport: () -> Unit,
    onExportContacts: () -> Unit,
    onExportVisible: (List<Contact>) -> Unit,
    onExportTemplate: () -> Unit,
    onCallContact: (Contact) -> Unit,
    onCallSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExportByAssignment: () -> Unit,
    onAssignUser: (Set<Long>, String) -> Unit,
    onClearAssignment: (Set<Long>) -> Unit,
    onManageUsers: () -> Unit,
    hasAssignment: Boolean
) {
    // Categorías por dimensión, derivadas de TODOS los contactos guardados:
    // tipificación (SEGUIMIENTO), estado (STATUS) y medio de contacto.
    val tipificaciones = remember(contacts) {
        contacts.map { it.gestion.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val estados = remember(contacts) {
        contacts.map { it.estado.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val medios = remember(contacts) {
        contacts.map { it.medio.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val hasBlankTipif = remember(contacts) { contacts.any { it.gestion.isBlank() } }
    val hasBlankEstado = remember(contacts) { contacts.any { it.estado.isBlank() } }
    val hasBlankMedio = remember(contacts) { contacts.any { it.medio.isBlank() } }
    // Conteos por valor, calculados UNA vez por lista (antes se recorría toda la
    // lista por cada chip en cada recomposición).
    val tipifCounts = remember(contacts) { contacts.groupingBy { it.gestion.trim() }.eachCount() }
    val estadoCounts = remember(contacts) { contacts.groupingBy { it.estado.trim() }.eachCount() }
    val medioCounts = remember(contacts) { contacts.groupingBy { it.medio.trim() }.eachCount() }
    // Usuarios disponibles para asignar (incluye los que ya traen los contactos).
    val assignableUsers = remember(users, contacts) {
        (users + contacts.map { it.assignment }.filter { it.isNotBlank() }).distinct().sorted()
    }

    var tipifFilter by remember { mutableStateOf(FILTER_ALL) }
    var estadoFilter by remember { mutableStateOf(FILTER_ALL) }
    var medioFilter by remember { mutableStateOf(FILTER_ALL) }

    // Si la categoría activa desaparece (p. ej. tras re-sincronizar) volvemos a Todos.
    LaunchedEffect(tipificaciones) {
        if (tipifFilter != FILTER_ALL && tipifFilter != FILTER_UNMANAGED &&
            tipifFilter !in tipificaciones
        ) tipifFilter = FILTER_ALL
    }
    LaunchedEffect(estados) {
        if (estadoFilter != FILTER_ALL && estadoFilter != FILTER_UNMANAGED &&
            estadoFilter !in estados
        ) estadoFilter = FILTER_ALL
    }
    LaunchedEffect(medios) {
        if (medioFilter != FILTER_ALL && medioFilter != FILTER_UNMANAGED &&
            medioFilter !in medios
        ) medioFilter = FILTER_ALL
    }

    fun matchesFilter(value: String, filter: String): Boolean = when (filter) {
        FILTER_ALL -> true
        FILTER_UNMANAGED -> value.isBlank()
        else -> value.trim() == filter
    }
    val visibleContacts = remember(contacts, tipifFilter, estadoFilter, medioFilter) {
        contacts.filter {
            matchesFilter(it.gestion, tipifFilter) &&
                matchesFilter(it.estado, estadoFilter) &&
                matchesFilter(it.medio, medioFilter)
        }
    }
    val anyFilterActive =
        tipifFilter != FILTER_ALL || estadoFilter != FILTER_ALL || medioFilter != FILTER_ALL

    fun selectTipif(value: String) {
        if (value != tipifFilter) { tipifFilter = value; onClearSelection() }
    }
    fun selectEstado(value: String) {
        if (value != estadoFilter) { estadoFilter = value; onClearSelection() }
    }
    fun selectMedio(value: String) {
        if (value != medioFilter) { medioFilter = value; onClearSelection() }
    }
    var assignTitle by remember { mutableStateOf("Asignar usuario") }
    var showAssignDialog by remember { mutableStateOf(false) }
    var onConfirmAssign: ((String) -> Unit)? by remember { mutableStateOf(null) }

    fun openAssignDialog(title: String, target: Set<Long>) {
        assignTitle = title
        onConfirmAssign = { userName ->
            onAssignUser(target, userName)
            showAssignDialog = false
        }
        showAssignDialog = true
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            title = "Mis Contactos",
            subtitle = if (!anyFilterActive) {
                "${contacts.size} contactos guardados"
            } else {
                "${visibleContacts.size} de ${contacts.size} (filtrados)"
            },
            icon = Icons.Default.Contacts
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (selected.isNotEmpty()) {
                // Modo selección: el menú de selección aparece arriba y los botones
                // de importar/descargar se ocultan para dejar espacio a los contactos.
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {},
                            leadingIcon = {
                                AppIcon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    size = 18.dp
                                )
                            },
                            label = { Text("${selected.size} seleccionados") },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onSelectAll(visibleContacts.map { it.id }) }) {
                            Text(
                                if (!anyFilterActive) "Todos"
                                else "Todos (${visibleContacts.size})",
                                fontSize = 13.sp
                            )
                        }
                        TextButton(onClick = onClearSelection) {
                            Text("Limpiar", fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    PrimaryActionButton(
                        text = "Llamar a ${selected.size}",
                        onClick = onCallSelected,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Phone,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        compact = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PrimaryActionButton(
                        text = "Asignar a usuario",
                        onClick = { openAssignDialog("Asignar usuario a ${selected.size} contactos", selected) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.PersonAdd,
                        compact = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SecondaryActionButton(
                        text = "Quitar asignación",
                        onClick = { onClearAssignment(selected) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Close,
                        compact = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SecondaryActionButton(
                        text = "Exportar Excel de seleccionados",
                        onClick = onExportContacts,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Download,
                        compact = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SecondaryActionButton(
                        text = "Eliminar seleccionados",
                        onClick = onDeleteSelected,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Delete,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        compact = true
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrimaryActionButton(
                        text = "Importar",
                        onClick = onImport,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.UploadFile,
                        compact = true
                    )
                    PrimaryActionButton(
                        text = "Nuevo",
                        onClick = onAddManual,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PersonAdd,
                        compact = true
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                SecondaryActionButton(
                    text = "Descargar plantilla Excel",
                    onClick = onExportTemplate,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Download,
                    compact = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                SecondaryActionButton(
                    text = "Gestionar usuarios",
                    onClick = onManageUsers,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Group,
                    compact = true
                )
                if (hasAssignment) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SecondaryActionButton(
                        text = "Exportar por asignación",
                        onClick = onExportByAssignment,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Group,
                        compact = true
                    )
                }
            }
        }

        // Los filtros se ocultan mientras hay contactos seleccionados: dejan
        // espacio visual y evitan cambiar de categoría con la selección activa.
        if (contacts.isNotEmpty() && selected.isEmpty()) {
            CategoryFilterRow(
                label = "Tipificación",
                categories = tipificaciones,
                hasBlank = hasBlankTipif,
                blankLabel = LABEL_SIN_TIPIF,
                selected = tipifFilter,
                countFor = { value ->
                    when (value) {
                        FILTER_ALL -> contacts.size
                        FILTER_UNMANAGED -> tipifCounts[""] ?: 0
                        else -> tipifCounts[value] ?: 0
                    }
                },
                onSelect = { selectTipif(it) }
            )
            CategoryFilterRow(
                label = "Estado",
                categories = estados,
                hasBlank = hasBlankEstado,
                blankLabel = LABEL_SIN_ESTADO,
                selected = estadoFilter,
                countFor = { value ->
                    when (value) {
                        FILTER_ALL -> contacts.size
                        FILTER_UNMANAGED -> estadoCounts[""] ?: 0
                        else -> estadoCounts[value] ?: 0
                    }
                },
                onSelect = { selectEstado(it) }
            )
            CategoryFilterRow(
                label = "Medio de contacto",
                categories = medios,
                hasBlank = hasBlankMedio,
                blankLabel = LABEL_SIN_MEDIO,
                selected = medioFilter,
                countFor = { value ->
                    when (value) {
                        FILTER_ALL -> contacts.size
                        FILTER_UNMANAGED -> medioCounts[""] ?: 0
                        else -> medioCounts[value] ?: 0
                    }
                },
                onSelect = { selectMedio(it) }
            )
            if (anyFilterActive) {
                Spacer(modifier = Modifier.height(6.dp))
                SecondaryActionButton(
                    text = "Exportar filtrados (${visibleContacts.size})",
                    onClick = { onExportVisible(visibleContacts) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    icon = Icons.Default.Download,
                    compact = true
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (contacts.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Contacts,
                title = "No hay contactos",
                description = "Importa un archivo Excel o agrega un contacto manualmente para empezar a enviar SMS.",
                modifier = Modifier.fillMaxSize()
            )
        } else if (visibleContacts.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Contacts,
                title = "Sin contactos con estos filtros",
                description = "Cambia las categorías en los filtros de arriba o sincroniza de nuevo desde Drive.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleContacts, key = { it.id }) { contact ->
                    ContactRow(
                        contact,
                        selected.contains(contact.id),
                        onToggle,
                        onCallContact,
                        onAssignSingle = {
                            openAssignDialog("Asignar usuario a ${contact.name}", setOf(contact.id))
                        }
                    )
                }
            }
        }
    }

    if (showAssignDialog) {
        AssignUserDialog(
            users = assignableUsers,
            title = assignTitle,
            onAssign = { userName -> onConfirmAssign?.invoke(userName) },
            onDismiss = { showAssignDialog = false }
        )
    }
}

@Composable
private fun CategoryFilterRow(
    label: String,
    categories: List<String>,
    hasBlank: Boolean,
    blankLabel: String,
    selected: String,
    countFor: (String) -> Int,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selected == FILTER_ALL,
                onClick = { onSelect(FILTER_ALL) },
                label = { Text("Todos (${countFor(FILTER_ALL)})") }
            )
            categories.forEach { category ->
                FilterChip(
                    selected = selected == category,
                    onClick = { onSelect(category) },
                    label = {
                        Text(
                            "$category (${countFor(category)})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
            if (hasBlank) {
                FilterChip(
                    selected = selected == FILTER_UNMANAGED,
                    onClick = { onSelect(FILTER_UNMANAGED) },
                    label = {
                        Text("$blankLabel (${countFor(FILTER_UNMANAGED)})", maxLines = 1)
                    }
                )
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    isSelected: Boolean,
    onToggle: (Long) -> Unit,
    onCallContact: (Contact) -> Unit,
    onAssignSingle: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        },
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(contact.id) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(contact.name)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    contact.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.assignment.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        contact.assignment,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Asignar usuario",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.clickable(onClick = onAssignSingle)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val categorias = listOf(contact.gestion, contact.estado, contact.medio)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (categorias.isEmpty()) {
                    Text(
                        LABEL_SIN_TIPIF,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        categorias.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            SelectionIndicator(isSelected)
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { onCallContact(contact) }) {
                AppIcon(
                    Icons.Default.Phone,
                    contentDescription = "Llamar a ${contact.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AssignUserDialog(
    users: List<String>,
    title: String,
    onAssign: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newUser by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (users.isEmpty()) {
                    Text(
                        "Aún no hay usuarios. Escribe uno nuevo abajo o asigna directamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    users.forEach { u ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onAssign(u) }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialsAvatar(name = u, size = 32.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                u,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            AppIcon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newUser,
                    onValueChange = { newUser = it },
                    label = { Text("Nuevo usuario") },
                    placeholder = { Text("Ej: María Pérez") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newUser.trim().isNotEmpty()) onAssign(newUser.trim())
                }
            ) { Text("Asignar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun SelectionIndicator(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            AppIcon(
                Icons.Default.Check,
                contentDescription = "Seleccionado",
                tint = MaterialTheme.colorScheme.onPrimary,
                size = 16.dp
            )
        }
    }
}

@Composable
private fun Avatar(name: String) {
    InitialsAvatar(name = name, size = 42.dp)
}