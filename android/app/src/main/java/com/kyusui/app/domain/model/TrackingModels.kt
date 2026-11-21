package com.kyusui.app.domain.model

/**
 * Satu titik posisi kurir yang dibaca dari `GET orders/{order}/tracking`.
 *
 * Datanya berasal dari tabel `courier_locations` yang diserialikan sebagai model
 * Eloquent penuh, jadi koordinatnya sudah di-cast `decimal` dan `accuracy`
 * bisa saja null bila tidak ada.
 */
data class CourierLocationPoint(
    val point: GeoPoint,
    val accuracyMeters: Double?,
    val recordedAt: String?
)

/**
 * Hasil `GET orders/{order}/tracking`.
 *
 * ## Bentuk respons yang tidak biasa
 *
 * Backend tidak mengembalikan objek tracking tersendiri. `OrderController::tracking`
 * mengembalikan model `CourierAssignment` yang masih aktif, lengkap dengan
 * relasi `locations`, dan membungkusnya di `data`. Jadi isi `data` di sini adalah
 * assignment, bukan Order, dan tidak ada blok status order di dalamnya.
 * Status pengiriman canonical tetap dibaca dari `GET orders/{order}`.
 *
 * ## Makna `data: null`
 *
 * Query backend memfilter `where('status', ACTIVE)`. Begitu assignment selesai
 * atau dibatalkan, tidak ada assignment aktif lagi dan backend mengirim
 * `data: null`. Itu kondisi normal "tracking sudah berhenti", bukan kegagalan.
 * Repository memetakannya ke `null` pada [OrderTrackingResult], bukan ke error.
 *
 * ## Urutan lokasi
 *
 * Urutan [locations] dipertahankan apa adanya dari backend. Backend memuat
 * relasinya dengan `latest('recorded_at')->limit(10)`, jadi indeks 0 adalah
 * titik terbaru dan urutannya menurun. Android tidak mengurutkan ulang
 * sendiri karena itu akan mengarang urutan yang tidak dinyatakan server.
 */
data class OrderTracking(
    val assignmentId: String,
    val orderId: String?,
    val assignmentStatus: AssignmentStatus?,
    val startedAt: String?,
    val completedAt: String?,
    val locations: List<CourierLocationPoint>
) {
    /** Titik terbaru, yaitu `locations.firstOrNull()`. Null bila belum ada posisi. */
    val latestLocation: CourierLocationPoint?
        get() = locations.firstOrNull()

    /** True bila backend sudah punya minimal satu titik posisi. */
    val hasPositions: Boolean
        get() = locations.isNotEmpty()
}

/**
 * Hasil pemanggilan tracking untuk satu order.
 *
 * Dibuat sebagai wrapper karena `data: null` adalah jawaban yang sah dari
 * backend dan tidak boleh tercampur dengan kegagalan jaringan atau penolakan
 * akses. Pemanggil bisa membedakan "tidak ada pengiriman aktif" dari
 * "gagal memuat" tanpa membaca pesan error.
 */
sealed interface OrderTrackingResult {
    /** Ada assignment aktif; [tracking] berisi posisi yang sudah ada. */
    data class Active(val tracking: OrderTracking) : OrderTrackingResult

    /**
     * Backend menjawab dengan `data: null`, artinya tidak ada assignment
     * aktif untuk order ini sehingga tracking sudah tidak berjalan.
     */
    data object NoActiveDelivery : OrderTrackingResult
}