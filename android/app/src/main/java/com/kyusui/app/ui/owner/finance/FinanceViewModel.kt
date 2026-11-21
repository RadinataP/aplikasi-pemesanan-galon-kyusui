package com.kyusui.app.ui.owner.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.core.userMessage
import com.kyusui.app.domain.model.ExpenseDraft
import com.kyusui.app.domain.model.FinanceBook
import com.kyusui.app.domain.model.IncomeDraft
import com.kyusui.app.domain.repository.FinanceDirectOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Seluruh state layar buku kas berada di ViewModel agar form tidak kehilangan
 * input saat recomposition. Nilai ringkasan selalu bersumber dari backend.
 */
data class FinanceUiState(
    val loading: Boolean = false,
    val appending: Boolean = false,
    val appendError: String? = null,
    val submitting: Boolean = false,
    val book: FinanceBook? = null,
    val error: String? = null,
    val success: String? = null,
    val entryType: String = "INCOME",
    val category: String = "",
    val amount: String = "",
    val paymentMethod: String = "CASH",
    val description: String = "",
    val fromDate: String = "",
    val toDate: String = "",
    val typeFilter: String = "",
    val sourceFilter: String = ""
) {
    val canLoadMore: Boolean
        get() = book != null &&
            !loading &&
            !appending &&
            book.transactions.size < book.total
}

/** ViewModel pembukuan Owner. Endpoint dilayani FinanceDirectOrderRepository. */
@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repository: FinanceDirectOrderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    /** Memuat data server dengan filter yang sedang aktif. */
    fun load() = viewModelScope.launch {
        val current = _uiState.value
        _uiState.value = current.copy(
            loading = true,
            appending = false,
            appendError = null,
            error = null,
            success = null
        )

        when (
            val result = repository.getFinance(
                from = current.fromDate.trim().ifBlank { null },
                to = current.toDate.trim().ifBlank { null },
                type = current.typeFilter.ifBlank { null },
                source = current.sourceFilter.ifBlank { null },
                page = 1
            )
        ) {
            is Result.Success -> {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    book = result.data
                )
            }

            is Result.Failure -> {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = result.exception.userMessage()
                )
            }
        }
    }

    /** Memuat halaman berikutnya tanpa menghapus transaksi yang sudah tampil. */
    fun loadNextPage() = viewModelScope.launch {
        val current = _uiState.value
        val book = current.book ?: return@launch
        if (!current.canLoadMore) return@launch

        val nextPage = book.currentPage + 1
        _uiState.value = current.copy(appending = true, appendError = null)

        when (
            val result = repository.getFinance(
                from = current.fromDate.trim().ifBlank { null },
                to = current.toDate.trim().ifBlank { null },
                type = current.typeFilter.ifBlank { null },
                source = current.sourceFilter.ifBlank { null },
                page = nextPage
            )
        ) {
            is Result.Success -> {
                val latest = _uiState.value.book
                val merged = (latest?.transactions.orEmpty() + result.data.transactions)
                    .distinctBy { it.id }

                _uiState.value = _uiState.value.copy(
                    appending = false,
                    appendError = null,
                    book = result.data.copy(transactions = merged)
                )
            }

            is Result.Failure -> {
                _uiState.value = _uiState.value.copy(
                    appending = false,
                    appendError = result.exception.userMessage()
                )
            }
        }
    }

    fun setEntryType(value: String) {
        if (value != "INCOME" && value != "EXPENSE") return
        _uiState.value = _uiState.value.copy(
            entryType = value,
            error = null,
            success = null
        )
    }

    fun setCategory(value: String) {
        _uiState.value = _uiState.value.copy(category = value, error = null)
    }

    fun setAmount(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' }
        val normalized = cleaned.indexOf('.').let { firstDot ->
            if (firstDot < 0) {
                cleaned
            } else {
                cleaned.substring(0, firstDot + 1) +
                    cleaned.substring(firstDot + 1).replace(".", "")
            }
        }
        _uiState.value = _uiState.value.copy(amount = normalized, error = null)
    }

    fun setPaymentMethod(value: String) {
        if (value !in setOf("CASH", "QRIS", "OTHER")) return
        _uiState.value = _uiState.value.copy(paymentMethod = value)
    }

    fun setDescription(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
    }

    fun setFromDate(value: String) {
        _uiState.value = _uiState.value.copy(fromDate = value, error = null)
    }

    fun setToDate(value: String) {
        _uiState.value = _uiState.value.copy(toDate = value, error = null)
    }

    fun setTypeFilter(value: String) {
        if (value !in setOf("", "INCOME", "EXPENSE")) return
        _uiState.value = _uiState.value.copy(typeFilter = value)
        load()
    }

    fun setSourceFilter(value: String) {
        if (value !in setOf("", "MANUAL", "APP_PAYMENT", "DIRECT_ORDER")) return
        _uiState.value = _uiState.value.copy(sourceFilter = value)
        load()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            fromDate = "",
            toDate = "",
            typeFilter = "",
            sourceFilter = ""
        )
        load()
    }

    fun saveTransaction() = viewModelScope.launch {
        val current = _uiState.value
        val amount = current.amount.toBigDecimalOrNull()

        if (current.category.isBlank() || amount == null || amount <= BigDecimal.ZERO) {
            _uiState.value = current.copy(
                error = "Kategori dan nominal wajib diisi dengan nilai lebih besar dari nol.",
                success = null
            )
            return@launch
        }

        val occurredAt = OffsetDateTime.now().toString()
        _uiState.value = current.copy(submitting = true, error = null, success = null)

        val outcome = if (current.entryType == "INCOME") {
            repository.createIncome(
                IncomeDraft(
                    category = current.category.trim(),
                    amount = amount.toPlainString(),
                    paymentMethod = current.paymentMethod,
                    description = current.description,
                    occurredAt = occurredAt
                )
            )
        } else {
            repository.createExpense(
                ExpenseDraft(
                    category = current.category.trim(),
                    amount = amount.toPlainString(),
                    paymentMethod = current.paymentMethod,
                    description = current.description,
                    occurredAt = occurredAt
                )
            )
        }

        when (outcome) {
            is Result.Success -> {
                _uiState.value = _uiState.value.copy(
                    submitting = false,
                    category = "",
                    amount = "",
                    description = "",
                    success = if (current.entryType == "INCOME") {
                        "Pemasukan berhasil dicatat."
                    } else {
                        "Pengeluaran berhasil dicatat."
                    },
                    error = null
                )
                load()
            }

            is Result.Failure -> {
                _uiState.value = _uiState.value.copy(
                    submitting = false,
                    error = outcome.exception.userMessage(),
                    success = null
                )
            }
        }
    }
}
