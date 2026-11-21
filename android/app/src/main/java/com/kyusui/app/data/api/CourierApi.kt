package com.kyusui.app.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Courier API.
 *
 * SELURUH path di file ini disalin apa adanya dari `backend/routes/api.php` dan
 * `backend/app/Http/Controllers/Api/CourierController.php`, bukan dari
 * `docs/06_Berkah Water_API_SPECIFICATION_REBUILT.md`.
 *
 * Alasan deviation dari specification perlu dicatat, karena keduanya memang
 * berbeda dan hanya satu yang bisa dipakai:
 *
 * | Yang dipakai di sini (backend)          | Yang tertulis di spec 06        |
 * |-----------------------------------------|----------------------------------|
 * | `GET  courier/assignments`              | `GET /courier/orders`            |
 * | `GET  courier/assignments/{assignment}` | `GET /courier/orders/{order}`    |
 * | `POST courier/assignments/{id}/start`   | -                                |
 * | `POST courier/assignments/{id}/complete`| -                                |
 * | `POST courier/assignments/{id}/location`| `POST /courier/orders/{id}/location` |
 * | `POST courier/assignments/{id}/cash/confirm` | `POST /courier/orders/{id}/payment-confirmation` |
 *
 * Backend ini assignment-centric dan memakai aksi eksplisit `start` dan
 * `complete`, sedangkan spec order-centric dan mengandalkan
 * `PATCH /courier/orders/{order}/status`. Endpoint PATCH tersebut tidak ada di
 * backend, jadi mengikuti spec akan membuat seluruh layar kurir gagal.
 *
 * Keputusan ini diambil atas arahan pemilik proyek: Android mengikuti backend
 * yang berjalan, dan spec section 15 dan 16 dicatat sebagai pekerjaan revisi
 * terpisah.
 *
 * Konsekuensi yang penting untuk layer Android:
 *
 * - Semua aksi mengembalikan Assignment Resource terbaru. Android menampilkan
 *   hasil itu apa adanya; tidak ada jalur yang menulis status assignment atau
 *   status pembayaran secara lokal.
 * - `POST .../cash/confirm` TIDAK menerima body. Android tidak mengirim
 *   `courier_id`, `order_id`, nominal, maupun bukti apa pun. Backend yang
 *   memeriksa apakah kurir adalah pemilik assignment, apakah delivery aktif,
 *   apakah payment-nya CASH, dan apakah masih PENDING.
 * - `GET courier/assignments` tidak dipaginasi: `data` berupa array penuh dan
 *   tidak ada objek `meta`. UI karena itu tidak boleh mengarang pagination.
 */
interface CourierApi {

    // Pesanan langsung Kurir. Backend menetapkan assignment bila DELIVERY.
    @POST("courier/direct-orders")
    suspend fun createDirectOrder(
        @Body request: CreateDirectOrderRequestDto
    ): Response<EnvelopeDto<DirectOrderCreatedDto>>

    // --- Daftar assignment milik kurir yang terautentikasi ---

    @GET("courier/assignments")
    suspend fun assignments(): EnvelopeDto<List<CourierAssignmentDto>>

    // --- Detail satu assignment ---

    /**
     * Backend membalas `403` bila assignment ini milik kurir lain. Android
     * menampilkan penolakan itu apa adanya dan tidak mencari jalan lain.
     */
    @GET("courier/assignments/{assignment}")
    suspend fun assignment(
        @Path("assignment") assignmentId: String
    ): EnvelopeDto<CourierAssignmentDto>

    // --- Mulai pengantaran ---

    /**
     * Backend menolak dengan `409` bila status assignment bukan `ASSIGNED`, bila
     * order belum `DITUGASKAN`, atau bila kurir masih punya assignment `ACTIVE`
     * lain.
     */
    @POST("courier/assignments/{assignment}/start")
    suspend fun startDelivery(
        @Path("assignment") assignmentId: String
    ): Response<EnvelopeDto<CourierAssignmentDto>>

    // --- Kirim lokasi kurir ---

    /**
     * Backend menolak dengan `409` bila delivery belum aktif, dan dengan `422`
     * bila jarak versus waktu tidak masuk akal (batas 150 km/jam).
     */
    @POST("courier/assignments/{assignment}/location")
    suspend fun updateLocation(
        @Path("assignment") assignmentId: String,
        @Body request: CourierLocationRequestDto
    ): Response<EnvelopeDto<CourierLocationAcceptedDto>>

    // --- Konfirmasi uang diterima ---

    /**
     * Tanpa body. Seluruh otorisasi ada di backend.
     */
    @POST("courier/assignments/{assignment}/cash/confirm")
    suspend fun confirmCashReceived(
        @Path("assignment") assignmentId: String
    ): Response<EnvelopeDto<CourierCashReceiptDto>>

    // --- Selesaikan pengantaran ---

    /**
     * Backend menolak dengan `409` bila delivery belum `ACTIVE` atau payment
     * belum `PAID`.
     */
    @POST("courier/assignments/{assignment}/complete")
    suspend fun completeDelivery(
        @Path("assignment") assignmentId: String
    ): Response<EnvelopeDto<CourierAssignmentDto>>

    // --- Dashboard kurir ---

    /**
     * Dashboard memakai Order Resource, bukan Assignment Resource, jadi
     * `active_assignment` di sini adalah ringkasan order milik assignment aktif
     * milik kurir tersebut. Order Resource ini tidak memuat nama customer.
     */
    @GET("dashboard/courier")
    suspend fun dashboard(): EnvelopeDto<CourierDashboardDto>

    // --- Sesi bertugas (on-duty) tanpa assignment ---

    /**
     * `GET courier/duty` — status sesi bertugas kurir saat ini.
     *
     * Kontrak baru (lihat `CourierDutyController`): sesi bertugas tidak terikat
     * pada assignment, jadi lokasinya tidak masuk ke `courier_locations` dan
     * tracking customer tidak berubah.
     */
    @GET("courier/duty")
    suspend fun dutyState(): EnvelopeDto<CourierDutyStateDto>

    /**
     * `POST courier/duty/start`. Backend menolak `409` bila sudah ada sesi
     * aktif untuk kurir yang sama.
     */
    @POST("courier/duty/start")
    suspend fun startDuty(): Response<EnvelopeDto<CourierDutySessionDto>>

    /**
     * `POST courier/duty/stop`. Backend menolak `409` bila tidak ada sesi aktif.
     */
    @POST("courier/duty/stop")
    suspend fun stopDuty(): Response<EnvelopeDto<CourierDutySessionDto>>

    /**
     * `POST courier/duty/location` — lokasi tanpa assignment.
     *
     * Backend menolak `409` bila tidak ada sesi aktif atau bila `duty_session_id`
     * yang dikirim tidak lagi sama dengan sesi aktif.
     */
    @POST("courier/duty/location")
    suspend fun sendDutyLocation(
        @Body request: CourierLocationRequestDto
    ): Response<EnvelopeDto<CourierLocationAcceptedDto>>
}

// --- Assignment Resource ---

/**
 * Bentuk `assignmentData()` pada `CourierController`.
 *
 * Hanya field yang benar-benar dikirim backend yang dimodelkan. `courier_id`
 * sengaja tidak ada di sini: kurir yang sedang memakai aplikasi ini sudah
 * tahu identity-nya, dan backend tidak pernah mengirimnya.
 */
@Serializable
data class CourierAssignmentDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val status: String? = null,
    val assigned_at: String? = null,
    val started_at: String? = null,
    val completed_at: String? = null,
    val order: CourierAssignmentOrderDto? = null,
    val latest_location: CourierLocationDto? = null
)

/**
 * Order di dalam assignment.
 *
 * Bentuk ini BUKAN Order Resource pada specification: backend memakai nama
 * field `subtotal`, `total`, dan `payment.method` / `payment.status`, bukan
 * `subtotal_amount`, `total_amount`, dan `payment_method` / `payment_status`.
 */
@Serializable
data class CourierAssignmentOrderDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val status: String? = null,
    val subtotal: String = "0.00",
    val total: String = "0.00",
    val delivery_address: String? = null,
    val delivery_location: CourierAssignmentDeliveryLocationDto? = null,
    val customer: CourierAssignmentCustomerDto? = null,
    val items: List<CourierAssignmentItemDto> = emptyList(),
    val payment: CourierAssignmentPaymentDto? = null
)

/** Backend hanya mengirim `name` untuk customer pada resource ini. */
@Serializable
data class CourierAssignmentDeliveryLocationDto(
    val address: String? = null,
    @Serializable(with = TolerantDecimalSerializer::class) val latitude: Double? = null,
    @Serializable(with = TolerantDecimalSerializer::class) val longitude: Double? = null
)

@Serializable
data class CourierAssignmentCustomerDto(
    val name: String = ""
)

@Serializable
data class CourierAssignmentItemDto(
    val product_name: String? = null,
    val quantity: Int = 0,
    val unit_price: String = "0.00",
    val line_total: String = "0.00"
)

@Serializable
data class CourierAssignmentPaymentDto(
    val method: String? = null,
    val status: String? = null
)

// --- Courier Location ---

/**
 * `courier_locations` diserialikan sebagai model Eloquent penuh, jadi latitude,
 * longitude, dan accuracy datang sebagai string desimal karena kolomnya di-cast
 * `decimal`.
 *
 * [TolerantDecimalSerializer] menerima string maupun angka supaya perubahan
 * cast di backend tidak membuat parsing gagal.
 */
@Serializable
data class CourierLocationDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long? = null,
    @Serializable(with = TolerantIdSerializer::class) val assignment_id: Long? = null,
    @Serializable(with = TolerantIdSerializer::class) val courier_id: Long? = null,
    @Serializable(with = TolerantDecimalSerializer::class) val latitude: Double? = null,
    @Serializable(with = TolerantDecimalSerializer::class) val longitude: Double? = null,
    @Serializable(with = TolerantDecimalSerializer::class) val accuracy_meters: Double? = null,
    val recorded_at: String? = null,
    val idempotency_key: String? = null
)

/**
 * Bungkus respons `POST courier/assignments/{assignment}/location`.
 *
 * Backend menjawab `data: {accepted, location}` (lihat `CourierController::location`
 * dan docs 06 §29.1), berbeda dari [CourierLocationDto] yang dipakai untuk daftar
 * lokasi pada `TrackingResource`. `data` di sini BUKAN objek lokasi itu sendiri,
 * jadi memakai [CourierLocationDto] sebagai tipe `data` akan membuat semua
 * koordinat terbaca null dan pengiriman tampak gagal. [accepted] dibaca apa
 * adanya; Android tidak pernah menulis nilainya.
 */
@Serializable
data class CourierLocationAcceptedDto(
    val accepted: Boolean = false,
    val location: CourierLocationDto? = null
)

/**
 * Body `POST courier/assignments/{assignment}/location`.
 *
 * Backend mewajibkan tiga field pertama. `recorded_at` sengaja biarkan null:
 * backend memakainya untuk menghitung kecepatan perpindahan lokasi, dan jam
 * perangkat bisa berbeda dari jam server sehingga mengirim waktu lokal justru
 * berisiko ditolak `422`. `idempotency_key` diisi repository.
 */
@Serializable
data class CourierLocationRequestDto(
    val latitude: Double,
    val longitude: Double,
    val accuracy_meters: Double,
    val recorded_at: String? = null,
    val idempotency_key: String? = null
)

// --- Cash confirmation ---

/**
 * Hasil `POST courier/assignments/{assignment}/cash/confirm`.
 *
 * `payment_status` dibaca apa adanya dari backend. Android tidak pernah
 * menulis `PAID` ke sini; nilai itu hanya boleh datang dari server.
 */
@Serializable
data class CourierCashReceiptDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val payment_method: String? = null,
    val payment_status: String? = null,
    val verified_at: String? = null
)

// --- Dashboard kurir ---

/**
 * `GET dashboard/courier`.
 *
 * `today_assignments` berisi Order Resource, bukan Assignment Resource. Petugas
 * kurir karena itu melihat id order dan alamat di beranda, sedangkan nama
 * customer hanya tersedia dari `GET /courier/assignments`.
 */
@Serializable
data class CourierDashboardDto(
    val active_assignment: CourierDashboardOrderDto? = null,
    val today_assignments: List<CourierDashboardOrderDto> = emptyList(),
    val summary: CourierDashboardSummaryDto? = null,
    val unread_notifications: Int = 0
)

@Serializable
data class CourierDashboardSummaryDto(
    val today_deliveries: Int = 0,
    val month_deliveries: Int = 0,
    val completed_deliveries: Int = 0,
    @Serializable(with = TolerantDecimalSerializer::class) val completion_rate: Double = 0.0
)

/** Bentuk `DashboardOrderResource` di backend. */
@Serializable
data class CourierDashboardOrderDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val status: String? = null,
    val subtotal_amount: String = "0.00",
    val total_amount: String = "0.00",
    val delivery_address: String? = null,
    val payment: CourierDashboardPaymentDto? = null,
    val items: List<CourierDashboardItemDto> = emptyList(),
    val assignment: CourierDashboardAssignmentRefDto? = null,
    val created_at: String? = null
)

@Serializable
data class CourierDashboardPaymentDto(
    val payment_method: String? = null,
    val payment_status: String? = null
)

@Serializable
data class CourierDashboardItemDto(
    @Serializable(with = TolerantIdSerializer::class) val product_id: Long? = null,
    val name: String? = null,
    val quantity: Int = 0,
    val unit_price: String = "0.00",
    val line_total_amount: String = "0.00"
)

@Serializable
data class CourierDashboardAssignmentRefDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val status: String? = null
)

// --- Sesi bertugas (on-duty) ---

/**
 * Satu sesi bertugas dari `POST courier/duty/start` / `stop`.
 *
 * Backend membalas `data: {id, started_at, ended_at, is_active}` dan sengaja
 * tidak mengirim foreign key internal (`courier_id`).
 */
@Serializable
data class CourierDutySessionDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val started_at: String? = null,
    val ended_at: String? = null,
    val is_active: Boolean = false
)

/**
 * Bungkus `GET courier/duty`: `{active, session, latest_location}`.
 *
 * `latest_location` memakai bentuk `CourierLocationDto` yang sama dengan lokasi
 * assignment, sehingga pemetaan domainnya juga sama.
 */
@Serializable
data class CourierDutyStateDto(
    val active: Boolean = false,
    val session: CourierDutySessionDto? = null,
    val latest_location: CourierLocationDto? = null
)

/**
 * Serializer untuk kolom decimal.
 *
 * Backend menyimpan latitude, longitude, dan accuracy sebagai `decimal`, sehingga
 * JSON-nya berisi string seperti `"-0.9480000"`. Serializer ini menerima string
 * maupun angka agar kedua bentuk itu bisa dibaca, dan melempar error untuk isi
 * yang bukan angka supaya data rusak tidak berubah diam-diam menjadi `0.0`.
 */
object TolerantDecimalSerializer : KSerializer<Double> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TolerantDecimal", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }

    override fun deserialize(decoder: Decoder): Double {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val primitive: JsonPrimitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        return primitive.content.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException(
                "Nilai desimal tidak valid: ${primitive.content}"
            )
    }
}