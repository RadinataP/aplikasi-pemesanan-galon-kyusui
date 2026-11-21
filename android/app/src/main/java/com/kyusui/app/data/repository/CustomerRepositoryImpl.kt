package com.kyusui.app.data.repository

import com.kyusui.app.core.Result
import com.kyusui.app.core.safeApiCall
import com.kyusui.app.data.api.CreateOrderItemDto
import com.kyusui.app.data.api.CreateOrderRequestDto
import com.kyusui.app.data.api.CustomerApi
import com.kyusui.app.data.api.DeliveryLocationDto
import com.kyusui.app.data.api.EnvelopeDto
import com.kyusui.app.data.api.SelectPaymentMethodRequestDto
import com.kyusui.app.data.api.UpdateCustomerProfileRequestDto
import com.kyusui.app.data.mapper.requirePayment
import com.kyusui.app.data.mapper.toDomain
import com.kyusui.app.data.upload.ProofFileSource
import com.kyusui.app.domain.model.ActiveQris
import com.kyusui.app.domain.model.CustomerProfile
import com.kyusui.app.domain.model.DeliveryLocation
import com.kyusui.app.domain.model.Order
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OrderTrackingResult
import com.kyusui.app.domain.model.PaginatedResult
import com.kyusui.app.domain.model.Payment
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.model.Product
import com.kyusui.app.domain.model.ProofImageSelection
import com.kyusui.app.domain.repository.CustomerRepository
import com.kyusui.app.domain.repository.OrderItemRequest
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepositoryImpl @Inject constructor(
    private val customerApi: CustomerApi,
    private val proofFileSource: ProofFileSource
) : CustomerRepository {

    override suspend fun getProfile(): Result<CustomerProfile> = safeApiCall {
        customerApi.profile().requireData().toDomain()
    }

    override suspend fun updateProfile(
        name: String,
        phone: String?,
        email: String?,
        defaultAddress: String?
    ): Result<CustomerProfile> = safeApiCall {
        customerApi.updateProfile(
            UpdateCustomerProfileRequestDto(
                name = name.trim(),
                phone = phone?.trim()?.takeIf { it.isNotEmpty() },
                email = email?.trim()?.takeIf { it.isNotEmpty() },
                default_address = defaultAddress?.trim()?.takeIf { it.isNotEmpty() }
            )
        ).requireData().toDomain()
    }

    override suspend fun getProducts(
        page: Int,
        perPage: Int
    ): Result<PaginatedResult<Product>> = safeApiCall {
        customerApi.products(page = page, perPage = perPage).toDomain { it.toDomain() }
    }

    override suspend fun createOrder(
        items: List<OrderItemRequest>,
        deliveryLocation: DeliveryLocation
    ): Result<Order> = safeApiCall {
        val response = customerApi.createOrder(
            CreateOrderRequestDto(
                items = items.map { item ->
                    CreateOrderItemDto(
                        product_id = item.productId.toLongOrNull()
                            ?: throw IllegalArgumentException(
                                "Product id tidak valid: ${item.productId}"
                            ),
                        quantity = item.quantity
                    )
                },
                delivery_location = DeliveryLocationDto(
                    latitude = deliveryLocation.point.latitude,
                    longitude = deliveryLocation.point.longitude,
                    address = deliveryLocation.address
                )
            )
        )

        if (!response.isSuccessful) {
            throw IllegalStateException("Gagal membuat order (${response.code()}).")
        }

        response.body()?.requireData()?.toDomain()
            ?: throw IllegalStateException("Respons backend tidak memuat order yang dibuat.")
    }

    override suspend fun getOrders(
        page: Int,
        perPage: Int,
        statuses: List<OrderStatus>
    ): Result<PaginatedResult<Order>> = safeApiCall {
        // Backend memvalidasi setiap nilai; nilai tak dikenal akan ditolak 422
        // alih-alih diabaikan diam-diam.
        customerApi.orders(
            page = page,
            perPage = perPage,
            orderStatus = statuses
                .joinToString(",") { it.apiValue }
                .ifEmpty { null }
        ).toDomain { it.toDomain() }
    }

    override suspend fun getOrder(orderId: String): Result<Order> = safeApiCall {
        customerApi.order(orderId).requireData().toDomain()
    }

    override suspend fun cancelOrder(orderId: String): Result<Order> = safeApiCall {
        val response = customerApi.cancelOrder(orderId)
        if (!response.isSuccessful) {
            // `409` (status bukan lagi MENUNGGU_PEMBAYARAN) dan `403`/`404`
            // dipetakan ke exception domain oleh safeApiCall, sehingga UI bisa
            // menjelaskan alasan kegagalan.
            throw HttpException(response)
        }

        response.body()?.requireData()?.toDomain()
            ?: throw IllegalStateException("Respons backend tidak memuat order yang dibatalkan.")
    }

    // --- Tracking (Phase 7) ---

    /**
     * `data: null` diterjemahkan menjadi [OrderTrackingResult.NoActiveDelivery].
     *
     * Ini penting karena `requireData()` di file ini melempar
     * `IllegalStateException` untuk `data` yang null, dan `safeApiCall`
     * meratakan `IllegalStateException` menjadi `UnknownException`. Kalau
     * tracking memakai `requireData()`, keadaan "tracking sudah selesai" akan
     * muncul sebagai error server dan tidak bisa dibedakan dari kegagalan
     * jaringan. Di sini `data` diperiksa eksplisit, jadi tidak ada exception
     * yang dilempar untuk keadaan normal itu.
     *
     * Penolakan akses milik order lain tetap muncul sebagai error karena
     * backend menjawab `403`, dan itu memang kegagalan yang harus
     * ditampilkan.
     */
    override suspend fun getTracking(orderId: String): Result<OrderTrackingResult> = safeApiCall {
        val data = customerApi.tracking(orderId).data
            ?: return@safeApiCall OrderTrackingResult.NoActiveDelivery
        OrderTrackingResult.Active(data.toDomain())
    }

    // --- Section 11.1 ---

    override suspend fun getActiveQris(): Result<ActiveQris> = safeApiCall {
        customerApi.activeQris().requireData().toDomain()
    }

    // --- Section 11.2 ---

    override suspend fun selectPaymentMethod(
        orderId: String,
        method: PaymentMethod
    ): Result<Payment> = safeApiCall {
        val response = customerApi.selectPaymentMethod(
            orderId = orderId,
            // Hanya method yang dikirim. `payment_status` selalu ditetapkan
            // backend, sehingga pemilihan method tidak dapat membuat PAID.
            request = SelectPaymentMethodRequestDto(payment_method = method.apiValue)
        )

        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        response.body()?.requireData()?.requirePayment()
            ?: throw IllegalStateException("Respons backend tidak memuat payment.")
    }

    // --- Section 11.3 ---

    override suspend fun getPayment(orderId: String): Result<Payment> = safeApiCall {
        customerApi.payment(orderId).requireData().toDomain()
    }

    // --- Section 11.4 ---

    override suspend fun getPayments(
        page: Int,
        perPage: Int,
        method: PaymentMethod?,
        status: PaymentStatus?
    ): Result<PaginatedResult<Payment>> = safeApiCall {
        customerApi.payments(
            page = page,
            perPage = perPage,
            paymentMethod = method?.apiValue,
            paymentStatus = status?.apiValue
        ).toDomain { it.toDomain() }
    }

    // --- Section 11.5 ---

    /**
     * Upload proof selalu dibaca apa adanya dari backend, karena hasil sukses
     * hanya `WAITING_VERIFICATION`. Tidak ada cabang di sini yang menulis `PAID`.
     */
    override suspend fun uploadQrIsProof(
        orderId: String,
        proof: ProofImageSelection
    ): Result<Payment> = safeApiCall {
        val part = proofFileSource.createProofPart(proof)

        val response = customerApi.uploadQrIsProof(
            orderId = orderId,
            proofImage = part
        )

        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        response.body()?.requireData()?.requirePayment()
            ?: throw IllegalStateException("Respons backend tidak memuat payment.")
    }

    private fun <T> EnvelopeDto<T>.requireData(): T =
        data ?: throw IllegalStateException("Respons backend tidak memuat data yang diminta.")
}