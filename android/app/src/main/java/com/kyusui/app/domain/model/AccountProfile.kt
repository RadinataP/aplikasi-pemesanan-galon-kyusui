package com.kyusui.app.domain.model

/**
 * Profil akun dari `GET /profile` (OWNER dan COURIER).
 *
 * Backend `UserResource` menempatkan field per role di relasi terpisah:
 * `owner` hanya punya id, sedangkan `courier` punya `phone` dan `vehicle`.
 * Karena itu [address] hanya terisi bila backend mengirimkannya lewat role yang
 * memang menyimpannya.
 */
data class AccountProfile(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val address: String? = null,
    val vehicle: String? = null
) {
    val isOwner: Boolean get() = role == ROLE_OWNER
    val isCourier: Boolean get() = role == ROLE_COURIER

    companion object {
        const val ROLE_OWNER = "OWNER"
        const val ROLE_COURIER = "COURIER"
    }
}
