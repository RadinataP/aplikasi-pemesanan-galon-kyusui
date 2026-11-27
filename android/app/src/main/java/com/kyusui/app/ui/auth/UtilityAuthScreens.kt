package com.kyusui.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyusui.app.ui.components.PrimaryButton

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Tentang Berkah Water") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AuthBrandHeader("Air minum lebih mudah.", "Layanan pemesanan air dari Berkah Water.", Modifier.fillMaxWidth(), compact = true, showBrandEyebrow = true)
            Text("Cara menggunakan layanan", style = MaterialTheme.typography.titleLarge)
            OnboardingFeature("1. Pilih produk dan buat pesanan", "Pilih produk, tentukan jumlah, lalu isi alamat pengantaran dan konfirmasi titik lokasi pada peta.")
            OnboardingFeature("2. Pilih metode pembayaran", "Gunakan QRIS dengan mengunggah bukti pembayaran untuk diverifikasi pengelola, atau pilih CASH dan serahkan uang kepada kurir.")
            OnboardingFeature("3. Pantau pengantaran", "Saat pesanan sedang diantar, posisi kurir ditampilkan berdasarkan pembaruan lokasi dari aplikasi kurir.")
            Text("Harga, status pesanan, dan status pembayaran ditentukan oleh sistem layanan. Pembayaran QRIS tidak dianggap berhasil sebelum diverifikasi.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton(text = "Mulai", onClick = onContinue, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OnboardingFeature(title: String, description: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, viewModel: PasswordOtpViewModel = hiltViewModel()) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val step = viewModel.step
    val busy = viewModel.busy
    androidx.compose.runtime.LaunchedEffect(step) {
        if (step != 1) { code = ""; password = ""; confirmation = "" }
        if (step == 0 && viewModel.email.isNotBlank()) email = viewModel.email
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text("Pemulihan akun") }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF062B45), Color(0xFF087F9B), Color(0xFF13B6BD))), RoundedCornerShape(28.dp)).padding(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("BERKAH WATER", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium)
                    Text(when (step) { 0 -> "Lupa kata sandi?"; 1 -> "Atur kata sandi baru"; else -> "Akun berhasil dipulihkan" }, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Text(when (step) { 0 -> "Langkah 1 dari 2 • Email akun"; 1 -> "Langkah 2 dari 2 • Kode dan kata sandi"; else -> "Silakan masuk dengan kata sandi baru." }, color = Color.White.copy(alpha = .9f))
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (step) {
                        0 -> {
                            Text("Masukkan email yang terdaftar pada akun Berkah Water.")
                            OutlinedTextField(value = email, onValueChange = { email = it }, enabled = !busy, singleLine = true, label = { Text("Alamat email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                            PrimaryButton(text = "Kirim kode OTP", onClick = { viewModel.requestCode(email) }, enabled = email.isNotBlank(), isLoading = busy)
                        }
                        1 -> {
                            Text("Email: ${viewModel.email}", style = MaterialTheme.typography.bodyMedium)
                            Text("Gunakan kode terbaru dari email. Kode berlaku 10 menit.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(value = code, onValueChange = { code = it.filter { c -> c in '0'..'9' }.take(6) }, enabled = !busy, label = { Text("Kode OTP (6 digit)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = password, onValueChange = { password = it }, enabled = !busy, label = { Text("Kata sandi baru") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = confirmation, onValueChange = { confirmation = it }, enabled = !busy, label = { Text("Konfirmasi kata sandi") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                            Text("Minimal 8 karakter, huruf besar, huruf kecil, dan angka.", style = MaterialTheme.typography.bodySmall)
                            PrimaryButton(text = "Simpan kata sandi", onClick = { viewModel.reset(code, password, confirmation) }, enabled = code.length == 6 && password.isNotBlank() && confirmation.isNotBlank(), isLoading = busy)
                            TextButton(enabled = !busy, onClick = { code = ""; viewModel.requestCode(viewModel.email) }) { Text("Kirim ulang kode (tunggu 60 detik)") }
                            TextButton(enabled = !busy, onClick = { viewModel.changeEmail() }) { Text("Ganti alamat email") }
                        }
                        else -> {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Kata sandi berhasil diperbarui. Sesi login sebelumnya telah dicabut.")
                            PrimaryButton(text = "Kembali ke login", onClick = onBack)
                        }
                    }
                    viewModel.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
