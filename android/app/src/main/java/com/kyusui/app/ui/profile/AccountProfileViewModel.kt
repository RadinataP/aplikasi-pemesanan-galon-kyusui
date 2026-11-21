package com.kyusui.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.AccountProfile
import com.kyusui.app.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val profile: AccountProfile? = null,
    val name: String = "",
    val phone: String = "",
    val vehicle: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val error: String? = null,
    val message: String? = null
)

/**
 * Profil Owner dan Courier dari `GET/PUT /profile`.
 *
 * Field yang dapat diubah mengikuti apa yang benar-benar disimpan backend:
 * `name` untuk kedua role, serta `phone` dan `vehicle` hanya untuk Courier.
 * Owner tidak punya kolom phone/vehicle di backend, jadi layar tidak menampilkan
 * field yang akan diabaikan server.
 */
@HiltViewModel
class AccountProfileViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountProfileUiState())
    val uiState: StateFlow<AccountProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        _uiState.update { it.copy(isLoading = true, error = null, message = null) }
        viewModelScope.launch {
            when (val result = repository.getProfile()) {
                is Result.Success -> _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        profile = result.data,
                        name = result.data.name,
                        phone = result.data.phone.orEmpty(),
                        vehicle = result.data.vehicle.orEmpty(),
                        error = null
                    )
                }

                is Result.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.exception.message ?: "Profil gagal dimuat."
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, message = null) }

    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value, message = null) }

    fun onVehicleChange(value: String) = _uiState.update { it.copy(vehicle = value, message = null) }

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, error = null, message = null)
    }

    fun onPasswordConfirmationChange(value: String) = _uiState.update {
        it.copy(passwordConfirmation = value, error = null, message = null)
    }

    fun saveProfile() {
        val current = _uiState.value
        if (current.isSaving) return
        if (current.name.isBlank()) {
            _uiState.update { it.copy(error = "Nama tidak boleh kosong.", message = null) }
            return
        }
        if (current.password.isNotEmpty()) {
            val password = current.password
            val validPassword = password.length >= 8 &&
                password.any(Char::isUpperCase) &&
                password.any(Char::isLowerCase) &&
                password.any(Char::isDigit)
            if (!validPassword) {
                _uiState.update {
                    it.copy(
                        error = "Kata sandi minimal 8 karakter dan harus memiliki huruf besar, huruf kecil, serta angka.",
                        message = null
                    )
                }
                return
            }
            if (password != current.passwordConfirmation) {
                _uiState.update {
                    it.copy(error = "Konfirmasi kata sandi tidak cocok.", message = null)
                }
                return
            }
        } else if (current.passwordConfirmation.isNotEmpty()) {
            _uiState.update {
                it.copy(error = "Isi kata sandi baru terlebih dahulu.", message = null)
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            val isCourier = current.profile?.isCourier == true
            val result = repository.updateProfile(
                name = current.name,
                // Owner tidak menyimpan phone/vehicle; mengirimkannya hanya
                // membuat UI terlihat menyimpan padahal server mengabaikannya.
                phone = current.phone.takeIf { isCourier },
                address = null,
                vehicle = current.vehicle.takeIf { isCourier },
                password = current.password.takeIf { it.isNotEmpty() }
            )
            when (result) {
                is Result.Success -> {
                    // Tampilkan hasil simpan lebih dulu, lalu segarkan dari
                    // `GET /profile` supaya layar memakai nilai authoritative
                    // server, sama seperti alur profil Customer.
                    _uiState.update { it.applyProfile(result.data) }
                    val refreshed = repository.getProfile()
                    _uiState.update { state ->
                        val authority = (refreshed as? Result.Success)?.data ?: result.data
                        state.applyProfile(authority).copy(
                            isSaving = false,
                            message = "Profil berhasil diperbarui.",
                            password = "",
                            passwordConfirmation = "",
                            error = null
                        )
                    }
                }

                is Result.Failure -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.exception.message ?: "Profil gagal disimpan."
                    )
                }
            }
        }
    }

    private fun AccountProfileUiState.applyProfile(profile: AccountProfile) = copy(
        profile = profile,
        name = profile.name,
        phone = profile.phone.orEmpty(),
        vehicle = profile.vehicle.orEmpty()
    )
}
