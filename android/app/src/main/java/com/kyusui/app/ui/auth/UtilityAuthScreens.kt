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

/** Reset otomatis belum dihubungkan karena backend saat ini belum menyediakan endpoint reset password. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text("Pemulihan akun") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke masuk") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF062B45), Color(0xFF087F9B), Color(0xFF13B6BD))), RoundedCornerShape(28.dp)).padding(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .14f)) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = Color.White, modifier = Modifier.padding(14.dp))
                    }
                    Text("Pulihkan akses akun", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text("Kami bantu arahkan langkah yang aman untuk mendapatkan kembali akses Berkah Water.",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .86f))
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Reset kata sandi otomatis belum tersedia", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Saat ini backend Berkah Water belum menyediakan endpoint untuk mengirim tautan atau kode reset kata sandi. Karena itu, aplikasi belum dapat memproses reset secara otomatis.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Untuk sementara, hubungi pemilik atau pengelola Berkah Water agar akun dapat dibantu melalui proses verifikasi.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(2.dp))
            PrimaryButton(text = "Kembali ke halaman masuk", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}
