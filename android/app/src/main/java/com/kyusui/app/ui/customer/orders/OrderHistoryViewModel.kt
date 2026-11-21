package com.kyusui.app.ui.customer.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.Order
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.repository.CustomerRepository
import com.kyusui.app.ui.state.toLoadError
import com.kyusui.app.ui.state.toPagedListLoadState
import com.kyusui.app.ui.state.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Riwayat order customer dari `GET /customer/orders`.
 *
 * Backend menerima `order_status` berupa satu nilai pasti, beberapa nilai
 * dipisah koma, maupun array. Android memilih tab dan mengirim gabungan status
 * yang tepat, sehingga "Aktif" (semua status selain `SELESAI`) benar-benar
 * disaring server-side. Sebelumnya grouping dilakukan pada data yang sudah
 * dimuat; cara itu salah karena halaman pertama bisa saja berisi order
 * `SELESAI` saja sehingga tab "Aktif" tampak kosong.
 */
data class OrderHistoryUiState(
    val ordersState: LoadState<List<Order>> = LoadState.Loading,
    val statusFilter: OrderStatusFilter = OrderStatusFilter.ALL,
    val allOrders: List<Order> = emptyList(),
    val page: Int = FIRST_PAGE,
    val hasMore: Boolean = false,
    val isAppending: Boolean = false,
    /**
     * Kegagalan saat memuat halaman berikutnya. Order yang sudah tampil tidak
     * dihapus; customer hanya diberi tahu dan bisa mencoba lagi.
     */
    val appendErrorMessage: String? = null
) {
    val isUnauthorized: Boolean
        get() = (ordersState as? LoadState.Error)?.isUnauthorized == true

    /**
     * Order yang sudah disaring server untuk [statusFilter]. Tidak ada
     * penyaringan ulang di sisi aplikasi, karena hasil server adalah
     * kebenarannya.
     */
    val filteredOrders: List<Order>
        get() = allOrders

    /**
     * Pesan kosong memakai filter yang sedang aktif supaya customer tahu bahwa
     * tidak ada order pada tab itu, bukan tidak ada order sama sekali.
     */
    val emptyMessage: String
        get() = when {
            allOrders.isNotEmpty() -> OrderHistoryViewModel.EMPTY_ORDERS_MESSAGE
            statusFilter == OrderStatusFilter.ACTIVE -> EMPTY_ACTIVE_ORDERS_MESSAGE
            statusFilter == OrderStatusFilter.COMPLETED -> EMPTY_COMPLETED_ORDERS_MESSAGE
            else -> OrderHistoryViewModel.EMPTY_ORDERS_MESSAGE
        }

    companion object {
        const val FIRST_PAGE = 1
    }
}

private const val EMPTY_ACTIVE_ORDERS_MESSAGE = "Tidak ada order yang sedang aktif."
private const val EMPTY_COMPLETED_ORDERS_MESSAGE = "Belum ada order yang selesai."

enum class OrderStatusFilter {
    ALL,
    ACTIVE,
    COMPLETED;

    fun label(): String = when (this) {
        ALL -> "Semua"
        ACTIVE -> "Aktif"
        COMPLETED -> "Selesai"
    }

    /**
     * Status pasti yang dikirim ke backend.
     *
     * `SELESAI` adalah satu-satunya status terminal, jadi "Aktif" adalah semua
     * status lain — termasuk status yang belum dikenal sekalipun tetap
     * diekspresikan lewat daftar status yang dikenal, bukan dengan menebak.
     */
    fun statuses(): List<OrderStatus> = when (this) {
        ALL -> emptyList()
        ACTIVE -> OrderStatus.values().filter { it != OrderStatus.SELESAI }
        COMPLETED -> listOf(OrderStatus.SELESAI)
    }
}

@HiltViewModel
class OrderHistoryViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderHistoryUiState())
    val uiState: StateFlow<OrderHistoryUiState> = _uiState.asStateFlow()

    init {
        loadOrders()
    }

    fun loadOrders() {
        val statuses = _uiState.value.statusFilter.statuses()

        _uiState.update {
            it.copy(
                ordersState = LoadState.Loading,
                allOrders = emptyList(),
                page = OrderHistoryUiState.FIRST_PAGE,
                hasMore = false,
                isAppending = false,
                appendErrorMessage = null
            )
        }

        viewModelScope.launch {
            val result = customerRepository.getOrders(
                page = OrderHistoryUiState.FIRST_PAGE,
                perPage = PER_PAGE,
                statuses = statuses
            )

            _uiState.update { state ->
                when (result) {
                    is Result.Success -> state.copy(
                        ordersState = result.toPagedListLoadState(EMPTY_ORDERS_MESSAGE),
                        allOrders = result.data.data,
                        hasMore = result.data.hasMore
                    )
                    is Result.Failure -> state.copy(ordersState = result.toLoadError())
                }
            }
        }
    }

    /**
     * Memuat halaman order berikutnya.
     *
     * Halaman yang gagal tidak menghapus order yang sudah tampil. `hasMore`
     * tetap aktif dan kegagalannya disimpan di `appendErrorMessage` supaya
     * customer bisa mencoba lagi secara eksplisit (bukan retry otomatis saat
     * menggulir).
     */
    fun loadNextPage() {
        val current = _uiState.value
        if (!current.hasMore || current.isAppending) return
        if (current.ordersState !is LoadState.Success) return

        val nextPage = current.page + 1
        _uiState.update { it.copy(isAppending = true, appendErrorMessage = null) }

        viewModelScope.launch {
            val result = customerRepository.getOrders(
                page = nextPage,
                perPage = PER_PAGE,
                statuses = current.statusFilter.statuses()
            )

            _uiState.update { state ->
                when (result) {
                    is Result.Success -> {
                        val merged = (state.allOrders + result.data.data).distinctBy { it.id }
                        state.copy(
                            allOrders = merged,
                            ordersState = LoadState.Success(merged),
                            page = nextPage,
                            hasMore = result.data.hasMore,
                            isAppending = false,
                            appendErrorMessage = null
                        )
                    }
                    is Result.Failure -> state.copy(
                        isAppending = false,
                        appendErrorMessage = result.toLoadError().message
                    )
                }
            }
        }
    }

    /**
     * Mengganti tab filter. Karena penyaringan dilakukan server-side, tab yang
     * berbeda benar-benar memuat ulang dari halaman pertama; kalau tidak,
     * "Aktif" hanya akan menyaring halaman yang kebetulan sudah dimuat.
     */
    fun setStatusFilter(filter: OrderStatusFilter) {
        if (_uiState.value.statusFilter == filter) return
        _uiState.update { it.copy(statusFilter = filter) }
        loadOrders()
    }

    companion object {
        const val PER_PAGE = 50
        const val EMPTY_ORDERS_MESSAGE = "Belum ada riwayat order."
    }
}