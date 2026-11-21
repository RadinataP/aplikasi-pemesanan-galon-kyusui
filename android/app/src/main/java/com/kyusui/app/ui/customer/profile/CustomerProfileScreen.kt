package com.kyusui.app.ui.customer.profile

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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyusui.app.ui.customer.CustomerBottomBar
import com.kyusui.app.ui.customer.CustomerTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileScreen(
    state: CustomerProfileUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (() -> Unit)? = null,
    editing: Boolean = true,
    onNavigateHome: (() -> Unit)? = null,
    onNavigateProducts: (() -> Unit)? = null,
    onNavigateOrders: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!editing && onNavigateHome != null && onNavigateProducts != null && onNavigateOrders != null) {
                CustomerBottomBar(CustomerTab.ACCOUNT, onNavigateHome, onNavigateProducts, onNavigateOrders, {})
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit profil" else "Akun saya", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = { if (!editing) TextButton(onClick = { onEdit?.invoke() }) { Text("Edit profil") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Column(
                Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Memuat profil…") }

            state.profile == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(12.dp)); Text(state.error ?: "Profil belum tersedia.")
                Spacer(Modifier.height(12.dp)); Button(onClick = onRetry) { Text("Coba lagi") }
            }

            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(62.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(state.profile.name.trim().firstOrNull()?.uppercase() ?: "B", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(state.profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(state.profile.email ?: "Email belum diisi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("BERKAH WATER CUSTOMER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (!state.error.isNullOrBlank()) Text(state.error, color = MaterialTheme.colorScheme.error)
                if (!state.message.isNullOrBlank()) Text(state.message, color = MaterialTheme.colorScheme.primary)

                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Text("Informasi pribadi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = state.name, onValueChange = onNameChange, label = { Text("Nama lengkap") }, leadingIcon = { Icon(Icons.Outlined.Person, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = editing && !state.isSaving, shape = RoundedCornerShape(15.dp))
                        OutlinedTextField(value = state.phone, onValueChange = onPhoneChange, label = { Text("Nomor telepon") }, leadingIcon = { Icon(Icons.Outlined.Call, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = editing && !state.isSaving, shape = RoundedCornerShape(15.dp))
                        OutlinedTextField(value = state.email, onValueChange = onEmailChange, label = { Text("Email") }, leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = editing && !state.isSaving, shape = RoundedCornerShape(15.dp))
                        OutlinedTextField(value = state.address, onValueChange = onAddressChange, label = { Text("Alamat default") }, leadingIcon = { Icon(Icons.Outlined.Home, null) }, modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = editing && !state.isSaving, shape = RoundedCornerShape(15.dp))
                    }
                }

                if (editing) {
                    Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(17.dp)) {
                        if (state.isSaving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(19.dp)) else Text("Simpan perubahan", fontWeight = FontWeight.Bold)
                    }
                }
                if (!editing && onLogout != null) {
                    Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Keluar dari akun", fontWeight = FontWeight.Bold)
                    }
                }
                Text("Data profil disimpan melalui server Berkah Water.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
