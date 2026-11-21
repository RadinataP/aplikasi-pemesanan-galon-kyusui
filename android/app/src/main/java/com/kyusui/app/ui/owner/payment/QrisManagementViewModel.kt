package com.kyusui.app.ui.owner.payment

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.ProofUploadPolicy
import com.kyusui.app.core.Result
import com.kyusui.app.core.NotFoundException
import com.kyusui.app.data.upload.ProofFileSource
import com.kyusui.app.domain.model.OwnerQrisConfig
import com.kyusui.app.domain.model.ProofImageSelection
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
 * Manajemen QRIS aktif oleh Owner.
 *
 * Dua endpoint dari `06_Berkah Water_API_SPECIFICATION_REBUILT.md` section 13:
 *
 * - `GET /owner/payment/qris` untuk membaca konfigurasi aktif (13.1)
 * - `PUT /owner/payment/qris` untuk menggantinya, multipart `qris_image` (13.2)
 *
 * Aturan yang dijaga:
 *
 * - QRIS aktif tidak pernah di-hardcode di Android dan tidak disimpan sebagai
 *   source of truth. Setelah penggantian, konfigurasi yang ditampilkan adalah
 *   konfigurasi yang dikembalikan backend, bukan file yang baru dipilih.
 * - Validasi ukuran dan format di [ProofUploadPolicy] hanya pre-check UX. Backend
 *   tetap validator final dan penolakannya ditampilkan apa adanya.
 * - `200` dengan `data: null` pada `GET /owner/payment/qris` berarti QRIS belum
 *   dikonfigurasi. Ini dipetakan menjadi `LoadState.Empty` sesuai kontrak backend.
 */
@HiltViewModel
class QrisManagementViewModel @Inject constructor(
    private val ownerRepository: OwnerRepository,
    private val proofFileSource: ProofFileSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrisManagementUiState())
    val uiState: StateFlow<QrisManagementUiState> = _uiState.asStateFlow()

    init {
        loadActiveQris()
    }

    fun loadActiveQris() {
        _uiState.update {
            it.copy(qrisState = LoadState.Loading, replacementState = SubmitState.Idle)
        }

        viewModelScope.launch {
            when (val result = ownerRepository.getActiveQris()) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        qrisState = result.data?.let { config -> LoadState.Success(config) }
                            ?: LoadState.Empty()
                    )
                }

                is Result.Failure -> {
                    if (result.exception is NotFoundException) {
                        // Endpoint ini menggunakan 404 untuk QRIS yang belum dikonfigurasi.
                        _uiState.update {
                            it.copy(qrisState = LoadState.Empty())
                        }
                    } else {
                        // Error autentikasi, otorisasi, jaringan, dan server
                        // tetap ditampilkan sebagai error.
                        _uiState.update {
                            it.copy(qrisState = result.toLoadError())
                        }
                    }
                }
            }
        }
    }

    /**
     * Membaca file yang dipilih Owner dari picker.
     *
     * Kegagalan membaca file ditampilkan sebagai error di layar, bukan
     * dilempar keluar sebagai exception yang membuat aplikasi berhenti.
     */
    fun onQrisImagePicked(uri: Uri) {
        val selection = proofFileSource.readSelection(uri)

        if (selection == null) {
            _uiState.update {
                it.copy(
                    selectionState = SelectionState.Error(
                        message = UNREADABLE_FILE_MESSAGE
                    )
                )
            }
            return
        }

        // Pre-check hanya mengurangi permintaan yang pasti ditolak. Backend tetap
        // deciding validator final, termasuk saat aturannya lebih longgar.
        when (
            val validation = ProofUploadPolicy.validate(
                mimeType = selection.mimeType,
                sizeBytes = selection.sizeBytes,
                fileName = selection.displayName
            )
        ) {
            is ProofUploadPolicy.Result.Invalid -> _uiState.update {
                it.copy(selectionState = SelectionState.Error(message = validation.reason.message))
            }

            ProofUploadPolicy.Result.Valid -> _uiState.update {
                it.copy(selectionState = SelectionState.Selected(selection))
            }
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectionState = SelectionState.Empty,
                replacementState = SubmitState.Idle
            )
        }
    }

    /**
     * Mengirim QRIS baru ke `PUT /owner/payment/qris`.
     *
     * Multipart memakai field `qris_image` sesuai section 13.2. Nama field
     * ditentukan dari konstanta, bukan ditulis ulang sebagai string di sini,
     * supaya tidak menyimpang dari implementasi upload lain.
     *
     * Tidak ada request yang dikirim tanpa selection: Owner harus memilih file
     * dulu, sehingga tidak ada keadaan "ganti QRIS" tanpa file.
     */
    fun replaceActiveQris() {
        val current = _uiState.value
        val selection = (current.selectionState as? SelectionState.Selected)?.selection ?: return

        // Guard duplicate submit: satu file tidak boleh terkirim dua kali.
        if (current.replacementState is SubmitState.Submitting) return

        _uiState.update {
            it.copy(replacementState = SubmitState.Submitting, replacementMessage = null)
        }

        viewModelScope.launch {
            when (val result = ownerRepository.replaceActiveQris(selection)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        // Konfigurasi aktif berasal dari response backend.
                        // File lokal yang dipilih Owner tidak lagi relevan sebagai
                        // sumber kebenaran.
                        qrisState = LoadState.Success(result.data),
                        selectionState = SelectionState.Empty,
                        replacementState = SubmitState.Idle,
                        replacementMessage = QRIS_REPLACED_MESSAGE
                    )
                }

                is Result.Failure -> _uiState.update {
                    // Penolakan backend, termasuk 401/403/404/409/422, ditampilkan
                    // apa adanya. QRIS aktif tidak diubah di Android.
                    it.copy(replacementState = result.toSubmitError())
                }
            }
        }
    }

    fun clearReplacementMessage() {
        _uiState.update {
            it.copy(replacementState = SubmitState.Idle, replacementMessage = null)
        }
    }

    companion object {
        const val UNREADABLE_FILE_MESSAGE = "File tidak dapat dibaca."
        const val QRIS_REPLACED_MESSAGE = "QRIS aktif berhasil diperbarui."
    }
}

/**
 * State layar manajemen QRIS.
 */
data class QrisManagementUiState(
    val qrisState: LoadState<OwnerQrisConfig> = LoadState.Loading,
    val selectionState: SelectionState = SelectionState.Empty,
    val replacementState: SubmitState = SubmitState.Idle,
    val replacementMessage: String? = null
) {
    val config: OwnerQrisConfig?
        get() = (qrisState as? LoadState.Success)?.data

    val selectedImage: ProofImageSelection?
        get() = (selectionState as? SelectionState.Selected)?.selection

    val isReplacing: Boolean
        get() = replacementState is SubmitState.Submitting

    val canReplace: Boolean
        get() = selectedImage != null && !isReplacing

    val replacementErrorMessage: String?
        get() = (replacementState as? SubmitState.Error)?.message

    /**
     * QRIS belum dikonfigurasi adalah kondisi kosong bisnis, bukan error HTTP.
     * Backend mengirim HTTP 200 dengan `data: null`, yang dipetakan menjadi
     * `LoadState.Empty`; Owner dapat langsung memasang gambar QRIS pertama.
     */
    val isQrisNotConfigured: Boolean
        get() = qrisState is LoadState.Empty
}

/**
 * File QRIS yang dipilih Owner sebelum dikirim.
 *
 * Dipisah dari [LoadState] karena ini bukan hasil pemuatan dari server, melainkan
 * pilihan lokal yang belum pernah sampai ke backend.
 */
sealed interface SelectionState {
    data object Empty : SelectionState

    data class Selected(val selection: ProofImageSelection) : SelectionState

    data class Error(val message: String) : SelectionState
}