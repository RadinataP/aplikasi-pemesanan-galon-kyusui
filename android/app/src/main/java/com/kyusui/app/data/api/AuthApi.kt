package com.kyusui.app.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Auth API sesuai `06_Berkah Water_API_SPECIFICATION_REBUILT.md` section 8.
 *
 * Hanya empat endpoint yang dipakai:
 * - `POST /auth/register` (8.1)
 * - `POST /auth/login`    (8.2)
 * - `POST /auth/logout`   (8.3)
 * - `GET  /auth/me`       (8.4)
 *
 * Request dan response mengikuti bentuk yang tertulis di specification.
 * Password dan token tidak pernah masuk ke log maupun source.
 */
interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body request: AuthRegisterRequestDto): AuthEnvelopeDto<AuthSessionDto>

    @POST("auth/login")
    suspend fun login(@Body request: AuthLoginRequestDto): AuthEnvelopeDto<AuthSessionDto>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("auth/me")
    suspend fun currentUser(): AuthCurrentUserDto
}

// --- Request (section 8.1 dan 8.2) ---

/**
 * Section 8.1. Register adalah registrasi customer anonim, sehingga `role`
 * tidak pernah dikirim dari Android. Role ditetapkan backend.
 *
 * `email` wajib dan selalu dikirim. Backend memvalidasinya dengan `required`,
 * jadi nullable di sini hanya akan menghasilkan 422 yang sudah bisa dihindari.
 */
@Serializable
data class AuthRegisterRequestDto(
    val name: String,
    val phone: String,
    val email: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String
)

/**
 * Section 8.2. Backend mengidentifikasi pengguna lewat `email` dengan aturan
 * `required|email|max:255`. Nama field di sini adalah nama field di backend;
 * field `login` tidak pernah ada di contract login.
 */
@Serializable
data class AuthLoginRequestDto(
    val email: String,
    val password: String
)

// --- Response envelope (section 4.4) ---

@Serializable
data class AuthEnvelopeDto<T>(
    val data: T? = null,
    val message: String? = null
)

@Serializable
data class AuthSessionDto(
    val user: AuthUserDto,
    val token: String
)

/**
 * Section 7.1 User Resource.
 *
 * Specification tidak menyertakan `created_at` dan `updated_at` pada User
 * Resource, dan `role` berupa object `name` + `display_name`, bukan string.
 *
 * Ketidaksesuaian yang masih tersisa terhadap `UserResource` backend, dan disengaja
 * dibiarkan nullable agar decoding tidak gagal total:
 * - backend mengirim `role` hanya berisi `id` + `name`, jadi `display_name` null
 *   dan `User.roleLabel()` memakai nama bawaan per role;
 * - backend tidak mengirim `status`, jadi `User.isActive()` bernilai false;
 * - nomor telepon ada di bawah `customer.phone` dan `courier.phone`, bukan di
 *   tingkat atas, jadi `phone` null pada respons backend mana pun.
 */
@Serializable
data class AuthUserDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val role: AuthRoleDto,
    val status: String? = null
)

@Serializable
data class AuthRoleDto(
    val name: String,
    val display_name: String? = null
)

/**
 * Section 8.4. `GET /auth/me` membungkus User Resource satu level dalam di bawah
 * `data`:
 *
 * ```json
 * { "data": { "user": { "id": 1, "name": "Budi", ... } }, "message": "..." }
 * ```
 *
 * Bentuk ini adalah satu-satunya yang pernah dikirim backend untuk endpoint ini,
 * jadi tidak ada bentuk alternatif yang ditebak. Respons yang tidak memuat
 * `data.user` menghasilkan `null` dari [resolveUser] dan ditangani pemanggilnya
 * sebagai session tidak berlaku, bukan sebagai user dengan field kosong.
 */
@Serializable
data class AuthCurrentUserDto(
    val data: AuthCurrentUserDataDto? = null,
    val message: String? = null
) {
    fun resolveUser(): AuthUserDto? = data?.user
}

@Serializable
data class AuthCurrentUserDataDto(
    val user: AuthUserDto? = null
)

/**
 * Specification menulis `id` sebagai angka. Serializer ini menerima angka maupun
 * string agar perubahan tipe di backend tidak membuat session restoration crash.
 */
object TolerantIdSerializer : KSerializer<Long> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TolerantId", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeLong()
        val primitive: JsonPrimitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        return primitive.content.trim().toLongOrNull()
            ?: throw IllegalArgumentException("User id is not a valid number: ${primitive.content}")
    }
}
