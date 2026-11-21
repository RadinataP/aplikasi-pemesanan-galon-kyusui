package com.kyusui.app.ui.maps

import android.animation.ValueAnimator
import android.util.Log
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyusui.app.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** Peta MapLibre/OpenFreeMap untuk tracking pelanggan. Routing memakai OSRM
 * demo hanya untuk prototipe; produksi sebaiknya memakai backend/instance routing
 * yang dikelola sendiri dan memiliki kebijakan layanan yang jelas. */
enum class MapUnavailableReason { AVAILABLE, API_KEY_MISSING, PLAY_SERVICES_MISSING }

@Composable
fun rememberMapAvailability(): MapUnavailableReason = MapUnavailableReason.AVAILABLE

@Composable
fun CourierPositionMap(
    courierPosition: GeoPoint?,
    destinationPosition: GeoPoint? = null,
    modifier: Modifier = Modifier,
    availability: MapUnavailableReason = rememberMapAvailability()
) {
    val courierValid = courierPosition?.takeIf(::isValidPoint)
    val destinationValid = destinationPosition?.takeIf(::isValidPoint)

    if (availability != MapUnavailableReason.AVAILABLE || courierValid == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.large) {
                Text(
                    text = if (courierPosition == null) "Menunggu lokasi kurir…" else "Lokasi kurir belum tersedia",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        MapLibreCourierMap(courierPoint = courierValid, destinationPoint = destinationValid, modifier = modifier)
    }
}

@Composable
private fun MapLibreCourierMap(
    courierPoint: GeoPoint,
    destinationPoint: GeoPoint?,
    modifier: Modifier
) {
    val mapState = remember { mutableStateOf<MapLibreMap?>(null) }
    val courierMarkerState = remember { mutableStateOf<Marker?>(null) }
    val destinationMarkerState = remember { mutableStateOf<Marker?>(null) }
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val animatorState = remember { AtomicReference<ValueAnimator?>(null) }
    val renderedPoint = remember { AtomicReference<GeoPoint?>(null) }
    val styleLoaded = remember { mutableStateOf(false) }
    val mapLoadFailed = remember { mutableStateOf(false) }
    val retryStyleAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeMessage by remember { mutableStateOf<String?>(null) }
    var lastRoutedFrom by remember { mutableStateOf<GeoPoint?>(null) }
    var lastRoutedDestination by remember { mutableStateOf<GeoPoint?>(null) }

    // Ambil geometri jalan dari OSRM. Permintaan tidak diulang setiap polling
    // kecil: hitung ulang bila tujuan berubah atau kurir bergerak sekitar 150 m.
    LaunchedEffect(courierPoint, destinationPoint) {
        val destination = destinationPoint ?: run {
            routePoints = emptyList()
            routeMessage = "Koordinat tujuan belum tersedia"
            return@LaunchedEffect
        }
        val previous = lastRoutedFrom
        val destinationChanged = lastRoutedDestination != destination

        // Penting: jika belum ada rute yang berhasil, setiap perubahan posisi
        // boleh mencoba lagi. Sebelumnya titik awal disimpan SEBELUM request
        // berhasil, sehingga satu kegagalan membuat retry tertahan sampai kurir
        // bergerak lebih dari ambang jarak.
        val routeMissing = routePoints.size < 2
        if (!routeMissing && previous != null && !destinationChanged &&
            distanceMeters(previous, courierPoint) < ROUTE_REFRESH_DISTANCE_METERS
        ) {
            return@LaunchedEffect
        }

        routeMessage = if (routeMissing) "Sedang mencari rute jalan…" else "Memperbarui rute jalan…"
        val result = withContext(Dispatchers.IO) { fetchOsrmRoute(courierPoint, destination) }
        result.fold(
            onSuccess = { points ->
                routePoints = points
                // Simpan posisi yang terakhir berhasil dirutekan hanya setelah
                // OSRM benar-benar mengembalikan geometri yang valid.
                lastRoutedFrom = courierPoint
                lastRoutedDestination = destination
                routeMessage = null
                Log.i(ROUTING_LOG_TAG, "Rute OSRM berhasil: ${points.size} titik geometri.")
            },
            onFailure = { error ->
                // Detail teknis masuk Logcat untuk diagnosis. UI hanya menerima
                // pesan aman dan ringkas, tanpa URL/token atau data pribadi.
                Log.w(ROUTING_LOG_TAG, "Permintaan rute OSRM gagal.", error)
                routeMessage = routingFailureMessage(error)
                // Jangan menghapus rute lama jika sebelumnya sudah berhasil.
                // Jika belum ada rute, routePoints tetap kosong dan request akan
                // dicoba lagi pada pembaruan posisi berikutnya.
            }
        )
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapLibre.getInstance(context.applicationContext)
                MapView(context).also { mapView ->
                    mapViewState.value = mapView
                    mapView.onCreate(null)
                    mapView.addOnDidFailLoadingMapListener { mapLoadFailed.value = true }
                    mapView.getMapAsync { map ->
                        mapState.value = map
                        map.uiSettings.isLogoEnabled = true
                        map.uiSettings.isAttributionEnabled = true
                        val loadStyle: () -> Unit = {
                            styleLoaded.value = false
                            mapLoadFailed.value = false
                            map.setStyle(Style.Builder().fromUri(OPENFREEMAP_STYLE_URL)) { style ->
                                val courierLatLng = LatLng(courierPoint.latitude, courierPoint.longitude)
                                courierMarkerState.value = map.addMarker(MarkerOptions().position(courierLatLng).title("Posisi kurir"))
                                destinationPoint?.let { point ->
                                    destinationMarkerState.value = map.addMarker(
                                        MarkerOptions().position(LatLng(point.latitude, point.longitude)).title("Tujuan pengiriman")
                                    )
                                }
                                style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray<Feature>())))
                                style.addLayer(
                                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                                        lineColor(ROUTE_COLOR), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
                                    )
                                )
                                renderedPoint.set(courierPoint)
                                focusCamera(map, courierPoint, destinationPoint)
                                styleLoaded.value = true
                            }
                        }
                        retryStyleAction.value = loadStyle
                        loadStyle()
                    }
                }
            },
            update = {
                val map = mapState.value
                val marker = courierMarkerState.value
                val previousPoint = renderedPoint.get()
                if (styleLoaded.value && map != null && marker != null && previousPoint != courierPoint) {
                    animatorState.get()?.cancel()
                    val from = GeoPoint(marker.position.latitude, marker.position.longitude)
                    val to = courierPoint
                    if (shouldAnimateMarker(from, to)) {
                        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = MARKER_ANIMATION_DURATION_MS
                            interpolator = DecelerateInterpolator()
                            addUpdateListener { valueAnimator ->
                                val fraction = valueAnimator.animatedValue as Float
                                val point = interpolateGeoPoint(from, to, fraction)
                                marker.position = LatLng(point.latitude, point.longitude)
                            }
                        }
                        animatorState.set(animator)
                        animator.start()
                    } else marker.position = LatLng(to.latitude, to.longitude)
                    renderedPoint.set(courierPoint)
                }
                // Marker tujuan diperbarui jika respons order berubah tanpa membuat ulang MapView.
                if (styleLoaded.value && map != null && destinationMarkerState.value?.position != destinationPoint?.let { LatLng(it.latitude, it.longitude) }) {
                    destinationMarkerState.value?.let { map.removeMarker(it) }
                    destinationMarkerState.value = destinationPoint?.let { point ->
                        map.addMarker(MarkerOptions().position(LatLng(point.latitude, point.longitude)).title("Tujuan pengiriman"))
                    }
                    // Jika order/koordinat tujuan baru diterima setelah map dibuat, tampilkan kedua titik.
                    focusCamera(map, courierPoint, destinationPoint)
                }
                // Source sudah dibuat setelah style siap; mengganti GeoJSON akan menggambar ulang jalur.
                if (styleLoaded.value) {
                    val features = if (routePoints.size >= 2) {
                        arrayOf(Feature.fromGeometry(LineString.fromLngLats(routePoints.map { Point.fromLngLat(it.longitude, it.latitude) })))
                    } else emptyArray()
                    map?.style?.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(
                        FeatureCollection.fromFeatures(features)
                    )
                }
            }
        )

        IconButton(
            onClick = { mapState.value?.let { focusCamera(it, courierPoint, destinationPoint) } },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).shadow(4.dp, CircleShape).size(44.dp)
        ) {
            Icon(Icons.Outlined.MyLocation, contentDescription = "Fokus ke rute", tint = MaterialTheme.colorScheme.primary)
        }
        if (!styleLoaded.value || mapLoadFailed.value) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            ) {
                if (!mapLoadFailed.value) CircularProgressIndicator(modifier = Modifier.padding(18.dp))
                else Text("Peta gagal dimuat. Periksa koneksi internet.", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.error)
            }
        } else if (routeMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 12.dp, end = 64.dp)
            ) { Text(routeMessage!!, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium) }
        }
    }

    MapViewLifecycleEffect(mapViewState.value)
    DisposableEffect(Unit) {
        onDispose {
            animatorState.get()?.cancel()
            retryStyleAction.value = null
            mapState.value = null
            courierMarkerState.value = null
            destinationMarkerState.value = null
            renderedPoint.set(null)
        }
    }
}

private fun focusCamera(map: MapLibreMap, courier: GeoPoint, destination: GeoPoint?) {
    if (destination == null) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(courier.latitude, courier.longitude), DEFAULT_ZOOM), CAMERA_ANIMATION_DURATION_MS)
        return
    }
    val bounds = LatLngBounds.Builder()
        .include(LatLng(courier.latitude, courier.longitude))
        .include(LatLng(destination.latitude, destination.longitude))
        .build()
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, CAMERA_PADDING_PX), CAMERA_ANIMATION_DURATION_MS)
}

/** OSRM memakai urutan longitude,latitude; GeoJSON juga menyimpan koordinat x,y. */
private fun fetchOsrmRoute(origin: GeoPoint, destination: GeoPoint): Result<List<LatLng>> =
    runCatching {
        require(isValidPoint(origin)) { "Koordinat awal kurir tidak valid." }
        require(isValidPoint(destination)) { "Koordinat tujuan tidak valid." }

        // OSRM menerima urutan longitude,latitude (bukan latitude,longitude).
        val endpoint =
            "$OSRM_BASE_URL/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}" +
                "?overview=full&geometries=geojson&steps=false&alternatives=false"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = OSRM_CONNECT_TIMEOUT_MS
            readTimeout = OSRM_READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Berkah Water-Android/1.0")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                // Baca body error bila tersedia agar Logcat dapat membedakan
                // pembatasan server, kesalahan permintaan, dan kegagalan server.
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                error("OSRM HTTP $responseCode${errorBody.take(240).takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val code = root.optString("code")
            check(code == "Ok") {
                "OSRM code=$code; message=${root.optString("message", "tidak ada pesan")}"
            }

            val routes = root.optJSONArray("routes")
                ?: error("Respons OSRM tidak memiliki array routes.")
            check(routes.length() > 0) { "OSRM tidak menemukan rute antara kedua koordinat (NoRoute)." }

            val geometry = routes.getJSONObject(0).optJSONObject("geometry")
                ?: error("Respons OSRM tidak memiliki geometry.")
            val coordinates = geometry.optJSONArray("coordinates")
                ?: error("Geometri OSRM tidak memiliki coordinates.")

            buildList {
                for (index in 0 until coordinates.length()) {
                    val pair = coordinates.optJSONArray(index) ?: continue
                    if (pair.length() < 2) continue
                    val longitude = pair.optDouble(0, Double.NaN)
                    val latitude = pair.optDouble(1, Double.NaN)
                    if (latitude.isFinite() && longitude.isFinite() &&
                        latitude in -90.0..90.0 && longitude in -180.0..180.0
                    ) {
                        add(LatLng(latitude, longitude))
                    }
                }
            }.also { points ->
                check(points.size >= 2) {
                    "Geometri OSRM kosong atau berisi kurang dari dua koordinat valid."
                }
            }
        } finally {
            connection.disconnect()
        }
    }

/** Pesan singkat untuk UI; detail exception tetap dicatat di Logcat. */
private fun routingFailureMessage(error: Throwable): String {
    val detail = generateSequence(error) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
        .lowercase(Locale.ROOT)

    return when {
        detail.contains("unknownhost") || detail.contains("unable to resolve host") ->
            "Server rute tidak dapat dijangkau. Periksa koneksi internet."
        detail.contains("timed out") || detail.contains("timeout") ->
            "Server rute terlalu lama merespons. Akan dicoba lagi."
        detail.contains("noroute") || detail.contains("no route") ->
            "Rute jalan tidak ditemukan untuk koordinat ini."
        detail.contains("http 429") ->
            "Layanan rute sedang membatasi permintaan. Coba lagi sebentar."
        detail.contains("http 5") ->
            "Layanan rute sedang bermasalah. Akan dicoba lagi."
        detail.contains("ssl") || detail.contains("certpath") ->
            "Koneksi aman ke layanan rute gagal."
        else -> "Rute jalan belum tersedia. Akan dicoba lagi."
    }
}

private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    // Approximation local yang hanya dipakai untuk membatasi frekuensi routing, bukan jarak yang ditampilkan ke pengguna.
    val meanLat = Math.toRadians((a.latitude + b.latitude) / 2.0)
    val dx = (a.longitude - b.longitude) * 111_320.0 * cos(meanLat)
    val dy = (a.latitude - b.latitude) * 110_540.0
    return sqrt(dx * dx + dy * dy)
}

fun formatCoordinates(position: GeoPoint): String = String.format(Locale.US, "%.6f, %.6f", position.latitude, position.longitude)

private fun isValidPoint(point: GeoPoint): Boolean = point.latitude.isFinite() && point.longitude.isFinite() && point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

private const val DEFAULT_ZOOM = 15.0
private const val MARKER_ANIMATION_DURATION_MS = 900L
private const val CAMERA_ANIMATION_DURATION_MS = 450
private const val CAMERA_PADDING_PX = 100
private const val ROUTE_REFRESH_DISTANCE_METERS = 150.0
private const val OSRM_CONNECT_TIMEOUT_MS = 8_000
private const val OSRM_READ_TIMEOUT_MS = 10_000
private const val ROUTING_LOG_TAG = "KyusuiRouting"
private const val ROUTE_SOURCE_ID = "kyusui-route-source"
private const val ROUTE_LAYER_ID = "kyusui-route-line"
private const val ROUTE_COLOR = "#168A66"
private const val OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
// Server demo hanya untuk pengembangan; jangan anggap sebagai SLA produksi.
private const val OSRM_BASE_URL = "https://router.project-osrm.org/route/v1/driving"
