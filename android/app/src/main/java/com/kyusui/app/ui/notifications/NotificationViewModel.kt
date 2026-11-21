package com.kyusui.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.PaginatedResult
import com.kyusui.app.domain.repository.NotificationRepository
import com.kyusui.app.domain.repository.NotificationSummary
import com.kyusui.app.ui.state.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel untuk layar notifikasi.
 *
 * Notification bukan source of truth. ViewModel ini hanya:
 * - mengambil daftar notifikasi dari backend;
 * - menandai notifikasi sebagai dibaca.
 *
 * Business state diambil dari endpoint masing-masing saat
 * notification dibuka.
 */
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        _uiState.update { it.copy(listState = LoadState.Loading) }
        viewModelScope.launch {
            notificationRepository.notifications(page = 1, perPage = 20)
                .collect { result ->
                    when (result) {
                        is Result.Success -> _uiState.update {
                            it.copy(
                                listState = LoadState.Success(result.data),
                                errorMessage = null
                            )
                        }
                        is Result.Failure -> _uiState.update {
                            it.copy(
                                listState = LoadState.Error(
                                    message = result.exception.message ?: "Gagal memuat notifikasi."
                                ),
                                errorMessage = null
                            )
                        }
                    }
                }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
                .onSuccess { loadNotifications() }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
                .onSuccess { loadNotifications() }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * State layar notifikasi.
 */
data class NotificationUiState(
    val listState: LoadState<PaginatedResult<NotificationSummary>> = LoadState.Loading,
    val errorMessage: String? = null
)