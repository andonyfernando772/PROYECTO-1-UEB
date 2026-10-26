package com.example.scanprod.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Fotos de productos guardadas en la carpeta privada de la app
object ProductImages {

    /** Guarda una foto tomada con la cámara y devuelve su ruta (file://...). */
    suspend fun saveBitmap(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val file = newFile(context)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Uri.fromFile(file).toString()
    }

    /** Copia una imagen elegida de la galería y devuelve su ruta (file://...). */
    suspend fun copyFromGallery(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(source) ?: return@withContext null
        val file = newFile(context)
        input.use { file.outputStream().use { out -> it.copyTo(out) } }
        Uri.fromFile(file).toString()
    }

    private fun newFile(context: Context): File =
        File(context.filesDir, "images").apply { mkdirs() }
            .resolve("product_${System.currentTimeMillis()}.jpg")
}

data class OnlineProduct(val name: String?, val imageUrl: String?)

/**
 * Busca un código de barras en Open Food Facts (base de datos gratuita de productos).
 * Devuelve null si no hay internet o el producto no está registrado.
 */
suspend fun lookupOnline(code: String): OnlineProduct? = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL(
            "https://world.openfoodfacts.org/api/v2/product/$code.json" +
                "?fields=product_name,product_name_es,image_front_url"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", "ScanProd/1.0 (Android)")
        }
        try {
            if (connection.responseCode != 200) return@runCatching null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optInt("status") != 1) return@runCatching null
            val product = json.getJSONObject("product")
            val name = product.optString("product_name_es").ifBlank { product.optString("product_name") }
            val image = product.optString("image_front_url")
            OnlineProduct(name.ifBlank { null }, image.ifBlank { null })
                .takeIf { it.name != null || it.imageUrl != null }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
