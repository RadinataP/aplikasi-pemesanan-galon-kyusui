package com.kyusui.app.core

import com.kyusui.app.domain.model.GeoPoint

/**
 * Hasil validasi data pengantaran sebelum `POST /customer/orders`.
 *
 * Disimpan sebagai tipe hasil, bukan boolean, supaya UI dan ViewModel bisa
 * membedakan alasan penolakan (alamat kosong, koordinat di luar rentang, dsb)
 * dan menampilkan pesan yang jujur. Backend tetap authority: validasi ini hanya
 * menyaring data yang jelas tidak valid agar tidak memboroskan satu perjalanan.
 */
sealed interface DeliveryLocationValidation {

    data object Valid : DeliveryLocationValidation

    data class Invalid(
        val reason: Reason,
        val message: String
    ) : DeliveryLocationValidation

    enum class Reason {
        ADDRESS_EMPTY,
        ADDRESS_PLACEHOLDER,
        COORDINATE_MISSING,
        COORDINATE_NOT_FINITE,
        COORDINATE_PLACEHOLDER,
        LATITUDE_OUT_OF_RANGE,
        LONGITUDE_OUT_OF_RANGE
    }
}

/**
 * Aturan validasi lokasi pengantaran customer.
 *
 * ## Rentang koordinat
 *
 * Latitude wajib berada pada `-90.0..90.0` dan longitude pada
 * `-180.0..180.0`. Nilai `NaN`/`Infinity` ditolak lebih dulu karena
 * perbandingan rentang terhadap nilai non-finite tidak bermakna.
 *
 * ## Placeholder
 *
 * Alamat default seperti `"-"`, `"n/a"`, atau alamat yang tidak memuat satu huruf
 * pun ditolak karena bukan tujuan pengantaran yang bisa dipakai kurir. Titik
 * `0,0` juga ditolak: nilainya adalah koordinat "belum diisi" pada perangkat,
 * bukan titik di area layanan.
 */
object DeliveryLocationValidator {

    const val MIN_LATITUDE = -90.0
    const val MAX_LATITUDE = 90.0
    const val MIN_LONGITUDE = -180.0
    const val MAX_LONGITUDE = 180.0

    private val PLACEHOLDER_ADDRESSES = setOf(
        "-",
        "--",
        "na",
        "n/a",
        "null",
        "nil",
        "none",
        "unknown",
        "tidak diketahui",
        "tidak ada",
        "alamat",
        "alamat kosong",
        "default",
        "placeholder"
    )

    fun validate(address: String, point: GeoPoint?): DeliveryLocationValidation {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) {
            return invalid(
                DeliveryLocationValidation.Reason.ADDRESS_EMPTY,
                "Alamat pengantaran belum diisi."
            )
        }
        if (isPlaceholderAddress(trimmed)) {
            return invalid(
                DeliveryLocationValidation.Reason.ADDRESS_PLACEHOLDER,
                "Alamat pengantaran bukan alamat yang valid."
            )
        }

        val location = point ?: return invalid(
            DeliveryLocationValidation.Reason.COORDINATE_MISSING,
            "Lokasi pengantaran belum dipilih."
        )
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) {
            return invalid(
                DeliveryLocationValidation.Reason.COORDINATE_NOT_FINITE,
                "Koordinat lokasi tidak valid."
            )
        }
        if (location.latitude < MIN_LATITUDE || location.latitude > MAX_LATITUDE) {
            return invalid(
                DeliveryLocationValidation.Reason.LATITUDE_OUT_OF_RANGE,
                "Latitude harus berada di antara -90 dan 90."
            )
        }
        if (location.longitude < MIN_LONGITUDE || location.longitude > MAX_LONGITUDE) {
            return invalid(
                DeliveryLocationValidation.Reason.LONGITUDE_OUT_OF_RANGE,
                "Longitude harus berada di antara -180 dan 180."
            )
        }
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            return invalid(
                DeliveryLocationValidation.Reason.COORDINATE_PLACEHOLDER,
                "Lokasi 0,0 bukan titik pengantaran yang valid."
            )
        }

        return DeliveryLocationValidation.Valid
    }

    private fun isPlaceholderAddress(address: String): Boolean {
        val normalized = address.lowercase()
        if (normalized in PLACEHOLDER_ADDRESSES) return true
        // Alamat harus memuat minimal satu huruf. "-", "123", atau "..." bukan
        // alamat pengantaran.
        return normalized.none { it.isLetter() }
    }

    private fun invalid(
        reason: DeliveryLocationValidation.Reason,
        message: String
    ): DeliveryLocationValidation = DeliveryLocationValidation.Invalid(reason, message)
}
