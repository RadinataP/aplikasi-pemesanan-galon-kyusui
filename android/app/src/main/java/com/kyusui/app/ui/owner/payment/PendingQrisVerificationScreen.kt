package com.kyusui.app.ui.owner.payment

import com.kyusui.app.ui.courier.design.CourierStatusTone
import com.kyusui.app.ui.courier.design.CourierStatusBadge
import com.kyusui.app.ui.courier.design.CourierSectionHeader
import com.kyusui.app.ui.courier.design.CourierSecondaryButton
import com.kyusui.app.ui.courier.design.CourierPrimaryButton
import com.kyusui.app.ui.courier.design.CourierCard
import com.kyusui.app.ui.courier.design.CourierSpacing
import com.kyusui.app.ui.courier.design.CourierColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kyusui.app.core.Money
import com.kyusui.app.domain.model.OwnerPaymentProof
import com.kyusui.app.domain.model.OwnerPaymentProofHistory
import com.kyusui.app.domain.model.OwnerPendingPayment
import com.kyusui.app.domain.model.OwnerVerificationAction
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.ui.components.EmptyState
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InfoRow
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.PaymentStatusChip
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.components.SecondaryButton
import com.kyusui.app.ui.customer.payment.PaymentStatusCard
import com.kyusui.app.ui.customer.payment.PaymentStatusExplanation
import com.kyusui.app.ui.state.LoadState

private const val LOAD_MORE_THRESHOLD = 3
private const val APPENDING_KEY = "owner-pending-appending"
private const val NO_PROOF_MESSAGE = "Tidak ada bukti pembayaran yang diunggah."
private const val PROOF_UNAVAILABLE_MESSAGE =
    "Bukti pembayaran tidak dapat dimuat. Periksa koneksi Anda."

/**
 * Antrean verifikasi QRIS untuk Owner.
 *
 * Sumber data layar ini:
 *
 * - daftar: `GET /owner/payments/pending` (section 12.1)
 * - bukti:  `GET /owner/orders/{order}/payment/proof` (section 12.2)
 * - aksi:   `POST /owner/orders/{order}/payment-verification` (section 12.3)
 *
 * Android tidak pernah menulis `PAID` maupun `PENDING` secara lokal. Setelah
 * approve atau reject, status yang tampil adalah status dari response backend,
 * dan pesannya menyebutkan nilai itu secara eksplisit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingQrisVerificationScreen(
    uiState: PendingQrisVerificationUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onViewProof: (String) -> Unit,
    onDismissProof: () -> Unit,
    onVerify: (OwnerVerificationAction, String?) -> Unit,
    imageLoader: ImageLoader,
    onViewHistory: (String) -> Unit = {},
    onDismissHistory: () -> Unit = {},
    onRetryHistory: (String) -> Unit = {},
    onViewHistoricalProof: (String, String) -> Unit = { _, _ -> },
    onRetryHistoricalProof: (String, String) -> Unit = { _, _ -> },
    onDismissHistoricalProof: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(uiState.hasMore, uiState.isAppending, uiState.payments) {
        derivedStateOf {
            if (!uiState.hasMore || uiState.isAppending) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Scaffold(
        containerColor = CourierColors.Background,
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "Verifikasi QRIS")
                        Text(
                            text = "Tinjau bukti pembayaran pelanggan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRetry) {
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
        ) {
            when (val state = uiState.paymentsState) {
                is LoadState.Loading -> LoadingState(message = "Memuat antrean verifikasi...")

                is LoadState.Error -> ErrorState(
                    message = state.message,
                    isRetryable = state.isRetryable,
                    onRetry = onRetry
                )

                is LoadState.Empty -> EmptyState(
                    title = "Tidak Ada Menunggu Verifikasi",
                    message = state.message,
                    actionText = "Muat Ulang",
                    onActionClick = onRetry
                )

                is LoadState.Success -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = CourierSpacing.Screen,
                        vertical = CourierSpacing.Lg
                    ),
                    verticalArrangement = Arrangement.spacedBy(CourierSpacing.Md)
                ) {
                    items(uiState.payments, key = { it.paymentId }) { payment ->
                        PendingPaymentCard(
                            payment = payment,
                            canVerify = uiState.canVerify(payment),
                            isVerifying = uiState.isVerifying,
                            onViewProof = { onViewProof(payment.orderId) }
                        )
                    }

                    if (uiState.isAppending) {
                        item(key = APPENDING_KEY) {
                            LoadingState(message = "Memuat antrean lainnya...")
                        }
                    }
                }
            }
        }
    }

    // Pratinjau bukti dan aksi verifikasi ditampilkan sebagai dialog supaya
    // antrean tetap terlihat dan Owner tidak kehilangan posisi.
    if (uiState.isProofOpen) {
        PaymentProofViewer(
            proofState = uiState.proofState,
            isVerifying = uiState.isVerifying,
            verificationErrorMessage = uiState.verificationErrorMessage,
            verificationMessage = uiState.verificationMessage,
            imageLoader = imageLoader,
            onDismiss = onDismissProof,
            onVerify = onVerify,
            orderId = uiState.proofOrderId.orEmpty(),
            onViewHistory = onViewHistory
        )
    }

    uiState.historyState?.let { historyState ->
        OwnerQrisProofHistoryDialog(
            state = historyState,
            onDismiss = onDismissHistory,
            onRetry = { uiState.historyOrderId?.let(onRetryHistory) },
            onViewProof = { historyId ->
                uiState.historyOrderId?.let { orderId ->
                    onViewHistoricalProof(orderId, historyId)
                }
            },
            proofState = uiState.historicalProofState,
            onRetryProof = {
                val orderId = uiState.historyOrderId
                val historyId = uiState.historicalProofId
                if (orderId != null && historyId != null) {
                    onRetryHistoricalProof(orderId, historyId)
                }
            },
            onDismissProof = onDismissHistoricalProof
        )
    }
}

/**
 * Pratinjau bukti pembayaran dan aksi approve/reject.
 *
 * Beding dari [PaymentProofViewer]:
 *
 * - Bukti hanya ditampilkan pada URL yang diberikan backend. Nilai itu dipakai
 *   apa adanya. Android tidak menyusun URL bukti sendiri dan tidak memakai
 *   `QrisImageUrlResolver`, karena kelas itu khusus untuk aset konfigurasi toko.
 * - Backend boleh melaporkan `available: false` atau tidak mengirim URL. Kedua
 *   keadaan itu ditampilkan sebagai "tidak ada bukti yang dapat ditampilkan",
 *   tanpa menuduh pembayaran tidak sah.
 * - Status pembayaran di dalam dialog berasal dari response backend. Setelah
 *   approve atau reject, baris antrean dihapus karena
 *   `GET /owner/payments/pending` hanya memuat `WAITING_VERIFICATION`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentProofViewer(
    proofState: LoadState<OwnerPaymentProof>?,
    isVerifying: Boolean,
    verificationErrorMessage: String?,
    verificationMessage: String?,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onVerify: (OwnerVerificationAction, String?) -> Unit,
    orderId: String,
    onViewHistory: (String) -> Unit
) {
    var rejectNote by rememberSaveable { mutableStateOf("") }
    var showRejectField by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Bukti Pembayaran") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (val state = proofState) {
                    null, is LoadState.Loading -> LoadingState(
                        modifier = Modifier.heightIn(min = 160.dp),
                        message = "Memuat bukti..."
                    )

                    is LoadState.Error -> ErrorState(
                        modifier = Modifier.heightIn(min = 160.dp),
                        message = state.message,
                        isRetryable = state.isRetryable,
                        onRetry = onDismiss
                    )

                    is LoadState.Empty -> Text(
                        text = NO_PROOF_MESSAGE,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    is LoadState.Success -> {
                        val proof = state.data

                        InfoRow(label = "Nomor Pesanan", value = proof.orderId)
                        proof.paymentMethod?.let {
                            InfoRow(label = "Metode", value = it.displayName)
                        }

                        proof.paymentStatus?.let { PaymentStatusChip(status = it) }

                        if (proof.canDisplayImage) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(proof.proofUrl)
                                    .crossfade(true)
                                    .build(),
                                imageLoader = imageLoader,
                                contentDescription = "Bukti pembayaran QRIS",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                            )
                        } else {
                            ProofUnavailableNotice()
                        }
                    }
                }

                if (orderId.isNotBlank()) {
                    TextButton(
                        onClick = { onViewHistory(orderId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lihat Riwayat Verifikasi")
                    }
                }

                verificationErrorMessage?.let { InlineError(message = it) }

                if (verificationMessage != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = verificationMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (showRejectField) {
                    OutlinedTextField(
                        value = rejectNote,
                        onValueChange = { rejectNote = it.take(PendingQrisVerificationViewModel.MAX_REJECTION_NOTE_LENGTH) },
                        label = { Text(text = "Alasan penolakan") },
                        supportingText = {
                            val length = rejectNote.trim().length
                            Text(
                                text = "Wajib 5–500 karakter ($length/500). Jelaskan alasan penolakan secara spesifik.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (length in PendingQrisVerificationViewModel.MIN_REJECTION_NOTE_LENGTH..PendingQrisVerificationViewModel.MAX_REJECTION_NOTE_LENGTH) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                            )
                        },
                        isError = rejectNote.trim().length !in PendingQrisVerificationViewModel.MIN_REJECTION_NOTE_LENGTH..PendingQrisVerificationViewModel.MAX_REJECTION_NOTE_LENGTH,
                        maxLines = 4,
                        enabled = !isVerifying,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Column {
                // Aksi tetap mengirim `action` saja. Backend yang menentukan
                // apakah aksi valid dan seperti apa status akhirnya.
                PrimaryButton(
                    text = "Setujui Pembayaran",
                    onClick = { onVerify(OwnerVerificationAction.APPROVE, null) },
                    enabled = !isVerifying,
                    isLoading = isVerifying,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                if (showRejectField) {
                    PrimaryButton(
                        text = "Kirim Penolakan",
                        onClick = {
                            onVerify(OwnerVerificationAction.REJECT, rejectNote)
                        },
                        enabled = !isVerifying && rejectNote.trim().length in PendingQrisVerificationViewModel.MIN_REJECTION_NOTE_LENGTH..PendingQrisVerificationViewModel.MAX_REJECTION_NOTE_LENGTH,
                        isLoading = isVerifying,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    SecondaryButton(
                        text = "Tolak Bukti",
                        onClick = { showRejectField = true },
                        enabled = !isVerifying,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Tutup")
            }
        }
    )
}

@Composable
private fun ProofUnavailableNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ImageNotSupported,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = PROOF_UNAVAILABLE_MESSAGE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Kartu satu pembayaran yang menunggu verifikasi.
 */
@Composable
private fun PendingPaymentCard(
    payment: OwnerPendingPayment,
    canVerify: Boolean,
    isVerifying: Boolean,
    onViewProof: () -> Unit,
    modifier: Modifier = Modifier
) {
    CourierCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 18.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = payment.orderNumber.takeIf { it.isNotBlank() } ?: "Pesanan",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = Money.format(payment.amount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            payment.customer?.name?.let { name ->
                Text(
                    text = "Pelanggan: $name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            payment.paymentStatus?.let { PaymentStatusChip(status = it) }

            if (!payment.hasProof) {
                // Backend melaporkan tidak ada bukti. Ini kondisi yang dilaporkan
                // server, bukan penilaian Android, dan tidak otomatis berarti
                // pembayaran tidak sah.
                Text(
                    text = "Backend tidak melaporkan bukti untuk pembayaran ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(
                    text = "Lihat Bukti Pembayaran",
                    onClick = onViewProof,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = if (canVerify) {
                    "Status pembayaran: ${payment.paymentStatus?.apiValue ?: PaymentStatus.WAITING_VERIFICATION.apiValue}."
                } else {
                    "Status bukan menunggu verifikasi, jadi aksi tidak diaktifkan."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Penjelasan singkat bahwa status setelah aksi berasal dari server.
 *
 * Dipakai di luar dialog agar Owner memahami bahwa `PAID` hanya muncul setelah
 * backend mengonfirmasi.
 */
@Composable
fun OwnerVerificationRuleNotice(modifier: Modifier = Modifier) {
    CourierCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = CourierColors.InfoContainer,
        contentPadding = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Status final ditentukan server",
                style = MaterialTheme.typography.titleSmall,
                color = CourierColors.Info
            )
            Text(
                text = "Menyetujui bukti membuat status WAITING_VERIFICATION menjadi " +
                    "PAID. Menolak bukti mengembalikannya ke PENDING supaya " +
                    "pelanggan dapat mengunggah ulang. Kedua keputusan berlaku " +
                    "setelah backend menerimanya.",
                style = MaterialTheme.typography.bodySmall,
                color = CourierColors.Info
            )
        }
    }
}
