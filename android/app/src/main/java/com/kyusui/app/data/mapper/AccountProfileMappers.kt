package com.kyusui.app.data.mapper

import com.kyusui.app.data.api.AccountUserDto
import com.kyusui.app.domain.model.AccountProfile

/**
 * Mengubah `UserResource` dari `GET /profile` menjadi [AccountProfile].
 *
 * Nomor telepon diambil dari relasi `courier` lebih dulu karena pada response
 * generik `phone` di level user hanya terisi bila role memilikinya di relasi.
 * Field yang tidak dikirim tidak dikarang.
 */
fun AccountUserDto.toDomain(): AccountProfile = AccountProfile(
    id = id.toString(),
    name = name,
    email = email?.takeIf { it.isNotBlank() },
    phone = (courier?.phone ?: phone)?.takeIf { it.isNotBlank() },
    role = role?.name?.takeIf { it.isNotBlank() },
    address = null,
    vehicle = courier?.vehicle?.takeIf { it.isNotBlank() }
)
