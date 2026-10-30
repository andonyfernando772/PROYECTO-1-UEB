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
import com.example.scanprod.data.Store
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
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

// Estilo gratuito de OpenFreeMap (el mismo de GeoKotlin): no necesita clave de API
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val STREET_ZOOM = 16.0
private const val CURRENT_SOURCE = "current-source"
private const val SELECTED_SOURCE = "selected-source"
private const val STORES_SOURCE = "stores-source"
private const val STORES_LAYER = "store-points"
private const val STORE_ID = "id"
private const val STORE_NAME = "name"

/**
 * Mapa de MapLibre con un punto azul en la posición actual, un punto verde con
 * nombre por cada tienda y un punto rojo en el lugar seleccionado. Cada vez que
 * cambia [centerRequest] el mapa se centra en [focus], o en la posición actual si es null.
 */
@Composable
fun LiveMap(
    location: Location,
    stores: List<Store>,
    selected: LatLng?,
    focus: LatLng?,
    centerRequest: Int,
    onMapClick: (LatLng) -> Unit,
    onStoreClick: (Store) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnStoreClick by rememberUpdatedState(onStoreClick)
    val currentStores by rememberUpdatedState(stores)

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
                // Si se tocó una tienda se selecciona; si no, se marca el punto tocado
                val screenPoint = mapLibreMap.projection.toScreenLocation(point)
                val storeId = mapLibreMap.queryRenderedFeatures(screenPoint, STORES_LAYER)
                    .firstOrNull()?.getNumberProperty(STORE_ID)?.toLong()
                val store = currentStores.firstOrNull { it.id == storeId }
                if (store != null) currentOnStoreClick(store) else currentOnMapClick(point)
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

    LaunchedEffect(style, stores) {
        val features = stores.map { store ->
            Feature.fromGeometry(Point.fromLngLat(store.longitude, store.latitude)).apply {
                addNumberProperty(STORE_ID, store.id)
                addStringProperty(STORE_NAME, store.name)
            }
        }
        style?.getSourceAs<GeoJsonSource>(STORES_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(style, selected) {
        val source = style?.getSourceAs<GeoJsonSource>(SELECTED_SOURCE) ?: return@LaunchedEffect
        if (selected == null) source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        else source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(selected.longitude, selected.latitude)))
    }

    // Centra al cargar el mapa y cada vez que se pide
    LaunchedEffect(map, centerRequest) {
        val target = focus ?: LatLng(location.latitude, location.longitude)
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, STREET_ZOOM))
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun installPointLayers(style: Style) {
    style.addSource(GeoJsonSource(CURRENT_SOURCE))
    style.addSource(GeoJsonSource(SELECTED_SOURCE))
    style.addSource(GeoJsonSource(STORES_SOURCE))
    style.addLayer(
        CircleLayer(STORES_LAYER, STORES_SOURCE).withProperties(
            circleRadius(7f),
            circleColor("#2E7D32"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(2f)
        )
    )
    style.addLayer(
        SymbolLayer("store-names", STORES_SOURCE).withProperties(
            textField("{$STORE_NAME}"),
            textFont(arrayOf("Noto Sans Regular")),
            textSize(11f),
            textColor("#1B5E20"),
            textHaloColor("#FFFFFF"),
            textHaloWidth(1.5f),
            textAnchor(Property.TEXT_ANCHOR_TOP),
            textOffset(arrayOf(0f, 0.8f)),
            // Si no cabe el nombre, el punto se sigue viendo
            textOptional(true)
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
    style.addLayer(
        CircleLayer("current-points", CURRENT_SOURCE).withProperties(
            circleRadius(9f),
            circleColor("#1E88E5"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(3f)
        )
    )
}
