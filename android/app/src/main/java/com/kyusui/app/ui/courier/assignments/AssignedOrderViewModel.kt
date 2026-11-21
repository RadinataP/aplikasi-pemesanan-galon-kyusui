package com.kyusui.app.ui.courier.assignments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.core.userMessage
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.repository.CourierRepository
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.toLoadError
import com.kyusui.app.ui.state.toListLoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Daftar assignment milik kurir dari `GET courier/assignments`.
 *
 * Endpoint ini tidak menerima query `status` dan tidak dipaginasi: backend
 * mengirim seluruh assignment milik kurir tersebut sebagai satu array. Karena
 * itu [CourierAssignmentFilter] hanya menyaring data yang SUDAH diterima dari
 * server, bukan meminta server menyaring. Tidak ada filter yang mengubah request.
 *
 * Status tiap baris dibaca apa adanya dari response backend, dan tidak ada nilai
 * status yang ditulis di Android.
 */
@HiltViewModel
class AssignedOrderViewModel @Inject constructor(
    private val courierRepository: CourierRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssignedOrderUiState())
    val uiState: StateFlow<AssignedOrderUiState> = _uiState.asStateFlow()

    init {
        loadAssignments()
    }

    /**
     * Memuat daftar assignment.
     *
     * [force] dipakai oleh aksi muat ulang. Tanpa itu, state yang sedang
     * `Success` tidak akan pernah kembali ke `Loading`, sehingga aksi muat ulang
     * tidak memberi umpan balik apa pun ke kurir.
     *
     * Kegagalan muat ulang pada daftar yang SUDAH tampil ditulis ke
     * [AssignedOrderUiState.refreshErrorMessage] dan TIDAK menimpa [LoadState].
     * alasan ini sama dengan beranda kurir:Daftar yang sedang dibaca lebih
     * berharga daripada layar error penuh, dan pada jaringan seluler kegagalannya
     * sering terjadi sementara.
     */
    fun loadAssignments(force: Boolean = false) {
        if (_uiState.value.isRefreshing) return

        _uiState.update {
            it.copy(
                listState = if (force) it.listState else LoadState.Loading,
                isRefreshing = true,
                refreshErrorMessage = null
            )
        }

        viewModelScope.launch {
            when (val result = courierRepository.getAssignments()) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        listState = result.data.toListLoadState(EMPTY_MESSAGE),
                        isRefreshing = false
                    )
                }

                is Result.Failure -> _uiState.update { state ->
                    if (force && state.listState is LoadState.Success) {
                        state.copy(
                            isRefreshing = false,
                            refreshErrorMessage = result.exception.userMessage()
                        )
                    } else {
                        state.copy(
                            // Kegagalan tidak pernah berubah jadi daftar kosong:
                            // `Empty` berarti backend tidak punya assignment untuk
                            // kurir ini, sedangkan `Error` berarti backend tidak
                            // bisa menjawab.
                            listState = result.toLoadError(),
                            isRefreshing = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Mengganti filter daftar.
     *
     * Filter hanya memilih baris mana yang tampil. Ia tidak mengubah request dan
     * tidak boleh dipakai untuk menyimpulkan bahwa backend menolak assignment
     * milik orang lain: daftar ini sudah milik kurir tersebut saja.
     */
    fun setFilter(filter: CourierAssignmentFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun clearFilter() {
        _uiState.update { it.copy(filter = CourierAssignmentFilter.ALL) }
    }

    companion object {
        const val EMPTY_MESSAGE = "Belum ada pengantaran yang ditugaskan."
    }
}

/**
 * Filter daftar assignment.
 *
 * [status] bernilai null untuk `ALL`, yang berarti tidak ada penyaringan sama
 * sekali. Nilai lainnya memakai status canonical dari
 * `AssignmentStatus`, bukan string yang diketik di layar.
 */
enum class CourierAssignmentFilter(val status: AssignmentStatus?, val label: String) {
    ALL(null, "Semua"),
    ASSIGNED(AssignmentStatus.ASSIGNED, "Ditugaskan"),
    ACTIVE(AssignmentStatus.ACTIVE, "Sedang Diantar"),
    COMPLETED(AssignmentStatus.COMPLETED, "Selesai");

    fun matches(delivery: CourierDelivery): Boolean =
        status == null || delivery.status == status
}

data class AssignedOrderUiState(
    val listState: LoadState<List<CourierDelivery>> = LoadState.Loading,
    val filter: CourierAssignmentFilter = CourierAssignmentFilter.ALL,
    val isRefreshing: Boolean = false,
    /**
     * Pesan kegagalan muat ulang pada data yang masih tampil.
     *
     * Dipisahkan dari [LoadState] supaya aksi muat ulang yang gagal tidak
     * menghapus daftar yang sedang dibaca kurir. Pola ini sama dengan beranda
     * kurir, jadi kedua layar gagal dengan cara yang sama.
     */
    val refreshErrorMessage: String? = null
) {
    val deliveries: List<CourierDelivery>
        get() = (listState as? LoadState.Success)?.data.orEmpty()

    /**
     * Baris yang benar-benar tampil setelah filter diterapkan.
     *
     * Filter yang menghasilkan daftar kosong pada data yang ada berarti filter itu
     * tidak punya baris, bukan backend gagal. Screen memakai [filteredIsEmpty] dan
     * [deliveries] untuk membedakan keduanya.
     */
    val visibleDeliveries: List<CourierDelivery>
        get() = deliveries.filter { filter.matches(it) }

    val filteredIsEmpty: Boolean
        get() = listState is LoadState.Success && visibleDeliveries.isEmpty()

    val errorMessage: String?
        get() = (listState as? LoadState.Error)?.message

    /**
     * Jumlah yang masih harus dikerjakan: ditugaskan tapi belum dimulai.
     *
     * Dihitung dari data yang sama dengan backend, bukan dari angka terpisah,
     * sehingga tidak bisa berbeda dari isi daftar.
     */
    val pendingStartCount: Int
        get() = deliveries.count { it.status == AssignmentStatus.ASSIGNED }

    val activeCount: Int
        get() = deliveries.count { it.status == AssignmentStatus.ACTIVE }
}
