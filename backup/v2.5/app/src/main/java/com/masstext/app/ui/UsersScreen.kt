package com.masstext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun UsersScreen(
    users: List<String>,
    countFor: (String) -> Int,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit
) {
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
                            contentDescription = "Volver a Contactos",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Usuarios",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Crea usuarios (nombres) para asignarlos a tus contactos. Después podrás exportar repartos por usuario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                var newUserName by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { newUserName = it },
                        label = { Text("Nombre del usuario") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PrimaryActionButton(
                        text = "Agregar",
                        onClick = {
                            if (newUserName.trim().isNotEmpty()) {
                                onAdd(newUserName.trim())
                                newUserName = ""
                            }
                        },
                        icon = Icons.Default.PersonAdd,
                        compact = true
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (users.isEmpty()) {
                    Text(
                        "Sin usuarios todavía. Agrega el primero con el campo de arriba.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(users, key = { it }) { user ->
                            UserRow(
                                name = user,
                                count = countFor(user),
                                onRename = { newName -> onRename(user, newName) },
                                onRemove = { onRemove(user) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    name: String,
    count: Int,
    onRename: (String) -> Unit,
    onRemove: () -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LocalAppShape.current,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(name = name, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    if (count == 1) "1 contacto asignado" else "$count contactos asignados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showRename = true }) {
                AppIcon(
                    Icons.Default.Edit,
                    contentDescription = "Renombrar",
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { showDelete = true }) {
                AppIcon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar usuario",
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showRename) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Renombrar usuario") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.trim().isNotEmpty()) onRename(text.trim())
                    showRename = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Eliminar usuario") },
            text = { Text("¿Eliminar \"$name\"? Los contactos asignados a este usuario perderán su asignación.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    showDelete = false
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancelar") }
            }
        )
    }
}