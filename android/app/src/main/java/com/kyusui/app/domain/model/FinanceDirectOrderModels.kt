package com.kyusui.app.domain.model

/** Model buku kas; seluruh nominal berasal dari API dan tidak dihitung dari order di Android. */
data class FinanceBook(
    val transactions: List<FinancialTransaction>,
    val income: String,
    val expense: String,
    val netCashFlow: String,
    val currentPage: Int,
    val perPage: Int,
    val total: Int
)

data class FinancialTransaction(
    val id: Long,
    val type: String,
    val category: String,
    val amount: String,
    val paymentMethod: String?,
    val source: String?,
    val orderId: Long?,
    val description: String?,
    val occurredAt: String?
)

/** Data input pemasukan manual sebelum diterjemahkan menjadi DTO API. */
data class IncomeDraft(
    val category: String,       // Kategori pemasukan yang dipilih Owner.
    val amount: String,         // Nominal dalam bentuk teks desimal agar tidak memakai Float/Double.
    val paymentMethod: String,  // Metode yang dipilih di form.
    val description: String,    // Catatan tambahan, boleh kosong.
    val occurredAt: String      // Waktu transaksi yang dikirim ke server.
)

/** Data input pengeluaran manual sebelum diterjemahkan menjadi DTO API. */
data class ExpenseDraft(
    val category: String,       // Kategori pengeluaran.
    val amount: String,         // Nominal positif sebagai teks desimal.
    val paymentMethod: String,  // CASH, QRIS, atau OTHER.
    val description: String,   // Catatan tambahan, boleh kosong.
    val occurredAt: String      // Waktu transaksi.
)

data class DirectOrderDraft(
    val customerName: String,
    val customerPhone: String,
    val fulfillmentType: String,
    val productQuantities: Map<String, Int>,
    val address: String,
    val latitude: String,
    val longitude: String,
    val description: String
)

data class DirectOrderReceipt(
    val orderNumber: String?,
    val subtotal: String,
    val deliveryFee: String,
    val total: String,
    val status: String?
)
