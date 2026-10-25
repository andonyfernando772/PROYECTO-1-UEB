package com.example.scanprod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.scanprod.data.Role
import com.example.scanprod.data.User

/**
 * Panel solo para administradores: ver usuarios, cambiar su rol y eliminarlos.
 * Las acciones devuelven un mensaje de error, o null si se realizaron.
 */
@Composable
fun AdminScreen(
    currentEmail: String,
    users: List<User>,
    onSetRole: (email: String, role: Role) -> String?,
    onDelete: (email: String) -> String?,
    onAddUser: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var error by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<User?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                text = "Usuarios",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${users.count { it.role == Role.ADMIN }} admin · " +
                    "${users.count { it.role == Role.CLIENTE }} clientes",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onAddUser, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null)
            Text("  Registrar usuario")
        }

        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(users, key = { it.email }) { user ->
                val isMe = user.email == currentEmail
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMe) "${user.name} (tú)" else user.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = user.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = user.role.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (user.role == Role.ADMIN) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // No se permite quitarse permisos ni eliminarse a uno mismo
                        if (!isMe) {
                            val next = if (user.role == Role.ADMIN) Role.CLIENTE else Role.ADMIN
                            IconButton(onClick = { error = onSetRole(user.email, next) }) {
                                Icon(
                                    imageVector = if (next == Role.ADMIN) Icons.Filled.AdminPanelSettings
                                    else Icons.Filled.Person,
                                    contentDescription = "Cambiar a ${next.label}"
                                )
                            }
                            IconButton(onClick = { toDelete = user }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Eliminar",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar usuario") },
            text = { Text("¿Eliminar la cuenta de ${user.email}?") },
            confirmButton = {
                TextButton(onClick = {
                    error = onDelete(user.email)
                    toDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancelar") }
            }
        )
    }
}
