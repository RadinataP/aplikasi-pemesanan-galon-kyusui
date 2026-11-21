package com.kyusui.app.ui.customer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.repository.CustomerRepository
import com.kyusui.app.domain.model.Order
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.toLoadError
import com.kyusui.app.ui.state.toPagedListLoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State beranda customer.
 *
 * Hanya ringkasan order yang dibutuhkan beranda. Data order berasal dari
 * `GET /customer/orders`, bukan data contoh, sehingga kartu "Pesanan Aktif"
 * tidak pernah menampilkan transaksi yang tidak ada.
 */
data class CustomerHomeUiState(
    val ordersState: LoadState<List<Order>> = LoadState.Loading
) {
    /**
     * Order yang belum selesai. `SELESAI` adalah satu-satunya status terminal,
     * jadi status yang tidak dikenal tetap dihitung aktif supaya customer tidak
     * kehilangan order yang belum tuntas.
     */
    val activeOrders: List<Order>
        get() = (ordersState as? LoadState.Success)?.data
            ?.filter { it.orderStatus != OrderStatus.SELESAI }
            .orEmpty()

    /** True bila ada order yang posisi kurirnya masih bisa dilacak. */
    val hasActiveDelivery: Boolean
        get() = trackableOrderId != null

    /** Order pertama yang statusnya memungkinkan pelacakan, bila ada. */
    val trackableOrderId: String?
        get() = activeOrders
            .firstOrNull { it.orderStatus?.isTrackable() == true }
            ?.id

    companion object {
        const val EMPTY_MESSAGE = "Belum ada pesanan."
    }
}

/**
 * Beranda customer: memuat ringkasan order dari backend.
 *
 * Tidak ada aksi tulis di sini. Pemuatan ulang dipakai oleh tombol "Coba Lagi"
 * saat `GET /customer/orders` gagal.
 */
@HiltViewModel
class CustomerHomeViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerHomeUiState())
    val uiState: StateFlow<CustomerHomeUiState> = _uiState.asStateFlow()

    init {
        loadHome()
    }

    fun loadHome() {
        _uiState.value = CustomerHomeUiState(ordersState = LoadState.Loading)

        viewModelScope.launch {
            val result = customerRepository.getOrders(
                page = FIRST_PAGE,
                perPage = PER_PAGE
            )
            _uiState.value = CustomerHomeUiState(
                ordersState = when (result) {
                    is Result.Success ->
                        result.toPagedListLoadState(CustomerHomeUiState.EMPTY_MESSAGE)
                    is Result.Failure -> result.toLoadError()
                }
            )
        }
    }

    companion object {
        const val FIRST_PAGE = 1
        const val PER_PAGE = 20
    }
}
