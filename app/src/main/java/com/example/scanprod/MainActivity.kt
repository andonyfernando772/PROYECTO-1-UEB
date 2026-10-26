package com.example.scanprod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.scanprod.data.Product
import com.example.scanprod.data.ProductRepository
import com.example.scanprod.data.Role
import com.example.scanprod.data.User
import com.example.scanprod.data.UserRepository
import com.example.scanprod.ui.AdminScreen
import com.example.scanprod.ui.HomeScreen
import com.example.scanprod.ui.LoginScreen
import com.example.scanprod.ui.RegisterScreen
import com.example.scanprod.ui.ScannerScreen
import com.example.scanprod.ui.theme.ScanProdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanProdTheme {
                AppRoot()
            }
        }
    }
}

private enum class Screen { LOGIN, REGISTER, HOME, SCANNER, ADMIN, ADMIN_REGISTER }

private val UserSaver = Saver<User?, List<String>>(
    save = { user -> user?.let { listOf(it.name, it.email, it.role.name) } },
    restore = { User(it[0], it[1], Role.valueOf(it[2])) }
)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val repository = remember { UserRepository(context.applicationContext) }
    // rememberSaveable mantiene la sesión abierta al girar la pantalla
    var user by rememberSaveable(stateSaver = UserSaver) { mutableStateOf<User?>(null) }
    var screen by rememberSaveable { mutableStateOf(Screen.LOGIN) }
    var users by remember { mutableStateOf(repository.users()) }
    val productRepository = remember { ProductRepository(context.applicationContext) }
    var products by remember { mutableStateOf(productRepository.products()) }

    // Ejecuta una acción del repositorio y refresca la lista si tuvo éxito
    fun applyChange(action: () -> String?): String? =
        action().also { if (it == null) users = repository.users() }

    // Guarda un producto y refresca el catálogo si tuvo éxito
    fun saveProduct(originalCode: String?, product: Product): String? =
        productRepository.save(originalCode, product).also {
            if (it == null) products = productRepository.products()
        }

    BackHandler(enabled = screen == Screen.REGISTER) { screen = Screen.LOGIN }
    BackHandler(enabled = screen == Screen.ADMIN) { screen = Screen.HOME }
    BackHandler(enabled = screen == Screen.SCANNER) { screen = Screen.HOME }
    BackHandler(enabled = screen == Screen.ADMIN_REGISTER) { screen = Screen.ADMIN }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val padded = Modifier.padding(innerPadding)
        val current = user
        when {
            screen == Screen.REGISTER -> RegisterScreen(
                onRegister = { name, email, password, _ ->
                    // El registro público siempre crea clientes
                    repository.register(name, email, password, Role.CLIENTE).also {
                        if (it == null) {
                            user = repository.login(email, password)
                            screen = Screen.HOME
                        }
                    }
                },
                onBack = { screen = Screen.LOGIN },
                modifier = padded
            )

            current == null -> LoginScreen(
                authenticate = repository::login,
                onLogin = {
                    user = it
                    screen = Screen.HOME
                },
                onRegisterClick = { screen = Screen.REGISTER },
                modifier = padded
            )

            // Las pantallas de administración solo se muestran a administradores
            current.role == Role.ADMIN && screen == Screen.ADMIN_REGISTER -> RegisterScreen(
                onRegister = { name, email, password, role ->
                    applyChange { repository.register(name, email, password, role) }.also {
                        if (it == null) screen = Screen.ADMIN
                    }
                },
                onBack = { screen = Screen.ADMIN },
                modifier = padded,
                allowRoleChoice = true
            )

            current.role == Role.ADMIN && screen == Screen.ADMIN -> AdminScreen(
                currentEmail = current.email,
                users = users,
                onSetRole = { email, role -> applyChange { repository.setRole(email, role) } },
                onDelete = { email -> applyChange { repository.delete(email) } },
                onAddUser = { screen = Screen.ADMIN_REGISTER },
                onBack = { screen = Screen.HOME },
                modifier = padded
            )

            screen == Screen.SCANNER -> ScannerScreen(
                products = products,
                onSave = ::saveProduct,
                onBack = { screen = Screen.HOME },
                modifier = padded
            )

            else -> HomeScreen(
                user = current,
                products = products,
                onSave = ::saveProduct,
                onDelete = { code ->
                    productRepository.delete(code)
                    products = productRepository.products()
                },
                onOpenScanner = { screen = Screen.SCANNER },
                onLogout = {
                    user = null
                    screen = Screen.LOGIN
                },
                onOpenAdmin = {
                    users = repository.users()
                    screen = Screen.ADMIN
                },
                modifier = padded
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    ScanProdTheme {
        LoginScreen(authenticate = { _, _ -> null }, onLogin = {}, onRegisterClick = {})
    }
}
