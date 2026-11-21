package com.kyusui.app.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * API profil generik untuk OWNER dan COURIER.
 *
 * Endpoint `GET/PUT /profile` adalah preserved endpoint (tidak ada di katalog
 * spec 06 section 30). Specification hanya mendefinisikan profil untuk CUSTOMER
 * (section 9.1 dan 9.2), sedangkan backend mengelola profil Owner dan Courier
 * lewat endpoint ini. Android mengikuti kontrak backend yang benar-benar
 * berjalan.
 *
 * Perbedaan bentuk dengan `GET /customer/profile`: response di sini membalut
 * `UserResource` di dalam `data.user`, bukan profile langsung di dalam `data`,
 * dan field per role berada di relasi `customer`/`owner`/`courier`.
 */
interface ProfileApi {

    @GET("profile")
    suspend fun profile(): EnvelopeDto<AccountEnvelopeDto>

    @PUT("profile")
    suspend fun updateProfile(
        @Body request: UpdateAccountProfileRequestDto
    ): EnvelopeDto<AccountEnvelopeDto>
}

/** `data` pada `GET/PUT /profile`. */
@Serializable
data class AccountEnvelopeDto(
    val user: AccountUserDto
)

/**
 * `UserResource` yang dikirim backend.
 *
 * Hanya field yang dipakai layar profil yang dimodelkan. `owner` tidak punya
 * phone/vehicle, jadi hubungan itu tidak perlu dibaca.
 */
@Serializable
data class AccountUserDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val status: String? = null,
    val role: AccountRoleDto? = null,
    val courier: AccountCourierDto? = null
)

@Serializable
data class AccountRoleDto(
    val name: String = ""
)

@Serializable
data class AccountCourierDto(
    val phone: String? = null,
    val vehicle: String? = null
)

/**
 * Body `PUT /profile`.
 *
 * Backend menerima `name`, `phone`, `address`, `vehicle`, dan `password`
 * (dengan `password_confirmation`). Field yang tidak dikirim dibiarkan null dan
 * diabaikan backend.
 */
@Serializable
data class UpdateAccountProfileRequestDto(
    val name: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val vehicle: String? = null,
    val password: String? = null,
    val password_confirmation: String? = null
)
