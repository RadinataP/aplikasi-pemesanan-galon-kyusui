package com.kyusui.app.core

/**
 * Validasi bentuk email sebelum request dikirim ke backend.
 *
 * Backend memvalidasi `email` dengan aturan `email` Laravel, yang dihitung dari
 * `filter_var($value, FILTER_VALIDATE_EMAIL)` dan mensyaratkan domain bertingkat
 * titik. Pola di bawah memakai syarat yang sama, sehingga validasi lokal tidak
 * menerima alamat yang pasti ditolak backend, dan tidak menolak alamat commitsan
 * yang akan diterima backend.
 *
 * Sengaja tanpa `android.util.Patterns`: fungsi ini dipanggil dari ViewModel dan
 * harus bisa diuji di JVM unit test tanpa Robolectric.
 *
 * Kesalahan format yang lolos ke backend tetap ditangani sebagai HTTP 422 biasa,
 * jadi validasi lokal ini hanya penghematan perjalanan, bukan pengganti aturan
 * backend.
 */
private val EMAIL_PATTERN = Regex(
    "^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*" +
        "@(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
)

fun isValidEmail(value: String): Boolean = EMAIL_PATTERN.matches(value.trim())