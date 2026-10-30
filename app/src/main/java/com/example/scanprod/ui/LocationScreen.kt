package com.example.scanprod.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Cada cuánto se pide una nueva posición al GPS
private const val UPDATE_INTERVAL_MS = 2_000L

private enum class LocationTab(val label: String, val icon: ImageVector) {
    MAPA("Mapa", Icons.Filled.Map),
    DATOS("Datos", Icons.Filled.Info)
}

/**
 * Muestra la ubicación del dispositivo en tiempo real: se actualiza cada pocos
 * segundos mientras la pantalla está abierta y se detiene al salir de ella.
 */
@Composable
fun LocationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            hasPermission = result.values.any { it }
        }
    var location by remember { mutableStateOf<Location?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var updates by remember { mutableStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(LocationTab.MAPA) }
    // Cada incremento vuelve a centrar el mapa en la posición actual
    var centerRequest by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Se suscribe a las actualizaciones solo mientras haya permiso y la pantalla esté visible
    if (hasPermission) {
        DisposableEffect(Unit) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS / 2)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        location = it
                        updates++
                    }
                }
            }
            startUpdates(client, request, callback)
            onDispose { client.removeLocationUpdates(callback) }
        }
    }

    // La dirección se busca de nuevo cuando la posición cambia
    LaunchedEffect(location?.latitude, location?.longitude) {
        location?.let { address = findAddress(context, it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(text = "Mi ubicación", style = MaterialTheme.typography.headlineSmall)
        }

        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            LocationTab.entries.forEach { option ->
                Tab(
                    selected = tab == option,
                    onClick = { tab = option },
                    text = { Text(option.label) },
                    icon = { Icon(option.icon, contentDescription = null) }
                )
            }
        }

        val current = location
        when {
            !hasPermission -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text("La app necesita permiso de ubicación para mostrar dónde estás")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }) { Text("Conceder permiso") }
            }

            current == null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Buscando señal… Verifica que la ubicación (GPS) esté activada.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            tab == LocationTab.MAPA -> Box(modifier = Modifier.weight(1f)) {
                LiveMap(
                    location = current,
                    title = address ?: "Mi ubicación",
                    centerRequest = centerRequest,
                    modifier = Modifier.fillMaxSize()
                )
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 84.dp, bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = address ?: "Buscando dirección…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format(
                                Locale.US, "%.6f, %.6f · ± %.0f m",
                                current.latitude, current.longitude, current.accuracy
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FloatingActionButton(
                    onClick = { centerRequest++ },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 16.dp)
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Centrar en mi ubicación")
                }
            }

            else -> Column(modifier = Modifier.padding(24.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("En tiempo real", style = MaterialTheme.typography.titleMedium)
                        }
                        address?.let { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
                        InfoLine("Latitud", String.format(Locale.US, "%.6f", current.latitude))
                        InfoLine("Longitud", String.format(Locale.US, "%.6f", current.longitude))
                        InfoLine("Precisión", String.format(Locale.US, "± %.0f m", current.accuracy))
                        if (current.hasAltitude()) {
                            InfoLine("Altitud", String.format(Locale.US, "%.0f m", current.altitude))
                        }
                        if (current.hasSpeed()) {
                            InfoLine("Velocidad", String.format(Locale.US, "%.1f km/h", current.speed * 3.6))
                        }
                        InfoLine("Actualizado", DateFormat.getTimeInstance().format(Date(current.time)))
                        InfoLine("Lecturas", updates.toString())
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { openInMaps(context, current) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Abrir en Google Maps")
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

// Solo se llama después de comprobar el permiso
@SuppressLint("MissingPermission")
private fun startUpdates(
    client: FusedLocationProviderClient,
    request: LocationRequest,
    callback: LocationCallback
) {
    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
}

// Convierte las coordenadas en una dirección legible; null si no hay internet o no se encuentra
private suspend fun findAddress(context: Context, location: Location): String? =
    withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        }.getOrNull()
    }

// Abre la posición en Google Maps u otra app de mapas instalada
private fun openInMaps(context: Context, location: Location) {
    val coords = "${location.latitude},${location.longitude}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coords?q=$coords(Mi ubicación)"))
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$coords"))
        )
    }
}
