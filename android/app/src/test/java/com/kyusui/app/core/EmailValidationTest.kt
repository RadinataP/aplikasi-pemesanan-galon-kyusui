package com.kyusui.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validasi lokal harus berhenti di tempat yang sama dengan aturan `email`
 * Laravel, yaitu `filter_var($value, FILTER_VALIDATE_EMAIL)`. Kegagalan di sini
 * hanya menghindari perjalanan yang pasti ditolak backend; backend tetap
 * memvalidasi ulang dan HTTP 422 tetap ditangani seperti biasa.
 */
class EmailValidationTest {

    @Test
    fun `ordinary addresses are accepted`() {
        listOf(
            "budi@example.com",
            "budi.santoso@example.co.id",
            "budi+tag@example.com",
            "budi_santoso99@sub.domain.example.org",
            "BUDI@EXAMPLE.COM"
        ).forEach { address ->
            assertTrue(address, isValidEmail(address))
        }
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertTrue(isValidEmail("  budi@example.com  "))
    }

    @Test
    fun `malformed addresses are rejected`() {
        listOf(
            "",
            "   ",
            "budi",
            "budi@",
            "@example.com",
            "budi@example",
            "budi example@domain.com",
            "budi@exa mple.com",
            "budi@@example.com",
            "budi@example.c"
        ).forEach { address ->
            assertFalse(address, isValidEmail(address))
        }
    }

    /**
     * Nomor telepon pernah menjadi field login di Android. Setelah backend
     * menetapkan `email` sebagai identitas, nomor telepon harus ditolak oleh
     * validasi lokal, bukan dikirim lalu ditolak dengan 422.
     */
    @Test
    fun `phone numbers are rejected because email is the only login identity`() {
        listOf("081234567890", "+6281234567890", "6281234567890").forEach { phone ->
            assertFalse(phone, isValidEmail(phone))
        }
    }
}