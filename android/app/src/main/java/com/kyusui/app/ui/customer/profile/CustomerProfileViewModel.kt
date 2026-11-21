package com.kyusui.app.ui.customer.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.core.isValidEmail
import com.kyusui.app.domain.model.CustomerProfile
import com.kyusui.app.domain.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val profile: CustomerProfile? = null,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class CustomerProfileViewModel @Inject constructor(
    private val repository: CustomerRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomerProfileUiState())
    val uiState: StateFlow<CustomerProfileUiState> = _uiState.asStateFlow()

    init { loadProfile() }

    fun loadProfile() {
        _uiState.update { it.copy(isLoading = true, error = null, message = null) }
        viewModelScope.launch {
            when (val result = repository.getProfile()) {
                is Result.Success -> _uiState.update { state ->
                    val p = result.data
                    state.copy(isLoading = false, profile = p, name = p.name,
                        phone = p.phone.orEmpty(), email = p.email.orEmpty(),
                        address = p.defaultAddress.orEmpty(), error = null)
                }
                is Result.Failure -> _uiState.update { it.copy(isLoading = false,
                    error = result.exception.message ?: "Profil gagal dimuat.") }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, message = null) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value, message = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, message = null) }
    fun onAddressChange(value: String) = _uiState.update { it.copy(address = value, message = null) }

    fun saveProfile() {
        val current = _uiState.value
        if (current.isSaving) return
        if (current.name.isBlank()) {
            _uiState.update { it.copy(error = "Nama tidak boleh kosong.", message = null) }
            return
        }
        if (current.email.isNotBlank() && !isValidEmail(current.email)) {
            _uiState.update { it.copy(error = "Format email tidak valid.", message = null) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            when (val result = repository.updateProfile(
                name = current.name, phone = current.phone, email = current.email,
                defaultAddress = current.address
            )) {
                is Result.Success -> {
                    // Tampilkan hasil simpan lebih dulu, lalu segarkan dari
                    // `GET /customer/profile` supaya layar memakai nilai
                    // authoritative server, bukan hanya body response PUT.
                    _uiState.update { it.applyProfile(result.data) }
                    val refreshed = repository.getProfile()
                    _uiState.update { state ->
                        val authority = (refreshed as? Result.Success)?.data ?: result.data
                        state.applyProfile(authority).copy(
                            isSaving = false,
                            message = "Profil berhasil diperbarui.",
                            error = null
                        )
                    }
                }
                is Result.Failure -> _uiState.update { it.copy(isSaving = false,
                    error = result.exception.message ?: "Profil gagal disimpan.") }
            }
        }
    }

    private fun CustomerProfileUiState.applyProfile(profile: CustomerProfile) = copy(
        profile = profile,
        name = profile.name,
        phone = profile.phone.orEmpty(),
        email = profile.email.orEmpty(),
        address = profile.defaultAddress.orEmpty()
    )
}
