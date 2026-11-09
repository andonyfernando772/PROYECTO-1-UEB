package com.example.scanprod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanprod.data.Cart
import com.example.scanprod.data.Product
import java.util.Locale

private val CartOrange = androidx.compose.ui.graphics.Color(0xFFFB6A00)

/**
 * Carrito guardado con los productos seleccionados (por ejemplo, los que se capturaron en la
 * pantalla AR) y, debajo, todo el catálogo para agregar más productos.
 */
@Composable
fun CartScreen(
    products: List<Product>,
    cart: Cart,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = cart.items.mapNotNull { (code, qty) -> products.find { it.code == code }?.let { it to qty } }
    val units = rows.sumOf { it.second }
    val total = rows.sumOf { (product, qty) -> product.price * qty }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al catálogo")
            }
            Text("Carrito", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Mi carrito · $units unid.",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (rows.isNotEmpty()) TextButton(onClick = { cart.clear() }) { Text("Vaciar") }
                }
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        text = "Todavía no hay productos. Toma una foto en AR o agrégalos desde el catálogo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(rows, key = { "cart_" + it.first.code }) { (product, qty) ->
                    CartRow(
                        product = product,
                        quantity = qty,
                        cart = cart,
                        showSubtotal = true,
                        savedAt = cart.savedAtOf(product.code)
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = cartMoney(total),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = CartOrange
                        )
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Catálogo completo · ${products.size} productos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(products, key = { "cat_" + it.code }) { product ->
                CartRow(product = product, quantity = cart.quantityOf(product.code), cart = cart, showSubtotal = false)
            }
        }
    }
}

/** Fila de producto: con cantidad 0 muestra "Agregar"; si ya está en el carrito, − cantidad +. */
@Composable
private fun CartRow(
    product: Product,
    quantity: Int,
    cart: Cart,
    showSubtotal: Boolean,
    savedAt: Long? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        ProductImage(image = product.image, size = 52.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (showSubtotal) {
                    "${cartMoney(product.price)} c/u · ${cartMoney(product.price * quantity)}"
                } else {
                    "${cartMoney(product.price)} · Stock: ${product.quantity}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            savedAt?.let {
                Text(
                    text = "Guardado: ${formatDateTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (quantity == 0) {
            Button(onClick = { cart.add(product.code) }) { Text("Agregar") }
        } else {
            IconButton(onClick = { cart.setQuantity(product.code, quantity - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Restar")
            }
            Text(text = quantity.toString(), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { cart.setQuantity(product.code, quantity + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "Sumar")
            }
        }
    }
}

private fun cartMoney(value: Double) = String.format(Locale.US, "$%.2f", value)
