package com.example.scanprod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.scanprod.data.Role

/**
 * Formulario de registro. Desde el login solo crea clientes; un administrador
 * puede usarlo con [allowRoleChoice] para crear cuentas con cualquier rol.
 * [onRegister] devuelve un mensaje de error, o null si se creó la cuenta.
 */
@Composable
fun RegisterScreen(
    onRegister: (name: String, email: String, password: String, role: Role) -> String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    allowRoleChoice: Boolean = false
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(Role.CLIENTE) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var emailError by rememberSaveable { mutableStateOf(false) }
    var passwordError by rememberSaveable { mutableStateOf(false) }
    var confirmError by rememberSaveable { mutableStateOf(false) }
    var generalError by rememberSaveable { mutableStateOf<String?>(null) }

    val visual = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (allowRoleChoice) "Nuevo usuario" else "Crear cuenta",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = if (allowRoleChoice) "Registra un usuario y elige su rol"
            else "Regístrate como cliente",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = false; generalError = null },
            label = { Text("Nombre") },
            singleLine = true,
            isError = nameError,
            supportingText = if (nameError) {
                { Text("Ingresa tu nombre") }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; emailError = false; generalError = null },
            label = { Text("Correo electrónico") },
            singleLine = true,
            isError = emailError,
            supportingText = if (emailError) {
                { Text("Ingresa un correo válido") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; passwordError = false; generalError = null },
            label = { Text("Contraseña") },
            singleLine = true,
            isError = passwordError,
            supportingText = if (passwordError) {
                { Text("Mínimo 6 caracteres") }
            } else null,
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña"
                        else "Mostrar contraseña"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; confirmError = false; generalError = null },
            label = { Text("Confirmar contraseña") },
            singleLine = true,
            isError = confirmError,
            supportingText = if (confirmError) {
                { Text("Las contraseñas no coinciden") }
            } else null,
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (allowRoleChoice) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rol:", style = MaterialTheme.typography.bodyLarge)
                Role.entries.forEach { option ->
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = role == option,
                        onClick = { role = option },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        generalError?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                nameError = name.isBlank()
                emailError = !email.contains("@") || !email.contains(".")
                passwordError = password.length < 6
                confirmError = confirm != password
                if (nameError || emailError || passwordError || confirmError) return@Button

                generalError = onRegister(name, email, password, role)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Registrar")
        }

        TextButton(onClick = onBack) {
            Text(if (allowRoleChoice) "Cancelar" else "¿Ya tienes cuenta? Inicia sesión")
        }
    }
}
