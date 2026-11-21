package com.kyusui.app.ui.owner.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.OwnerDashboardData
import com.kyusui.app.domain.repository.OwnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel dashboard Owner.
 *
 * Semua angka bisnis dan titik grafik berasal dari endpoint agregasi backend
 * GET /dashboard/owner. Android tidak menghitung omzet dari total order, tidak
 * mengelompokkan tanggal lokal, dan tidak membuat data grafik contoh.
 */
@HiltViewModel
class OwnerHomeViewModel @Inject constructor(
    private val ownerRepository: OwnerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OwnerHomeUiState())
    val uiState: StateFlow<OwnerHomeUiState> = _uiState.asStateFlow()

    init { loadSummary() }

    fun loadSummary() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = ownerRepository.getDashboard()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, dashboard = result.data, errorMessage = null)
                }
                is Result.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.exception.message ?: SUMMARY_ERROR_MESSAGE)
                }
            }
        }
    }

    companion object {
        const val SUMMARY_ERROR_MESSAGE = "Ringkasan bisnis tidak dapat dimuat."
    }
}

data class OwnerHomeUiState(
    val isLoading: Boolean = true,
    val dashboard: OwnerDashboardData? = null,
    val errorMessage: String? = null
) {
    val hasAnySummary: Boolean get() = dashboard != null
}
