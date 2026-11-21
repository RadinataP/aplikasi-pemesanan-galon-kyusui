package com.kyusui.app.ui.owner.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.PrimaryButton

/**
 * Form tambah/ubah produk Owner.
 *
 * Nilai awal mode ubah datang dari argumen rute (tidak ada
 * `GET /owner/products/{id}`), dan penyimpanan tetap divalidasi backend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerProductFormScreen(
    state: OwnerProductFormUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAvailabilityChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.saved) {
        if (state.saved) {
            onSaved()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = if (state.isEdit) "Ubah Produk" else "Tambah Produk")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Informasi produk",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        text = "Lengkapi nama, harga, dan deskripsi agar pelanggan memahami produk yang ditawarkan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(text = "Nama produk") },
                singleLine = true,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.price,
                onValueChange = onPriceChange,
                label = { Text(text = "Harga") },
                singleLine = true,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(text = "Deskripsi (opsional)") },
                enabled = !state.isSaving,
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tersedia untuk dibeli",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Produk yang dinonaktifkan tidak muncul di katalog pelanggan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.isAvailable,
                    onCheckedChange = onAvailabilityChange,
                    enabled = !state.isSaving
                )
            }
                }
            }

            // Error ditampilkan di luar card form agar mudah ditemukan dan
            // tidak mengubah nilai input yang sudah diketik Owner.
            state.error?.let { InlineError(message = it) }

            PrimaryButton(
                text = if (state.isEdit) "Simpan Perubahan" else "Tambah Produk",
                onClick = onSave,
                enabled = state.name.isNotBlank() && !state.isSaving,
                isLoading = state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
