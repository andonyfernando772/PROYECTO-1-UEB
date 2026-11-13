package com.example.scanprod.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * image: ruta local (file://) o dirección web de la foto del producto, o null si no tiene.
 * createdAt y updatedAt: fechas en milisegundos; savedBy: nombre de quien lo guardó por última vez.
 * Los tres los asigna ProductRepository.save y son null si el producto nunca se ha guardado con ellos.
 */
data class Product(
    val code: String,
    val name: String,
    val quantity: Int,0
    val price: Double,
    val image: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val savedBy: String? = null
)

// Inventario guardado en una base de datos SQLite del dispositivo
class ProductRepository(context: Context) :
    SQLiteOpenHelper(context, "scanprod.db", null, 5) {

    private val packageName = context.packageName

    // Asigna la imagen de ejemplo a los productos que todavía no tienen foto
    private fun applySampleImages(db: SQLiteDatabase) {
        SAMPLE_IMAGES.forEach { (code, drawable) ->
            db.execSQL(
                "UPDATE $TABLE SET image = ? WHERE code = ? AND image IS NULL",
                arrayOf("android.resource://$packageName/drawable/$drawable", code)
            )
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE (" +
                "code TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "price REAL NOT NULL, " +
                "image TEXT, " +
                "created_at INTEGER, " +
                "updated_at INTEGER, " +
                "saved_by TEXT)"
        )
        // Productos de ejemplo para empezar
        (SAMPLES_V1 + SAMPLES_V2).forEach { db.insert(TABLE, null, it.toValues()) }
        applySampleImages(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Columna para la foto de cada producto
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN image TEXT")
        }
        // Columnas con las fechas y el usuario que guardó el producto
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN created_at INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN updated_at INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN saved_by TEXT")
        }
        // Agrega los nuevos productos de ejemplo sin tocar los que ya existen
        if (oldVersion < 2) {
            SAMPLES_V2.forEach {
                db.insertWithOnConflict(TABLE, null, it.toValues(), SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
        // Imagen para cada producto de ejemplo
        if (oldVersion < 4) applySampleImages(db)
    }

    fun products(): List<Product> =
        readableDatabase.query(TABLE, COLUMNS, null, null, null, null, "name COLLATE NOCASE").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Product(
                            code = c.getString(0),
                            name = c.getString(1),
                            quantity = c.getInt(2),
                            price = c.getDouble(3),
                            image = c.getString(4),
                            createdAt = if (c.isNull(5)) null else c.getLong(5),
                            updatedAt = if (c.isNull(6)) null else c.getLong(6),
                            savedBy = c.getString(7)
                        )
                    )
                }
            }
        }

    /**
     * Crea el producto (originalCode null) o modifica el que tenía originalCode.
     * Registra la fecha y quién lo guardó; al modificar se conserva la fecha de creación.
     * Devuelve un mensaje de error, o null si se guardó.
     */
    fun save(originalCode: String?, product: Product, savedBy: String?): String? {
        if (product.code != originalCode && exists(product.code)) {
            return "Ya existe un producto con ese código"
        }
        val now = System.currentTimeMillis()
        val values = product.toValues().apply {
            put("updated_at", now)
            put("saved_by", savedBy)
        }
        val db = writableDatabase
        if (originalCode == null) {
            values.put("created_at", now)
            db.insert(TABLE, null, values)
        } else {
            db.update(TABLE, values, "code = ?", arrayOf(originalCode))
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
        private val COLUMNS =
            arrayOf("code", "name", "quantity", "price", "image", "created_at", "updated_at", "saved_by")

        // Código del producto de ejemplo -> imagen en res/drawable-nodpi
        private val SAMPLE_IMAGES = mapOf(
            "7501000111111" to "prod_leche",
            "7501000222222" to "prod_pan",
            "7501000333333" to "prod_cafe",
            "7501000444444" to "prod_arroz",
            "7501000555555" to "prod_aceite",
            "7501000666666" to "prod_azucar",
            "7501000777777" to "prod_atun",
            "7501000888888" to "prod_fideos",
            "7501000999999" to "prod_jabon",
            "7501000101010" to "prod_detergente"
        )

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
