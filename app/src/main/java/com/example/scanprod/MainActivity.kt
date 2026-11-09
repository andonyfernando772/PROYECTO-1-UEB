package com.example.scanprod

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.scanprod.data.Cart
import com.example.scanprod.data.Product
import com.example.scanprod.data.ProductRepository
import com.example.scanprod.data.Role
import com.example.scanprod.data.User
import com.example.scanprod.data.UserRepository
import com.example.scanprod.ui.AdminScreen
import com.example.scanprod.ui.ArScreen
import com.example.scanprod.ui.CartScreen
import com.example.scanprod.ui.HomeScreen
import com.example.scanprod.ui.LocationScreen
import com.example.scanprod.ui.LoginScreen
import com.example.scanprod.ui.RegisterScreen
import com.example.scanprod.ui.ScannerScreen
import com.example.scanprod.ui.ThemeToggleButton
import com.example.scanprod.ui.theme.ScanProdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("scanprod_prefs", MODE_PRIVATE)
        setContent {
            // Modo oscuro/claro: al principio sigue al sistema; después se recuerda la elección
            val systemDark = isSystemInDarkTheme()
            var dark by remember {
                mutableStateOf(if (prefs.contains(KEY_DARK)) prefs.getBoolean(KEY_DARK, systemDark) else systemDark)
            }
            // Los íconos de la barra de estado cambian de color junto con el tema
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            ScanProdTheme(darkTheme = dark) {
                AppRoot(
                    darkTheme = dark,
                    onToggleTheme = {
                        dark = !dark
                        prefs.edit().putBoolean(KEY_DARK, dark).apply()
                    }
                )
            }
        }
    }

    private companion object {
        const val KEY_DARK = "dark_mode"
    }
}

// Botón pequeño en la esquina superior izquierda para cambiar entre modo claro y oscuro
@Composable
private fun ThemeToggleBar(darkTheme: Boolean, onToggle: () -> Unit) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(40.dp)
            .padding(start = 12.dp)
    ) {
        ThemeToggleButton(darkTheme, onToggle)
    }
}

private enum class Screen { LOGIN, REGISTER, HOME, SCANNER, LOCATION, AR, CART, ADMIN, ADMIN_REGISTER }

private val UserSaver = Saver<User?, List<String>>(
    save = { user -> user?.let { listOf(it.name, it.email, it.role.name) } },
    restore = { User(it[0], it[1], Role.valueOf(it[2])) }
)

@Composable
fun AppRoot(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { UserRepository(context.applicationContext) }
    // rememberSaveable mantiene la sesión abierta al girar la pantalla
    // Si Android cerró la app en segundo plano, la sesión se recupera de lo guardado en el teléfono
    val restored = remember { repository.restoreSession() }
    var user by rememberSaveable(stateSaver = UserSaver) { mutableStateOf(restored) }
    var screen by rememberSaveable { mutableStateOf(if (restored != null) Screen.HOME else Screen.LOGIN) }
    LaunchedEffect(user) { repository.saveSession(user?.email) }
    var users by remember { mutableStateOf(repository.users()) }
    val productRepository = remember { ProductRepository(context.applicationContext) }
    var products by remember { mutableStateOf(productRepository.products()) }
    // Carrito guardado en el teléfono
    val cart = remember { Cart(context.applicationContext) }

    // Ejecuta una acción del repositorio y refresca la lista si tuvo éxito
    fun applyChange(action: () -> String?): String? =
        action().also { if (it == null) users = repository.users() }

    // Guarda un producto y refresca el catálogo si tuvo éxito
    fun saveProduct(originalCode: String?, product: Product): String? =
        productRepository.save(originalCode, product, savedBy = user?.name).also {
            if (it == null) products = productRepository.products()
        }

    BackHandler(enabled = screen == Screen.REGISTER) { screen = Screen.LOGIN }
    BackHandler(enabled = screen == Screen.ADMIN) { screen = Screen.HOME }
    BackHandler(enabled = screen == Screen.SCANNER) { screen = Screen.HOME }
    BackHandler(enabled = screen == Screen.LOCATION) { screen = Screen.HOME }
    BackHandler(enabled = screen == Screen.AR) { screen = Screen.HOME }
    BackHandler(enabled = screen == Screen.CART) { screen = Screen.HOME }
    BackHandler(enabled = screen == Screen.ADMIN_REGISTER) { screen = Screen.ADMIN }

    // imePadding sube el contenido cuando aparece el teclado para que no tape los campos
    // Las pantallas con cámara o mapa ocupan todo el espacio y ya traen sus propios botones arriba
    // En el catálogo el botón va en la misma fila que la barra de búsqueda (dentro de HomeScreen)
    val showThemeToggle = screen != Screen.AR && screen != Screen.SCANNER && screen != Screen.LOCATION &&
        !(screen == Screen.HOME && user != null)

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = { if (showThemeToggle) ThemeToggleBar(darkTheme, onToggleTheme) }
    ) { innerPadding ->
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

            screen == Screen.LOCATION -> LocationScreen(
                onBack = { screen = Screen.HOME },
                modifier = padded
            )

            screen == Screen.AR -> ArScreen(
                products = products,
                cart = cart,
                onOpenCart = { screen = Screen.CART },
                onBack = { screen = Screen.HOME },
                modifier = padded
            )

            screen == Screen.CART -> CartScreen(
                products = products,
                cart = cart,
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
                onOpenLocation = { screen = Screen.LOCATION },
                onOpenAr = { screen = Screen.AR },
                cartCount = cart.items.sumOf { it.quantity },
                onOpenCart = { screen = Screen.CART },
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme,
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
