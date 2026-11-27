package com.kyusui.app.ui.courier.location

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.location.readLastKnownLocationFix
import com.kyusui.app.ui.location.rememberLocationRequestState
import com.kyusui.app.ui.state.LoadState
import java.util.Locale

/**
 * Layar lokasi pelanggan untuk satu assignment.
 *
 * Isi layar ini sengaja hanya teks dan koordinat kurir, tanpa peta. Alasannya
 * tercatat di `CustomerLocationViewModel`: backend tidak mengirim koordinat
 * tujuan untuk kurir, hanya `delivery_address` berupa teks, dan tidak ada endpoint
 * maupun SDK peta di repo ini. Menampilkan titik tujuan berarti mengarang nilai.
 *
 * Tombol kirim posisi memakai pembacaan `LocationManager` dari platform melalui
 * helper bersama di `ui.location`, yang sama dipakai form order customer. Izin
 * diminta saat kurir menekan tombol, bukan otomatis saat layar dibuka.
 */
@Composable
fun CustomerLocationScreen(
    uiState: CustomerLocationUiState,
    onSendLocation: (CourierLocationFix) -> Unit,
    onLocationReadStatusChanged: (CourierLocationReadStatus) -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val locationRequest = rememberLocationRequestState(
        onPermissionDenied = {
            onLocationReadStatusChanged(CourierLocationReadStatus.PermissionDenied)
        },
        onPermissionGranted = {
            onLocationReadStatusChanged(CourierLocationReadStatus.Locating)
            // Pembacaan dilakukan setelah izin diberikan, bukan saat layar dibuka,
            // supaya kurir tidak mengirim koordinat yang tidak diizinkan.
            val fix = readLastKnownLocationFix(context)
            if (fix == null) {
                onLocationReadStatusChanged(CourierLocationReadStatus.Unavailable)
            } else {
                onLocationReadStatusChanged(CourierLocationReadStatus.Ready)
                onSendLocation(fix)
            }
        }
    )

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        modifier = modifier,
        containerColor = CourierColors.Background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = "Lokasi Pelanggan") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState.deliveryState) {
            is LoadState.Loading -> LoadingState(
                modifier = Modifier.padding(innerPadding),
                message = "Memuat data lokasi..."
            )

            is LoadState.Error -> ErrorState(
                modifier = Modifier.padding(innerPadding),
                message = state.message,
                isRetryable = state.isRetryable,
                onRetry = if (state.isRetryable) onRetry else null
            )

            is LoadState.Empty -> LoadingState(
                modifier = Modifier.padding(innerPadding),
                message = "Memuat data lokasi..."
            )

            is LoadState.Success -> LocationContent(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                onRequestLocation = locationRequest.launcher,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun LocationContent(
    uiState: CustomerLocationUiState,
    onRequestLocation: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CourierSpacing.Screen)
            .padding(top = CourierSpacing.Lg, bottom = CourierSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(CourierSpacing.Lg)
    ) {
        CourierPageIntro(
            eyebrow = "Operasional / Tujuan",
            title = "Lokasi pelanggan",
            description = "Periksa alamat tujuan dan lokasi yang terakhir diterima server."
        )
        DestinationCard(uiState = uiState)

        LatestLocationCard(uiState = uiState)

        uiState.errorMessage?.let { message ->
            InlineError(message = message, onRetry = onRetry)
        }

        Text(
            text = if (uiState.canSendLocation) "Lokasi kurir diperbarui otomatis selama pengantaran. Pastikan GPS dan internet aktif." else "Tracking berhenti setelah pengantaran selesai.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DestinationCard(uiState: CustomerLocationUiState, modifier: Modifier = Modifier) {
    CourierCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "Informasi Tujuan", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            uiState.customerName?.let { customerName ->
                LabeledValueRow(
                    icon = Icons.Outlined.Person,
                    label = "Pelanggan",
                    value = customerName
                )
            }

            LabeledValueRow(
                icon = Icons.Outlined.LocationOn,
                label = "Alamat",
                value = uiState.destinationAddress ?: ALAMAT_TIDAK_TERSEDIA
            )

            Spacer(Modifier.height(8.dp))
            // Backend tidak mengirim koordinat tujuan untuk kurir, jadi layar ini
            // tidak menampilkan titik tujuan. Menampilkan koordinat nol akan
            // terlihat seperti lokasi yang diketahui padahal tidak.
            Text(
                text = DESTINATION_COORDINATE_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LatestLocationCard(uiState: CustomerLocationUiState, modifier: Modifier = Modifier) {
    CourierCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "Posisi Terakhir Kurir", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            val location = uiState.latestLocation
            if (location == null) {
                Text(
                    text = CustomerLocationViewModel.NO_LOCATION_YET_MESSAGE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LocationValues(location = location)
            }
        }
    }
}

/**
 * Nilai koordinat dari server.
 *
 * Angka koordinat ditampilkan dengan format locale Indonesia dan hanya
 * apa adanya. Android tidak menghitung jarak ke tujuan karena koordinat tujuan
 * tidak tersedia untuk kurir.
 */
@Composable
private fun LocationValues(location: CourierDeviceLocation, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabeledValueRow(
            icon = Icons.Outlined.MyLocation,
            label = "Lintang",
            value = formatCoordinate(location.latitude, LEBAR_KOORDINAT)
        )
        LabeledValueRow(
            icon = Icons.Outlined.MyLocation,
            label = "Bujur",
            value = formatCoordinate(location.longitude, LEBAR_KOORDINAT)
        )
        LabeledValueRow(
            icon = Icons.Outlined.MyLocation,
            label = "Akurasi",
            value = location.accuracyMeters
                ?.let { accuracy ->
                    String.format(Locale("id", "ID"), "%.2f m", accuracy)
                }
                ?: AKURASI_TIDAK_TERSEDIA
        )
        location.recordedAt?.let { recordedAt ->
            LabeledValueRow(
                icon = Icons.Outlined.LocationOn,
                label = "Waktu Tercatat",
                value = recordedAt
            )
        }
    }
}

@Composable
private fun LocationReadStatusCard(
    status: CourierLocationReadStatus,
    modifier: Modifier = Modifier
) {
    if (status == CourierLocationReadStatus.Unknown) return

    CourierCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = status.label,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = status.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LabeledValueRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Label status pembacaan lokasi.
 *
 * Dipisah dari enum supaya label Bahasa Indonesia ikut bisa diuji tanpa lewat
 * komposisi.
 */
val CourierLocationReadStatus.label: String
    get() = when (this) {
        CourierLocationReadStatus.Unknown -> "Belum ada percobaan baca lokasi"
        CourierLocationReadStatus.Locating -> "Membaca lokasi perangkat..."
        CourierLocationReadStatus.Ready -> "Lokasi perangkat berhasil dibaca"
        CourierLocationReadStatus.PermissionDenied ->
            "Izin lokasi ditolak, jadi posisi tidak bisa dikirim"

        CourierLocationReadStatus.Unavailable ->
            "Izin lokasi ada, tetapi perangkat tidak memberikan lokasi yang bisa dibaca"
    }

val CourierLocationReadStatus.explanation: String
    get() = when (this) {
        CourierLocationReadStatus.Unknown ->
            "Tekan Kirim Posisi Saya untuk membaca lokasi perangkat."

        CourierLocationReadStatus.Locating ->
            "Tunggu sampai perangkat selesai memberikan koordinat terakhir."

        CourierLocationReadStatus.Ready ->
            "Koordinat sudah dikirim dan menunggu jawaban server."

        CourierLocationReadStatus.PermissionDenied ->
            "Android hanya mengirim koordinat bila izin lokasi diberikan. " +
                "Kurir tetap bisa melihat alamat tujuan tanpa izin ini."

        CourierLocationReadStatus.Unavailable ->
            "Tidak ada provider lokasi yang bisa dibaca. " +
                "Posisi tidak dikirim agar koordinat salah tidak terkirim ke server."
    }

private fun formatCoordinate(value: Double?, pattern: String): String {
    val coordinate = value ?: return KOORDINAT_TIDAK_TERSEDIA
    return String.format(Locale("id", "ID"), pattern, coordinate)
}

private const val LEBAR_KOORDINAT = "%.7f"
private const val ALAMAT_TIDAK_TERSEDIA = "Alamat tidak tersedia"
private const val AKURASI_TIDAK_TERSEDIA = "Akurasi tidak dilaporkan"
private const val KOORDINAT_TIDAK_TERSEDIA = "Koordinat tidak tersedia"

private const val DESTINATION_COORDINATE_NOTE =
    "Backend hanya mengirim alamat tujuan untuk kurir, bukan koordinat. " +
        "Karena itu layar ini tidak menampilkan peta atau titik tujuan."

private const val LOCATION_DISABLED_NOTE =
    "Kirim posisi hanya tersedia saat pengantaran sedang berjalan."