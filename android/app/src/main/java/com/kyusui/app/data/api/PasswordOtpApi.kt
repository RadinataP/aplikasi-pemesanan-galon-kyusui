package com.kyusui.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PasswordOtpApi {
    @POST("auth/forgot-password")
    suspend fun requestCode(@Body request: OtpRequest): Response<OtpMessage>

    @POST("auth/reset-password")
    suspend fun reset(@Body request: OtpResetRequest): Response<OtpMessage>
}

@Serializable data class OtpRequest(val email: String)
@Serializable data class OtpResetRequest(
    val email: String,
    val code: String,
    val password: String,
    @SerialName("password_confirmation") val confirmation: String
)
@Serializable data class OtpMessage(val message: String? = null)
