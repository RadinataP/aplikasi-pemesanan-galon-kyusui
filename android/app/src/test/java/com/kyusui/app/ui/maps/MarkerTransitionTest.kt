package com.kyusui.app.ui.maps

import com.kyusui.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan pergerakan marker kurir.
 *
 * Menjaga agar marker tidak beranimasi tanpa data baru, tidak bergerak ke
 * koordinat tidak valid, dan interpolasinya tidak melewati titik tujuan.
 */
class MarkerTransitionTest {

    @Test
    fun `a real coordinate inside the world is valid`() {
        assertTrue(isValidGeoPoint(GeoPoint(latitude = -6.175392, longitude = 106.827153)))
        assertTrue(isValidGeoPoint(GeoPoint(latitude = -90.0, longitude = 180.0)))
        assertTrue(isValidGeoPoint(GeoPoint(latitude = 90.0, longitude = -180.0)))
    }

    @Test
    fun `missing or out-of-range coordinates are rejected`() {
        assertFalse(isValidGeoPoint(null))
        assertFalse(isValidGeoPoint(GeoPoint(latitude = -90.5, longitude = 0.0)))
        assertFalse(isValidGeoPoint(GeoPoint(latitude = 90.5, longitude = 0.0)))
        assertFalse(isValidGeoPoint(GeoPoint(latitude = 0.0, longitude = -180.5)))
        assertFalse(isValidGeoPoint(GeoPoint(latitude = 0.0, longitude = 180.5)))
        assertFalse(isValidGeoPoint(GeoPoint(latitude = Double.NaN, longitude = 0.0)))
        assertFalse(isValidGeoPoint(GeoPoint(latitude = 0.0, longitude = Double.NaN)))
        assertFalse(isValidGeoPoint(GeoPoint(latitude = 0.0, longitude = Double.POSITIVE_INFINITY)))
    }

    @Test
    fun `an unchanged coordinate does not start a new animation`() {
        val from = GeoPoint(latitude = -6.1, longitude = 106.8)
        val same = GeoPoint(latitude = -6.1, longitude = 106.8)

        assertFalse(shouldAnimateMarker(from = from, to = same))
    }

    @Test
    fun `a new valid coordinate needs animation`() {
        val from = GeoPoint(latitude = -6.1, longitude = 106.8)
        val to = GeoPoint(latitude = -6.2, longitude = 106.9)

        assertTrue(shouldAnimateMarker(from = from, to = to))
    }

    @Test
    fun `an invalid or missing coordinate never needs animation`() {
        val from = GeoPoint(latitude = -6.1, longitude = 106.8)
        val invalid = GeoPoint(latitude = 91.0, longitude = 106.8)

        // Posisi lama belum ada.
        assertFalse(shouldAnimateMarker(from = null, to = from))
        // Data baru tidak valid, misalnya akibat parsing yang gagal.
        assertFalse(shouldAnimateMarker(from = from, to = invalid))
        assertFalse(shouldAnimateMarker(from = from, to = null))
    }

    @Test
    fun `interpolation at zero and one lands exactly on the endpoints`() {
        val from = GeoPoint(latitude = -6.0, longitude = 106.0)
        val to = GeoPoint(latitude = -7.0, longitude = 107.0)

        assertEquals(from, interpolateGeoPoint(from = from, to = to, fraction = 0f))
        assertEquals(to, interpolateGeoPoint(from = from, to = to, fraction = 1f))
    }

    @Test
    fun `interpolation halfway sits between the two points`() {
        val from = GeoPoint(latitude = -6.0, longitude = 106.0)
        val to = GeoPoint(latitude = -7.0, longitude = 107.0)

        val middle = interpolateGeoPoint(from = from, to = to, fraction = 0.5f)

        assertEquals(-6.5, middle.latitude, 1e-9)
        assertEquals(106.5, middle.longitude, 1e-9)
    }

    @Test
    fun `fraction outside zero to one is clamped so the marker never overshoots`() {
        val from = GeoPoint(latitude = -6.0, longitude = 106.0)
        val to = GeoPoint(latitude = -7.0, longitude = 107.0)

        assertEquals(from, interpolateGeoPoint(from, to, -0.5f))
        assertEquals(to, interpolateGeoPoint(from, to, 1.5f))
    }
}
