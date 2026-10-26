package com.example.scanprod.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.scanprod.data.Product
import com.example.scanprod.data.ProductImages
import com.example.scanprod.data.Role
import com.example.scanprod.data.User
import java.util.Locale
import kotlinx.coroutines.launch

// Producto que se está creando (original null) o modificando
internal data class Editing(val original: String?, val product: Product)

/**
 * Catálogo: muestra los productos de la base de datos y permite crearlos,
 * modificarlos y eliminarlos. onSave devuelve un mensaje de error, o null si se guardó.
 */
@Composable
fun HomeScreen(
    user: User,
    products: List<Product>,
    onSave: (originalCode: String?, product: Product) -> String?,
    onDelete: (code: String) -> Unit,
    onOpenScanner: () -> Unit,
    onLogout: () -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var search by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var toDelete by remember { mutableStateOf<Product?>(null) }

    val filtered = products.filter {
        it.name.contains(search.trim(), ignoreCase = true) || it.code.contains(search.trim())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Catálogo", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "${user.name} · ${user.role.label}",
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
            FilledIconButton(onClick = onOpenScanner) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Abrir escáner")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Buscar por nombre o código") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { editing = Editing(null, Product("", "", 0, 0.0)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Nuevo producto")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Productos (${products.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Unidades: ${products.sumOf { it.quantity }}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (products.isEmpty()) "El catálogo está vacío.\nCrea un producto."
                    else "Ningún producto coincide con la búsqueda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.code }) { product ->
                    ProductRow(
                        product = product,
                        onEdit = { editing = Editing(product.code, product) },
                        onDelete = { toDelete = product }
                    )
                }
            }
        }
    }

    editing?.let { current ->
        ProductDialog(
            editing = current,
            onSave = { onSave(current.original, it).also { error -> if (error == null) editing = null } },
            onDismiss = { editing = null }
        )
    }

    toDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar producto") },
            text = { Text("¿Eliminar \"${product.name}\" del catálogo?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(product.code)
                    toDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
internal fun ProductDialog(
    editing: Editing,
    onSave: (Product) -> String?,
    onDismiss: () -> Unit
) {
    val initial = editing.product
    var code by remember { mutableStateOf(initial.code) }
    var name by remember { mutableStateOf(initial.name) }
    var quantity by remember { mutableStateOf(initial.quantity.toString()) }
    var price by remember {
        mutableStateOf(if (initial.price == 0.0) "" else String.format(Locale.US, "%.2f", initial.price))
    }
    var image by remember { mutableStateOf(initial.image) }
    var error by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ProductImages.copyFromGallery(context, uri)?.let { image = it } }
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) scope.launch { image = ProductImages.saveBitmap(context, bitmap) }
    }
    // La app declara el permiso de cámara, así que hay que tenerlo antes de abrir la cámara del sistema
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) photoLauncher.launch(null)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing.original == null) "Nuevo producto" else "Modificar producto") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductImage(image = image, size = 88.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        TextButton(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) photoLauncher.launch(null)
                            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Text("  Tomar foto")
                        }
                        TextButton(onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                            Text("  Galería")
                        }
                        if (image != null) {
                            TextButton(onClick = { image = null }) { Text("Quitar foto") }
                        }
                    }
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim() },
                    label = { Text("Código") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter(Char::isDigit) },
                    label = { Text("Cantidad") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.replace(',', '.') },
                    label = { Text("Precio") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val qty = quantity.toIntOrNull()
                val cost = if (price.isBlank()) 0.0 else price.toDoubleOrNull()
                error = when {
                    code.isEmpty() -> "Escribe el código"
                    name.isBlank() -> "Escribe el nombre"
                    qty == null -> "Cantidad inválida"
                    cost == null || cost < 0 -> "Precio inválido"
                    else -> onSave(Product(code, name.trim(), qty, cost, image))
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ProductRow(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        ) {
            ProductImage(image = product.image, size = 56.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = product.code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(Locale.US, "$%.2f · Stock: %d", product.price, product.quantity),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Modificar")
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
