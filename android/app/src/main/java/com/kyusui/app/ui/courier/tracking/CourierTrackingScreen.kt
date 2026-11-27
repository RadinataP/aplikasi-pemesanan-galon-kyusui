package com.kyusui.app.ui.courier.tracking

import com.kyusui.app.ui.courier.design.CourierCard

import com.kyusui.app.ui.courier.design.CourierPageIntro

import com.kyusui.app.ui.courier.design.CourierSpacing
import com.kyusui.app.ui.courier.design.CourierColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocationDisabled
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyusui.app.core.TrackingIntervals
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InfoRow
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.components.SecondaryButton
import com.kyusui.app.ui.location.LocationPermissionState
import com.kyusui.app.ui.location.resolveLocationPermissionState
import com.kyusui.app.ui.location.rememberLocationRequestState
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.ui.maps.CourierDeliveryMap
import java.util.Locale

/**
 * Layar tracking sisi kurir untuk satu assignment aktif.
 *
 * ## Peran backend
 *
 * Layar ini hanya mengirim posisi lewat
 * `POST courier/assignments/{assignment}/location`. Backend yang menentukan
 * apakah penugasan milik kurir yang sedang login, apakah delivery masih aktif,
 * dan apakah perpindahan lokasi masuk akal. Tidak ada tombol di layar ini yang
 * menulis status delivery.
 *
 * ## Izin
 *
 * Izin diminta saat kurir menekan Mulai, bukan otomatis saat layar dibuka.
 * Acquisition tidak dijalankan tanpa izin, karena loop yang tidak akan pernah
 * mendapat fix hanya memboroskan daya dan membuat layar terlihat bekerja
 * padahal tidak.
 *
 * ## Latar belakang
 *
 * Selama "Mulai Kirim Posisi" aktif, pengiriman dijalankan
 * [CourierLocationShareController] dan dibungkus foreground service bertipe
 * `location` ([CourierLocationService]) dengan notifikasi permanen. Service
 * hanya hidup saat delivery aktif; layar ini tidak pernah meminta izin
 * `ACCESS_BACKGROUND_LOCATION`.
 *
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourierTrackingScreen(
    viewModel: CourierTrackingViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionRequest = rememberLocationRequestState(
        onPermissionDenied = {
            viewModel.onPermissionStateChanged(
                resolveLocationPermissionState(context, hasAsked = true)
            )
        },
        onPermissionGranted = {
            // Izin diberikan berarti state izin harus dibaca ulang dari sistem,
            // bukan diasumsikan tetap sama. Setelah itu baru acquisition dibuka.
            viewModel.onPermissionStateChanged(
                resolveLocationPermissionState(context, hasAsked = true)
            )
            viewModel.startTracking()
        }
    )

    // Pulihkan status izin saat layar dibuka. Jika izin pernah diberikan dan
    // assignment memang aktif, pengiriman dimulai otomatis saat layar tracking
    // dibuka; pengguna tidak perlu menekan tombol kirim untuk setiap koordinat.
    LaunchedEffect(context) {
        viewModel.onPermissionStateChanged(
            resolveLocationPermissionState(context, hasAsked = false)
        )
    }
    LaunchedEffect(uiState.permissionState, uiState.delivery?.canPostLocation, uiState.isTracking) {
        if (uiState.permissionState.allowsAcquisition &&
            uiState.delivery?.canPostLocation == true &&
            !uiState.isTracking
        ) {
            viewModel.startTracking()
        }
    }

    Scaffold(
        containerColor = CourierColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Kirim Posisi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CourierSpacing.Screen)
                .padding(top = CourierSpacing.Lg, bottom = CourierSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(CourierSpacing.Lg)
        ) {
            CourierPageIntro(
                eyebrow = "Operasional / GPS",
                title = "Pelacakan posisi",
                description = "Pantau izin lokasi, posisi perangkat, dan status pengiriman ke server."
            )
            when (val state = uiState.deliveryState) {
                is LoadState.Loading -> LoadingState(message = "Memuat penugasan")

                is LoadState.Error -> ErrorState(
                    message = state.message,
                    isRetryable = state.isRetryable,
                    onRetry = if (state.isRetryable) viewModel::loadDelivery else null
                )

                is LoadState.Empty -> Text(text = state.message)

                is LoadState.Success -> {
                    DeliverySummary(
                        orderId = state.data.order?.id,
                        destinationAddress = uiState.destinationAddress
                    )
                    state.data.order?.let { order ->
                        CourierDeliveryMap(
                            destination = order.deliveryLocation?.point,
                            courier = uiState.currentFix?.point ?: uiState.lastReportedLocation?.let { location ->
                                val lat = location.latitude
                                val lng = location.longitude
                                if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) GeoPoint(lat, lng) else null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            PermissionSection(
                permissionState = uiState.permissionState,
                locationReadStatus = uiState.locationReadStatus,
                onRequestPermission = permissionRequest.launcher
            )

            PositionSection(uiState = uiState)

            Text("Lokasi dikirim otomatis selama masih ada pengantaran aktif. Pastikan GPS dan internet menyala.", style = MaterialTheme.typography.bodySmall)

            uiState.message?.let { message ->
                CourierCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = CourierColors.InfoContainer,
                    elevation = 0.dp
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CourierColors.Info
                    )
                }
            }

            if (TrackingIntervals.IS_UNRESOLVED) {
                Text(
                    text = TrackingIntervals.UNRESOLVED_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeliverySummary(orderId: String?, destinationAddress: String?) {
    CourierCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Ringkasan Pengantaran", style = MaterialTheme.typography.titleLarge)
            // Assignment Resource hanya mengirim `order.id`, bukan
            // `order_number`. Yang ditampilkan adalah apa adanya.
            orderId?.let { InfoRow(label = "ID pesanan", value = it) }
            InfoRow(
                label = "Alamat tujuan",
                value = destinationAddress ?: "Alamat tidak dikirim backend"
            )
        }
    }
}

/**
 * Bagian izin lokasi.
 *
 * Keempat keadaan dipisahkan karena tindakannya berbeda. Menolak secara
 * permanen tidak bisa diperbaiki dengan dialog sistem, jadi layar mengarahkan
 * pengguna ke pengaturan aplikasi.
 */
@Composable
private fun PermissionSection(
    permissionState: LocationPermissionState,
    locationReadStatus: LocationReadStatus,
    onRequestPermission: () -> Unit
) {
    CourierCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Akses Lokasi", style = MaterialTheme.typography.titleLarge)

            when (permissionState) {
                LocationPermissionState.GRANTED -> Text(
                    text = "Izin lokasi sudah diberikan.",
                    style = MaterialTheme.typography.bodyMedium
                )

                LocationPermissionState.NOT_REQUESTED -> {
                    Text(
                        text = "Izin lokasi dibutuhkan untuk mengirim posisi kurir.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    PrimaryButton(
                        text = "Berikan Izin Lokasi",
                        onClick = onRequestPermission
                    )
                }

                LocationPermissionState.DENIED -> {
                    Text(
                        text = "Izin lokasi ditolak. Posisi tidak dapat dikirim " +
                                "sampai izin diberikan.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    PrimaryButton(
                        text = "Coba Lagi",
                        onClick = onRequestPermission
                    )
                }

                LocationPermissionState.DENIED_PERMANENTLY -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationDisabled,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Izin lokasi ditolak permanen. Buka pengaturan " +
                                    "aplikasi untuk mengaktifkannya kembali.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (permissionState == LocationPermissionState.GRANTED) {
                when (locationReadStatus) {
                    LocationReadStatus.Unknown, LocationReadStatus.Locating ->
                        Text(
                            text = "Membaca posisi perangkat...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                    LocationReadStatus.Ready -> Text(
                        text = "Posisi perangkat terbaca.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LocationReadStatus.PermissionDenied -> Text(
                        text = "Izin lokasi tidak tersedia saat pembacaan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    LocationReadStatus.Unavailable -> Text(
                        text = "Lokasi tidak dapat dibaca di perangkat ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Bagian posisi.
 *
 * Yang ditampilkan sebagai posisi resmi adalah nilai yang balik dari server.
 * Pembacaan perangkat terakhir ditampilkan terpisah karena belum tentu sudah
 * diterima backend.
 */
@Composable
private fun PositionSection(uiState: CourierTrackingUiState) {
    CourierCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(text = "Posisi Kurir", style = MaterialTheme.typography.titleLarge)
            }

            val confirmed = uiState.latestLocation
            if (confirmed == null) {
                Text(
                    text = "Belum ada posisi yang diterima backend untuk penugasan ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ConfirmedLocationRows(location = confirmed)
            }

            uiState.currentFix?.let { fix ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Pembacaan perangkat terakhir: " +
                            String.format(
                                Locale("id", "ID"),
                                "%.6f, %.6f",
                                fix.point.latitude,
                                fix.point.longitude
                            ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConfirmedLocationRows(location: CourierDeviceLocation) {
    // Koordinat nullable di model karena DTO-nya bisa kosong, tapi backend tidak
    // pernah mengirim koordinat yang tidak ada. Kalau sampai kosong, baris
    // koordinat tidak ditampilkan sama sekali. Menampilkan `0.000000` akan
    // mengklaim kurir berada di titik nol bumi, dan itu mengarang lokasi.
    val latitude = location.latitude
    val longitude = location.longitude
    if (latitude != null && longitude != null) {
        InfoRow(label = "Latitude", value = String.format(Locale("id", "ID"), "%.6f", latitude))
        InfoRow(label = "Longitude", value = String.format(Locale("id", "ID"), "%.6f", longitude))
    } else {
        Text(
            text = "Backend tidak mengirim koordinat untuk posisi ini.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Akurasi dan waktu tetap ditampilkan walaupun koordinat tidak ada,
    // karena keduanya berasal dari fields yang terpisah.
    location.accuracyMeters?.let { accuracy ->
        InfoRow(label = "Akurasi", value = formatAccuracy(accuracy))
    }
    location.recordedAt?.let { recordedAt ->
        InfoRow(label = "Waktu", value = recordedAt)
    }
}

/**
 * Tombol mulai dan hentikan.
 *
 * Tombol Mulai mati tanpa izin lokasi, karena menjalankannya tanpa izin akan
 * menghasilkan loop yang tidak pernah selesai. Backend tetap memeriksa ulang
 * setiap pengiriman, jadi tombol yang aktif tidak berarti pengiriman pasti
 * diterima.
 */
@Composable
private fun TrackingControls(
    uiState: CourierTrackingUiState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val canTrack = uiState.delivery?.canPostLocation == true
    val permissionGranted = uiState.permissionState.allowsAcquisition

    if (uiState.isTracking) {
        SecondaryButton(
            text = "Hentikan Pengiriman Posisi",
            onClick = onStop,
            modifier = Modifier.fillMaxWidth()
        )
        // Notifikasi foreground service yang tampil adalah yang menjadi
        // pengingat utama; catatan ini menjelaskan ke mana notifikasi itu
        // berasal. Tidak ada lokasi yang dikirim di luar layanan itu.
        Text(
            text = "Posisi terus dikirim lewat layanan latar belakang yang tampil " +
                    "sebagai notifikasi selama pengantaran berjalan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        PrimaryButton(
            text = if (permissionGranted) "Mulai Kirim Posisi" else "Beri Izin Lokasi",
            onClick = onStart,
            enabled = canTrack,
            modifier = Modifier.fillMaxWidth()
        )
    }

    uiState.stopReason?.let { reason ->
        Text(
            text = reason.explanation(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Penjelasan singkat kenapa tracking berhenti, dalam Bahasa Indonesia. */
private fun TrackingStopReason.explanation(): String = when (this) {
    TrackingStopReason.USER_REQUESTED -> "Pengiriman posisi dihentikan oleh kurir."
    TrackingStopReason.DELIVERY_NOT_ACTIVE ->
        "Pengiriman posisi dihentikan karena delivery tidak lagi aktif."
    TrackingStopReason.DUTY_SESSION_ENDED ->
        "Pengiriman posisi berhenti karena sesi bertugas sudah berakhir."
    TrackingStopReason.PERMISSION_NOT_GRANTED ->
        "Pengiriman posisi berhenti karena izin lokasi tidak diberikan."
    TrackingStopReason.PROVIDER_DISABLED ->
        "Pengiriman posisi berhenti karena layanan lokasi dimatikan."
    TrackingStopReason.LOCATION_UNAVAILABLE ->
        "Pengiriman posisi berhenti karena lokasi tidak dapat dibaca."
    TrackingStopReason.SESSION_EXPIRED ->
        "Pengiriman posisi berhenti karena sesi sudah berakhir."
}

private fun formatAccuracy(accuracy: Double): String =
    String.format(Locale("id", "ID"), "%.2f m", accuracy)
