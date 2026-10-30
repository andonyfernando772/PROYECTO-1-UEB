package com.example.scanprod.ui

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

// Estilo gratuito de OpenFreeMap (el mismo de GeoKotlin): no necesita clave de API
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val STREET_ZOOM = 16.0
private const val CURRENT_SOURCE = "current-source"
private const val SELECTED_SOURCE = "selected-source"

/**
 * Mapa de MapLibre con un punto azul en la posición actual y un punto rojo en el
 * lugar que el usuario toca. Cada vez que cambia [centerRequest] el mapa vuelve a
 * centrarse en la posición actual.
 */
@Composable
fun LiveMap(
    location: Location,
    selected: LatLng?,
    centerRequest: Int,
    onMapClick: (LatLng) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapClick by rememberUpdatedState(onMapClick)

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    // El mapa sigue el ciclo de vida de la pantalla para no gastar recursos en segundo plano
    DisposableEffect(lifecycleOwner) {
        var disposed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        mapView.getMapAsync { mapLibreMap ->
            if (disposed) return@getMapAsync
            mapLibreMap.uiSettings.isCompassEnabled = true
            mapLibreMap.addOnMapClickListener { point ->
                currentOnMapClick(point)
                true
            }
            mapLibreMap.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { loaded ->
                if (disposed) return@setStyle
                installPointLayers(loaded)
                map = mapLibreMap
                style = loaded
            }
        }

        onDispose {
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            val state = lifecycleOwner.lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(style, location) {
        style?.getSourceAs<GeoJsonSource>(CURRENT_SOURCE)
            ?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)))
    }

    LaunchedEffect(style, selected) {
        val source = style?.getSourceAs<GeoJsonSource>(SELECTED_SOURCE) ?: return@LaunchedEffect
        if (selected == null) source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        else source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(selected.longitude, selected.latitude)))
    }

    // Centra al cargar el mapa y cada vez que se pide
    LaunchedEffect(map, centerRequest) {
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), STREET_ZOOM)
        )
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun installPointLayers(style: Style) {
    style.addSource(GeoJsonSource(CURRENT_SOURCE))
    style.addSource(GeoJsonSource(SELECTED_SOURCE))
    style.addLayer(
        CircleLayer("current-points", CURRENT_SOURCE).withProperties(
            circleRadius(9f),
            circleColor("#1E88E5"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(3f)
        )
    )
    style.addLayer(
        CircleLayer("selected-points", SELECTED_SOURCE).withProperties(
            circleRadius(8f),
            circleColor("#E53935"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(3f)
        )
    )
}
