package com.kyusui.app.ui.owner.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.CourierSummary
import com.kyusui.app.domain.model.OrderAssignment
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OwnerOrder
import com.kyusui.app.domain.repository.OwnerRepository
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.SubmitState
import com.kyusui.app.ui.state.toLoadError
import com.kyusui.app.ui.state.toSubmitError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detail order Owner dari `GET /owner/orders/{order}` (specification 10.2),
 * beserta aksi `PATCH /owner/orders/{order}/status` (10.3) dan
 * `POST /owner/orders/{order}/assignment` (10.4).
 *
 * Prinsip yang dijaga di ViewModel ini:
 *
 * - Android hanya MENGIRIM aksi. Status pada [OwnerOrder] selalu disalin dari
 *   response backend, baik setelah load maupun setelah aksi. Tidak ada satu pun
 *   jalur yang menulis `OrderStatus.DIPROSES` atau status lain secara lokal,
 *   karena backend yang berwenang atas validasi transition.
 * - Aksi yang sedang berjalan disimpan sebagai [SubmitState.Submitting] sehingga
 *   tombol kedua tidak bisa dikirim ulang untuk request yang sama.
 * - `409` dan `422` dari backend ditampilkan apa adanya. Transition yang tidak
 *   valid adalah keputusan backend, bukan kondisi yang Android perbaiki diam-diam.
 */
@HiltViewModel
class OwnerOrderDetailViewModel @Inject constructor(
    private val ownerRepository: OwnerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val orderId: String = savedStateHandle.get<String>(ARG_ORDER_ID).orEmpty()

    private val _uiState = MutableStateFlow(OwnerOrderDetailUiState())
    val uiState: StateFlow<OwnerOrderDetailUiState> = _uiState.asStateFlow()

    init {
        loadOrder()
    }

    fun loadOrder() {
        if (orderId.isBlank()) {
            _uiState.update {
                it.copy(
                    orderState = LoadState.Error(
                        message = MISSING_ORDER_ID_MESSAGE,
                        isRetryable = false
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                orderState = LoadState.Loading,
                couriersState = LoadState.Loading,
                // Aksi lama dibatalkan supaya Owner tidak melihat tombol yang
                // masih ter-disable untuk request yang sudah tidak berlaku.
                statusSubmitState = SubmitState.Idle,
                assignmentSubmitState = SubmitState.Idle
            )
        }

        viewModelScope.launch {
            when (val result = ownerRepository.getOrder(orderId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(orderState = LoadState.Success(result.data))
                    }
                    // Daftar kurir hanya relevan saat order memang dapat
                    // ditugaskan; memuatnya lebih awal hanya membuang jaringan.
                    if (result.data.canRequestCourierAssignment) {
                        loadCouriers()
                    }
                }

                is Result.Failure -> _uiState.update {
                    it.copy(orderState = result.toLoadError())
                }
            }
        }
    }

    /**
     * Memuat daftar kurir tersedia dari `GET /owner/couriers`.
     *
     * Backend sudah menyaring kurir yang sedang mengantarkan order lain, jadi
     * Android menampilkan hasilnya apa adanya. Daftar kosong menjadi
     * [LoadState.Empty] supaya UI menjelaskan keadaannya, bukan menampilkan
     * kegagalan.
     */
    fun loadCouriers() {
        _uiState.update { it.copy(couriersState = LoadState.Loading) }

        viewModelScope.launch {
            when (val result = ownerRepository.getCouriers()) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        couriersState = if (result.data.isEmpty()) {
                            LoadState.Empty(EMPTY_COURIERS_MESSAGE)
                        } else {
                            LoadState.Success(result.data)
                        }
                    )
                }

                is Result.Failure -> _uiState.update {
                    it.copy(couriersState = result.toLoadError())
                }
            }
        }
    }

    /**
     * Meminta perubahan status order.
     *
     * Hanya [OrderStatus] dari [OwnerOrder.availableStatusActions] yang bisa
     * dikirim. Pemeriksaan itu hanya menjaga tombol UI; backend tetap
     * memvalidasi transition dan tetap boleh menolak dengan `409`/`422`.
     */
    fun updateStatus(nextStatus: OrderStatus, note: String? = null) {
        val current = _uiState.value

        // Id order kosong berarti tidak ada request yang boleh dikirim.
        if (orderId.isBlank()) return

        // Mounted guard: aksi sebelumnya masih berjalan. Ini yang mencegah
        // double submit ketika Owner menekan tombol dua kali.
        if (current.statusSubmitState is SubmitState.Submitting) return

        val order = current.order
        // Status yang tidak sedang tampil tidak boleh jadi dasar aksi.
        if (order == null) return
        if (nextStatus !in order.availableStatusActions) return

        _uiState.update { it.copy(statusSubmitState = SubmitState.Submitting) }

        viewModelScope.launch {
            when (
                val result = ownerRepository.updateOrderStatus(
                    orderId = orderId,
                    status = nextStatus,
                    note = note
                )
            ) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        // Order hasil server yang menggantikan order lama. Status
                    // di dalamnya apa adanya apa yang backend kirim.
                        orderState = LoadState.Success(result.data),
                        statusSubmitState = SubmitState.Success(orderId = orderId),
                        statusMessage = statusSuccessMessage(result.data.orderStatus)
                    )
                }

                is Result.Failure -> _uiState.update {
                    it.copy(statusSubmitState = result.toSubmitError())
                }
            }
        }
    }

    /**
     * Menugaskan kurir.
     *
     * Hanya `courier_id` yang dikirim sesuai section 10.4. `courierId` berasal
     * dari [OwnerOrderDetailUiState.couriers], yaitu daftar yang dimuat backend
     * lewat `GET /owner/couriers`. Backend tetap memvalidasi keberadaan kurir,
     * availability, order yang dapat ditugaskan, dan konflik assignment aktif;
     * Android tidak memvalidasi kurir secara lokal dan menampilkan penolakan
     * backend apa adanya.
     */
    fun assignCourier(courierId: String) {
        val current = _uiState.value

        if (orderId.isBlank()) return
        if (current.assignmentSubmitState is SubmitState.Submitting) return

        val order = current.order ?: return
        if (!order.canRequestCourierAssignment) return

        val trimmed = courierId.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    assignmentSubmitState = SubmitState.Error(
                        message = EMPTY_COURIER_ID_MESSAGE,
                        isUnauthorized = false,
                        isRetryable = true
                    )
                )
            }
            return
        }

        _uiState.update { it.copy(assignmentSubmitState = SubmitState.Submitting) }

        viewModelScope.launch {
            val result = ownerRepository.assignCourier(
                orderId = orderId,
                courierId = trimmed
            )

            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        // Assignment hanya ditampilkan bila backend mengirim
                        // `courier_id`. Kalau tidak, assignment tetap null dan
                        // Owner bisa mencoba lagi.
                        orderState = LoadState.Success(result.data),
                        assignmentSubmitState = SubmitState.Success(orderId = orderId),
                        assignmentMessage = assignmentSuccessMessage(result.data.assignment)
                    )
                }

                is Result.Failure -> _uiState.update {
                    it.copy(assignmentSubmitState = result.toSubmitError())
                }
            }
        }
    }

    /**
     * Menghapus pesan aksi setelah ditampilkan agar Owner tidak melihat
     * penjelasan yang sama lagi saat membuka ulang layar.
     */
    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null, statusSubmitState = SubmitState.Idle) }
    }

    fun clearAssignmentMessage() {
        _uiState.update {
            it.copy(assignmentMessage = null, assignmentSubmitState = SubmitState.Idle)
        }
    }

    companion object {
        const val ARG_ORDER_ID = "orderId"
        const val MISSING_ORDER_ID_MESSAGE = "Nomor pesanan tidak tersedia."
        const val EMPTY_COURIER_ID_MESSAGE = "Pilih kurir yang akan ditugaskan."
        const val EMPTY_COURIERS_MESSAGE = "Tidak ada kurir yang tersedia saat ini."

        private fun statusSuccessMessage(status: OrderStatus?): String =
            when (status) {
                OrderStatus.DIPROSES -> "Pesanan sedang diproses."
                OrderStatus.DITUGASKAN -> "Pesanan ditandai sudah ditugaskan."
                OrderStatus.DALAM_PENGANTARAN -> "Pesanan sedang diantar."
                OrderStatus.SELESAI -> "Pesanan ditandai selesai."
                OrderStatus.MENUNGGU_DIPROSES -> "Status pesanan menunggu diproses."
                OrderStatus.MENUNGGU_PEMBAYARAN -> "Pesanan masih menunggu pembayaran."
                OrderStatus.DIBATALKAN -> "Pesanan dibatalkan oleh customer."
                // Status tidak dikenal tidak diterjemahkan menjadi status lain.
                null -> "Status pesanan diperbarui oleh server."
            }

        private fun assignmentSuccessMessage(
            assignment: OrderAssignment?
        ): String =
            if (assignment != null) {
                "Kurir #${assignment.courierId} berhasil ditugaskan."
            } else {
                "Penugasan terkirim. Data penugasan dimuat ulang dari server."
            }
    }
}

/**
 * State layar detail order Owner.
 *
 * [statusSubmitState] dan [assignmentSubmitState] sengaja dipisah karena dua
 * aksi tersebut berbeda dan bisa gagal secara independen.
 */
data class OwnerOrderDetailUiState(
    val orderState: LoadState<OwnerOrder> = LoadState.Loading,
    val couriersState: LoadState<List<CourierSummary>> = LoadState.Loading,
    val statusSubmitState: SubmitState = SubmitState.Idle,
    val assignmentSubmitState: SubmitState = SubmitState.Idle,
    val statusMessage: String? = null,
    val assignmentMessage: String? = null
) {
    val order: OwnerOrder?
        get() = (orderState as? LoadState.Success)?.data

    val couriers: List<CourierSummary>
        get() = (couriersState as? LoadState.Success)?.data.orEmpty()

    val isUpdatingStatus: Boolean
        get() = statusSubmitState is SubmitState.Submitting

    val isAssigningCourier: Boolean
        get() = assignmentSubmitState is SubmitState.Submitting

    val statusErrorMessage: String?
        get() = (statusSubmitState as? SubmitState.Error)?.message

    val assignmentErrorMessage: String?
        get() = (assignmentSubmitState as? SubmitState.Error)?.message
}