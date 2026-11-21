package com.kyusui.app.domain.repository

import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.AuthResult
import com.kyusui.app.domain.model.AuthSession
import com.kyusui.app.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Register Customer sesuai API specification section 8.1.
 *
 * `role` tidak ada di sini. Registrasi adalah customer anonim dan role
 * ditetapkan backend, sehingga Android tidak pernah mengirimnya.
 *
 * `email` wajib diisi karena backend memvalidasinya dengan `required`. Menelepon
 * dengan email kosong berarti mengirim 422 yang pasti ditolak, jadi Android
 * gagal lebih dulu di [validateRegister] milik AuthViewModel.
 */
data class RegisterRequest(
    val name: String,
    val phone: String,
    val email: String,
    val password: String,
    val passwordConfirmation: String
)

/**
 * Login sesuai API specification section 8.2.
 *
 * Backend mengidentifikasi pengguna lewat satu field `email` dengan aturan
 * `required|email|max:255`. Nomor telepon bukan identitas login, jadi tidak ada
 * field `login` maupun nomor telepon pada request ini.
 */
data class LoginRequest(
    val email: String,
    val password: String
)

interface AuthRepository {

    val session: Flow<AuthSession?>

    suspend fun register(request: RegisterRequest): Result<AuthResult>

    suspend fun login(request: LoginRequest): Result<AuthResult>

    /**
     * Selalu membersihkan session lokal, baik server menerima logout
     * maupun tidak. Token lokal tidak boleh tetap hidup setelah logout.
     */
    suspend fun logout(): Result<Unit>

    /**
     * Session restoration. Memakai token tersimpan untuk memvalidasi ulang
     * lewat `GET /auth/me`. Bila backend menjawab 401, atau menjawab tanpa
     * `data.user`, session lokal dihapus karena token itu tidak dapat dipakai lagi.
     * Gangguan jaringan diteruskan sebagai kegagalan supaya pengguna dapat
     * mencoba ulang tanpa kehilangan session lokal.
     */
    suspend fun restoreSession(): Result<AuthSession?>

    suspend fun currentUser(): Result<User>

    suspend fun clearLocalSession(): Result<Unit>
}
