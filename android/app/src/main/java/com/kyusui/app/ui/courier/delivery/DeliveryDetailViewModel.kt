package com.kyusui.app.ui.courier.delivery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.repository.CourierRepository
import com.kyusui.app.ui.courier.tracking.CourierLocationSharing
import com.kyusui.app.ui.courier.tracking.TrackingStopReason
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
 * Detail pengantaran dari `GET courier/assignments/{assignment}`.
 *
 * Layar ini memegang aksi Mulai, Kirim Lokasi, dan Selesaikan. Konfirmasi tunai
 * punya layar sendiri di `ui.courier.payment` karena punya alur dan risksinya
 * sendiri, tapi tetap memakai Assignment Resource yang sama.
 *
 * Seluruh aksi mengikuti aturan yang sama:
 *
 * - Android hanya MENGIRIM aksi. [startDelivery] dan [completeDelivery] tidak
 *   menulis status apa pun; Assignment Resource yang dikembalikan backend-lah
 *   yang menggantikan data di layar.
 * - Otorisasi sepenuhnya milik backend. Urutan yang diperiksa backend adalah
 *   kurir terautentikasi, memiliki assignment tersebut, order milik assignment
 *   tersebut, delivery aktif, lalu validitas aksi. Android tidak meniru
 *   pemeriksaan itu sebagai sumber kebenaran; pemeriksaan lokal di sini hanya
 *   menentukan tombol mana yang ditampilkan.
 * - Setiap aksi punya [SubmitState] sendiri sehingga aksi yang gagal tidak
 *   mematikan aksi lain, dan `Submitting` menjadi guard double submit.
 * - `403` dan `409` dari backend ditampilkan apa adanya. Penolakan itu
 *   keputusan backend, bukan kondisi yang Android perbaiki diam-diam.
 */
@HiltViewModel
class DeliveryDetailViewModel @Inject constructor(
    private val courierRepository: CourierRepository,
    private val sharing: CourierLocationSharing,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val assignmentId: String =
        savedStateHandle.get<String>(ARG_ASSIGNMENT_ID).orEmpty()

    private val _uiState = MutableStateFlow(DeliveryDetailUiState())
    val uiState: StateFlow<DeliveryDetailUiState> = _uiState.asStateFlow()

    init {
        loadDelivery()
    }

    fun loadDelivery() {
        if (assignmentId.isBlank()) {
            _uiState.update {
                it.copy(
                    deliveryState = LoadState.Error(
                        message = MISSING_ASSIGNMENT_ID_MESSAGE,
                        isRetryable = false
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                deliveryState = LoadState.Loading,
                // Aksi lama dibatalkan supaya tombol tidak tetap ter-disable untuk
                // request yang sudah tidak berlaku setelah muat ulang.
                startState = SubmitState.Idle,
                completeState = SubmitState.Idle,
                locationState = SubmitState.Idle,
                actionMessage = null
            )
        }

        viewModelScope.launch {
            when (val result = courierRepository.getAssignment(assignmentId)) {
                is Result.Success -> _uiState.update {
                    it.copy(deliveryState = LoadState.Success(result.data))
                }

                is Result.Failure -> _uiState.update {
                    it.copy(deliveryState = result.toLoadError())
                }
            }
        }
    }

    /**
     * Aksi Mulai Pengantaran.
     *
     * Tombol hanya muncul saat status assignment `ASSIGNED`. Syarat lain yang
     * diperiksa backend, yaitu order sudah `DITUGASKAN` dan kurir tidak sedang
     * punya pengantaran aktif lain, tidak bisa dicek dari resource ini dan tetap
     * diserahkan ke backend.
     */
    fun startDelivery() {
        val current = _uiState.value
        if (assignmentId.isBlank()) return
        if (current.startState is SubmitState.Submitting) return

        val delivery = current.delivery ?: return
        if (!delivery.canStartDelivery) return

        _uiState.update {
            it.copy(startState = SubmitState.Submitting, actionMessage = null)
        }

        viewModelScope.launch {
            when (val result = courierRepository.startDelivery(assignmentId)) {
                is Result.Success -> _uiState.update { state ->
                    state.copy(
                        // Status assignment apa adanya dari backend. Tidak ada
                        // cabang yang menulis `ACTIVE` secara lokal.
                        deliveryState = LoadState.Success(result.data),
                        startState = SubmitState.Success(assignmentId),
                        lastReportedLocation = result.data.latestLocation
                            ?: state.lastReportedLocation,
                        actionMessage = START_SUCCESS_MESSAGE
                    )
                }

                is Result.Failure -> _uiState.update {
                    it.copy(startState = result.toSubmitError())
                }
            }
        }
    }

    /**
     * Aksi Selesaikan Pengantaran.
     *
     * Backend menolak dengan `409` bila delivery belum aktif atau pembayaran
     * belum lunas, sehingga untuk pembayaran `CASH` kurir harus lebih dulu
     * mengonfirmasi uang diterima.
     */
    fun completeDelivery() {
        val current = _uiState.value
        if (assignmentId.isBlank()) return
        if (current.completeState is SubmitState.Submitting) return

        val delivery = current.delivery ?: return
        if (!delivery.canCompleteDelivery) return

        _uiState.update {
            it.copy(completeState = SubmitState.Submitting, actionMessage = null)
        }

        viewModelScope.launch {
            when (val result = courierRepository.completeDelivery(assignmentId)) {
                is Result.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            deliveryState = LoadState.Success(result.data),
                            completeState = SubmitState.Success(assignmentId),
                            lastReportedLocation = result.data.latestLocation
                                ?: state.lastReportedLocation,
                            actionMessage = COMPLETE_SUCCESS_MESSAGE
                        )
                    }
                    // Delivery selesai = tidak ada lagi pengiriman posisi yang sah
                    // untuk assignment ini. Penghentian dijalankan controller:
                    // service latar belakang harus mati bersama delivery.
                    sharing.stop(TrackingStopReason.DELIVERY_NOT_ACTIVE)
                }

                is Result.Failure -> _uiState.update {
                    it.copy(completeState = result.toSubmitError())
                }
            }
        }
    }

    /**
     * Mengirim posisi kurir.
     *
     * [fix] adalah pembacaan dari perangkat, bukan titik tujuan pelanggan.
     * Backend yang memeriksa apakah delivery aktif, dan yang menolak dengan `422`
     * bila perpindahan lokasi tidak masuk akal.
     */
    fun postLocation(fix: CourierLocationFix) {
        val current = _uiState.value
        if (assignmentId.isBlank()) return
        if (current.locationState is SubmitState.Submitting) return

        val delivery = current.delivery ?: return
        if (!delivery.canPostLocation) return

        _uiState.update {
            it.copy(locationState = SubmitState.Submitting, actionMessage = null)
        }

        viewModelScope.launch {
            when (val result = courierRepository.updateLocation(assignmentId, fix)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        locationState = SubmitState.Success(assignmentId),
                        // Lokasi yang baru saja tercatat server langsung
                        // ditampilkan. Nilainya milik backend, bukan perkiraan
                        // Android, dan tidak pernah disimulasikan bila gagal.
                        lastReportedLocation = result.data,
                        actionMessage = LOCATION_SUCCESS_MESSAGE
                    )
                }

                is Result.Failure -> _uiState.update {
                    it.copy(locationState = result.toSubmitError())
                }
            }
        }
    }

    /**
     * Menghapus umpan balik aksi setelah ditampilkan.
     *
     * Semua `SubmitState` dikembalikan ke `Idle` supaya masuk lagi ke layar tidak
     * memunculkan pesan yang sama tanpa aksi baru.
     */
    fun clearActionMessage() {
        _uiState.update {
            it.copy(
                actionMessage = null,
                startState = SubmitState.Idle,
                completeState = SubmitState.Idle,
                locationState = SubmitState.Idle
            )
        }
    }

    companion object {
        const val ARG_ASSIGNMENT_ID = "assignmentId"
        const val MISSING_ASSIGNMENT_ID_MESSAGE = "Nomor penugasan tidak tersedia."
        const val START_SUCCESS_MESSAGE = "Pengantaran dimulai."
        const val COMPLETE_SUCCESS_MESSAGE = "Pengantaran berhasil diselesaikan."
        const val LOCATION_SUCCESS_MESSAGE = "Lokasi berhasil dikirim."

        /**
         * Pesan setelah konfirmasi tunai.
         *
         * Status pembayaran di sini dibaca dari response backend. Android tidak
         * pernah menyatakan pembayaran sudah lunas sebelum backend mengatakannya.
         */
        fun cashSuccessMessage(paymentStatus: PaymentStatus?): String = when (paymentStatus) {
            PaymentStatus.PAID -> "Uang diterima. Pembayaran ditandai lunas oleh server."
            // Status lain berarti server tidak menyatakan lunas. Nilainya
            // ditampilkan apa adanya dan tidak diterjemahkan menjadi lunas.
            else -> "Konfirmasi tunai terkirim. Status pembayaran: ${displayStatus(paymentStatus)}."
        }

        private fun displayStatus(paymentStatus: PaymentStatus?): String =
            paymentStatus?.displayName ?: "tidak diketahui"
    }
}

/**
 * State detail pengantaran.
 *
 * [startState], [completeState], dan [locationState] dipisah karena aksi-aksinya
 * berbeda dan bisa gagal secara independen: satu lokasi yang gagal dikirim tidak
 * boleh membatalkan kemampuan kurir menyelesaikan pengantaran.
 */
data class DeliveryDetailUiState(
    val deliveryState: LoadState<CourierDelivery> = LoadState.Loading,
    val startState: SubmitState = SubmitState.Idle,
    val completeState: SubmitState = SubmitState.Idle,
    val locationState: SubmitState = SubmitState.Idle,
    val lastReportedLocation: CourierDeviceLocation? = null,
    val actionMessage: String? = null
) {
    val delivery: CourierDelivery?
        get() = (deliveryState as? LoadState.Success)?.data

    val isStarting: Boolean get() = startState is SubmitState.Submitting
    val isCompleting: Boolean get() = completeState is SubmitState.Submitting
    val isPostingLocation: Boolean get() = locationState is SubmitState.Submitting

    val startErrorMessage: String? get() = (startState as? SubmitState.Error)?.message
    val completeErrorMessage: String? get() = (completeState as? SubmitState.Error)?.message
    val locationErrorMessage: String? get() = (locationState as? SubmitState.Error)?.message

    /**
     * Lokasi terakhir yang diketahui.
     *
     * [lastReportedLocation] diisi dari response aksi kirim lokasi, jadi nilainya
     * selalu data server, bukan pembacaan lokal perangkat.
     */
    val latestLocation: CourierDeviceLocation?
        get() = lastReportedLocation ?: delivery?.latestLocation

    /** Pengantaran selesai berarti backend sudah menandai assignment `COMPLETED`. */
    val isCompleted: Boolean
        get() = delivery?.status == AssignmentStatus.COMPLETED

    /** True ketika ada satu pun aksi yang sedang berjalan. */
    val isBusy: Boolean
        get() = isStarting || isCompleting || isPostingLocation
}