package com.kyusui.app.data.repository

import com.kyusui.app.core.Result
import com.kyusui.app.core.safeApiCall
import com.kyusui.app.data.api.EnvelopeDto
import com.kyusui.app.data.api.ProfileApi
import com.kyusui.app.data.api.UpdateAccountProfileRequestDto
import com.kyusui.app.data.mapper.toDomain
import com.kyusui.app.domain.model.AccountProfile
import com.kyusui.app.domain.repository.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileApi: ProfileApi
) : ProfileRepository {

    override suspend fun getProfile(): Result<AccountProfile> = safeApiCall {
        profileApi.profile().requireData().user.toDomain()
    }

    override suspend fun updateProfile(
        name: String,
        phone: String?,
        address: String?,
        vehicle: String?,
        password: String?
    ): Result<AccountProfile> = safeApiCall {
        val trimmedPassword = password?.takeIf { it.isNotEmpty() }
        profileApi.updateProfile(
            UpdateAccountProfileRequestDto(
                name = name.trim().takeIf { it.isNotEmpty() },
                phone = phone?.trim()?.takeIf { it.isNotEmpty() },
                address = address?.trim()?.takeIf { it.isNotEmpty() },
                vehicle = vehicle?.trim()?.takeIf { it.isNotEmpty() },
                // Backend memvalidasi `confirmed`; konfirmasi dikirim dengan
                // nilai yang sama karena UI tidak meminta pengguna mengetik dua
                // kali untuk perubahan yang tidak sensitif.
                password = trimmedPassword,
                password_confirmation = trimmedPassword
            )
        ).requireData().user.toDomain()
    }

    private fun <T> EnvelopeDto<T>.requireData(): T =
        data ?: throw IllegalStateException("Respons backend tidak memuat data yang diminta.")
}
