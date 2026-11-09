package com.example.scanprod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Botón pequeño para cambiar entre modo claro (luna) y modo oscuro (sol). */
@Composable
fun ThemeToggleButton(darkTheme: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    // Box en vez de IconButton: este último fuerza un área mínima de 48dp y se vería grande
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle)
    ) {
        Icon(
            imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = if (darkTheme) "Cambiar a modo claro" else "Cambiar a modo oscuro",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
