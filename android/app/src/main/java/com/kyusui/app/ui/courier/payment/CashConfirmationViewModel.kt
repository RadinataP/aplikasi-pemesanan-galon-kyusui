package com.kyusui.app.ui.courier.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.CourierCashReceipt
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.repository.CourierRepository
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
 * Konfirmasi "Uang Diterima" untuk pembayaran `CASH`.
 *
 * Ini satu-satunya tempat di aplikasi yang boleh mengirim aksi tunerima uang.
 * Owner tidak punya layar ini, dan customer juga tidak: pembayaran tunai
 * dikonfirmasi kurir yang mengantar, bukan pemilik toko dan bukan pemesan.
 *
 * Batas yang dijaga file ini:
 *
 * - Android hanya mengirim aksi. [confirmCashReceived] tidak menerima nominal,
 *   tidak menerima id kurir, tidak menerima id order, dan tidak menerima bukti
 *   pembayaran. Endpoint `POST courier/assignments/{assignment}/cash/confirm`
 *   memang tidak punya body.
 * - Android tidak menentukan otorisasi. Urutan yang diperiksa backend adalah kurir
 *   terautentikasi, memiliki assignment tersebut, order milik assignment
 *   tersebut, delivery aktif, dan konfirmasi tunai yang valid. Pemeriksaan di
 *   sini hanya memilih tombol mana yang ditampilkan; `403` dan `409` dari backend
 *   ditampilkan apa adanya.
 * - Status pembayaran pada hasil selalu dibaca dari response backend. Tidak ada
 *   jalur di Android yang menulis `PAID` sebelum server mengatakannya, dan tidak
 *   ada jalur yang menganggap aksi sukses berarti pembayaran sudah lunas.
 */
@HiltViewModel
class CashConfirmationViewModel @Inject constructor(
    private val courierRepository: CourierRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val assignmentId: String =
        savedStateHandle.get<String>(ARG_ASSIGNMENT_ID).orEmpty()

    private val _uiState = MutableStateFlow(CashConfirmationUiState())
    val uiState: StateFlow<CashConfirmationUiState> = _uiState.asStateFlow()

    init {
        loadDelivery()
    }

    /**
     * Memuat assignment supaya nominal dan status pembayaran yang ditampilkan
     * berasal dari server, bukan dari nilai yang diketik atau diasumsikan.
     */
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
                // Receipt lama dibuang karena ia milik aksi sebelumnya.
                // Menampilkannya lagi setelah muat ulang akan membuat kurir
                // mengira konfirmasi lama masih berlaku.
                receipt = null,
                submitState = SubmitState.Idle,
                message = null
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
     * Mengirim aksi "Uang Diterima".
     *
     * [SubmitState.Submitting] menjadi guard double submit: menekan tombol dua
     * kali hanya menghasilkan satu request. Setelah backend mengonfirmasi, isi
     * layar dimuat ulang supaya status pembayaran yang tampil adalah nilai terbaru
     * dari server, bukan asumsi dari aksi yang baru saja dikirim.
     *
     * Kegagalan pada muat ulang setelah aksi berhasil sengaja diabaikan: receipt
     * yang sudah diterima dari server tetap ditampilkan. Menampilkan error di sini
     * akan membuat kurir mengira konfirmasi tunai-nya gagal.
     */
    fun confirmCashReceived() {
        val current = _uiState.value
        if (assignmentId.isBlank()) return
        if (current.submitState is SubmitState.Submitting) return

        val delivery = current.delivery ?: return
        if (!delivery.canConfirmCash) return

        _uiState.update { it.copy(submitState = SubmitState.Submitting, message = null) }

        viewModelScope.launch {
            when (val result = courierRepository.confirmCashReceived(assignmentId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            submitState = SubmitState.Success(assignmentId),
                            // Receipt adalah response backend apa adanya.
                            receipt = result.data,
                            message = cashSuccessMessage(result.data.paymentStatus)
                        )
                    }
                    refreshDeliveryAfterConfirmation()
                }

                is Result.Failure -> _uiState.update {
                    it.copy(submitState = result.toSubmitError())
                }
            }
        }
    }

    /**
     * Menghapus receipt dan pesan setelah kurir menutup layar.
     *
     * Tanpa ini, masuk lagi ke layar akan menampilkan bukti konfirmasi lama yang
     * bukan lagi hasil aksi pada sesi ini. [loadDelivery] juga membersihkan
     * keduanya, jadi kedua jalur ini konsisten.
     */
    fun clearReceipt() {
        _uiState.update {
            it.copy(
                receipt = null,
                message = null,
                submitState = SubmitState.Idle
            )
        }
    }

    /**
     * Muat ulang assignment tanpa membersihkan receipt.
     *
     * Berbeda dengan [loadDelivery], state `Loading` tidak dipakai supaya layar
     * tidak berkedip kosong setelah konfirmasi berhasil.
     */
    private suspend fun refreshDeliveryAfterConfirmation() {
        val result = courierRepository.getAssignment(assignmentId)
        if (result is Result.Success) {
            _uiState.update { it.copy(deliveryState = LoadState.Success(result.data)) }
        }
    }

    companion object {
        const val ARG_ASSIGNMENT_ID = "assignmentId"
        const val MISSING_ASSIGNMENT_ID_MESSAGE = "Nomor penugasan tidak tersedia."

        /**
         * Pesan setelah konfirmasi.
         *
         * `PAID` di sini berarti backend mengatakannya. Status lain yang
         * dikembalikan server ditampilkan apa adanya dan tidak diterjemahkan
         * menjadi lunas.
         */
        fun cashSuccessMessage(paymentStatus: PaymentStatus?): String = when (paymentStatus) {
            PaymentStatus.PAID -> "Uang diterima. Pembayaran ditandai lunas oleh server."
            else -> "Konfirmasi tunai terkirim. Status pembayaran: " +
                (paymentStatus?.displayName ?: "tidak diketahui") + "."
        }
    }
}

/**
 * State konfirmasi tunai.
 *
 * [receipt] hanya terisi setelah backend mengonfirmasi. Selama itu kosong,
 * Android tidak menampilkan apa pun yang terlihat seperti bukti pembayaran
 * berhasil.
 */
data class CashConfirmationUiState(
    val deliveryState: LoadState<CourierDelivery> = LoadState.Loading,
    val submitState: SubmitState = SubmitState.Idle,
    val receipt: CourierCashReceipt? = null,
    val message: String? = null
) {
    val delivery: CourierDelivery?
        get() = (deliveryState as? LoadState.Success)?.data

    val isSubmitting: Boolean get() = submitState is SubmitState.Submitting

    val errorMessage: String? get() = (submitState as? SubmitState.Error)?.message

    /** True hanya setelah server menyatakan pembayaran lunas. */
    val isConfirmedPaid: Boolean
        get() = receipt?.paymentStatus == PaymentStatus.PAID
}