package com.masstext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.masstext.app.data.Contact

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
            subtitle = "${contacts.size} contactos guardados",
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
                        TextButton(onClick = { onSelectAll(contacts.map { it.id }) }) {
                            Text("Todos", fontSize = 13.sp)
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

        Spacer(modifier = Modifier.height(4.dp))

        if (contacts.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Contacts,
                title = "No hay contactos",
                description = "Importa un archivo Excel o agrega un contacto manualmente para empezar a enviar SMS.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
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
            users = (users + contacts.map { it.assignment }.filter { it.isNotBlank() }).distinct().sorted(),
            title = assignTitle,
            onAssign = { userName -> onConfirmAssign?.invoke(userName) },
            onDismiss = { showAssignDialog = false }
        )
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