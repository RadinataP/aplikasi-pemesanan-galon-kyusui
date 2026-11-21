package com.kyusui.app.ui.customer.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WaterfallChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyusui.app.core.Money
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.domain.model.Order
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.InlineLoading
import com.kyusui.app.ui.customer.CustomerBottomBar
import com.kyusui.app.ui.customer.CustomerTab
import com.kyusui.app.ui.state.LoadState

@Composable
fun CustomerHomeScreen(
    customerName: String?,
    homeState: CustomerHomeUiState,
    cartItemCount: Int,
    onBrowseProducts: () -> Unit,
    onOpenCart: () -> Unit,
    onViewActiveOrders: () -> Unit,
    onViewOrderDetail: (String) -> Unit,
    onTrackOrder: (String) -> Unit,
    onRetry: () -> Unit,
    onViewProfile: () -> Unit,
    selectedDeliveryPoint: GeoPoint?,
    modifier: Modifier = Modifier
) {
    val name = customerName?.trim()?.takeIf(String::isNotBlank) ?: "Pelanggan"
    val firstName = remember(name) { name.substringBefore(' ').ifBlank { name } }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            CustomerBottomBar(
                selected = CustomerTab.HOME,
                onHome = {},
                onProducts = onBrowseProducts,
                onOrders = onViewActiveOrders,
                onAccount = onViewProfile
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Compact identity header: cart remains one-tap accessible without consuming a full app bar.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Outlined.WaterDrop, contentDescription = "Berkah Water", tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(11.dp).size(25.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("BERKAH WATER", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
                    Text("AIR MINUM & PENGANTARAN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.1.sp)
                }
                IconButton(onClick = onOpenCart) {
                    Box {
                        Icon(Icons.Filled.ShoppingBag, contentDescription = "Keranjang", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(25.dp))
                        if (cartItemCount > 0) {
                            Surface(color = MaterialTheme.colorScheme.error, shape = CircleShape,
                                modifier = Modifier.align(Alignment.TopEnd).size(17.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(cartItemCount.coerceAtMost(99).toString(), color = Color.White,
                                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = true, enter = fadeIn(tween(350)) + slideInVertically(
                animationSpec = tween(350, easing = FastOutSlowInEasing), initialOffsetY = { it / 10 }
            )) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Selamat datang,", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(firstName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(7.dp))
                        Text("✦", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleLarge)
                    }
                    Text("Kebutuhan air minum harian, lebih mudah dari sini.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            PremiumHero(onBrowseProducts)

            // Compact location strip, informational only (address is selected in checkout by existing flow).
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(10.dp).size(21.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Lokasi pengantaran", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(if (selectedDeliveryPoint != null) "Titik lokasi siap digunakan saat checkout" else "Alamat dipilih saat checkout",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                }
            }

            // Quick action row gives a clear next step without inventing product data.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.WaterDrop,
                    title = "Jelajahi produk", subtitle = "Pilih kebutuhan air", onClick = onBrowseProducts)
                QuickActionCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.WaterfallChart,
                    title = "Pesanan saya", subtitle = "Pantau transaksi", onClick = onViewActiveOrders)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Pesanan aktif", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Perkembangan pesanan terbarumu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onViewActiveOrders) { Text("Semua", fontWeight = FontWeight.Bold) }
                }
                when (val state = homeState.ordersState) {
                    is LoadState.Loading -> Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InlineLoading(); Text("Memuat pesanan…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is LoadState.Error -> InlineError(message = state.message, onRetry = if (state.isRetryable) onRetry else null)
                    is LoadState.Empty -> EmptyOrdersCard(onBrowseProducts)
                    is LoadState.Success -> if (homeState.activeOrders.isEmpty()) EmptyOrdersCard(onBrowseProducts)
                        else homeState.activeOrders.take(2).forEach { order ->
                            ActiveOrderTile(order, onClick = { onViewOrderDetail(order.id) }, onTrack = { onTrackOrder(order.id) })
                        }
                }
            }

            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                Text("Terima kasih telah mempercayakan kebutuhan airmu kepada Berkah Water.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PremiumHero(onBrowseProducts: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF159FC5), Color(0xFF28BFC0), Color(0xFF68D9CA))))
        .padding(20.dp)) {
        // Abstract water-glass motif; no remote image or placeholder product photo is used.
        Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(138.dp)
            .background(Color.White.copy(alpha = 0.07f), CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 54.dp, bottom = 3.dp).size(70.dp)
            .background(Color(0xFF72E5E6).copy(alpha = 0.13f), CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(color = Color.White.copy(alpha = 0.13f), shape = RoundedCornerShape(50)) {
                Text("BERKAH WATER  /  EVERYDAY FRESH", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    color = Color(0xFFE8FFFF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Air segar.\nHari lebih nyaman.", style = MaterialTheme.typography.headlineMedium,
                        color = Color.White, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
                    Text("Pesan kebutuhan air minummu tanpa ribet.", style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f))
                }
                Box(Modifier.padding(start = 6.dp).size(78.dp).clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFB7F5F0).copy(alpha = .28f), Color.White.copy(alpha = .08f)))),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = Color.White, modifier = Modifier.size(47.dp))
                }
            }
            Surface(onClick = onBrowseProducts, color = Color.White, contentColor = Color(0xFF087FA8),
                shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mulai pesan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = modifier.animateContentSize(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyOrdersCard(onBrowseProducts: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(13.dp).size(27.dp))
            }
            Text("Belum ada pesanan aktif", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Pesanan yang kamu buat akan muncul di sini.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onBrowseProducts) { Text("Jelajahi produk", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ActiveOrderTile(order: Order, onClick: () -> Unit, onTrack: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ORDER ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    Text(order.orderNumber.ifBlank { "Pesanan" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(Money.format(order.totalAmount), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(order.orderStatus?.displayName ?: "Status diperbarui", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = if (order.orderStatus?.isTrackable() == true) onTrack else onClick) {
                    Text(if (order.orderStatus?.isTrackable() == true) "Lacak" else "Detail", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
