package com.kyusui.app.ui.courier.tracking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.repository.CourierRepository
import com.kyusui.app.ui.location.LocationPermissionState
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.SubmitState
import com.kyusui.app.ui.state.toLoadError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tracking sisi kurir untuk satu assignment aktif.
 *
 * ## Yang menentukan server, bukan Android
 *
 * ViewModel ini hanya mengirim posisi lewat
 * `POST courier/assignments/{assignment}/location`. Backend yang menentukan
 * apakah penugasan itu milik kurir yang sedang login, apakah delivery masih
 * aktif, dan apakah perpindahan lokasi masuk akal. Android tidak pernah
 * menentukan kelayakan mengirim lokasi dan tidak pernah menulis status apa pun
 * ke delivery.
 *
 * ## Pengiriman dijalankan controller singleton
 *
 * Loop acquisition dan pengiriman posisi hidup di
 * [CourierLocationShareController] (dibungkus [CourierLocationSharing]),
 * bukan di ViewModel ini. Controller bersifat proses-wide sehingga pengiriman
 * terus berjalan selama kurir berpindah layar atau meminimalkan aplikasi,
 * dibungkus foreground service bertipe `location`. ViewModel ini hanya:
 *
 *  1. membaca [assignmentId] dari argumen navigasi,
 *  2. membaca status assignment dari server ([loadDelivery]),
 *  3. meneruskan perintah mulai/hentikan ke controller,
 *  4. mencerminkan state controller ke [uiState] untuk layar.
 *
 * ViewModel tidak menghentikan controller ketika layar ditutup ([onCleared]):
 * menghentikan pengiriman hanya dilakukan lewat [stopTracking], keloguan
 * ([com.kyusui.app.ui.auth.AuthViewModel]), atau penyelesaian delivery
 * ([com.kyusui.app.ui.courier.delivery.DeliveryDetailViewModel]).
 *
 * ## Batas keamanan assignment
 *
 * [assignmentId] hanya dibaca dari argumen navigasi. Controller hanya
 * memulai satu session per proses, dan session itu milik assignment yang sama
 * dengan layar yang menyalakannya (guard 1:1). Bila route memberikan penugasan
 * yang bukan miliknya, backend menjawab `403` dan controller menghentikan
 * pengiriman.
 */
@HiltViewModel
class CourierTrackingViewModel @Inject constructor(
    private val courierRepository: CourierRepository,
    private val sharing: CourierLocationSharing,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val assignmentId: String =
        savedStateHandle.get<String>(ARG_ASSIGNMENT_ID).orEmpty()

    private val _uiState = MutableStateFlow(CourierTrackingUiState())
    val uiState: StateFlow<CourierTrackingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sharing.state.collect { share ->
                _uiState.update { it.withShare(share) }
            }
        }
        loadDelivery()
    }

    /**
     * Membaca status penugasan dari server, lalu menyesuaikan tracking.
     *
     * Mematikan tracking selalu mengikuti syarat yang baru dibaca dari server,
     * bukan tebakan layar. Pesan sesaat yang dimiliki controller dibersihkan
     * seperti perilaku lama: reload berarti pandangan baru ke backend.
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

        _uiState.update { it.copy(deliveryState = LoadState.Loading) }
        sharing.clearMessage()

        viewModelScope.launch {
            when (val result = courierRepository.getAssignment(assignmentId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(deliveryState = LoadState.Success(result.data)) }
                    if (result.data.canPostLocation && _uiState.value.permissionState.allowsAcquisition) {
                        sharing.start(assignmentId)
                    } else if (!result.data.canPostLocation) {
                        reconcileCourierTracking(courierRepository, sharing)
                    }
                    _uiState.update { it.withShare(sharing.state.value) }
                }

                is Result.Failure -> _uiState.update {
                    it.copy(deliveryState = result.toLoadError())
                }
            }
        }
    }

    /**
     * Status izin lokasi yang dilaporkan Composable.
     *
     * Izin yang ditolak permanen menghentikan acquisition lewat controller.
     * Menjalankan loop yang tidak akan pernah mendapat fix hanya memboroskan
     * daya dan membuat UI terlihat seperti sedang bekerja padahal tidak.
     */
    fun onPermissionStateChanged(state: LocationPermissionState) {
        _uiState.update { it.copy(permissionState = state) }
        if (!state.allowsAcquisition) {
            sharing.stop(TrackingStopReason.PERMISSION_NOT_GRANTED)
        } else if (_uiState.value.delivery?.canPostLocation == true) {
            sharing.start(assignmentId)
        }
    }

    /** Menyalakan acquisition lokasi dan pengiriman posisi berkala. */
    fun startTracking() {
        if (assignmentId.isBlank()) return
        if (_uiState.value.delivery?.canPostLocation != true) return
        sharing.start(assignmentId)
    }

    /** Mematikan acquisition dan pengiriman posisi. */
    fun stopTracking(reason: TrackingStopReason = TrackingStopReason.USER_REQUESTED) {
        sharing.stop(reason)
    }

    fun clearMessage() {
        sharing.clearMessage()
    }

    /**
     * Mencerminkan state controller ke state layar, hanya untuk session milik
     * assignment layar ini. Session milik assignment lain tidak boleh
     * memengaruhi layar ini.
     */
    private fun CourierTrackingUiState.withShare(share: CourierLocationShareState): CourierTrackingUiState {
        if (delivery?.canPostLocation != true) return copy(isTracking = false, stopReason = share.stopReason)
        return copy(
            isTracking = share.isSharing,
            stopReason = share.stopReason,
            locationReadStatus = share.locationReadStatus,
            submitState = share.submitState,
            currentFix = share.currentFix,
            lastReportedLocation = share.lastReportedLocation,
            message = share.message
        )
    }

    companion object {
        const val ARG_ASSIGNMENT_ID = "assignmentId"
        const val MISSING_ASSIGNMENT_ID_MESSAGE = "Nomor penugasan tidak tersedia."
    }
}

/**
 * State layar tracking kurir.
 *
 * [currentFix] adalah pembacaan perangkat terakhir dan belum tentu sudah
 * diterima server. [lastReportedLocation] adalah nilai yang balik dari
 * `POST .../location`, jadi hanya itu yang boleh ditampilkan sebagai posisi
 * resmi.
 */
data class CourierTrackingUiState(
    val deliveryState: LoadState<CourierDelivery> = LoadState.Loading,
    val submitState: SubmitState = SubmitState.Idle,
    val permissionState: LocationPermissionState = LocationPermissionState.NOT_REQUESTED,
    val locationReadStatus: LocationReadStatus = LocationReadStatus.Unknown,
    val isTracking: Boolean = false,
    val stopReason: TrackingStopReason? = null,
    val currentFix: CourierLocationFix? = null,
    val lastReportedLocation: CourierDeviceLocation? = null,
    val message: String? = null
) {
    /** Penugasan dari server, atau null bila belum termuat atau gagal. */
    val delivery: CourierDelivery?
        get() = (deliveryState as? LoadState.Success)?.data

    val isSubmitting: Boolean
        get() = submitState is SubmitState.Submitting

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
}