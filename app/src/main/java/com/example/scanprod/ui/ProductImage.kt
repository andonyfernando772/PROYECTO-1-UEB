package com.example.scanprod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Foto del producto con un tamaño fijo, o un ícono de caja si no tiene o no se pudo cargar. */
@Composable
fun ProductImage(image: String?, size: Dp, modifier: Modifier = Modifier, cornerRadius: Dp = size / 5) {
    ProductPhoto(image, modifier.size(size), cornerRadius)
}

/**
 * Foto del producto que ocupa el tamaño que le dé el modifier (por ejemplo, el ancho de una tarjeta).
 *
 * Usa AsyncImage y no SubcomposeAsyncImage: este último crea una subcomposición por imagen, lo que
 * hace que las listas se sientan con tirones al deslizar. El ícono de caja queda debajo de la foto
 * y se quita cuando la imagen termina de cargar.
 */
@Composable
fun ProductPhoto(image: String?, modifier: Modifier, cornerRadius: Dp = 0.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.secondaryContainer)
    ) {
        var loaded by remember(image) { mutableStateOf(false) }
        if (!loaded) {
            Icon(
                Icons.Filled.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
        if (image != null) {
            // Las imágenes de ejemplo vienen como android.resource://paquete/drawable/nombre
            val context = LocalContext.current
            val model: Any = remember(image) {
                if (image.startsWith("android.resource://")) {
                    context.resources.getIdentifier(image.substringAfterLast('/'), "drawable", context.packageName)
                        .takeIf { it != 0 } ?: image
                } else image
            }
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
