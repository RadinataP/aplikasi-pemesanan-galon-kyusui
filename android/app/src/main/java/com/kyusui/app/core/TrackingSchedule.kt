package com.kyusui.app.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sumber nilai interval tracking yang diinjeksi ke ViewModel.
 *
 * Ada supaya interval menjadi dependency, bukan konstanta yang dipanggil
 * langsung dari dalam ViewModel. Dengan begitu test bisa mengoper nilai kecil
 * sendiri dan tidak pernah bergantung pada angka produksi, dan angka produksi
 * tetap hanya berada di [TrackingIntervals].
 *
 * Nilai yang dikembalikan implementasi ini masih bersifat sementara karena
 * `UD-TRACK-01` dan `UD-TRACK-02` belum dikunci. Lihat
 * [TrackingIntervals] untuk alasannya.
 */
interface TrackingSchedule {
    /** Jarak minimum antar-acquisition lokasi kurir, dalam milidetik. */
    val courierSubmissionIntervalMillis: Long

    /** Jarak antar-pembacaan tracking customer, dalam milidetik. */
    val customerReadIntervalMillis: Long
}

/**
 * Implementasi yang memakai [TrackingIntervals].
 *
 * Membaca properties, bukan menyalin nilainya ke constructor, supaya
 * perubahan pada batas konfigurasi langsung terpakai tanpa perlu membangun
 * ulang objek ini.
 */
@Singleton
class TrackingIntervalsSchedule @Inject constructor() : TrackingSchedule {

    override val courierSubmissionIntervalMillis: Long
        get() = TrackingIntervals.COURIER_SUBMISSION_INTERVAL_MS

    override val customerReadIntervalMillis: Long
        get() = TrackingIntervals.CUSTOMER_READ_INTERVAL_MS
}