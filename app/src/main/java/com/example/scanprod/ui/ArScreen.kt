package com.example.scanprod.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scanprod.data.Cart
import com.example.scanprod.data.Product
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// Código detectado y su recuadro en píxeles de la imagen que analiza la cámara
private data class ArTarget(val code: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

// Todos los códigos de un fotograma, con el tamaño de ese fotograma (ya girado)
private data class ArFrame(val targets: List<ArTarget>, val imageWidth: Int, val imageHeight: Int)

private val ArGreen = Color(0xFF1DA27E)
private val ArOrange = Color(0xFFFB6A00)
private val ArRed = Color(0xFFD93025)
private val ArGray = Color(0xFF5F6368)

// Por debajo de esta cantidad la tarjeta avisa de "pocas unidades"
private const val AR_LOW_STOCK = 5

private val CardWidth = 172.dp
private val CardHeight = 62.dp
private val CardGap = 10.dp

/**
 * Realidad aumentada: la cámara en vivo detecta los códigos de barras y dibuja sobre
 * cada uno una tarjeta flotante con el nombre, precio y stock del producto del catálogo.
 */
@Composable
fun ArScreen(
    products: List<Product>,
    cart: Cart,
    onOpenCart: () -> Unit,
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
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var zoomed by rememberSaveable { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val flash = remember { Animatable(0f) }

    // Códigos seguidos: cada recuadro se suaviza entre fotogramas y se conserva un instante
    // al perderse, para que no parpadee ni tiemble
    val tracked = remember { mutableStateMapOf<String, TrackedTarget>() }
    var imageSize by remember { mutableStateOf(IntSize(1, 1)) }
    val frame = ArFrame(tracked.values.map { it.target }, imageSize.width, imageSize.height)

    fun onFrame(new: ArFrame) {
        imageSize = IntSize(new.imageWidth, new.imageHeight)
        val now = SystemClock.uptimeMillis()
        new.targets.forEach { target ->
            val old = tracked[target.code]?.target
            tracked[target.code] = TrackedTarget(old?.let { smooth(it, target) } ?: target, now)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            val now = SystemClock.uptimeMillis()
            tracked.keys.filter { now - (tracked[it]?.seenAt ?: 0L) > LOST_AFTER_MS }
                .forEach { tracked.remove(it) }
        }
    }

    // Agrega un producto al carrito y avisa con una vibración corta
    fun addToCart(code: String) {
        cart.add(code)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        Toast.makeText(context, "Agregado al carrito", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
    ) {
        if (!hasPermission) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text("La app necesita la cámara para la realidad aumentada", color = Color.White)
                Spacer(modifier = Modifier.size(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Permitir cámara")
                }
            }
        } else {
            ArCamera(torchOn = torchOn, zoomed = zoomed, onFrame = ::onFrame)
            ArOverlay(frame = frame, viewSize = viewSize, products = products, onCardClick = ::addToCart)
        }

        ArButton(
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al catálogo") },
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )
        if (hasPermission) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                ArButton(
                    icon = {
                        Icon(
                            if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = if (torchOn) "Apagar linterna" else "Encender linterna"
                        )
                    },
                    onClick = { torchOn = !torchOn }
                )
                // Zoom para leer códigos pequeños o lejanos (también se puede hacer con dos dedos)
                ArButton(
                    icon = { Text(if (zoomed) "2x" else "1x", fontWeight = FontWeight.Bold) },
                    onClick = { zoomed = !zoomed }
                )
            }
        }

        if (hasPermission) {
            val count = frame.targets.size
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Text(
                    text = when (count) {
                        0 -> "Apunta la cámara a los productos"
                        1 -> "1 producto detectado"
                        else -> "$count productos detectados"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.size(12.dp))
                // Botón de captura: agrega a la lista los productos que se ven en este momento
                IconButton(
                    onClick = {
                        val codes = frame.targets.map { it.code }.distinct()
                            .filter { code -> products.any { it.code == code } }
                        if (codes.isEmpty()) {
                            Toast.makeText(context, "No hay productos registrados a la vista", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            codes.forEach { cart.add(it) }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                flash.snapTo(0.7f)
                                flash.animateTo(0f, tween(250))
                            }
                            Toast.makeText(
                                context,
                                if (codes.size == 1) "Agregado al carrito" else "${codes.size} productos agregados al carrito",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White),
                    modifier = Modifier
                        .size(68.dp)
                        .border(4.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Agregar a la lista",
                        tint = Color(0xFF202124)
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                if (cart.items.isNotEmpty()) {
                    CapturedList(
                        cart = cart,
                        products = products,
                        onOpenCart = onOpenCart,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Destello blanco al tomar la foto
        if (flash.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flash.value))
            )
        }
    }
}

// Código seguido: recuadro actual y la última vez que se vio
private data class TrackedTarget(val target: ArTarget, val seenAt: Long)

// Tiempo que se conserva un recuadro después de perder el código de vista
private const val LOST_AFTER_MS = 450L

// Acerca el recuadro anterior al nuevo a la mitad del camino, para quitar el temblor
private fun smooth(old: ArTarget, new: ArTarget): ArTarget {
    fun mix(a: Float, b: Float) = a + (b - a) * 0.5f
    return ArTarget(
        new.code,
        mix(old.left, new.left), mix(old.top, new.top),
        mix(old.right, new.right), mix(old.bottom, new.bottom)
    )
}

/** Carrito guardado: cantidad por producto, subtotal y total. */
@Composable
private fun CapturedList(
    cart: Cart,
    products: List<Product>,
    onOpenCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = cart.items.mapNotNull { (code, qty) -> products.find { it.code == code }?.let { it to qty } }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Carrito · ${rows.sumOf { it.second }} unid.",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF202124),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = arMoney(rows.sumOf { (p, q) -> p.price * q }),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF202124)
            )
            TextButton(onClick = onOpenCart) { Text("Ver carrito") }
        }
        LazyColumn(modifier = Modifier.heightIn(max = 190.dp)) {
            items(rows, key = { it.first.code }) { (product, qty) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    ProductImage(image = product.image, size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF202124),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = arMoney(product.price * qty),
                            style = MaterialTheme.typography.bodySmall,
                            color = ArGray
                        )
                        cart.savedAtOf(product.code)?.let {
                            Text(
                                text = "Guardado: ${formatDateTime(it)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ArGray
                            )
                        }
                    }
                    IconButton(onClick = { cart.setQuantity(product.code, qty - 1) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Restar")
                    }
                    Text(text = qty.toString(), color = Color(0xFF202124), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { cart.setQuantity(product.code, qty + 1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Sumar")
                    }
                }
            }
        }
    }
}

private fun arMoney(value: Double) = String.format(Locale.US, "$%.2f", value)

/** Dibuja el recuadro y la tarjeta de cada código detectado sobre la imagen de la cámara. */
@Composable
private fun ArOverlay(
    frame: ArFrame,
    viewSize: IntSize,
    products: List<Product>,
    onCardClick: (code: String) -> Unit
) {
    if (frame.targets.isEmpty() || viewSize.width == 0 || viewSize.height == 0) return
    val density = LocalDensity.current

    // La vista previa llena la pantalla recortando los bordes (FILL_CENTER): se replica esa transformación
    val scale = max(
        viewSize.width / frame.imageWidth.toFloat(),
        viewSize.height / frame.imageHeight.toFloat()
    )
    val dx = (viewSize.width - frame.imageWidth * scale) / 2f
    val dy = (viewSize.height - frame.imageHeight * scale) / 2f
    val cardW = with(density) { CardWidth.toPx() }
    val cardH = with(density) { CardHeight.toPx() }
    val gap = with(density) { CardGap.toPx() }

    frame.targets.forEach { target ->
        val left = target.left * scale + dx
        val top = target.top * scale + dy
        val right = target.right * scale + dx
        val bottom = target.bottom * scale + dy
        val product = products.find { it.code == target.code }
        val accent = when {
            product == null -> ArGray
            product.quantity <= 0 -> ArRed
            product.quantity < AR_LOW_STOCK -> ArOrange
            else -> ArGreen
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = accent,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // La tarjeta va centrada sobre el código; si no cabe arriba, se pone debajo
        val x = ((left + right) / 2f - cardW / 2f).coerceIn(0f, (viewSize.width - cardW).coerceAtLeast(0f))
        val above = top - cardH - gap
        val y = if (above >= 0f) above else (bottom + gap).coerceAtMost(viewSize.height - cardH)

        ArCard(
            product = product,
            code = target.code,
            accent = accent,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                // Tocar la tarjeta de un producto registrado lo agrega al carrito
                .then(if (product != null) Modifier.clickable { onCardClick(target.code) } else Modifier)
        )
    }
}

@Composable
private fun ArCard(product: Product?, code: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .width(CardWidth)
            .height(CardHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.94f))
            .padding(8.dp)
    ) {
        if (product != null) {
            ProductImage(image = product.image, size = 44.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("?", color = accent, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = product?.name ?: "No registrado",
                color = Color(0xFF202124),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (product != null) {
                Text(
                    text = String.format(Locale.US, "$%.2f", product.price),
                    color = Color(0xFF202124),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        product.quantity <= 0 -> "Agotado"
                        product.quantity < AR_LOW_STOCK -> "Últimas ${product.quantity} unid."
                        else -> "Stock: ${product.quantity}"
                    },
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = code,
                    color = ArGray,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun ArCamera(torchOn: Boolean, zoomed: Boolean, onFrame: (ArFrame) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_QR_CODE
                )
                .build()
        )
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            // Imagen de análisis más grande para leer códigos pequeños o lejanos. Se mantiene 4:3,
            // igual que la vista previa, para que los recuadros coincidan con lo que se ve
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(1440, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
            setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                val image = proxy.image
                if (image == null) {
                    proxy.close()
                    return@setImageAnalysisAnalyzer
                }
                val rotation = proxy.imageInfo.rotationDegrees
                // ML Kit entrega los recuadros ya girados: con 90° o 270° el ancho y el alto se intercambian
                val sideways = rotation % 180 != 0
                val width = if (sideways) proxy.height else proxy.width
                val height = if (sideways) proxy.width else proxy.height
                scanner.process(InputImage.fromMediaImage(image, rotation))
                    .addOnSuccessListener { barcodes ->
                        val targets = barcodes.mapNotNull { barcode ->
                            val box = barcode.boundingBox ?: return@mapNotNull null
                            val code = barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
                                ?: return@mapNotNull null
                            ArTarget(
                                code,
                                box.left.toFloat(), box.top.toFloat(),
                                box.right.toFloat(), box.bottom.toFloat()
                            )
                        }
                        currentOnFrame(ArFrame(targets, width, height))
                    }
                    .addOnCompleteListener { proxy.close() }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    DisposableEffect(scanner) { onDispose { scanner.close() } }
    DisposableEffect(torchOn) {
        controller.enableTorch(torchOn)
        onDispose { }
    }
    // Zoom del botón 1x/2x (el zoom con dos dedos y tocar para enfocar ya vienen activados)
    DisposableEffect(zoomed) {
        controller.setLinearZoom(if (zoomed) 0.3f else 0f)
        onDispose { }
    }

    AndroidView(
        factory = {
            PreviewView(it).apply {
                this.controller = controller
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ArButton(icon: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White
        ),
        modifier = modifier.clip(CircleShape)
    ) {
        icon()
    }
}
