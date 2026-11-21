package com.kyusui.app.ui.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kyusui.app.ui.courier.design.CourierPageHeader
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.PrimaryButton

/** Profil bersama Owner dan Courier; field tetap mengikuti kemampuan backend. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountProfileScreen(
    title: String,
    state: AccountProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onVehicleChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit = {},
    onPasswordConfirmationChange: (String) -> Unit = {},
    onSave: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { bottomBar?.invoke() },
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Muat ulang")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        val profile = state.profile
        when {
            state.isLoading && profile == null -> LoadingState(
                modifier = Modifier.padding(innerPadding), message = "Memuat profil..."
            )
            profile == null -> ErrorState(
                modifier = Modifier.padding(innerPadding),
                message = state.error ?: "Profil belum tersedia.",
                isRetryable = true,
                onRetry = onRetry
            )
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (profile.isCourier) {
                    CourierPageHeader(
                        eyebrow = "Akun / Courier",
                        title = "Profil kurir",
                        subtitle = "Kelola identitas dan informasi operasional akun."
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(62.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(profile.name.trim().firstOrNull()?.uppercase() ?: "B",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text(profile.role?.takeIf { it.isNotBlank() } ?: "Peran tidak tersedia",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            profile.email?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Text("Informasi akun", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = state.name, onValueChange = onNameChange,
                            label = { Text("Nama") }, singleLine = true,
                            enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp)
                        )
                        if (profile.isCourier) {
                            OutlinedTextField(
                                value = state.phone, onValueChange = onPhoneChange,
                                label = { Text("Nomor telepon") }, singleLine = true,
                                enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp)
                            )
                            OutlinedTextField(
                                value = state.vehicle, onValueChange = onVehicleChange,
                                label = { Text("Kendaraan") }, singleLine = true,
                                enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp)
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Keamanan akun", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                        }
                        Text("Kosongkan kedua kolom jika tidak ingin mengganti kata sandi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = state.password, onValueChange = onPasswordChange,
                            label = { Text("Kata sandi baru") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp)
                        )
                        OutlinedTextField(
                            value = state.passwordConfirmation,
                            onValueChange = onPasswordConfirmationChange,
                            label = { Text("Konfirmasi kata sandi baru") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp)
                        )
                    }
                }

                state.error?.let { InlineError(message = it) }
                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                PrimaryButton(
                    text = "Simpan perubahan", onClick = onSave,
                    enabled = state.name.isNotBlank() && !state.isSaving,
                    isLoading = state.isSaving, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Perubahan profil dikirim ke server dan dimuat ulang setelah berhasil disimpan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
