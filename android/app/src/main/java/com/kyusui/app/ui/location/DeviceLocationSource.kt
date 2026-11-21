package com.kyusui.app.ui.location

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.model.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Status izin lokasi.
 *
 * [NOT_REQUESTED] dipisahkan dari [DENIED_PERMANENTLY] karena keduanya membuat
 * `shouldShowRequestPermissionRationale` mengembalikan false. Tanpa
 * pemisahan itu, pengguna yang belum pernah ditanya akan diberi tahu bahwa
 * telah menolak permanen dan tidak bisa membuka pengaturan, padahal dialog
 * masih bisa dimunculkan.
 */
enum class LocationPermissionState {
    /** Izin diberikan, entah fine atau coarse. */
    GRANTED,

    /** Belum pernah ditanyakan; dialog sistem masih bisa dimunculkan. */
    NOT_REQUESTED,

    /** Ditolak, dan sistem masih mengizinkan dialog ditanyakan lagi. */
    DENIED,

    /**
     * Ditolak dan sistem tidak akan menampilkan dialog lagi. Satu-satunya
     * jalan adalah membuka pengaturan aplikasi.
     */
    DENIED_PERMANENTLY;

    /** True bila acquisition boleh dijalankan. */
    val allowsAcquisition: Boolean
        get() = this == GRANTED
}

/**
 * Hasil satu pembacaan lokasi dari perangkat.
 *
 * Keadaan yang tidak berhasil dibedakan satu per satu karena cara pengguna
 * menanganinya berbeda: izin yang ditolak perlu dialog, sedangkan GPS yang
 * dimatikan harus diaktifkan lewat pengaturan perangkat.
 */
sealed interface LocationReadResult {
    /** Koordinat terbaca. */
    data class Fix(val fix: CourierLocationFix) : LocationReadResult

    /** Izin lokasi belum diberikan. */
    data object PermissionMissing : LocationReadResult

    /** GPS atau layanan lokasi dimatikan di perangkat. */
    data object ProviderDisabled : LocationReadResult

    /** Tidak ada sumber lokasi yang bisa dipakai, termasuk Play services absen. */
    data object Unavailable : LocationReadResult

    /** True bila pembacaan berhasil dan koordinat boleh dikirim. */
    val hasFix: Boolean
        get() = this is Fix
}

/**
 * Sumber lokasi perangkat, dibungkus interface agar ViewModel bisa diuji
 * tanpa Play services.
 *
 * Implementasi produksi adalah [FusedDeviceLocationSource].
 */
interface DeviceLocationSource {

    /** Membaca satu posisi terakhir yang diketahui. */
    suspend fun readOnce(): LocationReadResult

    /**
     * Memancarkan posisi secara terus-menerus.
     *
     * Implementasi wajib berhenti acquire begitu flow ini dibatalkan,
     * supaya listener tidak menggantung setelah tracking selesai.
     *
     * @param intervalMillis jarak minimum antar-acquisition, dalam milidetik.
     *   Nilainya diteruskan dari [com.kyusui.app.core.TrackingIntervals], bukan
     *   ditulis di sini.
     */
    fun stream(intervalMillis: Long): Flow<LocationReadResult>
}

/**
 * Sumber lokasi berbasis Fused Location Provider.
 *
 * Fused Location Provider dipilih karena specification tracking menyebutnya
 * secara eksplisit, dan karena FLP sudah menangani pemilihan provider GPS
 * versus jaringan serta terlihat dari sensor akselerometer, sehingga satu
 * listener cukup untuk berbagi koordinat yang lebih baik.
 *
 * ## Fallback ke platform
 *
 * FLP butuh Google Play services. Di perangkat tanpa Play services, kelas ini
 * mundur ke pembacaan `LocationManager` milik platform yang sudah dipakai
 * fase sebelumnya, sehingga kurir tetap bisa mengirim posisi. Keluarannya
 * dilapisi [LocationReadResult.Unavailable] hanya ketika kedua sumber gagal,
 * itu pun hanya bila izin sudah diberikan.
 */
class FusedDeviceLocationSource(context: Context) : DeviceLocationSource {

    private val appContext: Context = context.applicationContext

    override suspend fun readOnce(): LocationReadResult {
        if (!hasLocationPermission(appContext)) return LocationReadResult.PermissionMissing

        val fusedFix = readFusedLastKnown() ?: readPlatformLastKnown()
        return fusedFix?.let { LocationReadResult.Fix(it) }
            ?: if (isPlayServicesAvailable(appContext) || isPlatformLocationEnabled()) {
                LocationReadResult.Unavailable
            } else {
                LocationReadResult.ProviderDisabled
            }
    }

    override fun stream(intervalMillis: Long): Flow<LocationReadResult> =
        if (isPlayServicesAvailable(appContext)) {
            fusedLocationStream(intervalMillis)
        } else {
            // Beberapa perangkat/ROM (termasuk environment tanpa Google Play
            // Services valid) tidak dapat memakai Fused Location Provider.
            // Jangan biarkan stream gagal sebelum mengirim titik apa pun:
            // gunakan LocationManager Android sebagai fallback nyata.
            platformLocationStream(intervalMillis)
        }

    private fun fusedLocationStream(intervalMillis: Long): Flow<LocationReadResult> = callbackFlow {
        if (!hasLocationPermission(appContext)) {
            trySend(LocationReadResult.PermissionMissing)
            close()
            return@callbackFlow
        }

        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val fix = location.toCourierFix() ?: return
                trySend(LocationReadResult.Fix(fix))
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { error -> close(error) }

        awaitClose { client.removeLocationUpdates(callback) }
    }

    /** Stream lokasi Android platform untuk perangkat tanpa Play Services valid. */
    @Suppress("MissingPermission")
    private fun platformLocationStream(intervalMillis: Long): Flow<LocationReadResult> = callbackFlow {
        if (!hasLocationPermission(appContext)) {
            trySend(LocationReadResult.PermissionMissing)
            close()
            return@callbackFlow
        }

        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            trySend(LocationReadResult.Unavailable)
            close()
            return@callbackFlow
        }

        val providers = buildList {
            if (isPermissionGranted(appContext, LOCATION_PERMISSION)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (isPermissionGranted(appContext, COARSE_LOCATION_PERMISSION)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }.filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }

        if (providers.isEmpty()) {
            trySend(LocationReadResult.ProviderDisabled)
            close()
            return@callbackFlow
        }

        providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { it.hasAccuracy() }
            .maxByOrNull { it.time }
            ?.toCourierFix()
            ?.let { trySend(LocationReadResult.Fix(it)) }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val fix = location.toCourierFix() ?: return
                trySend(LocationReadResult.Fix(fix))
            }

            @Deprecated("Deprecated by Android API; retained for older supported devices")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }

        try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(
                    provider,
                    intervalMillis.coerceAtLeast(MIN_PLATFORM_LOCATION_INTERVAL_MS),
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {
            trySend(LocationReadResult.PermissionMissing)
            close()
        } catch (_: IllegalArgumentException) {
            trySend(LocationReadResult.Unavailable)
            close()
        }

        awaitClose { runCatching { manager.removeUpdates(listener) } }
    }

    /** Fused tidak selalu punya fix terakhir; null berarti belum pernah ada. */
    private suspend fun readFusedLastKnown(): CourierLocationFix? =
        if (!isPlayServicesAvailable(appContext)) {
            null
        } else {
            val client = LocationServices.getFusedLocationProviderClient(appContext)
            suspendCancellableCoroutine { continuation ->
                client.lastLocation
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) continuation.resume(location?.toCourierFix())
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        }

    private fun isPlatformLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .any { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
    }

    private fun readPlatformLastKnown(): CourierLocationFix? {
        if (!hasLocationPermission(appContext)) return null
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE)
                as? android.location.LocationManager ?: return null
        val providers = buildList {
            if (isPermissionGranted(appContext, LOCATION_PERMISSION)) {
                add(android.location.LocationManager.GPS_PROVIDER)
            }
            if (isPermissionGranted(appContext, COARSE_LOCATION_PERMISSION)) {
                add(android.location.LocationManager.NETWORK_PROVIDER)
            }
        }
        return providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { it.hasAccuracy() }
            .maxByOrNull { it.time }
            ?.toCourierFix()
    }
}

/** True bila Play services tersedia dan dapat dipakai. */
fun isPlayServicesAvailable(context: Context): Boolean =
    GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

/** True bila salah satu dari dua izin lokasi sudah diberikan. */
fun hasLocationPermission(context: Context): Boolean =
    isPermissionGranted(context, LOCATION_PERMISSION) ||
            isPermissionGranted(context, COARSE_LOCATION_PERMISSION)

private fun isPermissionGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

/**
 * Membangun fix dari angka yang dilaporkan perangkat.
 *
 * Mengembalikan `null` bila perangkat tidak melaporkan akurasi. Backend
 * mewajibkan `accuracy_meters` sebagai angka (`required`, `min:0`), jadi tidak
 * ada cara mengirim titik tanpa akurasi. Mengisi `0.0` sebagai pengganti akan
 * mengklaim akurasi 0 meter, padahal tidak ada pengukuran sama sekali, dan
 * karena backend mengembalikannya apa adanya, angka itu akan muncul lagi di
 * layar kurir seolah-olah hasil pengukuran. Jadi titik tersebut tidak dikirim.
 *
 * Aturannya dipisah dari [Location] supaya bisa diuji di JVM unit test, tempat
 * `android.location.Location` tidak bisa dibuat.
 */
internal fun buildCourierFix(
    latitude: Double,
    longitude: Double,
    hasAccuracy: Boolean,
    accuracy: Float
): CourierLocationFix? {
    if (!hasAccuracy) return null
    return CourierLocationFix(
        point = GeoPoint(latitude, longitude),
        accuracyMeters = accuracy.toDouble()
    )
}

/** [buildCourierFix] untuk pembacaan `Location` milik platform. */
internal fun Location.toCourierFix(): CourierLocationFix? = buildCourierFix(
    latitude = latitude,
    longitude = longitude,
    hasAccuracy = hasAccuracy(),
    accuracy = accuracy
)

/**
 * Menentukan status izin lokasi sekarang.
 *
 * @param hasAsked apakah aplikasi sudah pernah memunculkan dialog izin
 *   pada sesi ini. Nilai itu harus berasal dari ViewModel, karena Android
 *   tidak menyediakan cara untuk membedakannya dari "ditolak secara
 *   permanen".
 */
fun resolveLocationPermissionState(
    context: Context,
    hasAsked: Boolean
): LocationPermissionState {
    if (hasLocationPermission(context)) return LocationPermissionState.GRANTED

    val activity = context.findActivity()
    val canAskAgain = activity != null && LOCATION_PERMISSION_SET.any { permission ->
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    return decideLocationPermissionState(hasAsked = hasAsked, canAskAgain = canAskAgain)
}

/**
 * Keputusan status izin yang tidak bergantung pada `Context`.
 *
 * Dipisah supaya aturan pemisahan `NOT_REQUESTED` dengan
 * `DENIED_PERMANENTLY` bisa diuji di JVM unit test, tempat `ContextCompat`
 * dan `ActivityCompat` tidak tersedia.
 *
 * Aturannya:
 *  - kalau sistem masih mengizinkan dialog, jawabannya [LocationPermissionState.DENIED]
 *    karena pengguna masih punya jalan lain tanpa membuka pengaturan;
 *  - kalau tidak ada dialog dan aplikasi pernah menanyakan, jawabannya
 *    [LocationPermissionState.DENIED_PERMANENTLY];
 *  - kalau tidak ada dialog dan aplikasi belum pernah menanyakan, jawabannya
 *    [LocationPermissionState.NOT_REQUESTED], karena `false` dari sistem saat
 *    aplikasi belum pernah menanyakan berarti hal yang sama dengan penolakan
 *    permanen, dan reporting itu akan salah.
 */
fun decideLocationPermissionState(
    hasAsked: Boolean,
    canAskAgain: Boolean
): LocationPermissionState = when {
    canAskAgain -> LocationPermissionState.DENIED
    hasAsked -> LocationPermissionState.DENIED_PERMANENTLY
    else -> LocationPermissionState.NOT_REQUESTED
}

private const val MIN_PLATFORM_LOCATION_INTERVAL_MS = 1_000L

private val LOCATION_PERMISSION_SET = listOf(
    LOCATION_PERMISSION,
    COARSE_LOCATION_PERMISSION
)

/** Mencari [Activity] dari rantai [ContextWrapper]. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
