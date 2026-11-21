package com.kyusui.app.ui.owner.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OwnerOrder
import com.kyusui.app.domain.repository.OwnerRepository
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.toLoadError
import com.kyusui.app.ui.state.toPagedListLoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kyusui.app.core.userMessage

/**
 * Filter status yang dikirim ke backend sebagai query `status` pada
 * `GET /owner/orders`.
 *
 * Hanya status yang bagian lifecycle canonical dari
 * `04_Berkah Water_SYSTEM_WORKFLOW_REBUILT.md` yang ditawarkan.
 * `MENUNGGU_PEMBAYARAN` ikut ditawarkan karena Owner perlu melihat order yang
 * pembayaran belum selesai, bukan menganggap order itu hilang.
 *
 * `ALL` berarti TIDAK mengirim query `status` sama sekali. Nilai `null` pada
 * enum ini karena itu berarti "tanpa filter", bukan "filter status kosong".
 */
enum class OwnerOrderFilter(val status: OrderStatus?, val label: String) {
    ALL(null, "Semua"),
    MENUNGGU_DIPROSES(OrderStatus.MENUNGGU_DIPROSES, "Menunggu Diproses"),
    DIPROSES(OrderStatus.DIPROSES, "Sedang Diproses"),
    DITUGASKAN(OrderStatus.DITUGASKAN, "Ditugaskan"),
    DALAM_PENGANTARAN(OrderStatus.DALAM_PENGANTARAN, "Di Pengiriman"),
    SELESAI(OrderStatus.SELESAI, "Selesai"),
    MENUNGGU_PEMBAYARAN(OrderStatus.MENUNGGU_PEMBAYARAN, "Belum Bayar")
}

/**
 * State daftar order Owner (specification section 10.1).
 */
data class OwnerOrderListUiState(
    val ordersState: LoadState<List<OwnerOrder>> = LoadState.Loading,
    /**
     * Filter status yang dikirim ke backend sebagai query `status`.
     *
     * Ini bukan security boundary. Backend tetap menentukan order mana yang
     * masuk operational scope Owner; filter di sini hanya untuk mengetik
     * navigasi ke OwnerOrderDetailScreen.
     */
    val statusFilter: OrderStatus? = null,
    val page: Int = FIRST_PAGE,
    val hasMore: Boolean = false,
    val isAppending: Boolean = false,
    val appendError: String? = null
) {
    val orders: List<OwnerOrder>
        get() = (ordersState as? LoadState.Success)?.data.orEmpty()

    val isUnauthorized: Boolean
        get() = (ordersState as? LoadState.Error)?.isUnauthorized == true

    /**
     * Filter aktif dalam bentuk enum filter.
     *
     * Penting agar UI bisa membedakan "semua order" dari "filter status tertentu
     * yang kebetulan bernilai null", sehingga tidak mengirim query `status`
     * kosong.
     */
    val activeFilter: OwnerOrderFilter
        get() = OwnerOrderFilter.entries.firstOrNull { it.status == statusFilter }
            ?: OwnerOrderFilter.ALL

    companion object {
        const val FIRST_PAGE = 1
    }
}

/**
 * Daftar order dari `GET /owner/orders`.
 *
 * Order yang diterima dari backend ditampilkan apa adanya. Android tidak
 * memfilter ulang berdasarkan ownership, scope, atau status untuk keperluan
 * keamanan, karena backend sudah menjadi pengaman.
 */
@HiltViewModel
class OwnerOrderListViewModel @Inject constructor(
    private val ownerRepository: OwnerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OwnerOrderListUiState())
    val uiState: StateFlow<OwnerOrderListUiState> = _uiState.asStateFlow()

    init {
        loadOrders()
    }

    fun loadOrders() {
        val filter = _uiState.value.statusFilter

        _uiState.update {
            it.copy(
                ordersState = LoadState.Loading,
                page = OwnerOrderListUiState.FIRST_PAGE,
                hasMore = false,
                isAppending = false,
                appendError = null
            )
        }

        viewModelScope.launch {
            val result = ownerRepository.getOrders(
                page = OwnerOrderListUiState.FIRST_PAGE,
                perPage = PER_PAGE,
                status = filter
            )

            _uiState.update { state ->
                when (result) {
                    is Result.Success -> state.copy(
                        ordersState = result.toPagedListLoadState(EMPTY_ORDERS_MESSAGE),
                        hasMore = result.data.hasMore
                    )
                    is Result.Failure -> state.copy(ordersState = result.toLoadError())
                }
            }
        }
    }

    /**
     * Memuat halaman berikutnya.
     *
     * Halaman yang gagal tidak menghapus order yang sudah tampil, dan
     * `hasMore` dimatikan supaya scroll tidak mengulang request yang sama.
     */
    fun loadNextPage() {
        val current = _uiState.value
        if (!current.hasMore || current.isAppending) return
        if (current.ordersState !is LoadState.Success) return

        val nextPage = current.page + 1
        _uiState.update { it.copy(isAppending = true, appendError = null) }

        viewModelScope.launch {
            val result = ownerRepository.getOrders(
                page = nextPage,
                perPage = PER_PAGE,
                status = current.statusFilter
            )

            _uiState.update { state ->
                when (result) {
                    is Result.Success -> {
                        val merged = (state.orders + result.data.data).distinctBy { it.id }
                        state.copy(
                            ordersState = LoadState.Success(merged),
                            page = nextPage,
                            hasMore = result.data.hasMore,
                            isAppending = false
                        )
                    }
                    is Result.Failure -> state.copy(
                        // Hentikan pemanggilan otomatis/berulang setelah halaman gagal.
                        // Tombol retry dapat mengaktifkan kembali pemuatan secara eksplisit.
                        hasMore = false,
                        isAppending = false,
                        appendError = result.exception.userMessage()
                    )
                }
            }
        }
    }

    /** Mengulang request halaman yang gagal tanpa menghapus daftar yang sudah tampil. */
    fun retryNextPage() {
        val current = _uiState.value
        if (current.appendError == null || current.isAppending) return

        // Retry hanya dilakukan atas tindakan eksplisit pengguna.
        // Aktifkan kembali hasMore sebelum memanggil loadNextPage().
        _uiState.update {
            it.copy(
                hasMore = true,
                appendError = null
            )
        }
        loadNextPage()
    }

    /**
     * Mengubah filter status.
     *
     * Filter dikirim sebagai query ke backend. Nilai `null` berarti backend
     * mengembalikan seluruh order dalam scope Owner, bukan berarti Android
     * memfilter semua order sendiri.
     */
    fun setStatusFilter(filter: OwnerOrderFilter) {
        _uiState.update { it.copy(statusFilter = filter.status) }
        loadOrders()
    }

    companion object {
        const val PER_PAGE = 20
        const val EMPTY_ORDERS_MESSAGE = "Belum ada pesanan masuk."
    }
}
