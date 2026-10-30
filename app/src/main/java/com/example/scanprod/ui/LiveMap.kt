package com.example.scanprod.ui

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

// Zoom con el que se muestra la posición al centrar el mapa
private const val STREET_ZOOM = 17.0

/**
 * Mapa de OpenStreetMap (no necesita clave de API) con un marcador en la posición
 * actual y un círculo con la precisión. Cada vez que cambia [centerRequest] el
 * mapa vuelve a centrarse en la posición.
 */
@Composable
fun LiveMap(
    location: Location,
    title: String,
    centerRequest: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(STREET_ZOOM)
        }
    }
    val marker = remember {
        Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
    }
    val accuracyCircle = remember {
        Polygon(mapView).apply {
            fillPaint.color = 0x302196F3
            outlinePaint.color = 0x802196F3.toInt()
            outlinePaint.strokeWidth = 2f
        }
    }
    // Recuerda qué petición de centrado ya se atendió
    val centered = remember { intArrayOf(-1) }

    // El mapa sigue el ciclo de vida de la pantalla para no gastar recursos en segundo plano
    DisposableEffect(lifecycleOwner) {
        mapView.overlays.add(accuracyCircle)
        mapView.overlays.add(marker)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            val point = GeoPoint(location.latitude, location.longitude)
            marker.position = point
            marker.title = title
            accuracyCircle.points = Polygon.pointsAsCircle(point, location.accuracy.toDouble())
            if (centered[0] != centerRequest) {
                centered[0] = centerRequest
                map.controller.animateTo(point, STREET_ZOOM, 600L)
            }
            map.invalidate()
        }
    )
}
