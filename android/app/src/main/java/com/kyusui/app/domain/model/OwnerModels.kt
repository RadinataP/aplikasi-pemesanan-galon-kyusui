package com.kyusui.app.domain.model

/**
 * Domain model untuk Owner sesuai `06_Berkah Water_API_SPECIFICATION_REBUILT.md`
 * section 10, 12, dan 13.
 *
 * Dua aturan berlaku untuk seluruh model di file ini:
 *
 * 1. Tidak ada operasi yang menulis `paymentStatus = PAID` atau
 *    `orderStatus = <next>` secara lokal. Semua status hanya dibaca dari
 *    backend. Hasil verifikasi dibaca apa adanya dari response server.
 * 2. Nominal uang selalu decimal string dari backend. Android tidak menghitung
 *    ulang subtotal, delivery fee, maupun total.
 */

/**
 * Order untuk Owner.
 *
 * Berbeda dengan [Order] milik customer, resource ini juga membawa customer
 * context, delivery location, dan assignment. Seluruhnya opsional karena
 * specification section 10.2 menyatakan response "dapat menyertakan" field-field
 * tersebut, jadi backend yang menentukan apa yang benar-benar dikirim.
 */
data class OwnerOrder(
    val id: String,
    val orderNumber: String,
    val customerId: String? = null,
    val customer: OwnerCustomer? = null,
    val items: List<OrderItem> = emptyList(),
    val subtotalAmount: String,
    val deliveryFee: String,
    val totalAmount: String,
    val orderStatus: OrderStatus?,
    val payment: OwnerOrderPayment? = null,
    val deliveryAddress: String? = null,
    val deliveryLocation: GeoPoint? = null,
    val assignment: OrderAssignment? = null
) {
    /**
     * Status berikutnya yang boleh dicoba oleh Owner.
     *
     * Daftar ini hanya mengatur tombol apa yang ditampilkan. Ini bukan aturan
     * authorization: backend tetap memvalidasi transition dan menolak yang tidak
     * valid dengan `409`/`422`. Daftar ini mencegah Owner mengirim aksi yang
     * jelas tidak mungkin sebelum round-trip.
     *
     * `MENUNGGU_PEMBAYARAN` sengaja tidak punya aksi lanjutan karena payment
     * belum selesai, jadi order belum boleh diproses.
     */
    val availableStatusActions: List<OrderStatus>
        get() = when (orderStatus) {
            // Pembayaran sudah diterima backend; Owner mulai memproses.
            OrderStatus.MENUNGGU_DIPROSES -> listOf(OrderStatus.DIPROSES)
            OrderStatus.DIPROSES -> listOf(OrderStatus.DITUGASKAN)
            OrderStatus.DITUGASKAN -> listOf(OrderStatus.DALAM_PENGANTARAN)
            OrderStatus.DALAM_PENGANTARAN -> listOf(OrderStatus.SELESAI)
            OrderStatus.MENUNGGU_PEMBAYARAN,
            OrderStatus.SELESAI,
            OrderStatus.DIBATALKAN -> emptyList()
            // Status tidak dikenal tidak punya aksi yang bisa dicocokkan dengan
            // aman, jadi Android tidak menebak apa pun.
            null -> emptyList()
        }

    /**
     * Courier assignment relevan setelah order masuk tahap proses dan belum
     * punya assignment aktif. Backend tetap menolak assignment yang tidak valid.
     */
    val canRequestCourierAssignment: Boolean
        get() = orderStatus == OrderStatus.DIPROSES && assignment == null

    val totalQuantity: Int get() = items.sumOf { it.quantity }
}

/**
 * Customer context pada response Owner.
 *
 * Hanya `id` dan `name` yang dicontohkan di specification section 12.1, jadi
 * hanya kedua field itu yang dimodelkan. `phone` sengaja tidak ada karena
 * specification tidak menyebutkannya untuk endpoint Owner.
 */
data class OwnerCustomer(
    val id: String,
    val name: String
)

/**
 * Ringkasan payment yang ikut di Order Resource (section 7.4).
 *
 * Field `id`, `verified_by`, dan `verified_at` tidak ada pada embedded payment di
 * section 7.4, sehingga `id` di sini opsional.
 */
data class OwnerOrderPayment(
    val id: String? = null,
    val paymentMethod: PaymentMethod?,
    val paymentStatus: PaymentStatus?,
    val amount: String
)

/**
 * Courier assignment pada Order Resource (section 7.4, bernilai `null` bila
 * belum ada).
 *
 * API specification tidak mendefinisikan bentuk objek `assignment`, hanya
 * menyebutkan field tersebut ada di Order Resource. Bentuk di sini mencerminkan
 * kolom tabel `courier_assignments` pada
 * `05_Berkah Water_DATABASE_SCHEMA_REBUILT.md` section 16, satu-satunya sumber
 * contract yang menyebut isi assignment. Kolom yang tidak disebut backend
 * diabaikan lewat `ignoreUnknownKeys`, sehingga model ini tetap aman terhadap
 * perubahan bentuk response.
 *
 * Tidak ada `courier_name` atau `courier_phone` di sini: nama dan telepon kurir
 * tidak ada pada contract mana pun, jadi tidak boleh dikarang di Android.
 */
data class OrderAssignment(
    val id: String,
    val courierId: String,
    val status: AssignmentStatus?,
    val assignedAt: String? = null
)

/**
 * Courier assignment status section 6.6.
 *
 * Hanya `ASSIGNED`, `ACTIVE`, dan `COMPLETED`. Tidak ada `REJECTED` karena
 * specification menyatakan courier rejection belum menjadi requirement.
 */
enum class AssignmentStatus(val apiValue: String, val displayName: String) {
    ASSIGNED("ASSIGNED", "Ditugaskan"),
    ACTIVE("ACTIVE", "Dalam Pengiriman"),
    COMPLETED("COMPLETED", "Selesai");

    companion object {
        fun fromApiValue(value: String?): AssignmentStatus? =
            value?.trim()?.let { raw -> entries.firstOrNull { it.apiValue == raw } }
    }
}

/**
 * Baris item pada pending QRIS queue (specification section 12.1).
 *
 * `paymentId` adalah id payment, bukan id order. Keduanya disimpan terpisah
 * karena endpoint verifikasi bekerja pada `order`, sedangkan daftar ini dibaca
 * dari payment.
 */
data class OwnerPendingPayment(
    val paymentId: String,
    val orderId: String,
    val orderNumber: String,
    val customer: OwnerCustomer?,
    val paymentMethod: PaymentMethod?,
    val paymentStatus: PaymentStatus?,
    val amount: String,
    val hasProof: Boolean,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    /**
     * Backend sudah memfilter `QRIS` + `WAITING_VERIFICATION`, jadi Android
     * menampilkan apa yang benar-benar sampai. Status yang tidak dikenal tetap
     * ditampilkan apa adanya lewat `paymentStatus = null`.
     */
    val isAwaitingVerification: Boolean
        get() = paymentStatus == PaymentStatus.WAITING_VERIFICATION
}

/**
 * Bukti pembayaran untuk satu order (specification section 12.2).
 *
 * Endpoint ini mengembalikan keberadaan proof dan, kalau backend memilih
 * mengizinkan, URL terotorisasi. Android hanya menampilkan apa yang diberikan
 * backend dan tidak pernah menganggap proof valid hanya karena gambarnya berhasil
 * dimuat.
 */
data class OwnerPaymentProof(
    val orderId: String,
    val paymentId: String? = null,
    val paymentMethod: PaymentMethod?,
    val paymentStatus: PaymentStatus?,
    val hasProof: Boolean,
    /**
     * URL terotorisasi dari backend, atau null bila backend hanya melaporkan
     * keberadaan tanpa URL.
     *
     * Nilai ini tetap milik backend. Android tidak menyusun URL sendiri dan
     * tidak memakai `QrisImageUrlResolver` di sini: kelas itu khusus untuk aset
     * konfigurasi toko, bukan bukti pembayaran.
     */
    val proofUrl: String? = null
) {
    val canDisplayImage: Boolean get() = hasProof && !proofUrl.isNullOrBlank()
}

/**
 * Konfigurasi QRIS aktif milik Owner (specification section 13.1 dan 13.2).
 *
 * Active QRIS adalah singleton configuration pada `business_settings`, bukan
 * QRIS dinamis per transaksi. Android tidak menyimpan QRIS sebagai source of
 * truth dan tidak pernah meng-hardcode QRIS aktif.
 */
data class OwnerQrisConfig(
    val qrisImage: String?,
    val updatedAt: String? = null,
    val updatedBy: String? = null
) {
    /**
     * `404` pada `GET /owner/payment/qris` berarti QRIS belum pernah
     * dikonfigurasi. Kondisi itu harus dibedakan dari QRIS yang sudah ada,
     * karena hanya kondisi kedua yang gagal saat Owner mengganti QRIS.
     */
    val isDisplayable: Boolean get() = !qrisImage.isNullOrBlank()
}

/**
 * Aksi verifikasi QRIS yang dikirim ke
 * `POST /owner/orders/{order}/payment-verification` (section 12.3).
 *
 * Android hanya mengirim aksi. Backend yang menentukan apakah aksi valid, dan
 * karena itu enum ini tidak memetakan status apa pun.
 */
enum class OwnerVerificationAction(val apiValue: String) {
    APPROVE("APPROVE"),
    REJECT("REJECT");

    companion object {
        fun fromApiValue(value: String?): OwnerVerificationAction? =
            value?.trim()?.let { raw -> entries.firstOrNull { it.apiValue == raw } }
    }
}

/**
 * Hasil aksi verifikasi yang dikembalikan backend.
 *
 * `APPROVE` diikuti `PAID` dan `REJECT` diikuti `PENDING` menurut specification
 * section 12.3, tetapi Android tidak memaksa nilai itu. Status di dalam [payment]
 * dibaca apa adanya dari response; bila backend mengembalikan nilai lain, itu
 * keputusan backend dan ditampilkan apa adanya.
 */
data class OwnerVerificationResult(
    val payment: Payment,
    val action: OwnerVerificationAction
)

/**
 * Ringkasan kurir pada `GET /owner/couriers`.
 *
 * Endpoint ini "preserved": tidak ada di katalog spec 06 section 30, tetapi
 * benar-benar ada di `backend/routes/api.php` dan menjadi sumber `courier_id`
 * untuk penugasan section 10.4. Backend mengembalikan model `Courier` apa adanya
 * dengan relasi `user:id,name`, jadi hanya field yang memang dikirim yang
 * dimodelkan. Backend hanya mengirim kurir yang tidak sedang mengantarkan order
 * lain, sehingga Android tidak menghitung ulang ketersediaan.
 */
data class CourierSummary(
    val id: String,
    val name: String = "",
    val phone: String? = null,
    val vehicle: String? = null
) {
    /**
     * Label yang selalu terisi. Jatuh kembali ke id bila backend tidak mengirim
     * nama, supaya Owner tetap bisa membedakan kurir tanpa data karangan.
     */
    val displayLabel: String
        get() = name.takeIf { it.isNotBlank() } ?: "Kurir #$id"
}

/** Data dashboard bisnis yang sudah diagregasi backend. */
data class OwnerDashboardData(
    val totalOrders: Int,
    val waitingProcess: Int,
    val completedOrders: Int,
    val waitingQris: Int,
    val activeDeliveries: Int,
    val availableProducts: Int,
    val todayRevenue: String,
    val monthRevenue: String,
    val monthExpense: String,
    val monthNet: String,
    val averageOrderValue: String,
    val completionRate: Double,
    val revenueChart: List<OwnerRevenuePoint>,
    val recentOrders: List<OwnerDashboardOrder> = emptyList(),
    val waitingOrders: List<OwnerDashboardOrder> = emptyList(),
    val pendingQrisPayments: List<OwnerDashboardPayment> = emptyList(),
    val activeDeliveriesList: List<OwnerDashboardAssignment> = emptyList(),
    val activeCouriers: List<CourierSummary> = emptyList()
)

data class OwnerRevenuePoint(
    val month: String,
    val label: String,
    val income: Double,
    val expense: Double
)


/** Ringkasan order dashboard sesuai resource backend, bukan pengganti OwnerOrder detail. */
data class OwnerDashboardOrder(
    val id: String,
    val status: String?,
    val totalAmount: String?,
    val deliveryAddress: String?,
    val paymentMethod: String?,
    val paymentStatus: String?,
    val createdAt: String?
)

data class OwnerDashboardPayment(
    val id: String,
    val paymentMethod: String?,
    val paymentStatus: String?,
    val amount: String?,
    val orderId: String?,
    val customerName: String?,
    val createdAt: String?
)

data class OwnerDashboardAssignment(
    val id: String,
    val status: String?,
    val courierName: String?,
    val orderId: String?,
    val customerName: String?,
    val deliveryAddress: String?,
    val assignedAt: String?
)


/** Keputusan verifikasi bukti QRIS yang benar-benar dicatat backend. */
data class OwnerPaymentProofHistory(
    val id: String,
    val decision: String,
    val note: String?,
    val actor: String?,
    val decidedAt: String?,
    val proofAvailable: Boolean
)
