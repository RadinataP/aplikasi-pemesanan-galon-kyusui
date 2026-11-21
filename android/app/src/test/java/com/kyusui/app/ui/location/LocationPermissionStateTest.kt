package com.kyusui.app.ui.location

import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Status izin lokasi untuk GPS-001, GPS-002, dan GPS-003.
 *
 * Yang diuji adalah keputusan produksi
 * [decideLocationPermissionState], bukan salinannya. Fungsi itu dipisah dari
 * pemeriksa `Context` supaya aturannya bisa diuji di JVM unit test, tempat
 * `ContextCompat` dan `ActivityCompat` tidak tersedia.
 *
 * Hasil pembacaan koordinat sendiri tidak diuji di sini. Koordinat selalu
 * milik perangkat dan tidak boleh ditebak di Android, jadi test yang
 * mengarang angka hanya akan menguji dirinya sendiri.
 */
class LocationPermissionStateTest {

    @Test
    fun `GPS-001 granted is the only state that allows acquisition`() {
        assertTrue(LocationPermissionState.GRANTED.allowsAcquisition)

        assertFalse(LocationPermissionState.NOT_REQUESTED.allowsAcquisition)
        assertFalse(LocationPermissionState.DENIED.allowsAcquisition)
        assertFalse(LocationPermissionState.DENIED_PERMANENTLY.allowsAcquisition)
    }

    @Test
    fun `GPS-002 not requested is reported before any dialog has been shown`() {
        // `shouldShowRequestPermissionRationale` juga false saat aplikasi
        // belum pernah menanyakan. Kalau ini ikut dilaporkan sebagai penolakan
        // permanen, pengguna pertama kali akan disuruh membuka pengaturan.
        assertEquals(
            LocationPermissionState.NOT_REQUESTED,
            decideLocationPermissionState(hasAsked = false, canAskAgain = false)
        )
    }

    @Test
    fun `GPS-002 denied while the system can still show a dialog`() {
        assertEquals(
            LocationPermissionState.DENIED,
            decideLocationPermissionState(hasAsked = true, canAskAgain = true)
        )
    }

    @Test
    fun `GPS-002 permanent denial once the system refuses to show a dialog again`() {
        assertEquals(
            LocationPermissionState.DENIED_PERMANENTLY,
            decideLocationPermissionState(hasAsked = true, canAskAgain = false)
        )
    }

    @Test
    fun `GPS-002 canAskAgain wins over hasAsked`() {
        // Bila dialog masih mungkin, itu selalu DENIED walaupun aplikasi
        // sudah pernah menanyakan sebelumnya.
        assertEquals(
            LocationPermissionState.DENIED,
            decideLocationPermissionState(hasAsked = true, canAskAgain = true)
        )
    }

    @Test
    fun `GPS-003 a missing permission read never carries a coordinate`() {
        assertFalse(LocationReadResult.PermissionMissing.hasFix)
    }

    @Test
    fun `GPS-003 a disabled provider read never carries a coordinate`() {
        assertFalse(LocationReadResult.ProviderDisabled.hasFix)
    }

    @Test
    fun `GPS-003 an unavailable location read never carries a coordinate`() {
        assertFalse(LocationReadResult.Unavailable.hasFix)
    }

    @Test
    fun `GPS-003 only a fix is allowed to carry a coordinate`() {
        val fix = LocationReadResult.Fix(
            CourierLocationFix(point = GeoPoint(latitude = -6.2, longitude = 100.0), accuracyMeters = 8.5)
        )

        assertTrue(fix.hasFix)
    }

    @Test
    fun `GPS-003 exactly one of the four read results carries a coordinate`() {
        val results = listOf(
            LocationReadResult.PermissionMissing,
            LocationReadResult.ProviderDisabled,
            LocationReadResult.Unavailable,
            LocationReadResult.Fix(
                CourierLocationFix(
                    point = GeoPoint(latitude = -6.2, longitude = 100.0),
                    accuracyMeters = 8.5
                )
            )
        )

        assertEquals(1, results.count { it.hasFix })
    }

    @Test
    fun `a reading without a reported accuracy never becomes a submittable fix`() {
        // Backend mewajibkan `accuracy_meters` sebagai angka, jadi pembacaan yang
        // tidak punya laporan akurasi tidak bisa dikirim. Mengisi 0.0 akan
        // mengklaim akurasi 0 meter, dan karena backend mengembalikannya apa
        // adanya, angka itu akan tampil lagi di layar kurir seolah-olah hasil
        // pengukuran. Jadi titiknya tidak dikirim, bukan dikirim dengan angka
        // pengganti.
        assertNull(
            buildCourierFix(
                latitude = -6.2,
                longitude = 100.0,
                hasAccuracy = false,
                accuracy = 0f
            )
        )
    }

    @Test
    fun `a reported accuracy is carried into the fix unchanged`() {
        val fix = buildCourierFix(
            latitude = -6.2,
            longitude = 100.0,
            hasAccuracy = true,
            accuracy = 8.5f
        )

        assertNotNull(fix)
        assertEquals(8.5, fix!!.accuracyMeters, 1e-9)
        assertEquals(-6.2, fix.point.latitude, 1e-9)
        assertEquals(100.0, fix.point.longitude, 1e-9)

        // Akurasi 0.00 m yang benar-benar dilaporkan perangkat tetap dikirim
        // apa adanya. Yang dilarang adalah mengarang angka, bukan angka 0.
        val zeroAccuracy = buildCourierFix(
            latitude = 1.0,
            longitude = 2.0,
            hasAccuracy = true,
            accuracy = 0f
        )
        assertNotNull(zeroAccuracy)
        assertEquals(0.0, zeroAccuracy!!.accuracyMeters, 1e-9)
    }
}
