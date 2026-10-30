package com.example.scanprod.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng

// Cada cuánto se pide una nueva posición al GPS
private const val UPDATE_INTERVAL_MS = 2_000L
// La dirección solo se vuelve a buscar si el usuario se movió más que esto
private const val ADDRESS_REFRESH_METERS = 25f
private const val GEOCODE_TIMEOUT_MS = 8_000L

private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private enum class LocationTab(val label: String, val icon: ImageVector) {
    MAPA("Mapa", Icons.Filled.Map),
    DATOS("Datos", Icons.Filled.Info)
}

// Estado del interruptor de ubicación (GPS) del teléfono
private enum class GpsState { CHECKING, ON, OFF }

/**
 * Muestra la ubicación del dispositivo en tiempo real: se actualiza cada pocos
 * segundos mientras la pantalla está abierta y se detiene al salir de ella.
 * Si el GPS está apagado, ofrece el diálogo del sistema para encenderlo.
 */
@Composable
fun LocationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    // El usuario negó el permiso y marcó "no volver a preguntar": solo queda ir a Ajustes
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            hasPermission = result.values.any { it }
            permanentlyDenied = !hasPermission && activity != null &&
                LOCATION_PERMISSIONS.none { activity.shouldShowRequestPermissionRationale(it) }
        }

    val request = remember {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS / 2)
            .build()
    }
    var gps by remember { mutableStateOf(GpsState.CHECKING) }
    val gpsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            gps = if (result.resultCode == Activity.RESULT_OK) GpsState.ON else GpsState.OFF
        }

    // Comprueba que la ubicación del teléfono esté encendida y, si no, pide encenderla
    fun checkGps() {
        gps = GpsState.CHECKING
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(LocationSettingsRequest.Builder().addLocationRequest(request).build())
            .addOnSuccessListener { gps = GpsState.ON }
            .addOnFailureListener { error ->
                gps = GpsState.OFF
                if (error is ResolvableApiException) {
                    runCatching { gpsLauncher.launch(IntentSenderRequest.Builder(error.resolution).build()) }
                }
            }
    }

    var location by remember { mutableStateOf<Location?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var addressFor by remember { mutableStateOf<Location?>(null) }
    var updates by remember { mutableStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(LocationTab.MAPA) }
    // Cada incremento vuelve a centrar el mapa en la posición actual
    var centerRequest by remember { mutableStateOf(0) }
    // Punto que el usuario tocó en el mapa y su dirección
    var selected by remember { mutableStateOf<LatLng?>(null) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(LOCATION_PERMISSIONS)
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) checkGps()
    }

    // Se suscribe a las actualizaciones solo mientras haya permiso, GPS y la pantalla esté visible
    if (hasPermission && gps == GpsState.ON) {
        DisposableEffect(Unit) {
            val client = LocationServices.getFusedLocationProviderClient(context)
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

    // Busca la dirección al principio y cuando el usuario se ha desplazado
    LaunchedEffect(location) {
        val current = location ?: return@LaunchedEffect
        val previous = addressFor
        if (previous == null || current.distanceTo(previous) > ADDRESS_REFRESH_METERS) {
            addressFor = current
            findAddress(context, current.latitude, current.longitude)?.let { address = it }
        }
    }

    LaunchedEffect(selected) {
        selectedAddress = null
        val point = selected ?: return@LaunchedEffect
        selectedAddress = findAddress(context, point.latitude, point.longitude) ?: "Sin dirección para este punto"
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
            !hasPermission && permanentlyDenied -> Message(
                text = "El permiso de ubicación está bloqueado. Puedes habilitarlo en Ajustes.",
                action = "Abrir Ajustes",
                onAction = { openAppSettings(context) }
            )

            !hasPermission -> Message(
                text = "Se utiliza para mostrar dónde estás en el mapa. La ubicación solo se usa mientras esta pantalla está abierta.",
                action = "Conceder permiso",
                onAction = { permissionLauncher.launch(LOCATION_PERMISSIONS) }
            )

            gps == GpsState.OFF -> Message(
                text = "La ubicación del teléfono está apagada. Actívala para ver tu posición.",
                action = "Activar ubicación",
                onAction = ::checkGps
            )

            current == null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Buscando señal GPS…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            tab == LocationTab.MAPA -> Box(modifier = Modifier.weight(1f)) {
                LiveMap(
                    location = current,
                    selected = selected,
                    centerRequest = centerRequest,
                    onMapClick = { selected = it },
                    modifier = Modifier.fillMaxSize()
                )
                val point = selected
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 84.dp, bottom = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (point == null) "Estás aquí" else "Punto seleccionado",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (point == null) address ?: "Buscando dirección…"
                                else selectedAddress ?: "Buscando dirección…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (point == null) {
                                    String.format(
                                        Locale.US, "%.5f, %.5f · ± %.0f m",
                                        current.latitude, current.longitude, current.accuracy
                                    )
                                } else {
                                    String.format(
                                        Locale.US, "%.5f, %.5f · a %.0f m de ti",
                                        point.latitude, point.longitude, distanceTo(current, point)
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (point != null) {
                            IconButton(onClick = { selected = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Quitar punto")
                            }
                        }
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
                        InfoLine("Modo", if (hasPrecisePermission(context)) "Precisa" else "Aproximada")
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
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(text = text, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onAction) { Text(action) }
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
    LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun hasPrecisePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun distanceTo(location: Location, point: LatLng): Float {
    val result = FloatArray(1)
    Location.distanceBetween(location.latitude, location.longitude, point.latitude, point.longitude, result)
    return result[0]
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

// Convierte coordenadas en una dirección legible; null si no hay internet, no se encuentra o tarda demasiado
private suspend fun findAddress(context: Context, latitude: Double, longitude: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    return withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull()?.getAddressLine(0))
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.getAddressLine(0)
                }.getOrNull()
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
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
