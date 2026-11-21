package com.kyusui.app.ui.maps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyusui.app.domain.model.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Peta pemilih lokasi pelanggan dengan MapLibre Native + OpenFreeMap Liberty.
 *
 * Pin selalu berada di tengah viewport. Pengguna menggeser peta, lalu menekan
 * "Gunakan titik ini" untuk mengonfirmasi koordinat yang sedang berada di pusat
 * kamera. Titik kamera awal bukan lokasi tersimpan sampai pengguna mengonfirmasi.
 *
 * Perubahan penting:
 * - Kamera hanya mengikuti selectedPoint ketika nilai pilihan dari parent berubah.
 *   Panning pengguna tidak lagi membuat kamera snap kembali ke titik tersimpan.
 * - Koordinat konfirmasi dibaca langsung dari cameraPosition, bukan state kamera
 *   yang mungkin belum diperbarui ketika tombol ditekan.
 * - Konfirmasi dinonaktifkan sebelum style berhasil dimuat. Kegagalan peta
 *   ditampilkan dan dapat dicoba ulang, bukan diam-diam mengonfirmasi koordinat lama.
 */
@Composable
fun CustomerLocationPickerMap(
    selectedPoint: GeoPoint?,
    onConfirmLocation: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
    mapHeight: Int = 230
) {
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val mapState = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleLoaded = remember { mutableStateOf(false) }
    val mapLoadFailed = remember { mutableStateOf(false) }
    val retryStyleAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val initialCameraApplied = remember { mutableStateOf(false) }
    val lastAppliedSelectedPoint = remember { mutableStateOf<GeoPoint?>(null) }
    val latestSelectedPoint = remember { mutableStateOf(selectedPoint) }
    latestSelectedPoint.value = selectedPoint

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(mapHeight.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapLibre.getInstance(context.applicationContext)
                MapView(context).also { mapView ->
                    mapViewState.value = mapView
                    mapView.onCreate(null)
                    mapView.addOnDidFailLoadingMapListener { _ ->
                        mapLoadFailed.value = true
                    }
                    mapView.getMapAsync { map ->
                        mapState.value = map
                        map.uiSettings.isLogoEnabled = true
                        map.uiSettings.isAttributionEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = false

                        map.addOnCameraIdleListener {
                            // Camera position is read when the user confirms.
                            // Do not drive camera movement from this callback.
                        }

                        val loadStyle: () -> Unit = {
                            styleLoaded.value = false
                            mapLoadFailed.value = false
                            map.setStyle(Style.Builder().fromUri(LOCATION_PICKER_STYLE_URL)) {
                                mapLoadFailed.value = false
                                styleLoaded.value = true

                                if (!initialCameraApplied.value) {
                                    val initialPoint =
                                        latestSelectedPoint.value ?: INITIAL_CAMERA_POINT
                                    map.cameraPosition = CameraPosition.Builder()
                                        .target(
                                            LatLng(
                                                initialPoint.latitude,
                                                initialPoint.longitude
                                            )
                                        )
                                        .zoom(DEFAULT_ZOOM)
                                        .build()
                                    lastAppliedSelectedPoint.value = latestSelectedPoint.value
                                    initialCameraApplied.value = true
                                }
                            }
                        }

                        retryStyleAction.value = loadStyle
                        loadStyle()
                    }
                }
            },
            update = { mapView ->
                val map = mapState.value
                val point = selectedPoint

                // Recenter only when the parent changes the saved point. Do not
                // compare camera target to selectedPoint on every recomposition:
                // camera movement from a user pan must remain under user control.
                if (
                    map != null &&
                    styleLoaded.value &&
                    mapViewState.value === mapView &&
                    point != lastAppliedSelectedPoint.value
                ) {
                    lastAppliedSelectedPoint.value = point
                    if (point != null) {
                        map.moveCamera(
                            CameraUpdateFactory.newLatLng(
                                LatLng(point.latitude, point.longitude)
                            )
                        )
                    }
                }
            }
        )

        if (styleLoaded.value) {
            // Fixed center marker: the selected coordinate follows the map camera.
            Text(
                text = "●",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        IconButton(
            enabled = styleLoaded.value && selectedPoint != null,
            onClick = {
                val map = mapState.value ?: return@IconButton
                val point = selectedPoint ?: return@IconButton
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(point.latitude, point.longitude),
                        DEFAULT_ZOOM
                    ),
                    CAMERA_ANIMATION_DURATION_MS
                )
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .shadow(3.dp, CircleShape)
        ) {
            Icon(
                Icons.Outlined.MyLocation,
                contentDescription = "Kembali ke titik tersimpan"
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Text(
                text = "Geser peta, letakkan pin di alamat tujuan",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }

        if (!styleLoaded.value || mapLoadFailed.value) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!mapLoadFailed.value) {
                        CircularProgressIndicator()
                        Text(
                            text = "Memuat peta...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Peta gagal dimuat. Periksa koneksi internet lalu coba lagi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = { retryStyleAction.value?.invoke() },
                            enabled = retryStyleAction.value != null
                        ) {
                            Text("Coba lagi")
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val target = mapState.value?.cameraPosition?.target
                    ?: return@Button
                val point = GeoPoint(
                    latitude = target.latitude,
                    longitude = target.longitude
                )
                if (isValidPickerPoint(point)) {
                    onConfirmLocation(point)
                }
            },
            enabled = styleLoaded.value && mapState.value?.cameraPosition?.target != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text("Gunakan titik ini")
        }
    }

    MapViewLifecycleEffect(mapViewState.value)

    DisposableEffect(Unit) {
        onDispose {
            retryStyleAction.value = null
            mapState.value = null
        }
    }
}

private fun isValidPickerPoint(point: GeoPoint): Boolean =
    point.latitude.isFinite() &&
        point.longitude.isFinite() &&
        point.latitude in -90.0..90.0 &&
        point.longitude in -180.0..180.0

private val INITIAL_CAMERA_POINT = GeoPoint(
    latitude = -0.8354962,
    longitude = 100.3647581
)

private const val DEFAULT_ZOOM = 15.0
private const val CAMERA_ANIMATION_DURATION_MS = 350
private const val LOCATION_PICKER_STYLE_URL =
    "https://tiles.openfreemap.org/styles/liberty"
