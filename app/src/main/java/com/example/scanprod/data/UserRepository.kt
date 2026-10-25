package com.example.scanprod.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject

enum class Role(val label: String) {
    ADMIN("Administrador"),
    CLIENTE("Cliente")
}

data class User(val name: String, val email: String, val role: Role)

private data class StoredUser(val user: User, val salt: String, val hash: String)

// Usuarios guardados en el dispositivo mientras no haya backend
class UserRepository(context: Context) {
    private val prefs = context.getSharedPreferences("scanprod_users", Context.MODE_PRIVATE)

    init {
        if (load().isEmpty()) {
            // Cuenta inicial para poder administrar la app
            save(listOf(newStoredUser("Administrador", DEFAULT_ADMIN_EMAIL, "123456", Role.ADMIN)))
        }
    }

    fun users(): List<User> = load().map { it.user }

    fun login(email: String, password: String): User? {
        val stored = load().find { it.user.email.equals(email.trim(), ignoreCase = true) }
            ?: return null
        return stored.user.takeIf { hash(password, stored.salt) == stored.hash }
    }

    /** Devuelve un mensaje de error, o null si se registró correctamente. */
    fun register(name: String, email: String, password: String, role: Role = Role.CLIENTE): String? {
        val all = load()
        if (all.any { it.user.email.equals(email.trim(), ignoreCase = true) }) {
            return "Ya existe una cuenta con ese correo"
        }
        save(all + newStoredUser(name.trim(), email.trim(), password, role))
        return null
    }

    /** Devuelve un mensaje de error, o null si se cambió el rol. */
    fun setRole(email: String, role: Role): String? {
        val all = load()
        if (role == Role.CLIENTE && isLastAdmin(all, email)) {
            return "Debe quedar al menos un administrador"
        }
        save(all.map { if (it.user.email == email) it.copy(user = it.user.copy(role = role)) else it })
        return null
    }

    /** Devuelve un mensaje de error, o null si se eliminó. */
    fun delete(email: String): String? {
        val all = load()
        if (isLastAdmin(all, email)) return "Debe quedar al menos un administrador"
        save(all.filterNot { it.user.email == email })
        return null
    }

    private fun isLastAdmin(all: List<StoredUser>, email: String): Boolean {
        val admins = all.filter { it.user.role == Role.ADMIN }
        return admins.size == 1 && admins[0].user.email == email
    }

    private fun newStoredUser(name: String, email: String, password: String, role: Role): StoredUser {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        return StoredUser(User(name, email, role), salt, hash(password, salt))
    }

    private fun hash(password: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + password).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun load(): List<StoredUser> {
        val array = JSONArray(prefs.getString(KEY_USERS, "[]"))
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            StoredUser(
                User(o.getString("name"), o.getString("email"), Role.valueOf(o.getString("role"))),
                o.getString("salt"),
                o.getString("hash")
            )
        }
    }

    private fun save(users: List<StoredUser>) {
        val array = JSONArray()
        users.forEach {
            array.put(
                JSONObject()
                    .put("name", it.user.name)
                    .put("email", it.user.email)
                    .put("role", it.user.role.name)
                    .put("salt", it.salt)
                    .put("hash", it.hash)
            )
        }
        prefs.edit().putString(KEY_USERS, array.toString()).apply()
    }

    companion object {
        const val DEFAULT_ADMIN_EMAIL = "admin@scanprod.com"
        private const val KEY_USERS = "users"
    }
}
