package com.kyusui.app.ui.courier.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.core.userMessage
import com.kyusui.app.domain.model.CourierDashboard
import com.kyusui.app.domain.model.CourierDutyState
import com.kyusui.app.domain.repository.CourierRepository
import com.kyusui.app.ui.courier.tracking.CourierLocationSharing
import com.kyusui.app.ui.courier.tracking.TrackingStopReason
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.toLoadError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Beranda kurir dari `GET dashboard/courier`.
 *
 * Backend menghitung seluruh angka ringkasan: pengantaran hari ini, bulan ini,
 * yang selesai, tingkat penyelesaian, dan notifikasi belum dibaca. Android tidak
 * menghitung ulang satu pun angka itu dan tidak memakai nol sebagai pengganti
 * nilai yang gagal dimuat.
 *
 * Catatan kontrak yang penting untuk layar ini: `active_assignment` dan
 * `today_assignments` berisi Order Resource, bukan Assignment Resource. Order
 * Resource tersebut tidak memuat nama pelanggan, jadi beranda menampilkan id
 * order dan alamat. Nama pelanggan diambil dari `GET /courier/assignments`.
 */
@HiltViewModel
class CourierDashboardViewModel @Inject constructor(
    private val courierRepository: CourierRepository,
    private val sharing: CourierLocationSharing
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourierDashboardUiState())
    val uiState: StateFlow<CourierDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
        loadDutyState()
    }

    /**
     * Memuat beranda.
     *
     * [force] dipakai oleh aksi muat ulang: tanpa itu, state yang sedang
     * `Success` tidak akan pernah kembali ke `Loading`, sehingga aksi muat ulang
     * tidak memberi umpan balik apa pun ke kurir.
     *
     * Kegagalan pada muat ulang ditulis ke [CourierDashboardUiState.refreshErrorMessage]
     * dan TIDAK menimpa [LoadState]. Isi yang sudah tampil lebih berharga bagi
     * kurir daripada layar error penuh, dan angkanya tetap milik server.
     */
    fun loadDashboard(force: Boolean = false) {
        if (_uiState.value.isRefreshing) return

        _uiState.update {
            it.copy(
                dashboardState = if (force) it.dashboardState else LoadState.Loading,
                isRefreshing = true,
                refreshErrorMessage = null
            )
        }

        viewModelScope.launch {
            when (val result = courierRepository.getDashboard()) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        dashboardState = LoadState.Success(result.data),
                        isRefreshing = false
                    )
                }

                is Result.Failure -> _uiState.update { state ->
                    if (force && state.dashboardState is LoadState.Success) {
                        state.copy(
                            isRefreshing = false,
                            refreshErrorMessage = result.exception.userMessage()
                        )
                    } else {
                        state.copy(
                            dashboardState = result.toLoadError(),
                            isRefreshing = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Memuat status sesi bertugas dari `GET courier/duty`.
     *
     * Bila server menyatakan sesi masih aktif tetapi pengiriman belum berjalan
     * di perangkat (mis. aplikasi baru dibuka kembali), pengiriman dilanjutkan.
     * Backend tetap sumber kebenaran: state lokal menyalin [CourierDutyState],
     * bukan menyimpulkan sendiri.
     */
    fun loadDutyState() {
        viewModelScope.launch {
            when (val result = courierRepository.getDutyState()) {
                is Result.Success -> {
                    _uiState.update { it.copy(dutyState = LoadState.Success(result.data)) }
                    if (result.data.isActive && !sharing.state.value.isSharing) {
                        sharing.startDuty()
                    }
                }

                is Result.Failure -> _uiState.update {
                    it.copy(dutyState = result.toLoadError())
                }
            }
        }
    }

    /** Memulai sesi bertugas: server dulu, lalu pengiriman lokal. */
    fun startDuty() {
        val current = _uiState.value
        if (current.isDutyActionInProgress || current.isOnDuty) return

        _uiState.update { it.copy(isDutyActionInProgress = true, dutyMessage = null) }

        viewModelScope.launch {
            when (val result = courierRepository.startDuty()) {
                is Result.Success -> {
                    sharing.startDuty()
                    _uiState.update {
                        it.copy(
                            isDutyActionInProgress = false,
                            dutyState = LoadState.Success(
                                CourierDutyState(isActive = true, session = result.data)
                            ),
                            dutyMessage = "Sesi bertugas dimulai."
                        )
                    }
                    loadDutyState()
                }

                is Result.Failure -> _uiState.update {
                    it.copy(
                        isDutyActionInProgress = false,
                        dutyMessage = result.exception.userMessage()
                    )
                }
            }
        }
    }

    /** Mengakhiri sesi bertugas: server dulu, lalu hentikan pengiriman lokal. */
    fun stopDuty() {
        val current = _uiState.value
        if (current.isDutyActionInProgress || !current.isOnDuty) return

        _uiState.update { it.copy(isDutyActionInProgress = true, dutyMessage = null) }

        viewModelScope.launch {
            when (val result = courierRepository.stopDuty()) {
                is Result.Success -> {
                    sharing.stop(TrackingStopReason.USER_REQUESTED)
                    _uiState.update {
                        it.copy(
                            isDutyActionInProgress = false,
                            dutyState = LoadState.Success(CourierDutyState(isActive = false)),
                            dutyMessage = "Sesi bertugas diakhiri."
                        )
                    }
                    loadDutyState()
                }

                is Result.Failure -> _uiState.update {
                    it.copy(
                        isDutyActionInProgress = false,
                        dutyMessage = result.exception.userMessage()
                    )
                }
            }
        }
    }

    /** Hilangkan pesan aksi bertugas. */
    fun clearDutyMessage() {
        _uiState.update { it.copy(dutyMessage = null) }
    }
}

/**
 * State beranda kurir.
 *
 * [isRefreshing] dipisahkan dari [LoadState] supaya aksi muat ulang pada data
 * yang sudah ada tidak menghapus isi layar. [refreshErrorMessage] dipakai untuk
 * kegagalan muat ulang dengan isi yang masih tampil.
 */
data class CourierDashboardUiState(
    val dashboardState: LoadState<CourierDashboard> = LoadState.Loading,
    val isRefreshing: Boolean = false,
    val refreshErrorMessage: String? = null,
    val dutyState: LoadState<CourierDutyState> = LoadState.Loading,
    val isDutyActionInProgress: Boolean = false,
    val dutyMessage: String? = null
) {
    val dashboard: CourierDashboard?
        get() = (dashboardState as? LoadState.Success)?.data

    val errorMessage: String?
        get() = (dashboardState as? LoadState.Error)?.message

    val hasActiveDelivery: Boolean
        get() = dashboard?.activeDelivery != null

    /** True bila server menyatakan sesi bertugas sedang berjalan. */
    val isOnDuty: Boolean
        get() = (dutyState as? LoadState.Success)?.data?.isActive == true
}