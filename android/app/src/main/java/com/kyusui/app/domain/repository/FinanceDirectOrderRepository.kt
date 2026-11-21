package com.kyusui.app.domain.repository

import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.DirectOrderDraft
import com.kyusui.app.domain.model.DirectOrderReceipt
import com.kyusui.app.domain.model.ExpenseDraft
import com.kyusui.app.domain.model.IncomeDraft
import com.kyusui.app.domain.model.FinanceBook
import com.kyusui.app.domain.model.Product

/** Kontrak repository untuk fitur buku kas Owner dan pesanan langsung Owner/Kurir. */
interface FinanceDirectOrderRepository {
    suspend fun getFinance(
        from: String? = null,
        to: String? = null,
        type: String? = null,
        source: String? = null,
        page: Int = 1
    ): Result<FinanceBook>
    suspend fun createIncome(draft: IncomeDraft): Result<Unit>
    suspend fun createExpense(draft: ExpenseDraft): Result<Unit>
    suspend fun getAvailableProducts(): Result<List<Product>>
    suspend fun createDirectOrder(role: String, draft: DirectOrderDraft): Result<DirectOrderReceipt>
}
