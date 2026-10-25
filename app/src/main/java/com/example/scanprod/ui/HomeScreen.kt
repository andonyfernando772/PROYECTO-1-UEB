package com.example.scanprod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.scanprod.data.Role
import com.example.scanprod.data.User

data class ScannedProduct(val code: String, val name: String, val quantity: Int)

// Catálogo de ejemplo mientras no haya backend
private val catalog = mapOf(
    "7501000111111" to "Leche entera 1L",
    "7501000222222" to "Pan integral",
    "7501000333333" to "Café molido 500g",
    "7501000444444" to "Arroz 1kg",
    "7501000555555" to "Aceite vegetal 900ml"
)

@Composable
fun HomeScreen(
    user: User,
    onLogout: () -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var code by remember { mutableStateOf("") }
    val products = remember { mutableStateListOf<ScannedProduct>() }

    fun addProduct(scanned: String) {
        val trimmed = scanned.trim()
        if (trimmed.isEmpty()) return
        val index = products.indexOfFirst { it.code == trimmed }
        if (index >= 0) {
            products[index] = products[index].copy(quantity = products[index].quantity + 1)
        } else {
            products.add(0, ScannedProduct(trimmed, catalog[trimmed] ?: "Producto desconocido", 1))
        }
        code = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Bienvenido, ${user.name}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "${user.email} · ${user.role.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (user.role == Role.ADMIN) {
                IconButton(onClick = onOpenAdmin) {
                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Administrar usuarios")
                }
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Escanear producto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit) },
                    label = { Text("Código de barras") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    Button(
                        onClick = { addProduct(code) },
                        enabled = code.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Agregar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Simula la lectura de la cámara con un código del catálogo
                    OutlinedButton(
                        onClick = { addProduct(catalog.keys.random()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Escanear")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Productos escaneados",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Total: ${products.sumOf { it.quantity }}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aún no hay productos.\nEscanea o escribe un código.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(products, key = { it.code }) { product ->
                    ProductRow(
                        product = product,
                        onIncrease = { addProduct(product.code) },
                        onDecrease = {
                            val i = products.indexOf(product)
                            if (product.quantity > 1) {
                                products[i] = product.copy(quantity = product.quantity - 1)
                            } else {
                                products.removeAt(i)
                            }
                        },
                        onDelete = { products.remove(product) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductRow(
    product: ScannedProduct,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = product.code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDecrease) {
                Icon(Icons.Filled.Remove, contentDescription = "Restar")
            }
            Text(text = product.quantity.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onIncrease) {
                Icon(Icons.Filled.Add, contentDescription = "Sumar")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
