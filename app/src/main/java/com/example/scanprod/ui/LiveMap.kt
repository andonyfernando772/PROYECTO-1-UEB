package com.example.scanprod.ui

import android.graphics.Color
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon

// Zoom con el que se muestra la posición al centrar el mapa
private const val STREET_ZOOM = 17.0
// Radio en metros del punto que marca un lugar tocado en el mapa
private const val SELECTED_RADIUS_METERS = 6.0

/**
 * Mapa de OpenStreetMap (no necesita clave de API) con un punto azul en la
 * posición actual, un círculo con la precisión y un punto rojo en el lugar que el
 * usuario toca. Cada vez que cambia [centerRequest] el mapa vuelve a centrarse.
 */
@Composable
fun LiveMap(
    location: Location,
    selected: GeoPoint?,
    centerRequest: Int,
    onMapClick: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapClick by rememberUpdatedState(onMapClick)

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
    val accuracyCircle = remember { circle(mapView, 0x302196F3, 0x802196F3.toInt(), 2f) }
    val currentDot = remember { circle(mapView, 0xFF1E88E5.toInt(), Color.WHITE, 6f) }
    val selectedDot = remember { circle(mapView, 0xFFE53935.toInt(), Color.WHITE, 6f) }
    // Recuerda qué petición de centrado ya se atendió
    val centered = remember { intArrayOf(-1) }

    // El mapa sigue el ciclo de vida de la pantalla para no gastar recursos en segundo plano
    DisposableEffect(lifecycleOwner) {
        val taps = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                currentOnMapClick(point)
                return true
            }

            override fun longPressHelper(point: GeoPoint): Boolean = false
        })
        mapView.overlays.addAll(listOf(taps, accuracyCircle, currentDot, selectedDot))
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
            accuracyCircle.points = Polygon.pointsAsCircle(point, location.accuracy.toDouble())
            // El punto azul mantiene el mismo tamaño en pantalla a cualquier zoom
            currentDot.points = Polygon.pointsAsCircle(point, metersPerDot(map, point))
            selectedDot.isVisible = selected != null
            selected?.let { selectedDot.points = Polygon.pointsAsCircle(it, SELECTED_RADIUS_METERS) }
            if (centered[0] != centerRequest) {
                centered[0] = centerRequest
                map.controller.animateTo(point, STREET_ZOOM, 600L)
            }
            map.invalidate()
        }
    )
}

private fun circle(mapView: MapView, fill: Int, stroke: Int, strokeWidth: Float) =
    Polygon(mapView).apply {
        fillPaint.color = fill
        outlinePaint.color = stroke
        outlinePaint.strokeWidth = strokeWidth
        setOnClickListener { _, _, _ -> false }
    }

// Equivale a unos 8 píxeles de pantalla en el zoom actual
private fun metersPerDot(map: MapView, point: GeoPoint): Double {
    val metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(point.latitude)) /
        Math.pow(2.0, map.zoomLevelDouble)
    return metersPerPixel * 8 * map.resources.displayMetrics.density
}
