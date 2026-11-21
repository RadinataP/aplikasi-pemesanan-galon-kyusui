package com.kyusui.app.data.api

import com.kyusui.app.data.api.TolerantIdSerializer
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import okhttp3.ResponseBody
import retrofit2.http.Streaming
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Owner API sesuai `06_Berkah Water_API_SPECIFICATION_REBUILT.md` section 10, 12, dan
 * 13. Semua path di sini disalin apa adanya dari specification dan dari katalog
 * endpoint final pada section 30.
 *
 * - `GET  /owner/orders`                             (10.1)
 * - `GET  /owner/orders/{order}`                     (10.2)
 * - `PATCH /owner/orders/{order}/status`             (10.3)
 * - `POST /owner/orders/{order}/assignment`          (10.4)
 * - `GET  /owner/payments/pending`                   (12.1)
 * - `GET  /owner/orders/{order}/payment/proof`       (12.2)
 * - `POST /owner/orders/{order}/payment-verification`(12.3)
 * - `GET  /owner/payment/qris`                       (13.1)
 * - `PUT  /owner/payment/qris`                       (13.2)
 *
 * Endpoint dashboard Owner memakai route preserved `GET /dashboard/owner` yang
 * sudah terdaftar pada backend aktif. Monitoring pengiriman Owner tetap tidak
 * ditambahkan karena endpoint tracking yang ada
 *   (`GET /customer/orders/{order}/tracking`) hanya untuk role CUSTOMER.
 *
 * `GET /owner/couriers` tidak ada di katalog spec 06 section 30, tetapi ada di
 * `backend/routes/api.php` sebagai `preserved` dan memang dibutuhkan layar
 * section 10.4 untuk memilih kurir. Android mengikuti kontrak backend yang
 * benar-benar berjalan, bukan bagian spec yang sudah usang.
 *
 * Approve dan reject memakai endpoint yang sama, `payment-verification`, dengan
 * `action` yang berbeda. Tidak ada endpoint `/owner/qris/{id}/approve`.
 */
interface OwnerApi {

    /** Dashboard agregat Owner; endpoint ini sudah tersedia pada backend aktif. */
    @GET("dashboard/owner")
    suspend fun ownerDashboard(): EnvelopeDto<OwnerDashboardDto>

    // --- Pembukuan digital: path diverifikasi dari routes/api.php backend aktif ---
    @GET("owner/finance/transactions")
    suspend fun financeTransactions(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("type") type: String? = null,
        @Query("source") source: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): FinanceIndexDto

    @POST("owner/finance/incomes")
    suspend fun createIncome(
        @Body request: CreateIncomeRequestDto
    ): Response<EnvelopeDto<FinancialTransactionDto>>

    @POST("owner/finance/expenses")
    suspend fun createExpense(
        @Body request: CreateExpenseRequestDto
    ): Response<EnvelopeDto<FinancialTransactionDto>>

    // Pesanan langsung Owner; semua nominal dihitung backend.
    @POST("owner/direct-orders")
    suspend fun createDirectOrder(
        @Body request: CreateDirectOrderRequestDto
    ): Response<EnvelopeDto<DirectOrderCreatedDto>>

    // --- Section 10.1 List Orders ---

    @GET("owner/orders")
    suspend fun orders(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = DEFAULT_PER_PAGE,
        @Query("status") status: String? = null
    ): PageDto<OwnerOrderDto>

    // --- Section 10.2 Owner Order Detail ---

    @GET("owner/orders/{order}")
    suspend fun order(
        @Path("order") orderId: String
    ): EnvelopeDto<OwnerOrderDto>

    // --- Section 10.3 Owner Order Status ---

    /**
     * Meminta perubahan status order.
     *
     * Backend memvalidasi bahwa transition adalah next state yang valid dan
     * menjadi responsibility Owner. Transition tidak valid dijawab `409`/`422`,
     * dan Android menampilkan pesan dari server apa adanya.
     */
    @PATCH("owner/orders/{order}/status")
    suspend fun updateOrderStatus(
        @Path("order") orderId: String,
        @Body request: UpdateOwnerOrderStatusRequestDto
    ): Response<EnvelopeDto<OwnerOrderDto>>

    // --- Section 10.4 Courier Assignment ---

    /**
     * Menugaskan kurir ke order.
     *
     * Hanya `courier_id` yang dikirim. Backend memvalidasi keberadaan kurir,
     * availability, order yang dapat ditugaskan, dan konflik assignment aktif.
     */
    @POST("owner/orders/{order}/assignment")
    suspend fun assignCourier(
        @Path("order") orderId: String,
        @Body request: AssignCourierRequestDto
    ): Response<EnvelopeDto<OwnerOrderDto>>

    /**
     * Daftar kurir yang tidak sedang mengantarkan order lain.
     *
     * Preserved endpoint (tidak ada di katalog spec 06 section 30), tetapi
     * dipakai layar penugasan section 10.4 supaya Owner memilih kurir nyata
     * daripada mengetik `courier_id` secara manual. Response membalut array
     * model `Courier` di dalam `data` (bukan paginasi).
     */
    @GET("owner/couriers")
    suspend fun couriers(): EnvelopeDto<List<OwnerCourierDto>>

    // --- Section 12.1 List Pending QRIS Verification ---

    /**
     * Antrean QRIS yang menunggu verifikasi.
     *
     * Backend sudah memfilter `payment_method = QRIS` dan
     * `payment_status = WAITING_VERIFICATION`, jadi Android tidak menambahkan
     * filter apa pun.
     */
    @GET("owner/payments/pending")
    suspend fun pendingPayments(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = DEFAULT_PER_PAGE
    ): PageDto<OwnerPendingPaymentDto>

    // --- Section 12.2 View Payment Proof ---

    @GET("owner/orders/{order}/payment/proof")
    suspend fun paymentProof(
        @Path("order") orderId: String
    ): EnvelopeDto<OwnerPaymentProofDto>

    // --- Preserved backend: QRIS proof decision history ---
    @GET("owner/orders/{order}/payment/proof-history")
    suspend fun paymentProofHistory(
        @Path("order") orderId: String
    ): EnvelopeDto<List<OwnerPaymentProofHistoryDto>>

    @Streaming
    @GET("owner/orders/{order}/payment/proof-history/{history}/proof")
    suspend fun historicalPaymentProof(
        @Path("order") orderId: String,
        @Path("history") historyId: String
    ): Response<ResponseBody>

    // --- Section 12.3 Approve / Reject QRIS ---

    /**
     * Satu endpoint untuk approve dan reject.
     *
     * Status `PAID` atau `PENDING` hanya boleh berasal dari response backend.
     * Tidak ada kode di Android yang menetapkan status tersebut.
     */
    @POST("owner/orders/{order}/payment-verification")
    suspend fun verifyPayment(
        @Path("order") orderId: String,
        @Body request: PaymentVerificationRequestDto
    ): Response<EnvelopeDto<PaymentVerificationEnvelopeDto>>

    // --- Section 13.1 / 13.2 Owner QRIS Configuration ---

    /**
     * QRIS aktif pada singleton `business_settings`.
     *
     * Mengembalikan `404` ketika QRIS belum pernah dikonfigurasi.
     */
    @GET("owner/payment/qris")
    suspend fun activeQris(): EnvelopeDto<OwnerActiveQrisDto>

    /**
     * Mengganti QRIS aktif. Multipart dengan form field `qris_image` sesuai
     * section 13.2.
     */
    @Multipart
    @POST("owner/payment/qris")
    suspend fun replaceActiveQris(
        @Part("_method") method: RequestBody,
        @Part qrisImage: MultipartBody.Part
    ): Response<EnvelopeDto<OwnerActiveQrisDto>>

    // --- Preserved: Owner product management ---

    /**
     * Daftar seluruh produk Owner, termasuk yang tidak tersedia.
     *
     * Preserved endpoint (tidak ada di katalog spec 06 section 30). Berbeda
     * dengan `GET /products` milik customer yang hanya mengembalikan produk
     * tersedia, endpoint ini dipakai Owner untuk mengelola katalognya.
     */
    @GET("owner/products")
    suspend fun products(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = DEFAULT_PER_PAGE
    ): PageDto<OwnerProductDto>

    @POST("owner/products")
    suspend fun createProduct(
        @Body request: CreateOwnerProductRequestDto
    ): Response<EnvelopeDto<OwnerProductDto>>

    @PUT("owner/products/{product}")
    suspend fun updateProduct(
        @Path("product") productId: String,
        @Body request: UpdateOwnerProductRequestDto
    ): Response<EnvelopeDto<OwnerProductDto>>

    /**
     * Menonaktifkan produk (soft disable; backend mengubah `availability`
     * menjadi false, bukan menghapus baris).
     */
    @DELETE("owner/products/{product}")
    suspend fun deleteProduct(
        @Path("product") productId: String
    ): Response<EnvelopeDto<OwnerProductDto>>

    companion object {
        const val DEFAULT_PER_PAGE = 20
    }
}

// --- DTO section 10 ---

/**
 * Order Resource untuk Owner (section 7.4 dan 10.2).
 *
 * `delivery_location` dimodelkan sebagai objek terpisah dengan lat/lng dan
 * alamat karena specification section 9.4 mendefinisikan bentuk `delivery_location`
 * pada request create order. Field ini opsional pada response Owner: bagian atas
 * hanya menampilkan alamat bila backend mengirimkannya.
 */
@Serializable
data class OwnerOrderDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val order_number: String? = null,
    @Serializable(with = TolerantIdSerializer::class) val customer_id: Long? = null,
    val customer: OwnerCustomerDto? = null,
    val items: List<CustomerOrderItemDto> = emptyList(),
    val subtotal_amount: String = "0.00",
    val delivery_fee: String = "0.00",
    val total_amount: String = "0.00",
    val order_status: String? = null,
    val payment: OwnerOrderPaymentDto? = null,
    val delivery_location: OwnerDeliveryLocationDto? = null,
    val assignment: OrderAssignmentDto? = null
)

@Serializable
data class OwnerCustomerDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val name: String = ""
)

@Serializable
data class OwnerOrderPaymentDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long? = null,
    val payment_method: String? = null,
    val payment_status: String? = null,
    val amount: String = "0.00"
)

@Serializable
data class OwnerDeliveryLocationDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null
)

/**
 * Bentuk `assignment` mengikuti kolom tabel `courier_assignments` pada
 * `05_Berkah Water_DATABASE_SCHEMA_REBUILT.md` section 16, karena API specification
 * hanya menyebut keberadaan field ini tanpa mendefinisikan isinya.
 */
@Serializable
data class OrderAssignmentDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    @Serializable(with = TolerantIdSerializer::class) val courier_id: Long? = null,
    val status: String? = null,
    val assigned_at: String? = null
)

/**
 * Request 10.3. `status` harus next state yang valid; `note` opsional.
 */
@Serializable
data class UpdateOwnerOrderStatusRequestDto(
    val status: String,
    val note: String? = null
)

/** Request 10.4. Hanya `courier_id` yang dikirim. */
@Serializable
data class AssignCourierRequestDto(
    @Serializable(with = TolerantIdSerializer::class) val courier_id: Long
)

/**
 * Baris `GET /owner/couriers` (preserved, dipakai section 10.4).
 *
 * Backend mengirim model `Courier` apa adanya plus relasi `user:id,name`.
 * Kolom lain yang tidak dimodelkan di sini diabaikan lewat `ignoreUnknownKeys`.
 */
@Serializable
data class OwnerCourierDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val phone: String? = null,
    val vehicle: String? = null,
    val user: CourierUserDto? = null
)

/** Relasi user minimal (`user:id,name`) yang disertakan pada daftar kurir. */
@Serializable
data class CourierUserDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long? = null,
    val name: String = ""
)

// --- DTO section 12 ---

/**
 * Baris antrean pending QRIS (section 12.1).
 *
 * `proof` hanya melaporkan `available`; tidak ada path atau URL karena proof
 * disimpan pada private storage.
 */
@Serializable
data class OwnerPendingPaymentDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    @Serializable(with = TolerantIdSerializer::class) val order_id: Long,
    val order_number: String? = null,
    val customer: OwnerCustomerDto? = null,
    val payment_method: String? = null,
    val payment_status: String? = null,
    val amount: String = "0.00",
    val proof: PaymentProofDto? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

/**
 * Conceptual resource 12.2.
 *
 * `proof.url` opsional: specification menyatakan response "dapat berupa payment
 * proof resource atau authorized file response", sehingga backend boleh
 * melaporkan keberadaan tanpa URL.
 */
@Serializable
data class OwnerPaymentProofDto(
    @Serializable(with = TolerantIdSerializer::class) val order_id: Long,
    @Serializable(with = TolerantIdSerializer::class) val payment_id: Long? = null,
    val payment_method: String? = null,
    val payment_status: String? = null,
    val proof: OwnerProofDetailDto? = null
)

@Serializable
data class OwnerProofDetailDto(
    val available: Boolean = false,
    val url: String? = null
)

/**
 * Request 12.3.
 *
 * Hanya `action` yang wajib. `note` dipakai pada `REJECT` dan opsional
 * menurut specification.
 */
@Serializable
data class PaymentVerificationRequestDto(
    val action: String,
    val note: String? = null
)

/** Response 12.3 membalut payment di dalam objek `payment`. */
@Serializable
data class PaymentVerificationEnvelopeDto(
    val payment: CustomerPaymentDto? = null
)

// --- DTO section 13 ---

/**
 * Konfigurasi QRIS aktif (section 13.1 dan 13.2).
 *
 * `updated_by` hanya ada pada response Owner. Endpoint customer 11.1 memakai
 * DTO terpisah, jadi penambahan field ini tidak mengubah model customer.
 */
@Serializable
data class OwnerActiveQrisDto(
    val qris_image: String? = null,
    val updated_at: String? = null,
    @Serializable(with = TolerantIdSerializer::class) val updated_by: Long? = null
)

// --- Preserved: Owner product management ---

/**
 * Baris `GET /owner/products`.
 *
 * Bentuk mengikuti `ProductResource` section 7.3, jadi identik dengan
 * `GET /products` milik customer. Didefinisikan terpisah supaya perubahan pada
 * kontrak Owner tidak diam-diam mengubah DTO customer.
 */
@Serializable
data class OwnerProductDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val name: String,
    val description: String? = null,
    val price: String,
    val is_available: Boolean = false
)

/** Request `POST /owner/products`. */
@Serializable
data class CreateOwnerProductRequestDto(
    val name: String,
    val description: String? = null,
    val price: String,
    // Tanpa nilai default: `availability` harus selalu ikut terkirim. Backend
    // memvalidasi field ini (bukan `is_available`), dan nilai yang sama dengan
    // default akan dihilangkan oleh serialisasi sehingga kontraknya ambigu.
    val availability: Boolean
)

/**
 * Request `PUT /owner/products/{product}`.
 *
 * Semua field opsional; hanya yang dikirim yang diubah backend.
 */
@Serializable
data class UpdateOwnerProductRequestDto(
    val name: String? = null,
    val description: String? = null,
    val price: String? = null,
    val availability: Boolean? = null
)

/** Riwayat keputusan bukti QRIS dari backend. */
@Serializable
data class OwnerPaymentProofHistoryDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val decision: String,
    val note: String? = null,
    val actor: String? = null,
    val decided_at: String? = null,
    val proof_available: Boolean = false
)
