package com.kyusui.app.data.repository

import com.kyusui.app.core.Result
import com.kyusui.app.core.safeApiCall
import com.kyusui.app.data.api.CreateDirectOrderRequestDto
import com.kyusui.app.data.api.CreateExpenseRequestDto
import com.kyusui.app.data.api.CreateIncomeRequestDto
import com.kyusui.app.data.api.CourierApi
import com.kyusui.app.data.api.CustomerApi
import com.kyusui.app.data.api.DirectOrderLineRequestDto
import com.kyusui.app.data.api.DirectOrderLocationRequestDto
import com.kyusui.app.data.api.FinanceIndexDto
import com.kyusui.app.data.api.FinancialTransactionDto
import com.kyusui.app.data.api.OwnerApi
import com.kyusui.app.data.mapper.toDomain
import com.kyusui.app.domain.model.DirectOrderDraft
import com.kyusui.app.domain.model.DirectOrderReceipt
import com.kyusui.app.domain.model.ExpenseDraft
import com.kyusui.app.domain.model.FinanceBook
import com.kyusui.app.domain.model.FinancialTransaction
import com.kyusui.app.domain.model.IncomeDraft
import com.kyusui.app.domain.model.Product
import com.kyusui.app.domain.repository.FinanceDirectOrderRepository
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi repository buku kas dan pesanan langsung.
 *
 * Repository menjadi jembatan antara ViewModel/domain dan Retrofit API.
 * Nilai uang final, ongkir, dan status transaksi tetap ditentukan backend.
 */
@Singleton
class FinanceDirectOrderRepositoryImpl @Inject constructor(
    private val ownerApi: OwnerApi,
    private val courierApi: CourierApi,
    private val customerApi: CustomerApi
) : FinanceDirectOrderRepository {

    /**
     * Signature harus identik dengan FinanceDirectOrderRepository.
     * `type` dan `source` wajib ada karena digunakan sebagai filter riwayat.
     */
    override suspend fun getFinance(
        from: String?,
        to: String?,
        type: String?,
        source: String?,
        page: Int
    ): Result<FinanceBook> = safeApiCall {
        ownerApi.financeTransactions(
            from = from,
            to = to,
            type = type,
            source = source,
            page = page
        ).toDomain()
    }

    /**
     * Menyimpan pemasukan manual, misalnya penjualan langsung kepada warga.
     * Ini method yang sebelumnya hilang dari implementasi repository.
     */
    override suspend fun createIncome(draft: IncomeDraft): Result<Unit> = safeApiCall {
        // Validasi awal untuk menghindari request kosong; backend tetap validator utama.
        require(draft.category.isNotBlank()) { "Kategori pemasukan wajib diisi." }
        require(draft.amount.toBigDecimalOrNull()?.signum() == 1) {
            "Nominal pemasukan harus lebih besar dari nol."
        }
        require(draft.paymentMethod.isNotBlank()) { "Metode pembayaran wajib dipilih." }
        require(draft.occurredAt.isNotBlank()) { "Waktu transaksi wajib diisi." }

        // Gunakan endpoint pemasukan, bukan endpoint pengeluaran.
        val response = ownerApi.createIncome(
            CreateIncomeRequestDto(
                category = draft.category.trim(),
                amount = draft.amount.trim(),
                payment_method = draft.paymentMethod,
                description = draft.description.trim().ifBlank { null },
                occurred_at = draft.occurredAt
            )
        )

        // HTTP non-2xx harus menjadi Result.Failure melalui safeApiCall.
        if (!response.isSuccessful) throw HttpException(response)

        // Respons 2xx tanpa data bukan bukti transaksi berhasil disimpan.
        response.body()?.data
            ?: error("Respons server tidak memuat transaksi pemasukan.")

        Unit
    }

    /** Menyimpan pengeluaran manual seperti listrik dan perawatan alat depot. */
    override suspend fun createExpense(draft: ExpenseDraft): Result<Unit> = safeApiCall {
        require(draft.category.isNotBlank()) { "Kategori pengeluaran wajib diisi." }
        require(draft.amount.toBigDecimalOrNull()?.signum() == 1) {
            "Nominal pengeluaran harus lebih besar dari nol."
        }
        require(draft.paymentMethod.isNotBlank()) { "Metode pembayaran wajib dipilih." }
        require(draft.occurredAt.isNotBlank()) { "Waktu transaksi wajib diisi." }

        val response = ownerApi.createExpense(
            CreateExpenseRequestDto(
                category = draft.category.trim(),
                amount = draft.amount.trim(),
                payment_method = draft.paymentMethod,
                description = draft.description.trim().ifBlank { null },
                occurred_at = draft.occurredAt
            )
        )

        if (!response.isSuccessful) throw HttpException(response)

        response.body()?.data
            ?: error("Respons server tidak memuat transaksi pengeluaran.")

        Unit
    }

    /**
     * Memuat semua halaman katalog produk.
     * Backend sebelumnya menolak per_page=100 dengan HTTP 422, sehingga
     * gunakan CustomerApi.DEFAULT_PER_PAGE (20) dan ikuti metadata server.
     */
    override suspend fun getAvailableProducts(): Result<List<Product>> = safeApiCall {
        val products = mutableListOf<Product>()
        var page = 1
        var finished = false
        val maxPagesSafetyLimit = 100

        // for + break dipakai agar benar-benar berhenti pada halaman terakhir.
        for (pageAttempt in 1..maxPagesSafetyLimit) {
            val response = customerApi.products(
                page = page,
                perPage = CustomerApi.DEFAULT_PER_PAGE
            )

            // Mapper ini sama dengan mapper katalog customer.
            products += response.data.map { it.toDomain() }

            // PageDto.hasMore membaca current_page, per_page, dan total dari backend.
            if (!response.hasMore) {
                finished = true
                break
            }

            // Ambil halaman berikut dari metadata aktual, dengan pengaman progres.
            val nextPage = response.currentPage + 1
            if (nextPage <= page) {
                error("Metadata pagination katalog tidak valid dari server.")
            }
            page = nextPage
        }

        if (!finished) {
            error("Katalog melewati batas halaman atau metadata pagination tidak valid.")
        }

        products
    }

    /**
     * Membuat pesanan langsung oleh Owner atau Kurir.
     * Android hanya mengirim product_id dan quantity; harga dihitung backend.
     */
    override suspend fun createDirectOrder(
        role: String,
        draft: DirectOrderDraft
    ): Result<DirectOrderReceipt> = safeApiCall {
        require(role == "OWNER" || role == "COURIER") {
            "Peran pengguna tidak valid."
        }

        // Hilangkan item dengan kuantitas nol dan konversi ID ke Long.
        val lines = draft.productQuantities
            .filterValues { it > 0 }
            .map { (id, quantity) ->
                DirectOrderLineRequestDto(
                    product_id = id.toLongOrNull()
                        ?: error("ID produk tidak valid: $id"),
                    quantity = quantity
                )
            }

        require(lines.isNotEmpty()) { "Pilih minimal satu produk." }

        // Delivery membutuhkan alamat dan koordinat; pickup tidak membutuhkan lokasi.
        val location = if (draft.fulfillmentType == "DELIVERY") {
            val latitude = draft.latitude.toDoubleOrNull()
                ?: error("Latitude tidak valid.")
            val longitude = draft.longitude.toDoubleOrNull()
                ?: error("Longitude tidak valid.")

            require(latitude in -90.0..90.0) { "Latitude di luar rentang valid." }
            require(longitude in -180.0..180.0) { "Longitude di luar rentang valid." }

            val address = draft.address.trim()
            require(address.isNotBlank()) { "Alamat pengantaran wajib diisi." }

            DirectOrderLocationRequestDto(
                latitude = latitude,
                longitude = longitude,
                address = address
            )
        } else {
            require(draft.fulfillmentType == "PICKUP") {
                "Jenis pemenuhan pesanan tidak valid."
            }
            null
        }

        // Pesanan langsung hanya untuk CASH yang sudah diterima secara fisik.
        val request = CreateDirectOrderRequestDto(
            customer_name = draft.customerName.trim().ifBlank { null },
            customer_phone = draft.customerPhone.trim().ifBlank { null },
            fulfillment_type = draft.fulfillmentType,
            items = lines,
            delivery_location = location,
            payment_method = "CASH",
            payment_received = true,
            description = draft.description.trim().ifBlank { null }
        )

        // Endpoint dipilih berdasarkan role yang sedang login.
        val response = when (role) {
            "OWNER" -> ownerApi.createDirectOrder(request)
            "COURIER" -> courierApi.createDirectOrder(request)
            else -> error("Peran pengguna tidak didukung.")
        }

        if (!response.isSuccessful) throw HttpException(response)

        val data = response.body()?.data
            ?: error("Respons server tidak memuat pesanan yang dibuat.")

        // Receipt menggunakan nilai final dari backend.
        DirectOrderReceipt(
            orderNumber = data.order_number,
            subtotal = data.subtotal_amount,
            deliveryFee = data.delivery_fee,
            total = data.total_amount,
            status = data.order_status
        )
    }

    /** Memetakan respons ringkasan dan transaksi buku kas ke model domain. */
    private fun FinanceIndexDto.toDomain(): FinanceBook = FinanceBook(
        transactions = data.map { it.toDomain() },
        income = summary.income,
        expense = summary.expense,
        netCashFlow = summary.net_cash_flow,
        currentPage = meta.current_page,
        perPage = meta.per_page,
        total = meta.total
    )

    /** Memetakan satu baris transaksi API ke model domain. */
    private fun FinancialTransactionDto.toDomain(): FinancialTransaction =
        FinancialTransaction(
            id = id,
            type = type,
            category = category,
            amount = amount,
            paymentMethod = payment_method,
            source = source,
            orderId = order_id,
            description = description,
            occurredAt = occurred_at
        )
}
