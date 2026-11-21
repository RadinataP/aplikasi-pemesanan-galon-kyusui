package com.kyusui.app.data.api

import kotlinx.serialization.Serializable

/** DTO buku kas sesuai respons FinanceController; nominal tetap String desimal. */
@Serializable
data class FinanceIndexDto(
    val data: List<FinancialTransactionDto> = emptyList(),
    val summary: FinanceSummaryDto = FinanceSummaryDto(),
    val meta: FinanceMetaDto = FinanceMetaDto(),
    val message: String? = null
)

@Serializable
data class FinanceSummaryDto(
    val income: String = "0.00",
    val expense: String = "0.00",
    val net_cash_flow: String = "0.00"
)

@Serializable
data class FinanceMetaDto(
    val current_page: Int = 1,
    val per_page: Int = 20,
    val total: Int = 0
)

@Serializable
data class FinancialTransactionDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val type: String,
    val category: String,
    val amount: String,
    val payment_method: String? = null,
    val source: String? = null,
    @Serializable(with = TolerantIdSerializer::class) val order_id: Long? = null,
    @Serializable(with = TolerantIdSerializer::class) val payment_id: Long? = null,
    val recorded_by: String? = null,
    val description: String? = null,
    val occurred_at: String? = null,
    val created_at: String? = null
)

/**
 * Payload untuk pemasukan manual di luar alur pesanan.
 * Nama field harus sama dengan validasi FinanceController Laravel.
 */
@Serializable
data class CreateIncomeRequestDto(
    val category: String,             // Kategori pemasukan, misalnya penjualan langsung depot.
    val amount: String,               // Nominal rupiah dalam format desimal; server tetap memvalidasi.
    val payment_method: String,       // CASH, QRIS, atau OTHER sesuai kontrak backend.
    val description: String? = null,  // Keterangan opsional yang membantu audit.
    val occurred_at: String           // Waktu transaksi dalam format ISO-8601.
)

/**
 * Payload untuk pengeluaran manual depot.
 * Pemasukan tidak boleh dikirim ke endpoint pengeluaran.
 */
@Serializable
data class CreateExpenseRequestDto(
    val category: String,             // Kategori biaya, misalnya Listrik Depot.
    val amount: String,               // Nominal rupiah positif.
    val payment_method: String,       // CASH, QRIS, atau OTHER sesuai kontrak backend.
    val description: String? = null,  // Keterangan opsional.
    val occurred_at: String           // Waktu transaksi dalam format ISO-8601.
)

@Serializable
data class DirectOrderLineRequestDto(
    val product_id: Long,
    val quantity: Int
)

@Serializable
data class DirectOrderLocationRequestDto(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

/** Request mengikuti DirectOrderController; harga dan ongkir tidak pernah dikirim klien. */
@Serializable
data class CreateDirectOrderRequestDto(
    val customer_name: String? = null,
    val customer_phone: String? = null,
    val fulfillment_type: String,
    val items: List<DirectOrderLineRequestDto>,
    val delivery_location: DirectOrderLocationRequestDto? = null,
    val payment_method: String = "CASH",
    val payment_received: Boolean = true,
    val description: String? = null
)

@Serializable
data class DirectOrderCreatedDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val order_number: String? = null,
    val source: String? = null,
    val customer_name: String? = null,
    val customer_phone: String? = null,
    val fulfillment_type: String? = null,
    val subtotal_amount: String = "0.00",
    val delivery_fee: String = "0.00",
    val total_amount: String = "0.00",
    val order_status: String? = null,
    val payment: DirectOrderPaymentDto? = null
)

@Serializable
data class DirectOrderPaymentDto(
    val payment_method: String? = null,
    val payment_status: String? = null,
    val amount: String = "0.00"
)
