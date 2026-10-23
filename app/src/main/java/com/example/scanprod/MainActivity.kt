package com.example.scanprod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.scanprod.ui.HomeScreen
import com.example.scanprod.ui.LoginScreen
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

@Composable
fun AppRoot() {
    var user by remember { mutableStateOf<String?>(null) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        if (user == null) {
            LoginScreen(
                onLogin = { user = it },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            HomeScreen(
                email = user!!,
                onLogout = { user = null },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    ScanProdTheme {
        LoginScreen(onLogin = {})
    }
}
