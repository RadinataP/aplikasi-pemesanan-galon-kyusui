package com.kyusui.app.ui.courier.assignments

import com.kyusui.app.ui.courier.design.CourierCard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kyusui.app.ui.location.rememberLocationRequestState
import com.kyusui.app.ui.location.resolveLocationPermissionState
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyusui.app.core.Money
import com.kyusui.app.ui.courier.design.CourierColors
import com.kyusui.app.ui.courier.design.CourierSpacing
import com.kyusui.app.ui.courier.design.CourierRootBottomBar
import com.kyusui.app.ui.courier.design.CourierRootTab
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.ui.components.EmptyState
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.OutlinedButton
import com.kyusui.app.ui.components.PaymentStatusChip
import com.kyusui.app.ui.state.LoadState

/**
 * Daftar assignment milik kurir dari `GET courier/assignments`.
 *
 * Endpoint ini mengembalikan seluruh assignment milik kurir tersebut sebagai satu
 * array dan tidak menerima query apa pun, termasuk `status`. Filter di layar ini
 * karena itu menyaring data yang sudah diterima dari server, bukan meminta server
 * menyaring. Tidak ada request tambahan yang dibuat saat filter diganti.
 *
 * Nama pelanggan tersedia di sini karena Assignment Resource memang mengirim
 * `order.customer.name`. Backend tidak mengirim nomor telepon pada resource ini,
 * sehingga layar ini tidak pernah menampilkan nomor telepon pelanggan.
 */
@Composable
fun AssignedOrderScreen(
    uiState: AssignedOrderUiState,
    onOpenDelivery: (String) -> Unit,
    onSelectFilter: (CourierAssignmentFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenHome: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
    onClaim: (String) -> Unit = {},
    onRefreshPool: () -> Unit = {}
) {
    val context = LocalContext.current
    var pendingClaim by remember { mutableStateOf<String?>(null) }
    var permissionError by remember { mutableStateOf(false) }
    val permission = rememberLocationRequestState(
        onPermissionDenied = { pendingClaim = null; permissionError = true },
        onPermissionGranted = { pendingClaim?.let(onClaim); pendingClaim = null; permissionError = false }
    )
    LaunchedEffect(Unit) {
        while (true) { onRefreshPool(); onRefresh(); delay(15000) }
    }
    Scaffold(
        modifier = modifier,
        containerColor = CourierColors.Background,
        bottomBar = {
            CourierRootBottomBar(
                selected = CourierRootTab.TASKS,
                onHome = onOpenHome,
                onTasks = {},
                onProfile = onOpenProfile
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "Pengantaran")
                        Text(
                            text = "Kelola tugas dan status pesanan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Ruang kerja kurir",
                    style = MaterialTheme.typography.labelMedium,
                    color = CourierColors.Primary
                )
                Text(
                    text = "Daftar pengantaran",
                    style = MaterialTheme.typography.headlineSmall,
                    color = CourierColors.Navy
                )
                Text(
                    text = "Pilih pesanan untuk melihat detail dan tindakan berikutnya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CourierColors.TextSecondary
                )
            }
            // Kegagalan muat ulang tidak menghapus daftar yang sudah tampil, jadi
            // pesannya muncul di atas baris dan bukan menggantikannya. Pola ini
            // sama dengan beranda kurir.
            uiState.refreshErrorMessage?.let { message ->
                InlineError(
                    message = message,
                    onRetry = onRefresh,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Filter ditampilkan hanya setelah data ada. Saat masih memuat atau
            // gagal, pilihan status belum berarti apa-apa karena belum ada baris
            // yang bisa disaring.
            if (uiState.listState is LoadState.Success) {
                Text(
                    text = "Filter status",
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterRow(
                    selected = uiState.filter,
                    onSelect = onSelectFilter
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "pool_title") {
                    Text("Pesanan baru (${uiState.openOrders.size})", style = MaterialTheme.typography.titleLarge)
                    Text("Ambil pesanan yang siap Anda antar. Lokasi dibagikan otomatis saat diterima.")
                    OutlinedButton(text = "Muat ulang pesanan", onClick = { onRefreshPool(); onRefresh() })
                    if (permissionError) Text("Izinkan lokasi di pengaturan Android untuk menerima pesanan.", color = MaterialTheme.colorScheme.error)
                    uiState.poolError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    uiState.claimMessage?.let { Text(it) }
                    if (uiState.openOrders.isEmpty() && uiState.poolError == null) Text("Belum ada pesanan baru.")
                }
                items(uiState.openOrders, key = { "open-${it.id}" }) { order ->
                    CourierCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(order.number, style = MaterialTheme.typography.titleMedium)
                            Text(order.customerName.orEmpty())
                            Text(order.address.orEmpty())
                            Text(order.items.joinToString(" • ") { "${it.quantity} × ${it.productName}" })
                            Text("Total: ${Money.format(order.totalAmount)}")
                            Text("Pembayaran: ${order.payment?.paymentMethod?.displayName.orEmpty()} — ${order.payment?.paymentStatus?.displayName.orEmpty()}")
                            OutlinedButton(text = "Ambil Pesanan", enabled = uiState.claimingId == null, isLoading = uiState.claimingId == order.id,
                                onClick = {
                                    if (resolveLocationPermissionState(context, hasAsked = false).allowsAcquisition) onClaim(order.id)
                                    else { pendingClaim = order.id; permission.launcher() }
                                })
                        }
                    }
                }
                item(key = "assigned_title") {
                    Text("Pengantaran saya (${uiState.activeCount} aktif)", style = MaterialTheme.typography.titleLarge)
                    if (uiState.isRefreshing) Text("Memuat pengantaran…")
                    uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (uiState.visibleDeliveries.isEmpty()) Text("Tidak ada pengantaran pada filter ini.")
                }
                items(uiState.visibleDeliveries, key = { "assigned-${it.id}" }) { delivery ->
                    AssignmentCard(delivery = delivery, onClick = { onOpenDelivery(delivery.id) })
                }
            }
        }
    }
}

/**
 * Baris filter status.
 *
 * Filter memakai nilai [AssignmentStatus] canonical, bukan string yang diketik di
 * layar, sehingga pilihan di UI tidak mungkin berbeda dari nilai yang dibaca dari
 * backend.
 */
@Composable
private fun FilterRow(
    selected: CourierAssignmentFilter,
    onSelect: (CourierAssignmentFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = CourierAssignmentFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(text = filter.label) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CourierColors.Primary,
                    selectedLabelColor = CourierColors.White,
                    containerColor = CourierColors.Surface,
                    labelColor = CourierColors.TextSecondary
                )
            )
        }
    }
}

@Composable
private fun AssignmentCard(
    delivery: CourierDelivery,
    onClick: () -> Unit
) {
    CourierCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pesanan #${delivery.orderId ?: TIDAK_TERSEDIA}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                AssignmentStatusChip(status = delivery.status)
            }

            delivery.order?.customerName?.let { customerName ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = customerName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = delivery.order?.deliveryAddress ?: ALAMAT_TIDAK_TERSEDIA,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            delivery.order?.let { order ->
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        order.payment?.paymentMethod?.let { method ->
                            Text(
                                text = "Bayar: ${method.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        order.payment?.paymentStatus?.let { paymentStatus ->
                            PaymentStatusChip(status = paymentStatus)
                        }
                    }
                    Text(
                        text = Money.format(order.totalAmount),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (order.items.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${order.totalQuantity} item",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Chip status assignment.
 *
 * `AssignmentStatus` punya `displayName` Bahasa Indonesia sendiri, jadi label di
 * sini diambil dari domain model dan tidak diterjemahkan ulang di layar.
 */
@Composable
private fun AssignmentStatusChip(status: AssignmentStatus?, modifier: Modifier = Modifier) {
    val label = status?.displayName ?: STATUS_TIDAK_DIKETAHUI
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

private const val ALAMAT_TIDAK_TERSEDIA = "Alamat tidak tersedia"
private const val TIDAK_TERSEDIA = "-"
private const val STATUS_TIDAK_DIKETAHUI = "Status tidak diketahui"

/**
 * Pesan untuk filter yang tidak punya baris.
 *
 * Ditaruh di enum, bukan di layar, supaya alasan tiap filter ikut teruji bersama
 * definisinya.
 */
val CourierAssignmentFilter.emptyMessage: String
    get() = when (this) {
        CourierAssignmentFilter.ALL -> AssignedOrderViewModel.EMPTY_MESSAGE
        CourierAssignmentFilter.ASSIGNED -> "Tidak ada pengantaran yang menunggu dimulai."
        CourierAssignmentFilter.ACTIVE -> "Tidak ada pengantaran yang sedang berjalan."
        CourierAssignmentFilter.COMPLETED -> "Belum ada pengantaran yang selesai."
    }