package com.kyusui.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.data.api.OtpRequest
import com.kyusui.app.data.api.OtpResetRequest
import com.kyusui.app.data.api.PasswordOtpApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import javax.inject.Inject

@HiltViewModel
class PasswordOtpViewModel @Inject constructor(retrofit: Retrofit) : ViewModel() {
    private val api = retrofit.create(PasswordOtpApi::class.java)
    var step by mutableStateOf(0)
        private set
    var email by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun changeEmail() {
        if (busy) return
        step = 0
        message = null
        error = null
    }

    fun requestCode(rawEmail: String) {
        if (busy) return
        val target = rawEmail.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches() || target.length > 255) {
            error = "Masukkan alamat email yang valid."; return
        }
        busy = true; error = null; message = null
        viewModelScope.launch {
            try {
                val response = api.requestCode(OtpRequest(target))
                if (response.code() == 202) {
                    email = target; step = 1
                    message = "Jika akun aktif dan terdaftar, kode dikirim ke email. Periksa juga folder spam."
                } else error = when (response.code()) {
                    429 -> "Terlalu banyak permintaan. Coba lagi nanti."
                    503 -> "Layanan email belum aktif. Hubungi pengelola."
                    404, 405 -> "API pemulihan belum terpasang pada server."
                    422 -> "Periksa kembali alamat email."
                    else -> "Server belum dapat memproses permintaan. Coba lagi."
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Tidak dapat menghubungi server. Periksa koneksi internet." }
            finally { busy = false }
        }
    }

    fun reset(code: String, password: String, confirmation: String) {
        if (busy || step != 1) return
        if (!code.matches(Regex("[0-9]{6}"))) { error = "Masukkan kode 6 digit."; return }
        if (password.length < 8 || password.toByteArray(Charsets.UTF_8).size > 72 ||
            !password.any { it.isUpperCase() } || !password.any { it.isLowerCase() } || !password.any { it.isDigit() }) {
            error = "Gunakan minimal 8 karakter, huruf besar, huruf kecil, dan angka (maksimal 72 byte)."; return
        }
        if (password != confirmation) { error = "Konfirmasi kata sandi tidak cocok."; return }
        busy = true; error = null; message = null
        viewModelScope.launch {
            try {
                val response = api.reset(OtpResetRequest(email, code, password, confirmation))
                if (response.isSuccessful && response.body()?.message != null) {
                    step = 2
                } else error = when (response.code()) {
                    422 -> "Kode tidak valid, kedaluwarsa, atau data belum sesuai. Periksa isian atau minta kode baru."
                    429 -> "Terlalu banyak percobaan. Coba lagi nanti."
                    404, 405 -> "API pemulihan belum terpasang pada server."
                    else -> "Server belum dapat mengubah kata sandi. Coba lagi."
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Tidak dapat menghubungi server. Periksa koneksi internet." }
            finally { busy = false }
        }
    }
}
