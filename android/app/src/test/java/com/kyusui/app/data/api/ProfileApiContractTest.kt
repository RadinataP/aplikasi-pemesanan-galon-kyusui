package com.kyusui.app.data.api

import com.kyusui.app.data.mapper.toDomain
import com.kyusui.app.domain.model.AccountProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test kontrak `GET/PUT /profile` (preserved).
 *
 * Specification 06 hanya mendefinisikan profil CUSTOMER, sedangkan backend
 * mengelola OWNER dan COURIER lewat endpoint generik ini. Fixture diambil dari
 * bentuk `UserResource` yang benar-benar dikirim backend (`data.user`).
 *
 * Yang dijaga test ini:
 *
 * - profil dibaca dari `data.user`, bukan dari `data` langsung;
 * - phone/vehicle diambil dari relasi `courier` dan tidak dikarang;
 * - Owner tidak ditampilkan memiliki phone/vehicle.
 */
class ProfileApiContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `profile is decoded from data user`() {
        val body = """
            {
              "data": {
                "user": {
                  "id": 5,
                  "name": "Warung Kyusui",
                  "email": "owner@kyusui.test",
                  "role": { "name": "OWNER" }
                }
              },
              "message": "Profil berhasil diambil."
            }
        """.trimIndent()

        val profile = json.decodeFromString<EnvelopeDto<AccountEnvelopeDto>>(body)
            .requireData()
            .user
            .toDomain()

        assertEquals("5", profile.id)
        assertEquals("Warung Kyusui", profile.name)
        assertEquals(AccountProfile.ROLE_OWNER, profile.role)
        assertNull(profile.phone)
        assertNull(profile.vehicle)
    }

    @Test
    fun `courier profile reads phone and vehicle from the courier relation`() {
        val body = """
            {
              "data": {
                "user": {
                  "id": 8,
                  "name": "Budi Kurir",
                  "role": { "name": "COURIER" },
                  "courier": { "phone": "0812000000", "vehicle": "Motor" }
                }
              }
            }
        """.trimIndent()

        val profile = json.decodeFromString<EnvelopeDto<AccountEnvelopeDto>>(body)
            .requireData()
            .user
            .toDomain()

        assertEquals(AccountProfile.ROLE_COURIER, profile.role)
        assertTrue(profile.isCourier)
        assertEquals("0812000000", profile.phone)
        assertEquals("Motor", profile.vehicle)
    }

    @Test
    fun `blank phone and vehicle are reported as absent`() {
        val body = """
            {"data":{"user":{"id":8,"name":"Budi","courier":{"phone":"","vehicle":""}}}}
        """.trimIndent()

        val profile = json.decodeFromString<EnvelopeDto<AccountEnvelopeDto>>(body)
            .requireData()
            .user
            .toDomain()

        assertNull(profile.phone)
        assertNull(profile.vehicle)
    }

    @Test
    fun `update request sends only the fields provided`() {
        val encoded = json.encodeToString(
            UpdateAccountProfileRequestDto(
                name = "Budi",
                phone = "0812000000",
                address = null,
                vehicle = "Motor",
                password = null,
                password_confirmation = null
            )
        )

        assertTrue(encoded.contains("\"name\":\"Budi\""))
        assertTrue(encoded.contains("\"phone\":\"0812000000\""))
        assertTrue(encoded.contains("\"vehicle\":\"Motor\""))
        assertFalse(encoded.contains("\"password\""))
        assertFalse(encoded.contains("\"address\""))
    }

    @Test
    fun `owner update request does not carry phone or vehicle`() {
        val encoded = json.encodeToString(
            UpdateAccountProfileRequestDto(name = "Warung Baru")
        )

        assertTrue(encoded.contains("\"name\":\"Warung Baru\""))
        assertFalse(encoded.contains("\"phone\""))
        assertFalse(encoded.contains("\"vehicle\""))
    }

    private fun <T> EnvelopeDto<T>.requireData(): T =
        requireNotNull(data) { "Respons backend tidak memuat data." }
}
