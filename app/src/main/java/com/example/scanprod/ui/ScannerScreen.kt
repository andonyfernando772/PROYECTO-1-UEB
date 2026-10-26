package com.example.scanprod.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scanprod.data.Product
import com.example.scanprod.data.lookupOnline
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.Locale
import kotlinx.coroutines.launch

private data class ScanEntry(val code: String, val count: Int)

// Guarda la lista de artículos como [código, cantidad, código, cantidad, ...] al girar la pantalla
private val SessionSaver = listSaver<SnapshotStateList<ScanEntry>, String>(
    save = { list -> list.flatMap { listOf(it.code, it.count.toString()) } },
    restore = { saved ->
        saved.chunked(2).map { ScanEntry(it[0], it[1].toInt()) }.toMutableStateList()
    }
)

private val AddedGreen = Color(0xFF1DA27E)

// Tiempo mínimo entre dos lecturas del mismo código, para no contarlo varias veces seguidas
private const val SAME_CODE_DELAY_MS = 2000L

/**
 * Escáner con la cámara en vivo: cada código leído suma una unidad al stock del producto,
 * o abre el formulario para crearlo si no está en el catálogo.
 */
@Composable
fun ScannerScreen(
    products: List<Product>,
    onSave: (originalCode: String?, product: Product) -> String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    var cameraOn by rememberSaveable { mutableStateOf(true) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var creating by remember { mutableStateOf<Editing?>(null) }
    var lastAdded by remember { mutableStateOf<Product?>(null) }
    // Código que se está buscando en internet
    var searching by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Lecturas hechas mientras esta pantalla está abierta
    val session = rememberSaveable(saver = SessionSaver) { mutableStateListOf() }
    val beep = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }
    val lastRead = remember { mutableMapOf<String, Long>() }

    DisposableEffect(Unit) { onDispose { beep.release() } }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun changeCount(product: Product, delta: Int) {
        val index = session.indexOfFirst { it.code == product.code }
        val count = (if (index >= 0) session[index].count else 0) + delta
        when {
            count <= 0 -> if (index >= 0) session.removeAt(index)
            index >= 0 -> session[index] = session[index].copy(count = count)
            else -> session.add(0, ScanEntry(product.code, count))
        }
    }

    fun addOne(product: Product) {
        onSave(product.code, product.copy(quantity = product.quantity + 1))
        changeCount(product, 1)
        lastAdded = product
        beep.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    fun onScanned(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || creating != null || showManual || searching != null) return
        val existing = products.find { it.code == trimmed }
        if (existing != null) {
            addOne(existing)
        } else {
            // Código nuevo: se busca en internet para rellenar nombre e imagen
            searching = trimmed
            scope.launch {
                val online = lookupOnline(trimmed)
                searching = null
                creating = Editing(null, Product(trimmed, online?.name.orEmpty(), 1, 0.0, online?.imageUrl))
            }
        }
    }

    val currentOnScanned by rememberUpdatedState(::onScanned)

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Color.Black)
        ) {
            when {
                !hasPermission -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    Text("La app necesita la cámara para escanear", color = Color.White)
                    Spacer(modifier = Modifier.size(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Permitir cámara")
                    }
                }

                cameraOn -> CameraPreview(
                    torchOn = torchOn,
                    onCode = { now, code ->
                        val previous = lastRead[code]
                        if (previous == null || now - previous > SAME_CODE_DELAY_MS) {
                            lastRead[code] = now
                            currentOnScanned(code)
                        }
                    }
                )

                else -> Text(
                    "Cámara apagada",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                OverlayButton(Icons.Filled.Menu, "Volver al catálogo", onBack)
                OverlayButton(Icons.Filled.Dialpad, "Escribir código") { showManual = true }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                OverlayButton(
                    if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    if (torchOn) "Apagar linterna" else "Encender linterna"
                ) { torchOn = !torchOn }
                OverlayButton(
                    if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    if (cameraOn) "Apagar cámara" else "Encender cámara"
                ) {
                    cameraOn = !cameraOn
                    if (!cameraOn) torchOn = false
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Artículos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${session.sumOf { it.count }} unid.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            if (session.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Apunta la cámara a un código de barras.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(session, key = { it.code }) { entry ->
                        val product = products.find { it.code == entry.code } ?: return@items
                        ArticleRow(
                            product = product,
                            count = entry.count,
                            onIncrease = { addOne(product) },
                            onDecrease = {
                                onSave(product.code, product.copy(quantity = maxOf(0, product.quantity - 1)))
                                changeCount(product, -1)
                            }
                        )
                    }
                }
            }

            searching?.let { code ->
                Spacer(modifier = Modifier.size(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Buscando $code en internet…", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            // Tarjeta con la foto y los datos del último producto escaneado
            lastAdded?.let { added ->
                val product = products.find { it.code == added.code } ?: added
                Spacer(modifier = Modifier.size(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AddedGreen)
                        .padding(10.dp)
                ) {
                    ProductImage(image = product.image, size = 64.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = " Agregado",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Text(
                            text = product.name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Stock: ${product.quantity}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = money(product.price),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showManual) {
        ManualCodeDialog(
            onConfirm = {
                showManual = false
                onScanned(it)
            },
            onDismiss = { showManual = false }
        )
    }

    creating?.let { current ->
        ProductDialog(
            editing = current,
            onSave = { product ->
                onSave(null, product).also {
                    if (it == null) {
                        changeCount(product, 1)
                        lastAdded = product
                        creating = null
                    }
                }
            },
            onDismiss = { creating = null }
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(torchOn: Boolean, onCode: (timeMs: Long, code: String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)

    val controller = remember {
        LifecycleCameraController(context).apply {
            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_QR_CODE
                    )
                    .build()
            )
            setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                val image = proxy.image
                if (image == null) {
                    proxy.close()
                    return@setImageAnalysisAnalyzer
                }
                scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { barcodes ->
                        barcodes.firstNotNullOfOrNull { it.rawValue }
                            ?.let { currentOnCode(System.currentTimeMillis(), it) }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    DisposableEffect(torchOn) {
        controller.enableTorch(torchOn)
        onDispose { }
    }

    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller } },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun OverlayButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White
        ),
        modifier = Modifier.clip(CircleShape)
    ) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun ArticleRow(product: Product, count: Int, onIncrease: () -> Unit, onDecrease: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        ProductImage(image = product.image, size = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = money(product.price),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDecrease) {
            Icon(Icons.Filled.Remove, contentDescription = "Restar")
        }
        Text(text = count.toString(), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onIncrease) {
            Icon(Icons.Filled.Add, contentDescription = "Sumar")
        }
        Text(
            text = money(product.price * count),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ManualCodeDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escribir código") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit) },
                label = { Text("Código de barras") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code) }, enabled = code.isNotBlank()) { Text("Agregar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun money(value: Double) = String.format(Locale.US, "$%.2f", value)
