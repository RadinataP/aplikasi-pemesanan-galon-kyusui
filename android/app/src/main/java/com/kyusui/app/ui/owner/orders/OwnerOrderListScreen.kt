package com.kyusui.app.ui.owner.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.kyusui.app.ui.owner.components.OwnerBottomNavigation
import com.kyusui.app.ui.owner.components.OwnerMainTab
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyusui.app.core.Money
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.OwnerOrder
import com.kyusui.app.ui.components.EmptyState
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.PaymentStatusChip
import com.kyusui.app.ui.components.StatusChip
import com.kyusui.app.ui.state.LoadState

private const val UNKNOWN_STATUS_LABEL = "Status tidak dikenal"
private const val UNKNOWN_PAYMENT_LABEL = "Belum ada pembayaran"
private const val UNKNOWN_ASSIGNMENT_LABEL = "Status penugasan tidak dikenal"
private const val LOAD_MORE_THRESHOLD = 3
private const val APPENDING_KEY = "owner-appending-indicator"

/**
 * Daftar order Owner dari `GET /owner/orders` (specification 10.1).
 *
 * Filter status diteruskan sebagai query ke backend, bukan diterapkan di Android.
 * Backend tetap menentukan order mana yang masuk operational scope Berkah
 * Water; Android tidak pernah memfilter ulang berdasarkan kepemilikan.
 *
 * Filter bersifat opsional pada specification ("dapat digunakan bila sudah
 * tersedia"). Backend yang menolak parameter `status` akan menjawab error, dan
 * pesan itu ditampilkan apa adanya lewat [ErrorState] dengan aksi muat ulang.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerOrderListScreen(
    uiState: OwnerOrderListUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit = onLoadMore,
    onFilterChange: (OwnerOrderFilter) -> Unit,
    onOrderClick: (OwnerOrder) -> Unit,
    selectedTab: OwnerMainTab = OwnerMainTab.ORDERS,
    onSelectMainTab: (OwnerMainTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(uiState.hasMore, uiState.isAppending, uiState.orders) {
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
        modifier = modifier,
        bottomBar = { OwnerBottomNavigation(selectedTab, onSelectMainTab) },
        topBar = {
            TopAppBar(
                title = { Text(text = "Pesanan Masuk") },
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
            // Filter tetap memakai callback backend; tampilan hanya memperjelas
            // bahwa filter dapat digeser secara horizontal tanpa memotong chip.
            Text(
                text = "Pantau dan pilih pesanan untuk melihat detail serta tindakan berikutnya.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(OwnerOrderFilter.entries.toList(), key = { it.name }) { filter ->
                    FilterChip(
                        selected = uiState.activeFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(text = filter.label) }
                    )
                }
            }

            when (val state = uiState.ordersState) {
                is LoadState.Loading -> LoadingState(message = "Memuat pesanan...")

                is LoadState.Error -> ErrorState(
                    message = state.message,
                    isRetryable = state.isRetryable,
                    onRetry = onRetry
                )

                is LoadState.Empty -> EmptyState(
                    title = "Belum Ada Pesanan",
                    message = state.message,
                    actionText = "Muat Ulang",
                    onActionClick = onRetry
                )

                is LoadState.Success -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.orders, key = { it.id }) { order ->
                        OwnerOrderCard(order = order, onClick = { onOrderClick(order) })
                    }

                    uiState.appendError?.let { message ->
                        item(key = "owner-append-error") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                androidx.compose.material3.TextButton(
                                    onClick = onRetryLoadMore
                                ) {
                                    Text("Coba muat lagi")
                                }
                            }
                        }
                    }

                    if (uiState.isAppending) {
                        item(key = APPENDING_KEY) {
                            LoadingState(message = "Memuat pesanan lainnya...")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kartu order untuk Owner.
 *
 * Yang ditampilkan hanya field yang ada pada contract Order Resource. Tidak ada
 * nama kurir atau nomor telepon karena tidak ada pada contract mana pun.
 */
@Composable
private fun OwnerOrderCard(
    order: OwnerOrder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.orderNumber.takeIf { it.isNotBlank() } ?: "Pesanan",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = Money.format(order.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            order.customer?.name?.let { name ->
                Text(
                    text = "Pelanggan: $name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${order.totalQuantity} produk",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                order.orderStatus?.let { StatusChip(status = it) } ?: Text(
                    text = UNKNOWN_STATUS_LABEL,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                order.payment?.paymentStatus?.let { PaymentStatusChip(status = it) } ?: Text(
                    text = UNKNOWN_PAYMENT_LABEL,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            order.assignment?.let { assignment ->
                Text(
                    text = "Kurir #${assignment.courierId} - " +
                        assignment.status.ownerLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Label status penugasan untuk teks di luar kartu.
 *
 * Status yang tidak dikenal tidak diterjemahkan menjadi status lain, dan tidak
 * diklaim sebagai `COMPLETED` atau `ASSIGNED` tanpa dasar.
 */
fun AssignmentStatus?.ownerLabel(): String = this?.displayName ?: UNKNOWN_ASSIGNMENT_LABEL