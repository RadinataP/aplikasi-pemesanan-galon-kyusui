package com.kyusui.app.ui.courier.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyusui.app.core.Money
import com.kyusui.app.domain.model.CourierDashboard
import com.kyusui.app.domain.model.CourierDashboardOrder
import com.kyusui.app.ui.courier.design.CourierCard
import com.kyusui.app.ui.courier.design.CourierColors
import com.kyusui.app.ui.courier.design.CourierSpacing
import com.kyusui.app.ui.courier.design.CourierRootBottomBar
import com.kyusui.app.ui.courier.design.CourierRootTab
import com.kyusui.app.ui.components.EmptyState
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.OutlinedButton
import com.kyusui.app.ui.components.PaymentStatusChip
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.components.StatusChip
import com.kyusui.app.ui.components.TextButton
import com.kyusui.app.ui.state.LoadState
import java.util.Locale

/**
 * Beranda kurir dari `GET dashboard/courier`.
 *
 * Seluruh angka di layar ini adalah angka yang dihitung backend: pengantaran
 * hari ini, bulan ini, yang selesai, tingkat penyelesaian, dan notifikasi belum
 * dibaca. Android tidak menghitung ulang satu pun angka itu.
 *
 * Batas kontrak yang terlihat di layar ini: `active_assignment` dan
 * `today_assignments` berisi Order Resource, bukan Assignment Resource, dan Order
 * Resource tersebut tidak memuat nama pelanggan. Baris di beranda karena itu
 * menampilkan nomor order dan alamat, bukan nama. Nama pelanggan diambil dari
 * `GET courier/assignments` pada layar Daftar Pengantaran.
 */
@Composable
fun CourierDashboardScreen(
    uiState: CourierDashboardUiState,
    onOpenAssignments: () -> Unit,
    onOpenDelivery: (String) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onStartDuty: () -> Unit,
    onStopDuty: () -> Unit,
    onClearDutyMessage: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDirectOrder: () -> Unit,
    onOpenQrisVerification: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            CourierRootBottomBar(
                selected = CourierRootTab.HOME,
                onHome = {},
                onTasks = onOpenAssignments,
                onProfile = onOpenProfile
            )
        },
        containerColor = CourierColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Berkah Water")
                        Text(
                            text = "Kurir",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onOpenNotifications) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifikasi"
                        )
                    }
                    // Buka layar pengaturan dari dashboard sesuai role.
                    androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                        Text(text = "Pengaturan")
                    }
                    androidx.compose.material3.IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = "Keluar"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState.dashboardState) {
            is LoadState.Loading -> LoadingState(
                modifier = Modifier.padding(innerPadding),
                message = "Memuat data pengiriman..."
            )

            is LoadState.Error -> ErrorState(
                modifier = Modifier.padding(innerPadding),
                message = state.message,
                isRetryable = state.isRetryable,
                onRetry = if (state.isRetryable) onRefresh else null
            )

            // `Empty` tidak pernah dihasilkan ViewModel untuk dashboard karena
            // `GET dashboard/courier` selalu membalut satu objek. Cabang ini tetap
            // ditulis supaya ketiadaan data ditampilkan apa adanya, bukan diisi
            // dengan ringkasan nol yang terlihat seperti angka asli.
            is LoadState.Empty -> EmptyState(
                modifier = Modifier.padding(innerPadding),
                title = "Data Kosong",
                message = state.message,
                actionText = "Muat Ulang",
                onActionClick = onRefresh
            )

            is LoadState.Success -> DashboardContent(
                modifier = Modifier.padding(innerPadding),
                dashboard = state.data,
                isRefreshing = uiState.isRefreshing,
                refreshErrorMessage = uiState.refreshErrorMessage,
                isOnDuty = uiState.isOnDuty,
                isDutyActionInProgress = uiState.isDutyActionInProgress,
                dutyMessage = uiState.dutyMessage,
                onOpenAssignments = onOpenAssignments,
                onOpenDelivery = onOpenDelivery,
                onRefresh = onRefresh,
                onStartDuty = onStartDuty,
                onStopDuty = onStopDuty,
                onClearDutyMessage = onClearDutyMessage,
                onOpenProfile = onOpenProfile,
                // Teruskan callback dari NavigationHost ke tombol input pesanan langsung.
                onOpenDirectOrder = onOpenDirectOrder,
                onOpenQrisVerification = onOpenQrisVerification
            )
        }
    }
}

@Composable
private fun DashboardContent(
    dashboard: CourierDashboard,
    isRefreshing: Boolean,
    refreshErrorMessage: String?,
    isOnDuty: Boolean,
    isDutyActionInProgress: Boolean,
    dutyMessage: String?,
    onOpenAssignments: () -> Unit,
    onOpenDelivery: (String) -> Unit,
    onRefresh: () -> Unit,
    onStartDuty: () -> Unit,
    onStopDuty: () -> Unit,
    onClearDutyMessage: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDirectOrder: () -> Unit,
    onOpenQrisVerification: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (refreshErrorMessage != null) InlineError(message = refreshErrorMessage, onRetry = onRefresh)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Selamat bertugas!",
                style = MaterialTheme.typography.headlineMedium,
                color = CourierColors.Navy
            )
            Text(
                text = "Berikut prioritas operasional hari ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = CourierColors.TextSecondary
            )
        }

        DutyStatusCard(
            isOnDuty = isOnDuty,
            isActionInProgress = isDutyActionInProgress,
            message = dutyMessage,
            onStartDuty = onStartDuty,
            onStopDuty = onStopDuty,
            onClearMessage = onClearDutyMessage
        )

        if (dashboard.activeDeliveries.isNotEmpty()) {
            SectionTitle(text = "Pengantaran aktif (${dashboard.activeDeliveries.size})")
            dashboard.activeDeliveries.forEach { active ->
                ActiveDeliveryCard(order = active, onOpenDelivery = onOpenDelivery)
            }
        }

        SummarySection(dashboard = dashboard)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(text = "Aksi operasional")
            CourierActionTile(Icons.Outlined.LocalShipping, "Pesanan & pengantaran", "Ambil pesanan baru dan lihat tugas aktif", onOpenAssignments)
            CourierActionTile(Icons.Outlined.Payments, "Verifikasi pembayaran QRIS", "Tinjau bukti pembayaran yang masuk", onOpenQrisVerification)
            CourierActionTile(Icons.Outlined.AddShoppingCart, "Catat pesanan langsung", "Buat pesanan untuk transaksi depot", onOpenDirectOrder)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(text = "Pengantaran hari ini")
            TodayDeliveryList(
                deliveries = dashboard.todayDeliveries,
                totalTodayDeliveries = dashboard.summary?.todayDeliveries,
                onOpenDelivery = onOpenDelivery
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton("Muat ulang", onRefresh, enabled = !isRefreshing, isLoading = isRefreshing, modifier = Modifier.weight(1f), icon = Icons.Outlined.Refresh)
            OutlinedButton("Profil", onOpenProfile, modifier = Modifier.weight(1f), icon = Icons.Outlined.Person)
        }
    }
}

@Composable
private fun CourierActionTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    CourierCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, elevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp).size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DutyStatusCard(
    isOnDuty: Boolean,
    isActionInProgress: Boolean,
    message: String?,
    onStartDuty: () -> Unit,
    onStopDuty: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    CourierCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle(text = "Status Bertugas")

            Text(
                text = if (isOnDuty) {
                    "Sedang bertugas. Posisi dikirim berkala walau belum ada pengantaran."
                } else {
                    "Belum bertugas. Mulai bertugas agar posisi terkirim tanpa menunggu pengantaran."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    text = "Sembunyikan pesan",
                    onClick = onClearMessage
                )
            }

            if (isOnDuty) {
                OutlinedButton(
                    text = "Akhiri Tugas",
                    onClick = onStopDuty,
                    enabled = !isActionInProgress,
                    isLoading = isActionInProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PrimaryButton(
                    text = "Mulai Bertugas",
                    onClick = onStartDuty,
                    enabled = !isActionInProgress,
                    isLoading = isActionInProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SummarySection(dashboard: CourierDashboard, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle(text = "Ringkasan Aktivitas")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryMetricCard(
                icon = Icons.Outlined.LocalShipping,
                label = "Pengantaran hari ini",
                value = dashboard.summary.todayDeliveries.toString(),
                modifier = Modifier.weight(1f)
            )
            SummaryMetricCard(
                icon = Icons.Outlined.Payments,
                label = "Pengantaran bulan ini",
                value = dashboard.summary.monthDeliveries.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryMetricCard(
                icon = Icons.Outlined.CheckCircle,
                label = "Selesai bulan ini",
                value = dashboard.summary.completedDeliveries.toString(),
                modifier = Modifier.weight(1f),
                tone = CourierMetricTone.SUCCESS
            )
            SummaryMetricCard(
                icon = Icons.Outlined.Notifications,
                label = "Belum dibaca",
                value = dashboard.unreadNotifications.toString(),
                modifier = Modifier.weight(1f),
                tone = CourierMetricTone.WARNING
            )
        }

        CourierCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = CourierSpacing.Lg
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tingkat penyelesaian",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(
                            Locale("id", "ID"),
                            "%.2f%%",
                            dashboard.summary.completionRate
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = {
                        (dashboard.summary.completionRate / 100.0)
                            .toFloat()
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = CourierColors.Primary,
                    trackColor = CourierColors.SurfaceBlue
                )
                Text(
                    text = "Seluruh angka dihitung oleh server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private enum class CourierMetricTone { PRIMARY, SUCCESS, WARNING }

@Composable
private fun SummaryMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: CourierMetricTone = CourierMetricTone.PRIMARY
) {
    val iconColor = when (tone) {
        CourierMetricTone.PRIMARY -> CourierColors.Primary
        CourierMetricTone.SUCCESS -> CourierColors.Success
        CourierMetricTone.WARNING -> CourierColors.Warning
    }
    val iconBackground = when (tone) {
        CourierMetricTone.PRIMARY -> CourierColors.SurfaceBlue
        CourierMetricTone.SUCCESS -> CourierColors.SuccessContainer
        CourierMetricTone.WARNING -> CourierColors.WarningContainer
    }

    CourierCard(
        modifier = modifier,
        contentPadding = 14.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            androidx.compose.material3.Surface(
                color = iconBackground,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.padding(8.dp).size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

/**
 * Kartu pengantaran aktif.
 *
 * Backend mengirim `active_assignment` sebagai Order Resource yang memuat
 * referensi `{id, status}` untuk assignment-nya. Bila referensi itu tidak ada,
 * kartu tetap ditampilkan sebagai informasi, tapi tidak bisa dibuka karena
 * endpoint detail kurir bekerja pada id assignment.
 */
@Composable
private fun ActiveDeliveryCard(
    order: CourierDashboardOrder,
    onOpenDelivery: (String) -> Unit
) {
    val assignmentId = order.assignmentId

    CourierCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (assignmentId != null) {
            { onOpenDelivery(assignmentId) }
        } else {
            null
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pesanan #${order.orderId}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = order.deliveryAddress ?: ALAMAT_TIDAK_TERSEDIA,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                order.status?.let { status ->
                    StatusChip(status = status)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = Money.format(order.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (assignmentId == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = BELUM_ADA_ASSIGNMENT_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Daftar pengantaran hari ini.
 *
 * Order tanpa assignment ditampilkan dengan catatan, bukan disembunyikan,
 * karena backend memasukkan seluruh assignment hari ini ke daftar. Order
 * seperti ini bukan tanggung jawab kurir dan tidak bisa dibuka lewat endpoint
 * kurir yang ada.
 *
 * [totalTodayDeliveries] adalah angka dari `summary.today_deliveries`, yang
 * menghitung SEMUA assignment hari ini, sedangkan daftarnya sendiri dibatasi
 * backend menjadi 20 baris terbaru. Ketidaksesuaian jumlah itu ditampilkan
 * apa adanya supaya kurir tidak menganggap daftar yang tampil sudah lengkap.
 */
@Composable
private fun TodayDeliveryList(
    deliveries: List<CourierDashboardOrder>,
    totalTodayDeliveries: Int?,
    onOpenDelivery: (String) -> Unit
) {
    if (deliveries.isEmpty()) {
        CourierCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Belum ada pengantaran hari ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (totalTodayDeliveries != null && totalTodayDeliveries > deliveries.size) {
            CourierCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Menampilkan ${deliveries.size} pengantaran terbaru dari " +
                        "$totalTodayDeliveries pengantaran hari ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        deliveries.forEach { order ->
            val assignmentId = order.assignmentId
            CourierCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = if (assignmentId != null) {
                    { onOpenDelivery(assignmentId) }
                } else {
                    null
                }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pesanan #${order.orderId}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = order.deliveryAddress ?: ALAMAT_TIDAK_TERSEDIA,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        order.payment?.paymentStatus?.let { paymentStatus ->
                            PaymentStatusChip(status = paymentStatus)
                        }
                        Text(
                            text = Money.format(order.totalAmount),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (assignmentId == null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = BELUM_ADA_ASSIGNMENT_NOTE,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
    )
}

private const val ALAMAT_TIDAK_TERSEDIA = "Alamat tidak tersedia"

private const val BELUM_ADA_ASSIGNMENT_NOTE =
    "Pesanan ini belum punya penugasan, jadi tidak bisa dibuka dari aplikasi kurir."