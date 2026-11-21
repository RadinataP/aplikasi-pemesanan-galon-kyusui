package com.kyusui.app.ui.courier.location

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierLocationFix
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
 * Layar lokasi pelanggan untuk satu assignment.
 *
 * Berisi dua hal yang memang ada pada kontrak:
 *
 * 1. Alamat tujuan, dibaca dari `delivery_address` pada Assignment Resource.
 * 2. Posisi kurir sendiri, dibaca dari `latest_location` dan dapat dikirim ulang
 *    lewat `POST courier/assignments/{assignment}/location`.
 *
 * Yang TIDAK ada di layar ini, dan alasannya:
 *
 * - Peta in-app. Tidak ada endpoint peta maupun SDK peta di repo ini, dan
 *   menambahkan Google Maps SDK berarti butuh API key dan billing. Sesuai
 *   keputusan proyek, layar ini menampilkan alamat dan koordinat sebagai teks.
 * - Koordinat tujuan. Backend tidak mengirim `delivery_location` pada Assignment
 *   Resource untuk kurir, hanya `delivery_address` berupa teks. Menampilkan
 *   koordinat tujuan berarti mengarang nilai, jadi Android tidak melakukannya.
 * - Menentukan rute atau perkiraan jarak. Perhitungan rute bukan bagian dari
 *   kontrak ini dan akan mengarang angka yang tidak berasal dari server.
 *
 * Karena koordinat tujuan tidak dikirim backend, layar ini menampilkan alamat
 * teks apa adanya. Tidak ada koordinat tujuan yang ditampilkan, sehingga tidak
 * ada nilai nol yang terlihat seperti titik tujuan yang sudah diketahui.
 */
@HiltViewModel
class CustomerLocationViewModel @Inject constructor(
    private val courierRepository: CourierRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val assignmentId: String =
        savedStateHandle.get<String>(ARG_ASSIGNMENT_ID).orEmpty()

    private val _uiState = MutableStateFlow(CustomerLocationUiState())
    val uiState: StateFlow<CustomerLocationUiState> = _uiState.asStateFlow()

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
            it.copy(deliveryState = LoadState.Loading, submitState = SubmitState.Idle, message = null)
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
     * Status pembacaan lokasi perangkat.
     *
     * Berbeda dari `LocationStatus` milik form order customer, enum ini ditambah
     * `Unavailable` yang berarti lokasi tidak bisa dibaca padahal izin sudah
     * diberikan. Kurir perlu tahu itu bedanya: tanpa lokasi yang dapat dibaca,
     * posisi yang dikirim akan salah.
     */
    fun setLocationStatus(status: CourierLocationReadStatus) {
        _uiState.update { it.copy(locationReadStatus = status) }
    }

    /**
     * Meneruskan pembacaan perangkat ke repository.
     *
     * [fix] adalah koordinat perangkat kurir. Backend yang memeriksa apakah
     * delivery aktif dan menolak bila perpindahan lokasi tidak masuk akal.
     */
    fun submitFix(fix: CourierLocationFix) {
        val current = _uiState.value
        if (assignmentId.isBlank()) return
        if (current.submitState is SubmitState.Submitting) return

        val delivery = current.delivery ?: return
        if (!delivery.canPostLocation) return

        _uiState.update { it.copy(submitState = SubmitState.Submitting, message = null) }

        viewModelScope.launch {
            when (val result = courierRepository.updateLocation(assignmentId, fix)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        submitState = SubmitState.Success(assignmentId),
                        // Lokasi yang baru saja diterima server langsung
                        // ditampilkan. Nilai ini bukan pembacaan lokal.
                        lastReportedLocation = result.data,
                        locationReadStatus = CourierLocationReadStatus.Ready,
                        message = LOCATION_SENT_MESSAGE
                    )
                }

                is Result.Failure -> _uiState.update {
                    it.copy(submitState = result.toSubmitError())
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, submitState = SubmitState.Idle) }
    }

    companion object {
        const val ARG_ASSIGNMENT_ID = "assignmentId"
        const val MISSING_ASSIGNMENT_ID_MESSAGE = "Nomor penugasan tidak tersedia."
        const val LOCATION_SENT_MESSAGE = "Lokasi berhasil dikirim."
        const val NO_LOCATION_YET_MESSAGE = "Belum ada lokasi yang dikirim."
    }
}

/**
 * Status pembacaan lokasi perangkat.
 *
 * `Unknown` sebelum ada percobaan, `Locating` saat membaca, `Ready` bila
 * koordinat tersedia, `PermissionDenied` bila izin ditolak, dan `Unavailable`
 * bila izin ada tetapi tidak ada lokasi yang bisa dibaca.
 */
enum class CourierLocationReadStatus {
    Unknown,
    Locating,
    Ready,
    PermissionDenied,
    Unavailable
}

data class CustomerLocationUiState(
    val deliveryState: LoadState<CourierDelivery> = LoadState.Loading,
    val submitState: SubmitState = SubmitState.Idle,
    val locationReadStatus: CourierLocationReadStatus = CourierLocationReadStatus.Unknown,
    val lastReportedLocation: CourierDeviceLocation? = null,
    val message: String? = null
) {
    val delivery: CourierDelivery?
        get() = (deliveryState as? LoadState.Success)?.data

    val isSubmitting: Boolean get() = submitState is SubmitState.Submitting

    val errorMessage: String? get() = (submitState as? SubmitState.Error)?.message

    /**
     * Posisi kurir terakhir yang diketahui server.
     *
     * Null berarti kurir belum pernah mengirim lokasi untuk assignment ini.
     * Android tidak pernah mengarang posisi terakhir sebagai nilai cadangan.
     */
    val latestLocation: CourierDeviceLocation?
        get() = lastReportedLocation ?: delivery?.latestLocation

    /** Alamat tujuan, atau null bila backend tidak mengirimnya. */
    val destinationAddress: String?
        get() = delivery?.order?.deliveryAddress?.takeIf { it.isNotBlank() }

    /** Nama pelanggan dari Assignment Resource, bukan dari dashboard. */
    val customerName: String?
        get() = delivery?.order?.customerName

    /** Tombol kirim lokasi hanya aktif saat delivery sedang berjalan. */
    val canSendLocation: Boolean
        get() = delivery?.canPostLocation == true
}