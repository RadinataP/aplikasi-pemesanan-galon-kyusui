package com.kyusui.app.ui.owner.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyusui.app.domain.model.OwnerPaymentProofHistory
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.components.EmptyState
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.LoadingState

/**
 * Dialog riwayat keputusan bukti QRIS.
 * Panggil dengan state dari PendingQrisVerificationUiState.historyState.
 */
@Composable
fun OwnerQrisProofHistoryDialog(
    state: LoadState<List<OwnerPaymentProofHistory>>,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onViewProof: (String) -> Unit,
    proofState: LoadState<ByteArray>? = null,
    onRetryProof: () -> Unit = onRetry,
    onDismissProof: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Riwayat Verifikasi QRIS") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (state) {
                    is LoadState.Loading -> LoadingState(message = "Memuat riwayat...")
                    is LoadState.Error -> ErrorState(
                        message = state.message,
                        isRetryable = state.isRetryable,
                        onRetry = onRetry
                    )
                    is LoadState.Empty -> EmptyState(
                        title = "Belum Ada Riwayat",
                        message = state.message
                    )
                    is LoadState.Success -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.data, key = { it.id }) { item ->
                            HistoryItem(item, onViewProof)
                        }
                    }
                }

                proofState?.let { proof ->
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Text("Bukti historis", style = MaterialTheme.typography.titleSmall)
                    when (proof) {
                        is LoadState.Loading -> LoadingState(message = "Memuat bukti historis...")
                        is LoadState.Error -> ErrorState(
                            message = proof.message,
                            isRetryable = proof.isRetryable,
                            onRetry = onRetryProof
                        )
                        is LoadState.Empty -> EmptyState(
                            title = "Bukti tidak tersedia",
                            message = proof.message
                        )
                        is LoadState.Success -> {
                            AsyncImage(
                                model = proof.data,
                                contentDescription = "Bukti pembayaran historis",
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = onDismissProof,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Tutup bukti") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun HistoryItem(
    item: OwnerPaymentProofHistory,
    onViewProof: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(item.decision, style = MaterialTheme.typography.titleSmall)
            item.actor?.takeIf { it.isNotBlank() }?.let { Text("Petugas: $it") }
            item.decidedAt?.takeIf { it.isNotBlank() }?.let { Text("Waktu: $it") }
            item.note?.takeIf { it.isNotBlank() }?.let { Text("Catatan: $it") }
            Text(
                if (item.proofAvailable) "Bukti lama tersedia di server" else "Bukti lama tidak tersedia",
                style = MaterialTheme.typography.bodySmall
            )
            if (item.proofAvailable) {
                TextButton(onClick = { onViewProof(item.id) }) {
                    Text("Lihat bukti ini")
                }
            }
        }
    }
}
