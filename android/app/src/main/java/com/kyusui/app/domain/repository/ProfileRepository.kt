package com.kyusui.app.domain.repository

import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.AccountProfile

/**
 * Profil akun untuk OWNER dan COURIER lewat `GET/PUT /profile`.
 *
 * Endpoint ini preserved: tidak ada di katalog spec 06 section 30, tetapi
 * backend memakainya untuk mengelola profil kedua role tersebut. Endpoint
 * terpisah `GET /customer/profile` tetap dipakai Customer lewat
 * [CustomerRepository].
 */
interface ProfileRepository {

    suspend fun getProfile(): Result<AccountProfile>

    /**
     * Menyimpan perubahan profil.
     *
     * [password] bersifat opsional; bila diisi, backend juga memerlukan
     * konfirmasi yang dikirim dengan nilai yang sama.
     */
    suspend fun updateProfile(
        name: String,
        phone: String?,
        address: String?,
        vehicle: String?,
        password: String?
    ): Result<AccountProfile>
}
