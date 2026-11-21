package com.kyusui.app.ui.customer.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.Order
import com.kyusui.app.domain.repository.CustomerRepository
import com.kyusui.app.ui.state.toLoadError
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.SubmitState
import com.kyusui.app.ui.state.toSubmitError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detail order customer dari `GET /customer/orders/{order}`.
 *
 * Semua nominal dan status order berasal dari backend.
 */
@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val orderId: String = savedStateHandle.get<String>(ARG_ORDER_ID).orEmpty()

    private val _uiState = MutableStateFlow<LoadState<Order>>(LoadState.Loading)
    val uiState: StateFlow<LoadState<Order>> = _uiState.asStateFlow()

    /**
     * State aksi pembatalan. Dipisah dari [uiState] supaya kegagalan memuat
     * detail order tidak tercampur dengan kegagalan aksi pembatalan.
     */
    private val _cancelState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val cancelState: StateFlow<SubmitState> = _cancelState.asStateFlow()

    init {
        loadOrder()
    }

    fun loadOrder() {
        if (orderId.isBlank()) {
            _uiState.value = LoadState.Error(
                message = "Order tidak ditemukan.",
                isRetryable = false
            )
            return
        }

        _uiState.value = LoadState.Loading

        viewModelScope.launch {
            when (val result = customerRepository.getOrder(orderId)) {
                is Result.Success -> _uiState.value = LoadState.Success(result.data)
                is Result.Failure -> _uiState.value = result.toLoadError()
            }
        }
    }

    /**
     * Membatalkan order lewat `POST /customer/orders/{order}/cancel`.
     *
     * Tombol hanya tersedia untuk order yang masih `MENUNGGU_PEMBAYARAN`.
     * Backend tetap sumber kebenaran: `409` ditampilkan sebagai kegagalan, dan
     * status order baru diambil dari payload response, tidak ditulis lokal.
     */
    fun cancelOrder() {
        if (orderId.isBlank()) return
        if (_cancelState.value is SubmitState.Submitting) return

        val order = (_uiState.value as? LoadState.Success)?.data ?: return
        if (!order.canBeCancelled) return

        _cancelState.value = SubmitState.Submitting

        viewModelScope.launch {
            when (val result = customerRepository.cancelOrder(orderId)) {
                is Result.Success -> {
                    // Status `DIBATALKAN` berasal dari response backend.
                    _uiState.value = LoadState.Success(result.data)
                    _cancelState.value = SubmitState.Success(result.data.id)
                }
                is Result.Failure -> _cancelState.value = result.toSubmitError()
            }
        }
    }

    /** Membersihkan pesan pembatalan setelah ditampilkan. */
    fun clearCancelState() {
        _cancelState.value = SubmitState.Idle
    }

    companion object {
        const val ARG_ORDER_ID = "orderId"
    }
}