package com.kyusui.app.ui.maps

import com.kyusui.app.domain.model.GeoPoint

/**
 * Aturan pergerakan marker kurir.
 *
 * Dipisahkan dari composable peta supaya dapat diuji sebagai fungsi murni.
 * Animasi marker tidak boleh dipicu ulang oleh data yang sama, tidak boleh
 * bergerak ke koordinat yang tidak valid, dan tidak boleh bergerak ketika
 * pembaruan gagal — pada kasus gagal, posisi di state tidak berubah sehingga
 * `LaunchedEffect` tidak dijalankan ulang.
 */
internal fun isValidGeoPoint(point: GeoPoint?): Boolean {
    if (point == null) {
        return false
    }
    val latitude = point.latitude
    val longitude = point.longitude
    return latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in MIN_LATITUDE..MAX_LATITUDE &&
        longitude in MIN_LONGITUDE..MAX_LONGITUDE
}

/**
 * True bila marker perlu dianimasikan menuju [target].
 *
 * Koordinat identik tidak memicu animasi ulang, dan koordinat tidak valid
 * ditolak supaya marker tidak melonjak ke lokasi yang salah.
 */
internal fun shouldAnimateMarker(from: GeoPoint?, to: GeoPoint?): Boolean =
    isValidGeoPoint(from) && isValidGeoPoint(to) && from != to

/**
 * Titik antara [from] dan [to] pada [fraction] (dibatasi 0..1).
 *
 * Langsung menginterpolasi antar dua koordinat; [fraction] di luar rentang
 * dibatasi supaya marker tidak pernah melewati titik tujuan.
 */
internal fun interpolateGeoPoint(from: GeoPoint, to: GeoPoint, fraction: Float): GeoPoint {
    val progress = fraction.coerceIn(0f, 1f).toDouble()
    return GeoPoint(
        latitude = from.latitude + (to.latitude - from.latitude) * progress,
        longitude = from.longitude + (to.longitude - from.longitude) * progress
    )
}

private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
