package com.kyusui.app.domain.model

/**
 * Domain model untuk role COURIER.
 *
 * Kontrak acuannya adalah implementasi backend pada
 * `backend/app/Http/Controllers/Api/CourierController.php` dan
 * `backend/app/Http/Controllers/Api/DashboardController.php`, bukan
 * specification 06 section 15 dan 16. Alasan dan tabel perbedaannya dijelaskan
 * di `data/api/CourierApi.kt`.
 *
 * Tiga aturan berlaku untuk seluruh model di file ini:
 *
 * 1. Tidak ada operasi Android yang menulis status. `AssignmentStatus`,
 *    `OrderStatus`, dan `PaymentStatus` pada hasil aksi selalu disalin dari
 *    response backend. `PAID` untuk CASH hanya boleh datang dari
 *    `POST courier/assignments/{assignment}/cash/confirm`.
 * 2. Nominal uang selalu decimal string milik backend. Android tidak menghitung
 *    ulang subtotal maupun total.
 * 3. Properti turunan seperti [CourierDelivery.canStartDelivery] hanya mengatur
 *    tombol apa yang ditampilkan. Mereka BUKAN pemeriksaan otorisasi. Backend
 *    tetap memeriksa ownership assignment, status delivery, metode pembayaran,
 *    dan status pembayaran sebelum menerima aksi apa pun.
 */

/**
 * Satu assignment pengantaran milik kurir yang sedang login.
 *
 * Id pada resource ini adalah id `courier_assignments`, bukan id order.
 * [order] berisi order yang diantar. Keduanya sengaja dipisah agar tidak ada
 * kode yang keliru mengirim id order ke endpoint assignment.
 */
data class CourierDelivery(
    val id: String,
    val status: AssignmentStatus?,
    val assignedAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val order: CourierDeliveryOrder? = null,
    val latestLocation: CourierDeviceLocation? = null
) {
    /**
     * Aksi mulai pengantaran hanya ditampilkan saat status assignment masih
     * `ASSIGNED`.
     *
     * Backend juga mensyaratkan order berstatus `DITUGASKAN` dan kurir tidak
     * sedang punya assignment `ACTIVE` lain. Android tidak dapat memeriksa
     * kedua syarat itu dari resource ini, jadi dua syarat yang tidak bisa
     * diperiksa itu tetap diserahkan sepenuhnya ke backend.
     */
    val canStartDelivery: Boolean
        get() = status == AssignmentStatus.ASSIGNED

    /**
     * Lokasi hanya bisa dikirim saat delivery aktif. Backend menolak dengan
     * `409` bila assignment belum `ACTIVE` atau order belum
     * `DALAM_PENGANTARAN`.
     */
    val canPostLocation: Boolean
        get() = status == AssignmentStatus.ACTIVE

    /**
     * Tombol "Uang Diterima" hanya ditampilkan untuk pembayaran `CASH` yang
     * masih `PENDING`.
     *
     * `canConfirmCash` TIDAK berarti pembayaran dianggap sah. Itu keputusan
     * backend: kurir harus pemilik assignment, delivery harus aktif, dan
     * payment harus CASH serta belum lunas. Backend juga menutup aksi ini dengan
     * row lock sehingga dua konfirmasi bersamaan tidak bisa do dua kali.
     *
     * Syarat backend yang TIDAK bisa diperiksa dari resource ini:
     *
     * - `CourierController::cash` juga mensyaratkan order berstatus
     *   `DALAM_PENGANTARAN`, dan resource ini bisa saja menampilkan payment
     *   `PENDING` untuk order yang belum masuk pengantaran. Syarat itu tetap
     *   itu tetap milik backend; Android tidak mengira bisa menggantinya.
     * - Keberadaan payment. Backend membalas `409` kalau payment tidak ada sama
     *   sekali, dan Assignment Resource boleh mengirim `payment: null`.
     */
    val canConfirmCash: Boolean
        get() = status == AssignmentStatus.ACTIVE &&
            order?.payment?.paymentMethod == PaymentMethod.CASH &&
            order?.payment?.paymentStatus == PaymentStatus.PENDING

    /**
     * Selesaikan delivery hanya ditampilkan saat assignment aktif dan payment
     * sudah `PAID`.
     *
     * Untuk QRIS, `PAID` berarti Owner sudah memverifikasi. Untuk CASH, `PAID`
     * berarti kurir sendiri sudah mengonfirmasi uang diterima. Android tidak
     * pernah menulis `PAID` untuk jalan pintas ini.
     */
    val canCompleteDelivery: Boolean
        get() = status == AssignmentStatus.ACTIVE &&
            order?.payment?.paymentStatus == PaymentStatus.PAID

    /** Setelah selesai, tidak ada aksi kurir yang boleh dikirim lagi. */
    val isFinished: Boolean
        get() = status == AssignmentStatus.COMPLETED

    val orderId: String? get() = order?.id
}

/**
 * Order yang diantar, sesuai bentuk `order` pada Assignment Resource.
 *
 * Bentuk ini berbeda dari Order Resource pada specification: backend memakai
 * `subtotal` dan `total`, serta `payment.method` dan `payment.status`.
 */
data class CourierDeliveryOrder(
    val id: String,
    val status: OrderStatus?,
    val subtotalAmount: String,
    val totalAmount: String,
    val deliveryAddress: String? = null,
    val deliveryLocation: DeliveryLocation? = null,
    /**
     * Backend hanya mengirim `name` untuk customer pada resource ini. Tidak ada
     * `phone`, jadi nomor telepon customer tidak pernah ditampilkan ke kurir.
     */
    val customerName: String? = null,
    val items: List<CourierDeliveryItem> = emptyList(),
    val payment: CourierDeliveryPayment? = null
) {
    val totalQuantity: Int get() = items.sumOf { it.quantity }
}

data class CourierDeliveryItem(
    val productName: String,
    val quantity: Int,
    val unitPrice: String,
    val lineTotal: String
)

data class CourierDeliveryPayment(
    val paymentMethod: PaymentMethod?,
    val paymentStatus: PaymentStatus?
)

/**
 * Lokasi kurir terakhir yang tercatat server.
 *
 * Ini reading, bukan writing: Android menampilkan lokasi yang benar-benar
 * disimpan backend. Nilai `null` berarti kurir belum pernah mengirim lokasi
 * untuk assignment ini.
 */
data class CourierDeviceLocation(
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Double?,
    val recordedAt: String? = null
) {
    /** Koordinat hanya ditampilkan bila lat dan lng keduanya ada. */
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null
}

/**
 * Pembacaan lokasi dari perangkat, sebelum dikirim ke backend.
 *
 * Dipakai sebagai argumen repository. [point] memakai [GeoPoint] yang sama
 * dengan form order customer agar tidak ada dua representasi koordinat yang
 * berbeda di aplikasi.
 */
data class CourierLocationFix(
    val point: GeoPoint,
    val accuracyMeters: Double
)

/**
 * Hasil konfirmasi uang diterima.
 *
 * `paymentStatus` apa adanya dari backend. Nilai `PAID` pada objek ini berarti
 * server yang mengatakannya, bukan Android.
 */
data class CourierCashReceipt(
    val paymentId: String,
    val paymentMethod: PaymentMethod?,
    val paymentStatus: PaymentStatus?,
    val verifiedAt: String? = null
)

/**
 * Ringkasan beranda kurir dari `GET dashboard/courier`.
 *
 * [activeDelivery] berisi Order Resource, bukan Assignment Resource, sehingga
 * nama customer tidak ada di dalamnya. Android karena itu tidak menampilkan
 * nama customer pada beranda dan mengambil nama customer dari
 * `GET /courier/assignments` saat kurir membuka daftar assignment.
 */
data class CourierDashboard(
    val activeDelivery: CourierDashboardOrder? = null,
    val todayDeliveries: List<CourierDashboardOrder> = emptyList(),
    val summary: CourierDashboardSummary,
    val unreadNotifications: Int = 0
)

data class CourierDashboardSummary(
    val todayDeliveries: Int = 0,
    val monthDeliveries: Int = 0,
    val completedDeliveries: Int = 0,
    /**
     * Persentase 0..100 yang dihitung backend. Android tidak menghitung ulang,
     * dan tidak mengarang nilai bila backend tidak mengirim `summary`.
     */
    val completionRate: Double = 0.0
)

/**
 * Baris order pada dashboard kurir.
 *
 * Id di sini adalah id order, BUKAN id assignment. Backend tidak mengirim
 * assignment pada `today_assignments` selain referensi `{id, status}`, jadi
 * kurir membuka daftar assignment untuk mengambil id assignment sebelum membuka
 * detail delivery.
 */
data class CourierDashboardOrder(
    val orderId: String,
    val status: OrderStatus?,
    val subtotalAmount: String,
    val totalAmount: String,
    val deliveryAddress: String? = null,
    val payment: CourierDeliveryPayment? = null,
    val items: List<CourierDeliveryItem> = emptyList(),
    val assignmentId: String? = null,
    val assignmentStatus: AssignmentStatus? = null,
    val createdAt: String? = null
) {
    val totalQuantity: Int get() = items.sumOf { it.quantity }

    /**
     * Dashboard hanya menampilkan order yang punya assignment, karena order
     * tanpa assignment bukan tanggung jawab kurir.
     */
    val isDeliverable: Boolean get() = assignmentId != null
}

/**
 * Satu sesi bertugas (on-duty) kurir.
 *
 * `isActive` dibaca apa adanya dari backend. Android tidak menyimpulkan sendiri
 * apakah sesi masih berjalan; satu-satunya sumber benar adalah server.
 */
data class CourierDutySession(
    val id: String,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val isActive: Boolean = false
)

/**
 * Status bertugas dari `GET courier/duty`.
 *
 * [latestLocation] adalah lokasi yang benar-benar tersimpan server untuk sesi
 * yang sedang berjalan, bukan pembacaan perangkat.
 */
data class CourierDutyState(
    val isActive: Boolean = false,
    val session: CourierDutySession? = null,
    val latestLocation: CourierDeviceLocation? = null
)
