package com.kyusui.app.ui.maps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyusui.app.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.net.HttpURLConnection
import java.net.URL

/** Peta tujuan pengantaran kurir. Tidak memakai Google Maps atau Google Directions. */
@Composable
fun CourierDeliveryMap(
    destination: GeoPoint?,
    courier: GeoPoint?,
    modifier: Modifier = Modifier
) {
    var mapError by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var mapStyleRef by remember { mutableStateOf<Style?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var courierMarker by remember { mutableStateOf<Marker?>(null) }
    var renderedDestination by remember { mutableStateOf<GeoPoint?>(null) }
    var renderedCourier by remember { mutableStateOf<GeoPoint?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeLoading by remember { mutableStateOf(false) }

    LaunchedEffect(destination, courier) {
        val start = courier
        val end = destination
        if (start == null || end == null || !valid(start) || !valid(end)) {
            routePoints = emptyList()
            return@LaunchedEffect
        }
        routeLoading = true
        routePoints = withContext(Dispatchers.IO) { requestRoute(start, end) }
        routeLoading = false
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapLibre.getInstance(context.applicationContext)
                    MapView(context).also { view ->
                        mapViewRef = view
                        view.onCreate(null)
                        view.addOnDidFailLoadingMapListener { mapError = true }
                        view.getMapAsync { map ->
                            mapRef = map
                            map.uiSettings.isLogoEnabled = true
                            map.uiSettings.isAttributionEnabled = true
                            map.uiSettings.isRotateGesturesEnabled = false
                            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                                mapStyleRef = style
                                mapReady = true
                                mapError = false
                            }
                        }
                    }
                },
                update = {
                    val map = mapRef
                    if (mapReady && map != null) {
                        val validDestination = destination?.takeIf(::valid)
                        val validCourier = courier?.takeIf(::valid)
                        val locationChanged = validDestination != renderedDestination || validCourier != renderedCourier
                        if (validDestination != renderedDestination) {
                            destinationMarker?.let(map::removeMarker)
                            destinationMarker = validDestination?.let { point ->
                                map.addMarker(MarkerOptions().position(LatLng(point.latitude, point.longitude)).title("Alamat pelanggan"))
                            }
                            renderedDestination = validDestination
                        }
                        if (validCourier != renderedCourier) {
                            courierMarker?.let(map::removeMarker)
                            courierMarker = validCourier?.let { point ->
                                map.addMarker(MarkerOptions().position(LatLng(point.latitude, point.longitude)).title("Lokasi kurir"))
                            }
                            renderedCourier = validCourier
                        }
                        val points = listOfNotNull(validCourier, validDestination)
                        if (locationChanged && points.size == 2) {
                            val bounds = LatLngBounds.Builder()
                                .include(LatLng(points[0].latitude, points[0].longitude))
                                .include(LatLng(points[1].latitude, points[1].longitude))
                                .build()
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 56))
                        } else if (locationChanged) {
                            points.firstOrNull()?.let { point ->
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 15.0))
                            }
                        }
                    }
                    val style = mapStyleRef
                    if (style != null && routePoints.size > 1) {
                        runCatching {
                            val coordinates = routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                            val feature = Feature.fromGeometry(LineString.fromLngLats(coordinates))
                            val source = style.getSourceAs<GeoJsonSource>("bw-courier-route")
                            if (source == null) {
                                style.addSource(GeoJsonSource("bw-courier-route", feature))
                                style.addLayer(
                                    LineLayer("bw-courier-route-line", "bw-courier-route").withProperties(
                                        lineColor("#0EA5E9"), lineWidth(5f), lineCap("round"), lineJoin("round")
                                    )
                                )
                            } else source.setGeoJson(feature)
                        }
                    }
                }
            )
            if (!mapReady && !mapError) {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Memuat peta tujuan…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (mapError) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                    Text("Peta tidak dapat dimuat. Periksa koneksi internet.", Modifier.padding(16.dp))
                }
            }
        }
        if (destination == null) {
            Text("Koordinat tujuan belum tersedia dari server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else if (courier == null) {
            Text("Titik tujuan pelanggan ditampilkan. Rute jalan akan muncul setelah lokasi kurir tersedia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (routeLoading) {
            Text("Menghitung rute jalan…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (routePoints.isEmpty()) {
            Text("Rute jalan belum tersedia. Titik lokasi tetap ditampilkan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Rute jalan dari OSRM · peta OpenFreeMap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    MapViewLifecycleEffect(mapViewRef)
}

private fun valid(point: GeoPoint): Boolean =
    point.latitude.isFinite() && point.longitude.isFinite() &&
        point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

private fun requestRoute(start: GeoPoint, end: GeoPoint): List<LatLng> = runCatching {
    val url = URL("https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 10_000
        requestMethod = "GET"
    }
    try {
        if (connection.responseCode !in 200..299) return@runCatching emptyList()
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val coordinates = json.getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        buildList {
            for (index in 0 until coordinates.length()) {
                val pair = coordinates.getJSONArray(index)
                add(LatLng(pair.getDouble(1), pair.getDouble(0)))
            }
        }
    } finally { connection.disconnect() }
}.getOrDefault(emptyList())
