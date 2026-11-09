package com.example.scanprod.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.scanprod.data.Product
import com.example.scanprod.data.ProductImages
import com.example.scanprod.data.Role
import com.example.scanprod.data.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// Producto que se está creando (original null) o modificando
internal data class Editing(val original: String?, val product: Product)

// Colores de acento del catálogo estilo tienda en línea
private val ShopOrange = Color(0xFFFB6A00)
private val ShopGreen = Color(0xFF0A8A3A)
// Por debajo de esta cantidad el stock se resalta como "últimas unidades"
private const val LOW_STOCK = 5

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
    onOpenLocation: () -> Unit,
    onOpenAr: () -> Unit,
    cartCount: Int,
    onOpenCart: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var search by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var toDelete by remember { mutableStateOf<Product?>(null) }
    // Código del producto recién guardado, para mostrar la confirmación con sus detalles
    var savedCode by remember { mutableStateOf<String?>(null) }
    // Código del producto que se tocó en el catálogo, para mostrar sus detalles
    var viewingCode by remember { mutableStateOf<String?>(null) }

    // Solo se vuelve a filtrar cuando cambian los productos o la búsqueda, no en cada repintado
    val filtered = remember(products, search) {
        products.filter {
            it.name.contains(search.trim(), ignoreCase = true) || it.code.contains(search.trim())
        }
    }

    // El Scaffold de MainActivity ya aplica los márgenes del sistema
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(windowInsets = WindowInsets(0)) {
                ShopNavItem("Inicio", Icons.Filled.Home, selected = true) {}
                ShopNavItem("Escanear", Icons.Filled.QrCodeScanner, onClick = onOpenScanner)
                ShopNavItem("AR", Icons.Filled.ViewInAr, onClick = onOpenAr)
                ShopNavItem("Carrito", Icons.Filled.ShoppingCart, badge = cartCount, onClick = onOpenCart)
                ShopNavItem("Ubicación", Icons.Filled.LocationOn, onClick = onOpenLocation)
                if (user.role == Role.ADMIN) {
                    ShopNavItem("Usuarios", Icons.Filled.AdminPanelSettings, onClick = onOpenAdmin)
                }
                ShopNavItem("Salir", Icons.AutoMirrored.Filled.Logout, onClick = onLogout)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Editing(null, Product("", "", 0, 0.0)) },
                containerColor = ShopOrange,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuevo") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // El botón de modo claro/oscuro va a la izquierda, al mismo nivel que la búsqueda
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)
            ) {
                ThemeToggleButton(darkTheme = darkTheme, onToggle = onToggleTheme)
                Spacer(modifier = Modifier.width(8.dp))
                SearchBar(
                    value = search,
                    onValueChange = { search = it },
                    onCameraClick = onOpenScanner,
                    modifier = Modifier.weight(1f)
                )
            }

            // Franja de resumen, como la de beneficios de las tiendas en línea
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                SummaryItem("${products.size} productos")
                Text(" | ", color = MaterialTheme.colorScheme.outline)
                SummaryItem("${products.sumOf { it.quantity }} unidades")
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.code }, contentType = { "product" }) { product ->
                        ProductCard(
                            product = product,
                            onOpen = { viewingCode = product.code },
                            onEdit = { editing = Editing(product.code, product) },
                            onDelete = { toDelete = product }
                        )
                    }
                    item(span = { GridItemSpan(2) }) { Spacer(modifier = Modifier.height(4.dp)) }
                }
            }
        }
    }

    editing?.let { current ->
        ProductDialog(
            editing = current,
            onSave = { product ->
                onSave(current.original, product).also { error ->
                    if (error == null) {
                        editing = null
                        savedCode = product.code
                    }
                }
            },
            onDismiss = { editing = null }
        )
    }

    products.find { it.code == savedCode }?.let { product ->
        AlertDialog(
            onDismissRequest = { savedCode = null },
            title = { Text("Producto guardado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(image = product.image, size = 56.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(product.name, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ProductFacts(product)
                }
            },
            confirmButton = {
                TextButton(onClick = { savedCode = null }) { Text("Aceptar") }
            }
        )
    }

    products.find { it.code == viewingCode }?.let { product ->
        AlertDialog(
            onDismissRequest = { viewingCode = null },
            title = { Text(product.name) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    ProductPhoto(
                        image = product.image,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ProductFacts(product)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewingCode = null
                    editing = Editing(product.code, product)
                }) { Text("Modificar") }
            },
            dismissButton = {
                TextButton(onClick = { viewingCode = null }) { Text("Cerrar") }
            }
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
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .padding(start = 18.dp, end = 4.dp)
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = "Buscar por nombre o código",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(ShopOrange),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Borrar búsqueda",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .padding(6.dp)
                    .size(20.dp)
                    .clickableNoRipple { onValueChange("") }
            )
        }
        Icon(
            Icons.Filled.PhotoCamera,
            contentDescription = "Escanear código",
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(24.dp)
                .clickableNoRipple(onCameraClick)
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 52.dp, height = 38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface)
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun SummaryItem(text: String) {
    Icon(Icons.Filled.Check, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(16.dp))
    Spacer(modifier = Modifier.width(2.dp))
    Text(text = text, color = ShopGreen, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ShopNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    badge: Int = 0,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (badge > 0) {
                BadgedBox(badge = { Badge(containerColor = ShopOrange) { Text(badge.toString()) } }) {
                    Icon(icon, contentDescription = null)
                }
            } else {
                Icon(icon, contentDescription = null)
            }
        },
        // Con 7 pestañas el espacio es justo: una sola línea para que "Ubicación" no se parta
        label = { Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = ShopOrange,
            selectedTextColor = ShopOrange,
            indicatorColor = Color.Transparent
        )
    )
}

// Clic sin la onda de Material, para íconos dentro de la barra de búsqueda
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = null,
            indication = null,
            onClick = onClick
        )
    )

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
                SavedDetails(initial)
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

/** Fecha y hora con el formato 06/11/2026 14:30. */
internal fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

private fun shopMoney(value: Double) = String.format(Locale.US, "$%.2f", value)

// Datos del producto, tanto en la ventana de detalles como en la confirmación de guardado
@Composable
private fun ProductFacts(product: Product) {
    DetailRow("Código", product.code)
    DetailRow("Cantidad", product.quantity.toString())
    DetailRow("Precio", shopMoney(product.price))
    DetailRow("Valor en stock", shopMoney(product.price * product.quantity))
    SavedDetails(product)
}

// Fechas y usuario del último guardado; los productos sin esos datos no muestran las filas
@Composable
private fun SavedDetails(product: Product) {
    product.createdAt?.let { DetailRow("Creado", formatDateTime(it)) }
    product.updatedAt?.let { DetailRow("Última modificación", formatDateTime(it)) }
    product.savedBy?.let { DetailRow("Guardado por", it) }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProductCard(
    product: Product,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Box con foto cuadrada: más liviano que BoxWithConstraints al deslizar la lista
        Box {
            ProductPhoto(image = product.image, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            // Botón pequeño para eliminar, sobre la esquina de la foto
            Surface(
                onClick = onDelete,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "Eliminar", modifier = Modifier.size(16.dp))
                }
            }
            if (product.quantity in 1..LOW_STOCK) {
                Text(
                    text = "ÚLTIMAS UNIDADES",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(ShopOrange)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = product.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = when {
                    product.quantity == 0 -> "AGOTADO"
                    product.quantity <= LOW_STOCK -> "Quedan ${product.quantity}"
                    else -> "Stock: ${product.quantity}"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (product.quantity <= LOW_STOCK) ShopOrange else MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Siempre ocupa una línea, para que las tarjetas de una misma fila tengan igual altura
            Text(
                text = product.updatedAt?.let { "Guardado: ${formatDateTime(it)}" } ?: "Sin fecha de guardado",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shopMoney(product.price),
                    color = ShopOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                OutlinedIconButton(
                    onClick = onEdit,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Modificar", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
