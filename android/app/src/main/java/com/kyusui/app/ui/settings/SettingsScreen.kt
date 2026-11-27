package com.kyusui.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kyusui.app.BuildConfig
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyusui.app.ui.courier.design.CourierColors
import com.kyusui.app.ui.courier.design.CourierPageHeader
import com.kyusui.app.ui.components.OutlinedButton
import com.kyusui.app.ui.components.PrimaryButton

/** Pengaturan yang tersedia saat ini; tidak menampilkan toggle preferensi fiktif. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit
) {
    val openPublicLink = rememberPublicLinkOpener()
    Scaffold(
        containerColor = CourierColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CourierPageHeader(
                eyebrow = "Preferensi aplikasi",
                title = "Pengaturan",
                subtitle = "Informasi aplikasi, keamanan, dan akses akun."
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Outlined.WaterDrop, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Berkah Water", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("Layanan pemesanan dan pengantaran air galon.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("Informasi aplikasi", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Pembayaran dan keamanan", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Pembayaran QRIS menunggu verifikasi pemilik depot. Pembayaran CASH " +
                            "dikonfirmasi oleh kurir. Status transaksi mengikuti data server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Versi ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                text = "Pelajari cara kerja Berkah Water",
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth()
            )
            listOf(
                "Panduan penggunaan" to "manual-book",
                "Kebijakan privasi" to "privacy-policy",
                "Syarat dan ketentuan" to "terms-and-conditions",
                "Bantuan dan dukungan" to "support",
                "Ajukan penghapusan akun" to "delete-account"
            ).forEach { (label, path) ->
                OutlinedButton(text = label, onClick = { openPublicLink(path) }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.size(4.dp))
            PrimaryButton(
                text = "Keluar dari akun",
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
