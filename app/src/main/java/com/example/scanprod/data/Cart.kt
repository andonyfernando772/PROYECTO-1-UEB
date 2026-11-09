package com.example.scanprod.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** savedAt: fecha en milisegundos del último cambio; null en lo guardado antes de registrarla. */
data class CartItem(val code: String, val quantity: Int, val savedAt: Long? = null)

/**
 * Carrito de productos seleccionados (código y cantidad). Se guarda en el teléfono en cada cambio,
 * así que sigue ahí aunque se cierre la app. La lista es observable desde Compose.
 */
class Cart(context: Context) {
    private val prefs = context.getSharedPreferences("scanprod_cart", Context.MODE_PRIVATE)

    val items = mutableStateListOf<CartItem>().apply { addAll(load()) }

    fun quantityOf(code: String): Int = items.find { it.code == code }?.quantity ?: 0

    fun savedAtOf(code: String): Long? = items.find { it.code == code }?.savedAt

    fun add(code: String, amount: Int = 1) = setQuantity(code, quantityOf(code) + amount)

    /** Cambia la cantidad de un producto; con 0 o menos lo quita del carrito. */
    fun setQuantity(code: String, quantity: Int) {
        val index = items.indexOfFirst { it.code == code }
        val item = CartItem(code, quantity, System.currentTimeMillis())
        when {
            quantity <= 0 -> if (index >= 0) items.removeAt(index)
            index >= 0 -> items[index] = item
            else -> items.add(0, item)
        }
        persist()
    }

    fun clear() {
        items.clear()
        persist()
    }

    private fun load(): List<CartItem> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            CartItem(o.getString("code"), o.getInt("qty"), if (o.has("savedAt")) o.getLong("savedAt") else null)
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().put("code", it.code).put("qty", it.quantity).put("savedAt", it.savedAt))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
