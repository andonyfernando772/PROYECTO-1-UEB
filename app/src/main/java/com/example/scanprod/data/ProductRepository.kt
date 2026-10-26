package com.example.scanprod.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** image: ruta local (file://) o dirección web de la foto del producto, o null si no tiene. */
data class Product(
    val code: String,
    val name: String,
    val quantity: Int,
    val price: Double,
    val image: String? = null
)

// Inventario guardado en una base de datos SQLite del dispositivo
class ProductRepository(context: Context) :
    SQLiteOpenHelper(context, "scanprod.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE (" +
                "code TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "price REAL NOT NULL, " +
                "image TEXT)"
        )
        // Productos de ejemplo para empezar
        (SAMPLES_V1 + SAMPLES_V2).forEach { db.insert(TABLE, null, it.toValues()) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Columna para la foto de cada producto
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN image TEXT")
        }
        // Agrega los nuevos productos de ejemplo sin tocar los que ya existen
        if (oldVersion < 2) {
            SAMPLES_V2.forEach {
                db.insertWithOnConflict(TABLE, null, it.toValues(), SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }

    fun products(): List<Product> =
        readableDatabase.query(TABLE, COLUMNS, null, null, null, null, "name COLLATE NOCASE").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Product(c.getString(0), c.getString(1), c.getInt(2), c.getDouble(3), c.getString(4)))
                }
            }
        }

    /**
     * Crea el producto (originalCode null) o modifica el que tenía originalCode.
     * Devuelve un mensaje de error, o null si se guardó.
     */
    fun save(originalCode: String?, product: Product): String? {
        if (product.code != originalCode && exists(product.code)) {
            return "Ya existe un producto con ese código"
        }
        val db = writableDatabase
        if (originalCode == null) {
            db.insert(TABLE, null, product.toValues())
        } else {
            db.update(TABLE, product.toValues(), "code = ?", arrayOf(originalCode))
        }
        return null
    }

    fun delete(code: String) {
        writableDatabase.delete(TABLE, "code = ?", arrayOf(code))
    }

    private fun exists(code: String): Boolean =
        readableDatabase.query(TABLE, arrayOf("code"), "code = ?", arrayOf(code), null, null, null)
            .use { it.moveToFirst() }

    private fun Product.toValues() = ContentValues().apply {
        put("code", code)
        put("name", name)
        put("quantity", quantity)
        put("price", price)
        put("image", image)
    }

    companion object {
        private const val TABLE = "products"
        private val COLUMNS = arrayOf("code", "name", "quantity", "price", "image")

        private val SAMPLES_V1 = listOf(
            Product("7501000111111", "Leche entera 1L", 10, 1.25),
            Product("7501000222222", "Pan integral", 15, 0.90),
            Product("7501000333333", "Café molido 500g", 8, 4.50),
            Product("7501000444444", "Arroz 1kg", 20, 1.10),
            Product("7501000555555", "Aceite vegetal 900ml", 12, 2.75)
        )

        private val SAMPLES_V2 = listOf(
            Product("7501000666666", "Azúcar 1kg", 18, 1.05),
            Product("7501000777777", "Atún en lata 170g", 24, 1.60),
            Product("7501000888888", "Fideos 500g", 30, 0.85),
            Product("7501000999999", "Jabón de tocador", 25, 0.75),
            Product("7501000101010", "Detergente 1kg", 10, 3.20)
        )
    }
}
