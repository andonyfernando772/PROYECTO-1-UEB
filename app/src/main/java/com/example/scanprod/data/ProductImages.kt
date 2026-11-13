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


// Bases de datos abiertas: comida, cosméticos, productos generales y comida de mascotas
private val OPEN_FACTS_HOSTS = listOf(
    "world.openfoodfacts.org",
    "world.openbeautyfacts.org",
    "world.openproductsfacts.org",
    "world.openpetfoodfacts.org"
)

private fun getJson(url: String): JSONObject? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000
        readTimeout = 5000
        setRequestProperty("User-Agent", "ScanProd/1.0 (Android)")
    }
    return try {
        if (connection.responseCode != 200) null
        else JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
        connection.disconnect()
    }
}

/**
 * Busca un código de barras en internet: primero en las bases Open Facts (gratuitas) y,
 * si no está, en UPCitemdb (productos en general). Devuelve null si no hay internet
 * o el producto no está registrado.
 */
suspend fun lookupOnline(code: String): OnlineProduct? = withContext(Dispatchers.IO) {
    for (host in OPEN_FACTS_HOSTS) {
        val found = runCatching {
            val json = getJson(
                "https://$host/api/v2/product/$code.json" +
                    "?fields=product_name,product_name_es,brands,image_front_url"
            ) ?: return@runCatching null
            if (json.optInt("status") != 1) return@runCatching null
            val product = json.getJSONObject("product")
            val name = product.optString("product_name_es").ifBlank { product.optString("product_name") }
            val brand = product.optString("brands").substringBefore(',').trim()
            val full = if (brand.isNotBlank() && !name.contains(brand, ignoreCase = true)) "$name $brand".trim() else name
            OnlineProduct(full.ifBlank { null }, product.optString("image_front_url").ifBlank { null })
                .takeIf { it.name != null || it.imageUrl != null }
        }.getOrNull()
        if (found != null) return@withContext found
    }
    runCatching {
        val json = getJson("https://api.upcitemdb.com/prod/trial/lookup?upc=$code") ?: return@runCatching null
        val item = json.optJSONArray("items")?.optJSONObject(0) ?: return@runCatching null
        val image = item.optJSONArray("images")?.optString(0).orEmpty()
        OnlineProduct(item.optString("title").ifBlank { null }, image.ifBlank { null })
            .takeIf { it.name != null || it.imageUrl != null }
    }.getOrNull()
}
