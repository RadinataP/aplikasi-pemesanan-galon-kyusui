package com.kyusui.app.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.model.GeoPoint

const val LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION
const val COARSE_LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

/**
 * Pembungkus permintaan izin lokasi runtime.
 *
 * File ini berada di paket bersama, bukan di `ui.customer`, karena dua role
 * membutuhkannya dengan aturan yang sama: customer memproses titik pengantaran
 * saat membuat order, dan kurir mengirim posisinya selama pengantaran berjalan.
 * Duplikasinya berarti dua salinan alur izin yang bisa berbeda perilaku.
 *
 * Android menggunakan `LocationManager` dari platform, tanpa dependency lokasi
 * pihak ketiga. Jika izin ditolak atau lokasi tidak tersedia, status dikembalikan
 * ke ViewModel supaya UI bisa menjelaskan kondisinya dengan jujur dan pengguna
 * tidak unknowingly mengirim koordinat yang salah.
 */
class LocationRequestState internal constructor(
    internal val launcher: () -> Unit
) {
    /** True bila izin lokasi sudah diberikan sebelumnya. */
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, LOCATION_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, COARSE_LOCATION_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
}

@Composable
fun rememberLocationRequestState(
    onPermissionDenied: () -> Unit,
    onPermissionGranted: () -> Unit = {}
): LocationRequestState {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[LOCATION_PERMISSION] == true ||
            result[COARSE_LOCATION_PERMISSION] == true
        // Callback granted wajib dipanggil: status "Locating" diset sebelum
        // permintaan, jadi tanpa ini UI akan menggantung di status tersebut.
        if (granted) onPermissionGranted() else onPermissionDenied()
    }

    return remember(context) {
        LocationRequestState(
            launcher = {
                launcher.launch(
                    arrayOf(LOCATION_PERMISSION, COARSE_LOCATION_PERMISSION)
                )
            }
        )
    }
}

/**
 * Membaca koordinat terakhir yang diketahui perangkat.
 *
 * Mengembalikan null bila izin belum diberikan atau tidak ada provider yang
 * dapat dibaca. Nilai yang dikembalikan adalah nilai perangkat, bukan estimasi
 * dari backend.
 */
fun readLastKnownLocation(context: Context): GeoPoint? =
    readLastKnownLocationFix(context)?.point

/**
 * Membaca koordinat dan akurasi terakhir yang diketahui perangkat.
 *
 * [CourierRepository.updateLocation] mewajibkan `accuracy_meters`, jadi pembacaan
 * untuk kurir memakai bentuk yang menyimpan ketiganya. Akurasi dibaca dari
 * `Location` apa adanya; bila provider tidak melaporkannya, `0.0` dikirim dan
 * backend tetap yang memutuskan apakah angka itu diterima.
 *
 * Nilai yang dikembalikan adalah nilai perangkat, bukan estimasi dari backend,
 * dan tidak pernah diisi dengan titik tujuan customer.
 */
fun readLastKnownLocationFix(context: Context): CourierLocationFix? {
    val location = readLastKnownPlatformLocation(context) ?: return null
    val accuracy = if (location.hasAccuracy()) {
        location.accuracy.toDouble()
    } else {
        0.0
    }
    return CourierLocationFix(
        point = GeoPoint(location.latitude, location.longitude),
        accuracyMeters = accuracy
    )
}

/**
 * Membaca `Location` mentah dari provider yang tersedia.
 *
 * Dipisah agar logika izin dan pemilihan provider hanya ada di satu tempat,
 * sementara [readLastKnownLocation] dan [readLastKnownLocationFix] hanya
 * memilih bentuk domain yang dibutuhkan pemanggilnya.
 */
private fun readLastKnownPlatformLocation(context: Context): Location? {
    val hasFine = ContextCompat.checkSelfPermission(context, LOCATION_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, COARSE_LOCATION_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
        as? LocationManager
        ?: return null

    val providers = buildList {
        if (hasFine) add(LocationManager.GPS_PROVIDER)
        if (hasCoarse) add(LocationManager.NETWORK_PROVIDER)
    }

    return providers.mapNotNull { provider ->
        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
}