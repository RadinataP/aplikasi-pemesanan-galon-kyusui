package com.kyusui.app.ui.customer.tracking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.core.TrackingSchedule
import com.kyusui.app.domain.model.Order
import com.kyusui.app.domain.model.OrderTracking
import com.kyusui.app.domain.model.OrderTrackingResult
import com.kyusui.app.domain.repository.CustomerRepository
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.toLoadError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tracking sisi customer untuk satu order miliknya sendiri.
 *
 * ## Dua pemanggilan, bukan satu
 *
 * `GET orders/{order}/tracking` tidak memuat status order. Backend hanya
 * mengembalikan assignment aktif beserta posisinya. Status pengiriman canonical
 * tetap dibaca dari `GET orders/{order}` yang juga memberi alamat tujuan.
 * Kedua endpoint dilindungi kepemilikan yang sama di backend, jadi
 * menggabungkannya tidak membuka data milik orang lain.
 *
 * Keadaan "tidak ada pengiriman aktif" bukan kegagalan. Backend menjawab
 * `data: null` ketika tidak ada assignment berstatus `ACTIVE`, dan itu muncul
 * sebagai [LoadState.Empty], bukan [LoadState.Error], supaya pengguna
 * membedakan tracking yang selesai dari jaringan yang putus.
 *
 * ## Interval
 *
 * Interval polling datang dari [TrackingSchedule], yang nilainya merujuk ke
 * [com.kyusui.app.core.TrackingIntervals] dan ditandai belum dikunci oleh
 * specification. Angka interval tidak ditulis di file ini. Test mengoper nilai
 * sendiri sehingga tidak bergantung pada angka produksi.
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val trackingSchedule: TrackingSchedule,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val orderId: String =
        savedStateHandle.get<String>(ARG_ORDER_ID).orEmpty()

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    /**
     * True begitu backend pernah melaporkan assignment `ACTIVE` di layar ini.
     *
     * Dipakai untuk membedakan "belum ada pengiriman" (order masih menunggu
     * kurir, jadi polling diteruskan) dari "pengiriman yang tadi berjalan sudah
     * berakhir" (tracking berhenti dan polling dihentikan).
     */
    private var hasSeenActiveTracking = false

    init {
        load()
    }

    /**
     * Memuat order dan tracking sekali, lalu menyalakan polling.
     *
     * Polling hanya berjalan saat layar terlihat. Android membatalkan
     * [pollingJob] saat ViewModel dibuang, dan layar memanggil [stopPolling]
     * ketika lifecycle-nya berhenti.
     */
    fun load() {
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
            it.copy(orderState = LoadState.Loading, refreshErrorMessage = null)
        }

        viewModelScope.launch {
            refreshTracking(isPolling = false)
            refreshOrder()
            // Order yang sudah final tidak punya assignment aktif lagi, jadi
            // polling tidak perlu dinyalakan.
            if (isOrderTerminal()) stopPolling()
        }
        startPolling()
    }

    /**
     * Memuat ulang secara manual.
     *
     * Mematikan polling lebih dulu supaya permintaan manual tidak menumpuk
     * dengan permintaan polling yang sedang berjalan dan menghabiskan kuota
     * `throttle:5,1`.
     */
    fun refresh() {
        pollingJob?.cancel()
        if (orderId.isBlank()) return

        _uiState.update { it.copy(refreshErrorMessage = null) }
        viewModelScope.launch {
            refreshTracking(isPolling = false)
            refreshOrder()
            if (isOrderTerminal()) stopPolling()
        }
        startPolling()
    }

    private suspend fun refreshOrder() {
        when (val result = customerRepository.getOrder(orderId)) {
            is Result.Success -> _uiState.update {
                it.copy(orderState = LoadState.Success(result.data))
            }

            is Result.Failure -> _uiState.update {
                it.copy(orderState = result.toLoadError())
            }
        }
    }

    /**
     * True bila status order yang terakhir diketahui sudah final.
     *
     * Status hanya dapat diketahui dari `GET orders/{order}`; selama itu belum
     * pernah termuat, jawabannya false supaya polling tetap boleh menyala.
     */
    private fun isOrderTerminal(): Boolean =
        (_uiState.value.orderState as? LoadState.Success)
            ?.data?.orderStatus?.isTerminal() == true

    /**
     * Memuat posisi kurir.
     *
     * Kegagalan saat polling tidak menimpa data yang sudah tampil. Data posisi
     * yang masih berlaku lebih berguna daripada layar kosong karena satu
     * permintaan gagal, jadi pesan disimpan terpisah di
     * [TrackingUiState.refreshErrorMessage].
     */
    private suspend fun refreshTracking(isPolling: Boolean) {
        when (val result = customerRepository.getTracking(orderId)) {
            is Result.Success -> when (val tracking = result.data) {
                is OrderTrackingResult.Active -> {
                    hasSeenActiveTracking = true
                    _uiState.update {
                        it.copy(
                            trackingState = LoadState.Success(tracking.tracking),
                            refreshErrorMessage = null
                        )
                    }
                }

                OrderTrackingResult.NoActiveDelivery -> {
                    _uiState.update {
                        it.copy(
                            trackingState = LoadState.Empty(NO_ACTIVE_DELIVERY_MESSAGE),
                            refreshErrorMessage = null
                        )
                    }
                    // Pengiriman yang tadi terlihat sudah berakhir. Perbarui
                    // status order sekali lagi agar status akhir (mis. SELESAI)
                    // ikut tampil, lalu hentikan polling: order tidak lagi
                    // dapat dilacak. Saat polling belum pernah melihat
                    // assignment aktif, `NoActiveDelivery` berarti kurir belum
                    // mulai dan polling tetap diteruskan.
                    if (isPolling && hasSeenActiveTracking) {
                        refreshOrder()
                        stopPolling()
                    }
                }
            }

            is Result.Failure -> {
                val error = result.toLoadError()
                val message = (error as? LoadState.Error)?.message
                _uiState.update { current ->
                    if (isPolling) {
                        current.copy(refreshErrorMessage = message)
                    } else {
                        current.copy(trackingState = error, refreshErrorMessage = null)
                    }
                }
            }
        }
    }

    /**
     * Menyalakan polling posisi kurir.
     *
     * Dipanggil oleh [load]/[refresh] dan oleh layar via lifecycle
     * ([androidx.lifecycle.compose.LifecycleStartEffect]): polling hanya
     * berjalan ketika layar STARTED, dan berhenti saat layar pindah ke
     * background atau dibuang. Guard `pollingJob.isActive` memastikan
     * masuk ulang tidak membuat polling ganda, dan [TrackingUiState.isPolling]
     * mencerminkan keadaan itu untuk layar.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        // Order yang sudah final tidak akan pernah punya assignment aktif lagi,
        // jadi jangan menyalakan polling lagi ketika layar kembali STARTED.
        if (isOrderTerminal()) return
        _uiState.update { it.copy(isPolling = true) }
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(trackingSchedule.customerReadIntervalMillis)
                if (!isActive) break
                refreshTracking(isPolling = true)
            }
        }
    }

    /** Menghentikan polling, misalnya saat layar tidak terlihat lagi. */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.update { it.copy(isPolling = false) }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val ARG_ORDER_ID = "orderId"

        const val MISSING_ORDER_ID_MESSAGE = "Nomor pesanan tidak tersedia."

        /** Ditampilkan saat backend menjawab `data: null`. */
        const val NO_ACTIVE_DELIVERY_MESSAGE =
            "Tidak ada pengiriman aktif untuk pesanan ini."
    }
}

/**
 * State layar tracking customer.
 *
 * [trackingState] menyimpan posisi, [orderState] menyimpan status dan alamat.
 * Keduanya dimuat terpisah karena dua endpoint itu terpisah.
 */
data class TrackingUiState(
    val orderState: LoadState<Order> = LoadState.Loading,
    val trackingState: LoadState<OrderTracking> = LoadState.Loading,
    val isPolling: Boolean = false,
    val refreshErrorMessage: String? = null
) {
    /**
     * Posisi kurir terakhir yang diterima backend, atau null bila belum ada.
     *
     * hanya nilai dari server; koordinat tidak pernah dihitung atau diperkirakan
     * di Android.
     */
    val confirmedTracking: OrderTracking?
        get() = (trackingState as? LoadState.Success)?.data

    /**
     * True ketika posisi yang masih tampil mungkin sudah tidak terbaru.
     *
     * Terjadi bila pembaruan terakhir gagal sementara posisi sebelumnya masih
     * ditampilkan. Data lama tetap berguna, tetapi tidak boleh disebut realtime.
     * Ambang "stale" berbasis waktu (`UD-TRACK-07`) belum dikunci, jadi penanda
     * ini tidak memakai ambang waktu karangan; ia hanya menyatakan bahwa
     * pembaruan terakhir tidak berhasil.
     */
    val isPositionStale: Boolean
        get() = refreshErrorMessage != null && confirmedTracking?.hasPositions == true
}