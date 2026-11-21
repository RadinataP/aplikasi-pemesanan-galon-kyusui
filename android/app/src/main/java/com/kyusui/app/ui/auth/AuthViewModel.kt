package com.kyusui.app.ui.auth

import com.kyusui.app.core.Result
import com.kyusui.app.core.ValidationException
import com.kyusui.app.core.isValidEmail
import com.kyusui.app.domain.model.AuthSession
import com.kyusui.app.domain.model.User
import com.kyusui.app.domain.model.UserRole
import com.kyusui.app.domain.repository.AuthRepository
import com.kyusui.app.domain.repository.LoginRequest
import com.kyusui.app.domain.repository.RegisterRequest
import com.kyusui.app.ui.courier.tracking.CourierLocationSharing
import com.kyusui.app.ui.courier.tracking.TrackingStopReason
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {

    /** Session sedang dipulihkan dari penyimpanan lokal lewat `GET /auth/me`. */
    data object RestoringSession : AuthUiState

    /** Belum ada session. Menampilkan login atau register. */
    data class Unauthenticated(
        val isSubmitting: Boolean = false,
        val errorMessage: String? = null,
        val fieldErrors: Map<String, String> = emptyMap()
    ) : AuthUiState

    /** Session valid. Role menentukan home tujuan. */
    data class Authenticated(val user: User) : AuthUiState

    /**
     * Session tidak bisa dipulihkan karena masalah jaringan atau server.
     * User tetap diberi jalan untuk login, atau logout bila token ditolak.
     */
    data class RestoreFailed(val message: String) : AuthUiState
}

/**
 * Titik tujuan setelah Splash ditentukan oleh role yang berasal dari backend.
 * Role hanya memilih layar; authorization tetap dikerjakan backend.
 */
enum class AuthEntryPoint {
    CUSTOMER_HOME,
    OWNER_HOME,
    COURIER_HOME,
    LOGIN;

    companion object {
        fun fromRole(role: UserRole): AuthEntryPoint = when (role) {
            UserRole.CUSTOMER -> CUSTOMER_HOME
            UserRole.OWNER -> OWNER_HOME
            UserRole.COURIER -> COURIER_HOME
        }
    }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sharing: CourierLocationSharing
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.RestoringSession)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _entryPoint = MutableStateFlow<AuthEntryPoint?>(null)
    val entryPoint: StateFlow<AuthEntryPoint?> = _entryPoint.asStateFlow()

    init {
        restoreSession()
    }

    fun restoreSession() {
        _uiState.value = AuthUiState.RestoringSession
        viewModelScope.launch {
            when (val result = authRepository.restoreSession()) {
                is Result.Success -> onSessionRestored(result.data)
                is Result.Failure -> _uiState.value =
                    AuthUiState.RestoreFailed(readableMessage(result.exception))
            }
        }
    }

    fun login(email: String, password: String) {
        val localErrors = validateLogin(email, password)
        if (localErrors.isNotEmpty()) {
            _uiState.value = AuthUiState.Unauthenticated(
                errorMessage = VALIDATION_MESSAGE,
                fieldErrors = localErrors
            )
            return
        }

        _uiState.value = AuthUiState.Unauthenticated(isSubmitting = true)
        viewModelScope.launch {
            when (
                val result = authRepository.login(
                    LoginRequest(email = email.trim(), password = password)
                )
            ) {
                is Result.Success -> onAuthenticated(result.data.user)
                is Result.Failure -> _uiState.value = AuthUiState.Unauthenticated(
                    errorMessage = readableMessage(result.exception),
                    fieldErrors = fieldErrorsOf(result.exception)
                )
            }
        }
    }

    fun register(request: RegisterRequest) {
        val localErrors = validateRegister(request)
        if (localErrors.isNotEmpty()) {
            _uiState.value = AuthUiState.Unauthenticated(
                errorMessage = VALIDATION_MESSAGE,
                fieldErrors = localErrors
            )
            return
        }

        _uiState.value = AuthUiState.Unauthenticated(isSubmitting = true)
        viewModelScope.launch {
            when (val result = authRepository.register(request)) {
                is Result.Success -> onAuthenticated(result.data.user)
                is Result.Failure -> _uiState.value = AuthUiState.Unauthenticated(
                    errorMessage = readableMessage(result.exception),
                    fieldErrors = fieldErrorsOf(result.exception)
                )
            }
        }
    }

    /**
     * Keloguan dari semua peran menghentikan pengiriman posisi kurir.
     *
     * Service latar belakang tidak boleh hidup setelah sesi berakhir: token
     * sudah tidak valid, jadi pengiriman berikutnya (dan satu-satunya jalan
     * keluar lain, 401 dari endpoint lokasi) pasti ditolak. Mematikan di sini
     * menjamin tidak ada loop yang menyala tanpa session.
     */
    fun logout() {
        sharing.stop(TrackingStopReason.SESSION_EXPIRED)
        viewModelScope.launch {
            authRepository.logout()
            _entryPoint.value = AuthEntryPoint.LOGIN
            _uiState.value = AuthUiState.Unauthenticated()
        }
    }

    /** Clear only the field being edited; keep unrelated validation feedback visible. */
    fun clearFieldError(field: String) {
        _uiState.update { state ->
            if (state is AuthUiState.Unauthenticated) {
                state.copy(errorMessage = null, fieldErrors = state.fieldErrors - field)
            } else {
                state
            }
        }
    }

    fun clearError() {
        _uiState.update { state ->
            if (state is AuthUiState.Unauthenticated) {
                state.copy(errorMessage = null, fieldErrors = emptyMap())
            } else {
                state
            }
        }
    }

    private fun onSessionRestored(session: AuthSession?) {
        if (session == null) {
            _entryPoint.value = AuthEntryPoint.LOGIN
            _uiState.value = AuthUiState.Unauthenticated()
            return
        }
        onAuthenticated(session.user)
    }

    private fun onAuthenticated(user: User) {
        _entryPoint.value = AuthEntryPoint.fromRole(user.role)
        _uiState.value = AuthUiState.Authenticated(user)
    }

    /**
     * Backend login memakai `required|email|max:255`, jadi email kosong maupun
 * bentuk yang salah ditolak di sini sebelum request dikirim. Password hanya
 * diperiksa kosong; aturan panjang minimal milik backend, jadi tidak
 * ditirukan dua kali dengan nilai yang bisa berbeda.
 */
private fun validateLogin(email: String, password: String): Map<String, String> = buildMap {
        if (email.isBlank()) {
            put(FIELD_EMAIL, EMAIL_REQUIRED_MESSAGE)
        } else if (!isValidEmail(email)) {
            put(FIELD_EMAIL, EMAIL_FORMAT_MESSAGE)
        }
        if (password.isBlank()) {
            put(FIELD_PASSWORD, PASSWORD_REQUIRED_MESSAGE)
        }
    }

    /**
     * Backend register mewajibkan `email`, jadi email kosong adalah kesalahan
     * lokal, bukan sesuatu yang diteruskan ke server untuk ditolak dengan 422.
     */
    private fun validateRegister(request: RegisterRequest): Map<String, String> = buildMap {
        if (request.name.trim().length < MIN_NAME_LENGTH) {
            put(FIELD_NAME, "Nama minimal $MIN_NAME_LENGTH karakter.")
        }
        if (request.phone.trim().length < MIN_PHONE_LENGTH) {
            put(FIELD_PHONE, "Nomor telepon minimal $MIN_PHONE_LENGTH karakter.")
        }
        if (request.email.isBlank()) {
            put(FIELD_EMAIL, EMAIL_REQUIRED_MESSAGE)
        } else if (!isValidEmail(request.email)) {
            put(FIELD_EMAIL, EMAIL_FORMAT_MESSAGE)
        }
        if (request.password.length < MIN_PASSWORD_LENGTH) {
            put(FIELD_PASSWORD, "Kata sandi minimal $MIN_PASSWORD_LENGTH karakter.")
        }
        if (request.password != request.passwordConfirmation) {
            put(FIELD_PASSWORD_CONFIRMATION, "Konfirmasi kata sandi tidak cocok.")
        }
    }

    private fun fieldErrorsOf(exception: Exception): Map<String, String> {
        val validation = exception as? ValidationException ?: return emptyMap()
        return validation.errors.mapNotNull { (field, messages) ->
            messages.firstOrNull()?.let { normalizeField(field) to it }
        }.toMap()
    }

    private fun normalizeField(field: String): String = when (field.substringAfterLast('.')) {
        FIELD_NAME -> FIELD_NAME
        FIELD_PHONE -> FIELD_PHONE
        FIELD_PASSWORD -> FIELD_PASSWORD
        FIELD_PASSWORD_CONFIRMATION -> FIELD_PASSWORD_CONFIRMATION
        FIELD_EMAIL -> FIELD_EMAIL
        else -> field.substringAfterLast('.')
    }

    private fun readableMessage(exception: Exception): String =
        exception.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR_MESSAGE

    companion object {
        const val FIELD_NAME = "name"
        const val FIELD_PHONE = "phone"
        const val FIELD_EMAIL = "email"
        const val FIELD_PASSWORD = "password"
        const val FIELD_PASSWORD_CONFIRMATION = "password_confirmation"

        const val MIN_NAME_LENGTH = 3
        const val MIN_PHONE_LENGTH = 8
        const val MIN_PASSWORD_LENGTH = 8

        /**
         * Pesan lokal sengaja memakai teks yang sama dengan terjemahan di
         * [com.kyusui.app.core.ApiErrorParser], sehingga pesan yang tampil tidak
         * berubah hanya karena kesalahan ditemukan di sisi Android atau backend.
         */
        const val EMAIL_REQUIRED_MESSAGE = "Email wajib diisi."
        const val EMAIL_FORMAT_MESSAGE = "Format email tidak valid."
        const val PASSWORD_REQUIRED_MESSAGE = "Kata sandi wajib diisi."
        const val VALIDATION_MESSAGE = "Periksa kembali data yang Anda isi."
        const val GENERIC_ERROR_MESSAGE = "Terjadi kesalahan. Silakan coba lagi."
    }
}
