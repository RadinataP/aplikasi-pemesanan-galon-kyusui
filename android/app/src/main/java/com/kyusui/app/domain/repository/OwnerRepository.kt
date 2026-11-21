package com.kyusui.app.domain.repository

import com.kyusui.app.core.Result
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
import com.kyusui.app.domain.model.ProofImageSelection
import com.kyusui.app.domain.model.Product

/**
 * Owner data source sesuai `06_Berkah Water_API_SPECIFICATION_REBUILT.md` section 10,
 * 12, dan 13.
 *
 * Seluruh operasi memakai endpoint yang benar-benar ada pada backend aktif.
 * Dashboard Owner memakai endpoint preserved `GET /dashboard/owner`, yang
 * memang sudah didaftarkan pada routes/api.php. Tracking Owner tidak disediakan;
 * endpoint tracking yang tersedia hanya untuk role CUSTOMER.
 *
 * `getCouriers()` memakai `GET /owner/couriers`. Endpoint ini "preserved"
 * (tidak ada di katalog spec 06 section 30) tetapi ada di
 * `backend/routes/api.php`, sehingga [assignCourier] punya sumber `courier_id`
 * yang nyata dan bukan input manual Owner.
 *
 * Tidak ada operasi yang menulis status dari client. [updateOrderStatus] hanya
 * meminta next state; [verifyPayment] hanya mengirim aksi. Status final selalu
 * dibaca dari response backend.
 */
interface OwnerRepository {

    /** Mengambil ringkasan bisnis dan grafik yang dihitung oleh backend. */
    suspend fun getDashboard(): Result<OwnerDashboardData>

    // --- Section 10.1 ---

    suspend fun getOrders(
        page: Int,
        perPage: Int,
        status: OrderStatus? = null
    ): Result<PaginatedResult<OwnerOrder>>

    // --- Section 10.2 ---

    suspend fun getOrder(orderId: String): Result<OwnerOrder>

    // --- Section 10.3 ---

    suspend fun updateOrderStatus(
        orderId: String,
        status: OrderStatus,
        note: String? = null
    ): Result<OwnerOrder>

    // --- Section 10.4 ---

    suspend fun assignCourier(
        orderId: String,
        courierId: String
    ): Result<OwnerOrder>

    /**
     * Daftar kurir yang tidak sedang mengantarkan order lain.
     *
     * Backend sudah menyaring kurir sibuk, jadi Android menampilkan hasilnya apa
     * adanya tanpa menghitung ulang ketersediaan.
     */
    suspend fun getCouriers(): Result<List<CourierSummary>>

    // --- Preserved: owner product management ---

    suspend fun getProducts(
        page: Int,
        perPage: Int
    ): Result<PaginatedResult<Product>>

    suspend fun createProduct(
        name: String,
        description: String?,
        price: String,
        availability: Boolean
    ): Result<Product>

    suspend fun updateProduct(
        productId: String,
        name: String?,
        description: String?,
        price: String?,
        availability: Boolean?
    ): Result<Product>

    /** Menonaktifkan produk; backend tidak menghapus barisnya. */
    suspend fun deleteProduct(productId: String): Result<Unit>

    // --- Section 12.1 ---

    suspend fun getPendingPayments(
        page: Int,
        perPage: Int
    ): Result<PaginatedResult<OwnerPendingPayment>>

    // --- Section 12.2 ---

    suspend fun getPaymentProof(orderId: String): Result<OwnerPaymentProof>

    /** Riwayat keputusan bukti QRIS per order (endpoint backend preserved). */
    suspend fun getPaymentProofHistory(orderId: String): Result<List<OwnerPaymentProofHistory>> =
        Result.failure("Riwayat bukti QRIS belum didukung oleh implementasi repository.")

    /** Mengambil file bukti historis melalui endpoint terautentikasi backend. */
    suspend fun getHistoricalPaymentProof(
        orderId: String,
        historyId: String
    ): Result<ByteArray> =
        Result.failure("Bukti historis belum didukung oleh implementasi repository.")

    // --- Section 12.3 ---

    suspend fun verifyPayment(
        orderId: String,
        action: OwnerVerificationAction,
        note: String? = null
    ): Result<OwnerVerificationResult>

    // --- Section 13.1 ---

    suspend fun getActiveQris(): Result<OwnerQrisConfig?>

    // --- Section 13.2 ---

    suspend fun replaceActiveQris(image: ProofImageSelection): Result<OwnerQrisConfig>
}