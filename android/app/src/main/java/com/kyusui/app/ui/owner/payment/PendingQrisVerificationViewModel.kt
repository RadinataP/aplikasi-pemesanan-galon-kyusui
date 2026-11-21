package com.kyusui.app.ui.owner.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.OwnerPaymentProof
import com.kyusui.app.domain.model.OwnerPaymentProofHistory
import com.kyusui.app.domain.model.OwnerPendingPayment
import com.kyusui.app.domain.model.OwnerVerificationAction
import com.kyusui.app.domain.model.Payment
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.repository.OwnerRepository
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.SubmitState
import com.kyusui.app.ui.state.toLoadError
import com.kyusui.app.ui.state.toPagedListLoadState
import com.kyusui.app.ui.state.toSubmitError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Antrean verifikasi QRIS untuk Owner.
 *
 * Tiga endpoint dipakai apa adanya sesuai
 * `06_Berkah Water_API_SPECIFICATION_REBUILT.md` section 12:
 *
 * - `GET  /owner/payments/pending`                     untuk daftar antrean (12.1)
 * - `GET  /owner/orders/{order}/payment/proof`         untuk melihat bukti (12.2)
 * - `POST /owner/orders/{order}/payment-verification`  untuk approve/reject (12.3)
 *
 * Aturan yang tidak boleh dilanggar:
 *
 * - Android tidak pernah menetapkan `PAID` maupun `PENDING` secara lokal.
 *   `APPROVE` dan `REJECT` hanya dikirim sebagai `action`; status baru diambil
 *   dari response backend pada [verify].
 * - Reject mengembalikan payment ke `PENDING`, bukan ke status `REJECTED`.
 *   Status `REJECTED` tidak ada pada contract dan tidak pernah dikirim.
 * - Bukti tidak pernah dianggap valid atau tidak valid di Android. Owner hanya
 *   melihat bukti yang diberikan backend lalu memutuskan.
 */
@HiltViewModel
class PendingQrisVerificationViewModel @Inject constructor(
    private val ownerRepository: OwnerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Order yang sedang dibuka, opsional.
     *
     * Route `owner_orders/{orderId}/qris_verification` bisa dibuka dengan
     * `orderId` untuk fokus ke satu pembayaran, atau tanpa `orderId` untuk
     * menampilkan seluruh antrean.
     */
    private val focusedOrderId: String? =
        savedStateHandle.get<String>(ARG_ORDER_ID)?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(PendingQrisVerificationUiState())
    val uiState: StateFlow<PendingQrisVerificationUiState> = _uiState.asStateFlow()

    init {
        loadPendingPayments()
        // dibuka lewat route per order: langsung muat bukti pembayaran itu,
        // tidak menunggu Owner memilih baris dari antrean.
        focusedOrderId?.let(::loadProof)
    }

    /**
     * Memuat antrean verifikasi.
     *
     * `GET /owner/payments/pending` sudah difilter backend ke
     * `QRIS` + `WAITING_VERIFICATION`. Android tidak menambahkan filter apa pun,
     * sehingga antrean yang tampil sama persis dengan yang dilihat backend.
     */
    fun loadPendingPayments() {
        _uiState.update {
            it.copy(
                paymentsState = LoadState.Loading,
                page = PendingQrisVerificationUiState.FIRST_PAGE,
                hasMore = false,
                isAppending = false
            )
        }

        viewModelScope.launch {
            val result = ownerRepository.getPendingPayments(
                page = PendingQrisVerificationUiState.FIRST_PAGE,
                perPage = PER_PAGE
            )

            _uiState.update { state ->
                when (result) {
                    is Result.Success -> state.copy(
                        paymentsState = result.toPagedListLoadState(EMPTY_QUEUE_MESSAGE),
                        hasMore = result.data.hasMore
                    )
                    is Result.Failure -> state.copy(paymentsState = result.toLoadError())
                }
            }
        }
    }

    /**
     * Memuat halaman antrean berikutnya.
     *
     * Halaman yang gagal tidak menghapus antrean yang sudah tampil dan
     * `hasMore` dimatikan supaya request yang sama tidak diulang terus-menerus.
     */
    fun loadNextPage() {
        val current = _uiState.value
        if (!current.hasMore || current.isAppending) return
        if (current.paymentsState !is LoadState.Success) return

        val nextPage = current.page + 1
        _uiState.update { it.copy(isAppending = true) }

        viewModelScope.launch {
            val result = ownerRepository.getPendingPayments(
                page = nextPage,
                perPage = PER_PAGE
            )

            _uiState.update { state ->
                when (result) {
                    is Result.Success -> {
                        val merged = (state.payments + result.data.data).distinctBy { it.paymentId }
                        state.copy(
                            paymentsState = LoadState.Success(merged),
                            page = nextPage,
                            hasMore = result.data.hasMore,
                            isAppending = false
                        )
                    }

                    is Result.Failure -> state.copy(
                        hasMore = false,
                        isAppending = false
                    )
                }
            }
        }
    }

    /**
     * Membuka bukti pembayaran dari `GET /owner/orders/{order}/payment/proof`.
     *
     * Bukti dimuat terpisah dari daftar karena endpoint ini per order. State
     * bukti yang gagal tidak menghapus antrean, jadi Owner tetap bisa memilih
     * pembayaran lain.
     */
    fun loadProof(orderId: String) {
        if (orderId.isBlank()) return

        _uiState.update {
            it.copy(
                proofOrderId = orderId,
                proofState = LoadState.Loading,
                verificationState = SubmitState.Idle,
                verificationMessage = null
            )
        }

        viewModelScope.launch {
            when (val result = ownerRepository.getPaymentProof(orderId)) {
                is Result.Success -> _uiState.update {
                    it.copy(proofState = LoadState.Success(result.data))
                }

                // `404` pada endpoint ini berarti tidak ada bukti untuk order itu.
                // Kondisi itu ditampilkan apa adanya, tanpa menyiratkan bahwa
                // pembayaran tidak sah.
                is Result.Failure -> _uiState.update {
                    it.copy(proofState = result.toLoadError())
                }
            }
        }
    }

    /** Memuat riwayat keputusan QRIS untuk order yang dipilih. */
    fun loadProofHistory(orderId: String) {
        if (orderId.isBlank()) return
        _uiState.update { it.copy(historyOrderId = orderId, historyState = LoadState.Loading) }
        viewModelScope.launch {
            when (val result = ownerRepository.getPaymentProofHistory(orderId)) {
                is Result.Success -> _uiState.update { state ->
                    state.copy(
                        historyState = if (result.data.isEmpty()) {
                            LoadState.Empty("Belum ada riwayat keputusan bukti untuk pesanan ini.")
                        } else LoadState.Success(result.data)
                    )
                }
                is Result.Failure -> _uiState.update { it.copy(historyState = result.toLoadError()) }
            }
        }
    }

    fun dismissProofHistory() {
        _uiState.update {
            it.copy(
                historyOrderId = null,
                historyState = null,
                historicalProofId = null,
                historicalProofState = null
            )
        }
    }

    /** Mengambil file bukti historis dengan otorisasi API, bukan URL publik. */
    fun loadHistoricalProof(orderId: String, historyId: String) {
        if (orderId.isBlank() || historyId.isBlank()) return
        _uiState.update {
            it.copy(
                historicalProofId = historyId,
                historicalProofState = LoadState.Loading
            )
        }
        viewModelScope.launch {
            when (val result = ownerRepository.getHistoricalPaymentProof(orderId, historyId)) {
                is Result.Success -> _uiState.update {
                    it.copy(historicalProofState = LoadState.Success(result.data))
                }
                is Result.Failure -> _uiState.update {
                    it.copy(historicalProofState = result.toLoadError())
                }
            }
        }
    }

    fun dismissHistoricalProof() {
        _uiState.update {
            it.copy(historicalProofId = null, historicalProofState = null)
        }
    }

    /**
     * Menutup pratinjau bukti.
     */
    fun dismissProof() {
        _uiState.update {
            it.copy(
                proofOrderId = null,
                proofState = null,
                verificationState = SubmitState.Idle,
                verificationMessage = null
            )
        }
    }

    /**
     * Mengirim aksi approve atau reject ke
     * `POST /owner/orders/{order}/payment-verification`.
     *
     * Yang dikirim hanya `action` dan, untuk reject, `note`. Status `PAID`
     * maupun `PENDING` TIDAK pernah ditulis di Android: keduanya dibaca dari
     * [com.kyusui.app.domain.model.Payment] yang dikembalikan backend pada
     * response.
     */
    fun verify(action: OwnerVerificationAction, note: String? = null) {
        val current = _uiState.value
        val orderId = current.proofOrderId ?: return

        // Guard duplicate submit: aksi yang sama tidak boleh terkirim dua kali
        // untuk order yang sama.
        if (current.verificationState is SubmitState.Submitting) return

        // Validasi UX di client; backend tetap menjadi sumber validasi final.
        if (action == OwnerVerificationAction.REJECT) {
            val normalizedNote = note?.trim().orEmpty()
            if (normalizedNote.length !in MIN_REJECTION_NOTE_LENGTH..MAX_REJECTION_NOTE_LENGTH) {
                _uiState.update {
                    it.copy(
                        verificationState = SubmitState.Error(
                            message = "Alasan penolakan wajib 5–500 karakter."
                        ),
                        verificationMessage = null
                    )
                }
                return
            }
        }

        // Hanya aksi dari enum canonical yang boleh dikirim. Tidak ada aksi lain
        // yang bisa dipanggil dari UI.
        if (action != OwnerVerificationAction.APPROVE &&
            action != OwnerVerificationAction.REJECT
        ) {
            return
        }

        _uiState.update {
            it.copy(verificationState = SubmitState.Submitting, verificationMessage = null)
        }

        viewModelScope.launch {
            when (
                val result = ownerRepository.verifyPayment(
                    orderId = orderId,
                    action = action,
                    note = note
                )
            ) {
                is Result.Success -> {
                    val payment = result.data.payment
                    // Status dibaca apa adanya dari server. Bila backend
                    // mengirim nilai yang tidak dikenal, Android menampilkannya
                    // sebagai "tidak diketahui" dan tidak menebak artinya.
                    val serverStatus = payment.paymentStatus

                    _uiState.update { state ->
                        state.copy(
                            verificationState = SubmitState.Success(orderId = orderId),
                            verificationMessage = verificationMessage(action, serverStatus),
                            // Antrean lokal diperbarui dari response server:
                            // approve mengubah status sehingga baris tidak lagi
                            // menunggu verifikasi, reject mengembalikan ke
                            // PENDING sehingga juga tidak ada di antrean ini.
                            paymentsState = removeFromQueue(state.paymentsState, orderId),
                            proofState = payment.toProofState(serverStatus)
                        )
                    }
                }

                // `401`, `403`, `404`, `409`, dan `422` semuanya melewati
                // pemetaan error yang sama dengan endpoint lain. Tidak ada
                // status yang dipaksa berubah di Android saat aksi ditolak.
                is Result.Failure -> _uiState.update {
                    it.copy(verificationState = result.toSubmitError())
                }
            }
        }
    }

    /**
     * Menghapus pembayaran dari antrean lokal setelah aksi sukses.
     *
     * Ini bukan pengubahan status. Antrean `GET /owner/payments/pending`
     * hanya berisi `WAITING_VERIFICATION`; begitu backend mengembalikan status
     * lain, baris itu sudah tidak termasuk antrean. Mempertahankannya di layar
     * akan membuat Owner melihat antrean yang tidak sama dengan server.
     *
     * Status pada [OwnerPendingPayment] di sini tidak ditulis ulang: baris
     * dihapus, bukan diubah statusnya.
     */
    private fun removeFromQueue(
        state: LoadState<List<OwnerPendingPayment>>,
        orderId: String
    ): LoadState<List<OwnerPendingPayment>> {
        val success = state as? LoadState.Success ?: return state
        val remaining = success.data.filterNot { it.orderId == orderId }
        return if (remaining.isEmpty()) {
            LoadState.Empty(EMPTY_QUEUE_MESSAGE)
        } else {
            LoadState.Success(remaining)
        }
    }

    /**
     * Menyusun state bukti dari payment hasil verifikasi.
     *
     * Keberadaan gambar bukti tidak diubah Android: nilai itu tetap mengikuti
     * apa yang backend laporkan terakhir. Yang diperbarui hanyalah ringkasan
     * pembayaran yang baru dikembalikan server.
     */
    private fun Payment.toProofState(status: PaymentStatus?): LoadState<OwnerPaymentProof> {
        val currentProof = (_uiState.value.proofState as? LoadState.Success)?.data
        return LoadState.Success(
            OwnerPaymentProof(
                orderId = orderId,
                paymentId = id,
                paymentMethod = paymentMethod,
                paymentStatus = status,
                hasProof = currentProof?.hasProof ?: false,
                proofUrl = currentProof?.proofUrl
            )
        )
    }

    companion object {
        const val ARG_ORDER_ID = "orderId"
        const val PER_PAGE = 20
        const val MIN_REJECTION_NOTE_LENGTH = 5
        const val MAX_REJECTION_NOTE_LENGTH = 500
        const val EMPTY_QUEUE_MESSAGE = "Tidak ada pembayaran yang menunggu verifikasi."

        /**
         * Pesan hasil aksi.
         *
         * Status di dalam pesan adalah nilai yang backend kirim, bukan nilai
         * yang Android tetapkan. Bila backend mengembalikan status lain, teks
         * ini tetap memuat status itu apa adanya.
         */
        private fun verificationMessage(
            action: OwnerVerificationAction,
            serverStatus: PaymentStatus?
        ): String {
            val statusText = when (serverStatus) {
                PaymentStatus.PENDING -> "PENDING"
                PaymentStatus.WAITING_VERIFICATION -> "WAITING_VERIFICATION"
                PaymentStatus.PAID -> "PAID"
                null -> "tidak dikenal"
            }

            return when (action) {
                OwnerVerificationAction.APPROVE ->
                    "Pembayaran disetujui. Status dari server: $statusText."

                OwnerVerificationAction.REJECT ->
                    "Bukti ditolak. Status dari server: $statusText. " +
                        "Pelanggan dapat mengunggah bukti ulang."
            }
        }
    }
}

/**
 * State layar verifikasi QRIS.
 *
 * [paymentsState] berisi antrean global. [proofState] berisi bukti dari endpoint
 * terpisah dan hanya relevan ketika [proofOrderId] tidak null.
 */
data class PendingQrisVerificationUiState(
    val paymentsState: LoadState<List<OwnerPendingPayment>> = LoadState.Loading,
    val page: Int = FIRST_PAGE,
    val hasMore: Boolean = false,
    val isAppending: Boolean = false,
    val proofOrderId: String? = null,
    val proofState: LoadState<OwnerPaymentProof>? = null,
    val historyOrderId: String? = null,
    val historyState: LoadState<List<OwnerPaymentProofHistory>>? = null,
    val historicalProofId: String? = null,
    val historicalProofState: LoadState<ByteArray>? = null,
    val verificationState: SubmitState = SubmitState.Idle,
    val verificationMessage: String? = null
) {
    val payments: List<OwnerPendingPayment>
        get() = (paymentsState as? LoadState.Success)?.data.orEmpty()

    val isVerifying: Boolean
        get() = verificationState is SubmitState.Submitting

    val verificationErrorMessage: String?
        get() = (verificationState as? SubmitState.Error)?.message

    val isProofOpen: Boolean
        get() = proofOrderId != null

    /**
     * Aksi verifikasi hanya boleh ditampilkan untuk pembayaran yang benar-benar
     * masih `WAITING_VERIFICATION`.
     *
     * Ini bukan security boundary, hanya mencegah Owner mengirim aksi yang
     * jelas tidak mungkin. Backend tetap menjadi satu-satunya penentu keabsahan
     * aksi.
     */
    fun canVerify(payment: OwnerPendingPayment): Boolean =
        payment.isAwaitingVerification && !isVerifying

    companion object {
        const val FIRST_PAGE = 1
    }
}
