package com.example.scanprod.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Tienda tomada de OpenStreetMap; kind es la etiqueta "shop" (supermarket, bakery…)
data class Store(
    val id: Long,
    val name: String,
    val kind: String,
    val latitude: Double,
    val longitude: Double
)

private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
private const val MAX_STORES = 300

// Nombres en español de los tipos de tienda más comunes
private val KIND_LABELS = mapOf(
    "supermarket" to "Supermercado",
    "convenience" to "Tienda de barrio",
    "bakery" to "Panadería",
    "butcher" to "Carnicería",
    "greengrocer" to "Frutas y verduras",
    "clothes" to "Ropa",
    "shoes" to "Zapatos",
    "hardware" to "Ferretería",
    "doityourself" to "Ferretería",
    "chemist" to "Droguería",
    "pharmacy" to "Farmacia",
    "mobile_phone" to "Celulares",
    "electronics" to "Electrónica",
    "beauty" to "Belleza",
    "hairdresser" to "Peluquería",
    "car_repair" to "Taller mecánico",
    "car_parts" to "Repuestos",
    "furniture" to "Muebles",
    "optician" to "Óptica",
    "jewelry" to "Joyería",
    "books" to "Librería",
    "stationery" to "Papelería",
    "pet" to "Mascotas",
    "variety_store" to "Bazar",
    "department_store" to "Tienda departamental",
    "mall" to "Centro comercial",
    "kiosk" to "Kiosco",
    "alcohol" to "Licorería",
    "beverages" to "Bebidas",
    "seafood" to "Mariscos",
    "laundry" to "Lavandería",
    "gift" to "Regalos",
    "toys" to "Juguetes",
    "sports" to "Deportes",
    "bicycle" to "Bicicletas",
    "computer" to "Computadoras",
    "general" to "Tienda general"
)

fun Store.kindLabel(): String =
    KIND_LABELS[kind] ?: kind.replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * Busca en OpenStreetMap todas las tiendas a [radiusMeters] metros del punto.
 * Devuelve null si no hay internet o el servicio no responde.
 */
suspend fun nearbyStores(latitude: Double, longitude: Double, radiusMeters: Int): List<Store>? =
    withContext(Dispatchers.IO) {
        runCatching {
            val around = "(around:$radiusMeters,$latitude,$longitude)"
            val query = "[out:json][timeout:25];(node[\"shop\"]$around;way[\"shop\"]$around;);out center $MAX_STORES;"
            val connection = (URL(OVERPASS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "ScanProd/1.0 (Android)")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                connection.outputStream.use { it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray()) }
                if (connection.responseCode != 200) return@runCatching null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val elements = json.getJSONArray("elements")
                (0 until elements.length()).mapNotNull { i ->
                    val element = elements.getJSONObject(i)
                    val tags = element.optJSONObject("tags") ?: return@mapNotNull null
                    // Los nodos traen lat/lon; los edificios (way) traen su centro
                    val position = element.optJSONObject("center") ?: element
                    if (!position.has("lat")) return@mapNotNull null
                    val kind = tags.optString("shop", "general").ifBlank { "general" }
                    Store(
                        id = element.getLong("id"),
                        name = tags.optString("name").ifBlank { tags.optString("brand") }.ifBlank { "" },
                        kind = kind,
                        latitude = position.getDouble("lat"),
                        longitude = position.getDouble("lon")
                    )
                }.map { if (it.name.isBlank()) it.copy(name = it.kindLabel()) else it }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
