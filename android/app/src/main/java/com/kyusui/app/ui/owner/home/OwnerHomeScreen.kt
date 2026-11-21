package com.kyusui.app.ui.owner.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyusui.app.domain.model.OwnerDashboardData
import com.kyusui.app.domain.model.OwnerRevenuePoint
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.owner.components.OwnerBottomNavigation
import com.kyusui.app.ui.owner.components.OwnerMainTab
import java.text.NumberFormat
import java.util.Locale

private val OwnerBlue = Color(0xFF087FA8)
private val OwnerTeal = Color(0xFF20B8B0)
private val OwnerInk = Color(0xFF163B50)

/** Dashboard Owner dengan hierarki analitik dan operasional. */
@Composable
fun OwnerHomeScreen(
    uiState: OwnerHomeUiState,
    onOpenOrderList: () -> Unit,
    onOpenQrisVerification: () -> Unit,
    onOpenQrisSettings: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenDirectOrder: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    selectedTab: OwnerMainTab = OwnerMainTab.HOME,
    onSelectMainTab: (OwnerMainTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BERKAH WATER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Owner workspace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Pengaturan") }
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Outlined.Logout, "Keluar") }
                }
            )
        },
        // Bottom navigation menggunakan route state dari NavigationHost.
        // Screen tidak membuat NavController sendiri agar back stack tetap terpusat.
        bottomBar = {
            OwnerBottomNavigation(
                selectedTab = selectedTab,
                onSelect = onSelectMainTab
            )
        }
    ) { insets ->
        when {
            uiState.isLoading && !uiState.hasAnySummary -> LoadingState(
                modifier = Modifier.padding(insets), message = "Menyiapkan dashboard usaha..."
            )
            uiState.errorMessage != null && !uiState.hasAnySummary -> ErrorState(
                modifier = Modifier.padding(insets), message = uiState.errorMessage, onRetry = onRetry
            )
            else -> {
                val data = uiState.dashboard
                Column(
                    modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Selamat datang kembali", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = OwnerInk)
                    }
                    data?.let { dashboard ->
                        RevenueHeroCard(dashboard)
                        SectionTitle("Ringkasan operasional", "Status aktivitas depot saat ini")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard("Total pesanan", dashboard.totalOrders.toString(), Icons.AutoMirrored.Outlined.ReceiptLong, Modifier.weight(1f), onClick = onOpenOrderList)
                            MetricCard("Menunggu QRIS", dashboard.waitingQris.toString(), Icons.Outlined.QrCode2, Modifier.weight(1f), warning = dashboard.waitingQris > 0, onClick = onOpenQrisVerification)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard("Menunggu proses", dashboard.waitingProcess.toString(), Icons.Outlined.Receipt, Modifier.weight(1f), onClick = onOpenOrderList)
                            MetricCard("Pengiriman aktif", dashboard.activeDeliveries.toString(), Icons.Outlined.LocalShipping, Modifier.weight(1f), onClick = onOpenOrderList)
                        }
                        RevenueChartCard(dashboard.revenueChart)
                        if (dashboard.recentOrders.isNotEmpty()) {
                            SectionTitle("Pesanan terbaru", "Data pesanan langsung dari server")
                            dashboard.recentOrders.take(5).forEach { order ->
                                DashboardInfoRow(
                                    title = "Pesanan #${order.id}",
                                    subtitle = listOfNotNull(order.status?.dashboardLabel(), order.paymentStatus?.dashboardLabel()).joinToString(" · "),
                                    trailing = order.totalAmount?.let(::formatRupiah) ?: "—"
                                )
                            }
                            TextButton(onClick = onOpenOrderList, modifier = Modifier.align(Alignment.End)) { Text("Lihat semua pesanan") }
                        }
                        if (dashboard.waitingOrders.isNotEmpty()) {
                            SectionTitle("Perlu diproses", "Pesanan yang masih menunggu tindakan Owner")
                            dashboard.waitingOrders.take(5).forEach { order ->
                                DashboardInfoRow(
                                    title = "Pesanan #${order.id}",
                                    subtitle = order.status?.dashboardLabel() ?: "Status belum tersedia",
                                    trailing = order.totalAmount?.let(::formatRupiah) ?: "—"
                                )
                            }
                            TextButton(onClick = onOpenOrderList, modifier = Modifier.align(Alignment.End)) { Text("Kelola pesanan") }
                        }
                        if (dashboard.pendingQrisPayments.isNotEmpty()) {
                            SectionTitle("Menunggu verifikasi QRIS", "Pembayaran yang perlu ditinjau")
                            dashboard.pendingQrisPayments.take(5).forEach { payment ->
                                DashboardInfoRow(
                                    title = payment.customerName?.takeIf { it.isNotBlank() } ?: "Pembayaran #${payment.id}",
                                    subtitle = payment.orderId?.let { "Pesanan #$it" } ?: "ID pembayaran ${payment.id}",
                                    trailing = payment.amount?.let(::formatRupiah) ?: "—"
                                )
                            }
                            TextButton(onClick = onOpenQrisVerification, modifier = Modifier.align(Alignment.End)) { Text("Buka verifikasi QRIS") }
                        }
                        if (dashboard.activeDeliveriesList.isNotEmpty()) {
                            SectionTitle("Pengiriman aktif", "Penugasan yang sedang berjalan")
                            dashboard.activeDeliveriesList.take(5).forEach { delivery ->
                                DashboardInfoRow(
                                    title = delivery.customerName?.takeIf { it.isNotBlank() } ?: delivery.orderId?.let { "Pesanan #$it" } ?: "Pengiriman #${delivery.id}",
                                    subtitle = listOfNotNull(delivery.courierName, delivery.status?.dashboardLabel()).joinToString(" · ").ifBlank { "Informasi pengiriman tersedia" },
                                    trailing = delivery.orderId?.let { "#$it" } ?: "—"
                                )
                            }
                        }
                        if (dashboard.activeCouriers.isNotEmpty()) {
                            SectionTitle("Kurir aktif", "Kurir yang memiliki penugasan berjalan")
                            dashboard.activeCouriers.take(5).forEach { courier ->
                                DashboardInfoRow(
                                    title = courier.displayLabel,
                                    subtitle = listOfNotNull(courier.vehicle, courier.phone).joinToString(" · ").ifBlank { "Detail kurir tidak tersedia" },
                                    trailing = "Aktif"
                                )
                            }
                        }
                        SectionTitle("Aktivitas utama", "Akses cepat untuk operasional harian")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ShortcutTile("Pesanan", Icons.AutoMirrored.Outlined.ReceiptLong, onOpenOrderList, Modifier.weight(1f))
                            ShortcutTile("Produk", Icons.Outlined.Inventory2, onOpenProducts, Modifier.weight(1f))
                            ShortcutTile("QRIS", Icons.Outlined.QrCode2, onOpenQrisVerification, Modifier.weight(1f))
                            ShortcutTile("Finance", Icons.Outlined.Wallet, onOpenFinance, Modifier.weight(1f))
                        }
                        ActionRow("Buat pesanan langsung", "Catat transaksi pelanggan di depot", Icons.Outlined.AddShoppingCart, onOpenDirectOrder)
                        ActionRow("Pengaturan QRIS", "Kelola kode QR pembayaran aktif", Icons.Outlined.QrCode2, onOpenQrisSettings)
                        ActionRow("Manajemen produk", "Ubah harga, deskripsi, dan ketersediaan", Icons.Outlined.Inventory2, onOpenProducts)
                        if (uiState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick = onRetry, enabled = !uiState.isLoading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(if (uiState.isLoading) "Memperbarui..." else "Perbarui dashboard")
                        }
                    } ?: run {
                        Text("Data dashboard belum tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onRetry) { Text("Coba lagi") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RevenueHeroCard(data: OwnerDashboardData) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = OwnerBlue)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(OwnerBlue, Color(0xFF075477)))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PEMASUKAN BULAN INI", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(formatRupiah(data.monthRevenue), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FinanceMiniValue("Hari ini", formatRupiah(data.todayRevenue))
                FinanceMiniValue("Pengeluaran", formatRupiah(data.monthExpense))
                FinanceMiniValue("Arus bersih", formatRupiah(data.monthNet))
            }
        }
    }
}

@Composable
private fun FinanceMiniValue(label: String, value: String) {
    Column(Modifier.widthIn(max = 120.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RevenueChartCard(points: List<OwnerRevenuePoint>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Tren arus kas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Enam bulan terakhir · berdasarkan buku kas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (points.isEmpty()) {
                Text("Belum ada data transaksi untuk grafik.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Canvas(Modifier.fillMaxWidth().height(132.dp)) {
                    val maxValue = points.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1.0)
                    val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                    val incomePath = Path()
                    val expensePath = Path()
                    points.forEachIndexed { index, point ->
                        val x = index * stepX
                        val incomeY = size.height - (point.income / maxValue).toFloat() * size.height * .88f - size.height * .05f
                        val expenseY = size.height - (point.expense / maxValue).toFloat() * size.height * .88f - size.height * .05f
                        if (index == 0) { incomePath.moveTo(x, incomeY); expensePath.moveTo(x, expenseY) }
                        else { incomePath.lineTo(x, incomeY); expensePath.lineTo(x, expenseY) }
                    }
                    drawPath(incomePath, OwnerBlue, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(expensePath, OwnerTeal, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    points.forEachIndexed { index, point ->
                        val x = index * stepX
                        val y = size.height - (point.income / maxValue).toFloat() * size.height * .88f - size.height * .05f
                        drawCircle(OwnerBlue, radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    points.forEach { Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(OwnerBlue, "Pemasukan")
                    LegendDot(OwnerTeal, "Pengeluaran")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    warning: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, null, tint = if (warning) MaterialTheme.colorScheme.error else OwnerBlue, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShortcutTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = OwnerBlue, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Icon(icon, null, tint = OwnerBlue, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardInfoRow(title: String, subtitle: String, trailing: String) {
    Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle.ifBlank { "Status belum tersedia" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(trailing, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OwnerBlue)
        }
    }
}

private fun String.dashboardLabel(): String = lowercase(Locale("id", "ID"))
    .replace('_', ' ')
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("id", "ID")) else it.toString() }

private fun formatRupiah(raw: String): String = runCatching {
    val amount = raw.toBigDecimal()
    "Rp" + NumberFormat.getNumberInstance(Locale("id", "ID")).format(amount)
}.getOrDefault("Rp—")
