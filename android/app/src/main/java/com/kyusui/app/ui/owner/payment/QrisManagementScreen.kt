package com.kyusui.app.ui.owner.payment

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kyusui.app.core.ProofUploadPolicy
import com.kyusui.app.core.QrisImageUrlResolver
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InfoRow
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.components.SecondaryButton
import com.kyusui.app.ui.state.LoadState

/**
 * Manajemen QRIS aktif oleh Owner.
 *
 * Sumber data layar ini:
 *
 * - `GET /owner/payment/qris` untuk konfigurasi aktif (specification 13.1)
 * - `PUT /owner/payment/qris` untuk menggantinya, multipart `qris_image` (13.2)
 *
 * QRIS tidak pernah di-hardcode dan tidak pernah disimpan di Android sebagai
 * source of truth. Setelah penggantian, gambar yang ditampilkan adalah gambar
 * yang dikembalikan backend, bukan file lokal yang baru dipilih.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrisManagementScreen(
    uiState: QrisManagementUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onImagePicked: (Uri) -> Unit,
    onClearSelection: () -> Unit,
    onReplace: () -> Unit,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    // Android Photo Picker dipakai agar Owner tidak perlu memberi izin baca
    // media. `Selected = false` membatasi ke gambar yang sudah ada di perangkat.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onImagePicked) }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Pengaturan QRIS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Muat ulang"
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "QRIS Aktif", style = MaterialTheme.typography.titleMedium)

            when (val state = uiState.qrisState) {
                is LoadState.Loading -> LoadingState(
                    modifier = Modifier.heightIn(min = 200.dp),
                    message = "Memuat QRIS aktif..."
                )

                is LoadState.Error -> if (state.isNotFound) {
                    // Fallback defensif untuk backend lama; kontrak saat ini
                    // memakai HTTP 200 dengan data:null untuk state kosong.
                    NotConfiguredNotice()
                } else {
                    ErrorState(
                        modifier = Modifier.heightIn(min = 200.dp),
                        message = state.message,
                        isRetryable = state.isRetryable,
                        onRetry = onRefresh
                    )
                }

                is LoadState.Empty -> NotConfiguredNotice()

                is LoadState.Success -> ActiveQrisSection(
                    imageLoader = imageLoader,
                    qrisImage = state.data.qrisImage,
                    updatedAt = state.data.updatedAt
                )
            }

            ReplacementSection(
                uiState = uiState,
                onPickFile = {
                    picker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                onClearSelection = onClearSelection,
                onReplace = onReplace
            )
        }
    }
}

/**
 * Tampilan QRIS aktif dari backend.
 *
 * URL gambar selalu lewat [QrisImageUrlResolver] supaya request hanya menuju
 * origin API. Nilai mentah dari backend tidak pernah dipakai langsung sebagai
 * model Coil, dan ketika tidak bisa di-resolve, Android menampilkan pemberitahuan
 * alih-alih memuat dari host lain atau mengarang QRIS.
 */
@Composable
private fun ActiveQrisSection(
    imageLoader: ImageLoader,
    qrisImage: String?,
    updatedAt: String?
) {
    val qrisImageUrl = remember(qrisImage) {
        QrisImageUrlResolver.resolve(qrisImage)
    }

    if (qrisImageUrl == null) {
        Text(
            text = "Gambar QRIS tidak dapat dimuat dengan aman. Periksa koneksi Anda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(qrisImageUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "QRIS aktif Berkah Water",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
            )

            updatedAt?.let {
                InfoRow(label = "Terakhir diperbarui", value = it)
            }
        }
    }
}

/**
 * Bagian penggantian QRIS.
 */
@Composable
private fun ReplacementSection(
    uiState: QrisManagementUiState,
    onPickFile: () -> Unit,
    onClearSelection: () -> Unit,
    onReplace: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalRule()
        Text(text = "Ganti QRIS", style = MaterialTheme.typography.titleMedium)

        Text(
            text = "Ganti gambar QRIS yang aktif untuk toko. Pelanggan akan " +
                "melihat QRIS baru setelah backend menyimpannya.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val selectedImage = uiState.selectedImage

        if (selectedImage == null) {
            PrimaryButton(
                text = "Pilih Gambar QRIS",
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            AsyncImage(
                model = Uri.parse(selectedImage.localUri),
                contentDescription = "Pratinjau QRIS yang dipilih",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
            )

            SecondaryButton(
                text = "Ganti Foto",
                onClick = onClearSelection,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isReplacing
            )

            PrimaryButton(
                text = "Simpan QRIS Baru",
                onClick = onReplace,
                modifier = Modifier.fillMaxWidth(),
                // `canReplace` sudah memperhitungkan selection dan status
                // submitting, sehingga tombol ini sekaligus menjadi guard
                // double submit.
                enabled = uiState.canReplace,
                isLoading = uiState.isReplacing
            )
        }

        (uiState.selectionState as? SelectionState.Error)?.let { error ->
            InlineError(message = error.message)
        }

        uiState.replacementErrorMessage?.let { InlineError(message = it) }

        if (uiState.replacementMessage != null) {
            Text(
                text = uiState.replacementMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "Format JPG atau PNG, maksimal ${ProofUploadPolicy.MAX_SIZE_LABEL}. " +
                "Ukuran dan format divalidasi ulang oleh server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NotConfiguredNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "QRIS belum dikonfigurasi",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Belum ada QRIS aktif di server, jadi pembayaran QRIS " +
                    "belum tersedia untuk pelanggan. Pilih gambar QRIS di bawah " +
                    "untuk memasangnya.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun HorizontalRule() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}