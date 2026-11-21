package com.kyusui.app.data.repository

import com.kyusui.app.core.Result
import com.kyusui.app.core.safeApiCall
import com.kyusui.app.data.api.AssignCourierRequestDto
import com.kyusui.app.data.api.CreateOwnerProductRequestDto
import com.kyusui.app.data.api.EnvelopeDto
import com.kyusui.app.data.api.OwnerApi
import com.kyusui.app.data.api.OwnerOrderDto
import com.kyusui.app.data.api.OwnerProductDto
import com.kyusui.app.data.api.PaymentVerificationRequestDto
import com.kyusui.app.data.api.UpdateOwnerOrderStatusRequestDto
import com.kyusui.app.data.api.UpdateOwnerProductRequestDto
import com.kyusui.app.data.mapper.toDomain
import com.kyusui.app.data.mapper.toVerificationResult
import com.kyusui.app.data.upload.ContentResolverProofFileSource
import com.kyusui.app.data.upload.ProofFileSource
import com.kyusui.app.domain.model.CourierSummary
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OwnerOrder
import com.kyusui.app.domain.model.OwnerDashboardData
import com.kyusui.app.domain.model.OwnerPaymentProof
import com.kyusui.app.domain.model.OwnerPaymentProofHistory
import com.kyusui.app.domain.model.OwnerPendingPayment
import com.kyusui.app.domain.model.OwnerQrisConfig
import com.kyusui.app.domain.model.OwnerVerificationAction
import com.kyusui.app.domain.model.OwnerVerificationResult
import com.kyusui.app.domain.model.PaginatedResult
import com.kyusui.app.domain.model.Product
import com.kyusui.app.domain.model.ProofImageSelection
import com.kyusui.app.domain.repository.OwnerRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OwnerRepositoryImpl @Inject constructor(
    private val ownerApi: OwnerApi,
    private val proofFileSource: ProofFileSource
) : OwnerRepository {

    /** Dashboard memakai endpoint agregat yang sama dengan sumber buku kas. */
    override suspend fun getDashboard(): Result<OwnerDashboardData> = safeApiCall {
        ownerApi.ownerDashboard().requireData().toDomain()
    }

    override suspend fun getOrders(
        page: Int,
        perPage: Int,
        status: OrderStatus?
    ): Result<PaginatedResult<OwnerOrder>> = safeApiCall {
        ownerApi.orders(
            page = page,
            perPage = perPage,
            // Section 10.1 menyebut filter status boleh dipakai bila sudah
            // tersedia. Nilai dikirim apa adanya dari enum canonical, dan
            // backend tetap berwenang menentukan mana yang masuk scope Owner.
            status = status?.apiValue
        ).toDomain { it.toDomain() }
    }

    override suspend fun getOrder(orderId: String): Result<OwnerOrder> = safeApiCall {
        ownerApi.order(orderId).requireData().toDomain()
    }

    /**
     * Hanya meminta next state.
     *
     * Status pada hasil selalu dibaca dari response backend. Tidak ada cabang di
     * sini yang menetapkan `DIPROSES` atau status lain secara lokal, dan
     * transition yang tidak valid dijawab backend dengan `409`/`422`.
     */
    override suspend fun updateOrderStatus(
        orderId: String,
        status: OrderStatus,
        note: String?
    ): Result<OwnerOrder> = safeApiCall {
        val response = ownerApi.updateOrderStatus(
            orderId = orderId,
            request = UpdateOwnerOrderStatusRequestDto(
                status = status.apiValue,
                // `note` bersifat opsional. String kosong dibuang agar tidak
                // mengirim field kosong yang tidak berarti apa pun.
                note = note?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
        response.requireOrderBody()
    }

    override suspend fun assignCourier(
        orderId: String,
        courierId: String
    ): Result<OwnerOrder> = safeApiCall {
        val courier = courierId.trim().toLongOrNull()
            ?: throw IllegalArgumentException("Id kurir tidak valid: $courierId")

        val response = ownerApi.assignCourier(
            orderId = orderId,
            request = AssignCourierRequestDto(courier_id = courier)
        )
        response.requireOrderBody()
    }

    /**
     * Daftar kurir tersedia dari `GET /owner/couriers`.
     *
     * Backend mengirim array model `Courier` di dalam `data`; `null` tidak
     * diharapkan, tetapi diperlakukan sebagai daftar kosong supaya UI
     * menampilkan empty state, bukan kegagalan.
     */
    override suspend fun getCouriers(): Result<List<CourierSummary>> = safeApiCall {
        ownerApi.couriers().data.orEmpty().map { it.toDomain() }
    }

    override suspend fun getProducts(
        page: Int,
        perPage: Int
    ): Result<PaginatedResult<Product>> = safeApiCall {
        ownerApi.products(page = page, perPage = perPage).toDomain { it.toDomain() }
    }

    override suspend fun createProduct(
        name: String,
        description: String?,
        price: String,
        availability: Boolean
    ): Result<Product> = safeApiCall {
        ownerApi.createProduct(
            CreateOwnerProductRequestDto(
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                price = price.trim(),
                availability = availability
            )
        ).requireProductBody()
    }

    override suspend fun updateProduct(
        productId: String,
        name: String?,
        description: String?,
        price: String?,
        availability: Boolean?
    ): Result<Product> = safeApiCall {
        ownerApi.updateProduct(
            productId = productId,
            request = UpdateOwnerProductRequestDto(
                name = name?.trim()?.takeIf { it.isNotEmpty() },
                // Description dikirim apa adanya supaya pemilik bisa
                // mengosongkan deskripsi; `takeIf` di sini akan diam-diam
                // mempertahankan nilai lama.
                description = description?.trim(),
                price = price?.trim()?.takeIf { it.isNotEmpty() },
                availability = availability
            )
        ).requireProductBody()
    }

    /**
     * Menonaktifkan produk.
     *
     * Backend membalas `data: null` dan hanya mengubah `availability` menjadi
     * false, jadi keberhasilan dinilai dari status HTTP, bukan dari payload.
     */
    override suspend fun deleteProduct(productId: String): Result<Unit> = safeApiCall {
        val response = ownerApi.deleteProduct(productId)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    override suspend fun getPendingPayments(
        page: Int,
        perPage: Int
    ): Result<PaginatedResult<OwnerPendingPayment>> = safeApiCall {
        ownerApi.pendingPayments(page = page, perPage = perPage).toDomain { it.toDomain() }
    }

    override suspend fun getPaymentProof(orderId: String): Result<OwnerPaymentProof> =
        safeApiCall {
            ownerApi.paymentProof(orderId).requireData().toDomain()
        }

    override suspend fun getPaymentProofHistory(
        orderId: String
    ): Result<List<OwnerPaymentProofHistory>> = safeApiCall {
        ownerApi.paymentProofHistory(orderId).data.orEmpty().map { it.toDomain() }
    }

    override suspend fun getHistoricalPaymentProof(
        orderId: String,
        historyId: String
    ): Result<ByteArray> = safeApiCall {
        val response = ownerApi.historicalPaymentProof(orderId, historyId)
        if (!response.isSuccessful) throw HttpException(response)
        response.body()?.use { body ->
            val bytes = body.bytes()
            if (bytes.isEmpty()) {
                throw IllegalStateException("File bukti historis kosong.")
            }
            bytes
        } ?: throw IllegalStateException("Backend tidak mengirim file bukti historis.")
    }

    /**
     * Satu jalur untuk approve dan reject.
     *
     * Android hanya mengirim `action` dan `note` untuk reject. Status `PAID` atau
     * `PENDING` pada hasil dibaca apa adanya dari response backend; tidak ada
     * cabang di sini yang menulis status tersebut secara lokal.
     */
    override suspend fun verifyPayment(
        orderId: String,
        action: OwnerVerificationAction,
        note: String?
    ): Result<OwnerVerificationResult> = safeApiCall {
        val response = ownerApi.verifyPayment(
            orderId = orderId,
            request = PaymentVerificationRequestDto(
                action = action.apiValue,
                note = note?.trim()?.takeIf { it.isNotEmpty() }
            )
        )

        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        response.body()?.requireData()
            ?.toVerificationResult(action)
            ?: throw IllegalStateException("Respons backend tidak memuat payment.")
    }

    override suspend fun getActiveQris(): Result<OwnerQrisConfig?> = safeApiCall {
        // Backend contract: HTTP 200 + data:null means QRIS belum dikonfigurasi.
        // Jangan ubah kondisi bisnis ini menjadi ParsingException.
        ownerApi.activeQris().data?.toDomain()
    }

    /**
     * Mengganti QRIS aktif.
     *
     * Multipart dengan field `qris_image` sesuai section 13.2. Konfigurasi yang
     * dikembalikan backend dipakai apa adanya, sehingga Android selalu
     * menampilkan QRIS yang benar-benar aktif di server.
     */
    override suspend fun replaceActiveQris(
        image: ProofImageSelection
    ): Result<OwnerQrisConfig> = safeApiCall {
        val part = proofFileSource.createImagePart(
            fieldName = ContentResolverProofFileSource.QRIS_IMAGE_FIELD_NAME,
            fileName = ContentResolverProofFileSource.QRIS_IMAGE_DISPLAY_NAME,
            selection = image
        )

        val methodOverride = "PUT".toRequestBody("text/plain".toMediaType())
        val response = ownerApi.replaceActiveQris(
            method = methodOverride,
            qrisImage = part
        )
        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        response.body()?.requireData()?.toDomain()
            ?: throw IllegalStateException("Respons backend tidak memuat konfigurasi QRIS.")
    }

    /**
     * Endpoint 10.3 dan 10.4 membalut order di dalam `data`.
     *
     * Status HTTP non-2xx dilempar sebagai [HttpException] agar dipetakan ke
     * exception yang sama seperti endpoint lain. Ini penting untuk `409` dan
     * `422`, yang harus tampil sebagai penolakan backend dan bukan keberhasilan.
     */
    private fun Response<EnvelopeDto<OwnerProductDto>>.requireProductBody(): Product {
        if (!isSuccessful) {
            throw HttpException(this)
        }

        return body()?.requireData()?.toDomain()
            ?: throw IllegalStateException("Respons backend tidak memuat produk.")
    }

    private fun Response<EnvelopeDto<OwnerOrderDto>>.requireOrderBody(): OwnerOrder {
        if (!isSuccessful) {
            throw HttpException(this)
        }
        return body()?.requireData()?.toDomain()
            ?: throw IllegalStateException("Respons backend tidak memuat order.")
    }

    private fun <T> EnvelopeDto<T>.requireData(): T =
        data ?: throw IllegalStateException("Respons backend tidak memuat data yang diminta.")
}