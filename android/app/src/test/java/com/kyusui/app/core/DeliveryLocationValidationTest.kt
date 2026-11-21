package com.kyusui.app.core

import com.kyusui.app.core.DeliveryLocationValidation.Reason
import com.kyusui.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validasi data pengantaran sebelum `POST /customer/orders`.
 *
 * Test ini menjaga agar data yang jelas tidak valid tidak pernah dikirim ke
 * backend: alamat kosong/placeholder, koordinat hilang atau non-finite, dan
 * koordinat di luar rentang geografis.
 */
class DeliveryLocationValidationTest {

    private val validPoint = GeoPoint(-0.9471, 100.4172)
    private val validAddress = "Jl. Merdeka No. 10"

    @Test
    fun `valid address and coordinates pass`() {
        assertEquals(
            DeliveryLocationValidation.Valid,
            DeliveryLocationValidator.validate(validAddress, validPoint)
        )
    }

    @Test
    fun `blank address is rejected`() {
        assertReason(Reason.ADDRESS_EMPTY, DeliveryLocationValidator.validate("   ", validPoint))
    }

    @Test
    fun `placeholder address is rejected`() {
        listOf("-", "--", "n/a", "null", "tidak diketahui", "alamat kosong").forEach { address ->
            assertReason(
                Reason.ADDRESS_PLACEHOLDER,
                DeliveryLocationValidator.validate(address, validPoint)
            )
        }
    }

    @Test
    fun `address without a single letter is rejected`() {
        assertReason(
            Reason.ADDRESS_PLACEHOLDER,
            DeliveryLocationValidator.validate("12345", validPoint)
        )
    }

    @Test
    fun `address keeps numbers and letters`() {
        // Alamat nyata sering memuat nomor; penolakan hanya berlaku untuk
        // string yang sama sekali tidak memuat huruf.
        assertEquals(
            DeliveryLocationValidation.Valid,
            DeliveryLocationValidator.validate("Jl. Merdeka No. 10", validPoint)
        )
    }

    @Test
    fun `missing coordinate is rejected`() {
        assertReason(
            Reason.COORDINATE_MISSING,
            DeliveryLocationValidator.validate(validAddress, null)
        )
    }

    @Test
    fun `non finite coordinates are rejected`() {
        assertReason(
            Reason.COORDINATE_NOT_FINITE,
            DeliveryLocationValidator.validate(validAddress, GeoPoint(Double.NaN, 100.0))
        )
        assertReason(
            Reason.COORDINATE_NOT_FINITE,
            DeliveryLocationValidator.validate(
                validAddress,
                GeoPoint(Double.POSITIVE_INFINITY, 100.0)
            )
        )
        assertReason(
            Reason.COORDINATE_NOT_FINITE,
            DeliveryLocationValidator.validate(
                validAddress,
                GeoPoint(-0.9471, Double.NEGATIVE_INFINITY)
            )
        )
    }

    @Test
    fun `latitude outside the range is rejected`() {
        assertReason(
            Reason.LATITUDE_OUT_OF_RANGE,
            DeliveryLocationValidator.validate(validAddress, GeoPoint(90.0001, 100.0))
        )
        assertReason(
            Reason.LATITUDE_OUT_OF_RANGE,
            DeliveryLocationValidator.validate(validAddress, GeoPoint(-90.0001, 100.0))
        )
    }

    @Test
    fun `longitude outside the range is rejected`() {
        assertReason(
            Reason.LONGITUDE_OUT_OF_RANGE,
            DeliveryLocationValidator.validate(validAddress, GeoPoint(0.5, 180.0001))
        )
        assertReason(
            Reason.LONGITUDE_OUT_OF_RANGE,
            DeliveryLocationValidator.validate(validAddress, GeoPoint(0.5, -180.0001))
        )
    }

    @Test
    fun `latitude boundaries are accepted`() {
        assertEquals(
            DeliveryLocationValidation.Valid,
            DeliveryLocationValidator.validate(validAddress, GeoPoint(90.0, 100.0))
        )
        assertEquals(
            DeliveryLocationValidation.Valid,
            DeliveryLocationValidator.validate(validAddress, GeoPoint(-90.0, 100.0))
        )
    }

    @Test
    fun `longitude boundaries are accepted`() {
        assertEquals(
            DeliveryLocationValidation.Valid,
            DeliveryLocationValidator.validate(validAddress, GeoPoint(0.5, 180.0))
        )
        assertEquals(
            DeliveryLocationValidation.Valid,
            DeliveryLocationValidator.validate(validAddress, GeoPoint(0.5, -180.0))
        )
    }

    @Test
    fun `zero zero is treated as an unset placeholder`() {
        assertReason(
            Reason.COORDINATE_PLACEHOLDER,
            DeliveryLocationValidator.validate(validAddress, GeoPoint(0.0, 0.0))
        )
    }

    @Test
    fun `missing coordinates are reported before range checks`() {
        // Tanpa titik sama sekali, alasan yang benar adalah koordinat hilang,
        // bukan rentang.
        assertReason(Reason.COORDINATE_MISSING, DeliveryLocationValidator.validate(validAddress, null))
    }

    private fun assertReason(
        expected: Reason,
        actual: DeliveryLocationValidation
    ) {
        assertTrue(
            "Diharapkan Invalid($expected) tetapi hasilnya $actual",
            actual is DeliveryLocationValidation.Invalid && actual.reason == expected
        )
    }
}
