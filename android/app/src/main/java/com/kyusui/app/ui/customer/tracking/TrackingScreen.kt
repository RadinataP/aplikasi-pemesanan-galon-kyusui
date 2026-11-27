package com.kyusui.app.ui.customer.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyusui.app.domain.model.Order
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OrderTracking
import com.kyusui.app.ui.maps.CourierPositionMap
import com.kyusui.app.ui.state.LoadState

/** Tracking pelanggan dengan hierarki visual seperti aplikasi delivery:
 * peta menjadi latar, header mengambang di atas, dan panel progres di bawah.
 * Status tetap bersumber dari OrderResource backend, bukan disimpulkan dari GPS. */
@Composable
fun TrackingScreen(viewModel: TrackingViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Polling hanya hidup saat layar terlihat, seperti perilaku sebelum redesign.
    LifecycleStartEffect(Unit) {
        viewModel.startPolling()
        onStopOrDispose { viewModel.stopPolling() }
    }

    val order = (uiState.orderState as? LoadState.Success)?.data
    val latestCourierPoint = uiState.confirmedTracking?.latestLocation?.point
    val destinationPoint = order?.deliveryLocation?.point

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        // Peta menutup seluruh area layar; koordinat tujuan hanya dipakai jika backend mengirimnya.
        if (latestCourierPoint != null) {
            CourierPositionMap(
                courierPosition = latestCourierPoint,
                destinationPosition = destinationPoint,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            MapWaitingBackground(
                trackingState = uiState.trackingState,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Header mengambang meniru pola tracking delivery: kembali, judul, nomor order, refresh.
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).shadow(5.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Lacak Pesanan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = order?.orderNumber?.takeIf { it.isNotBlank() }?.let { "Pesanan $it" } ?: "Berkah Water",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Muat ulang tracking")
                }
            }
        }

        // Panel status adalah satu-satunya ringkasan utama; data koordinat dan riwayat teknis disembunyikan.
        TrackingStatusSheet(
            order = order,
            tracking = uiState.confirmedTracking,
            orderState = uiState.orderState,
            trackingState = uiState.trackingState,
            refreshError = uiState.refreshErrorMessage,
            onRefresh = viewModel::refresh,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun MapWaitingBackground(
    trackingState: LoadState<OrderTracking>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f), shape = RoundedCornerShape(20.dp), shadowElevation = 4.dp) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (trackingState) {
                    is LoadState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text("Memuat posisi kurir…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is LoadState.Error -> {
                        Icon(Icons.Outlined.LocalShipping, contentDescription = null)
                        Text("Posisi kurir belum dapat dimuat", style = MaterialTheme.typography.titleSmall)
                    }
                    is LoadState.Empty -> {
                        Icon(Icons.Outlined.WaterDrop, contentDescription = null)
                        Text("Menunggu pengantaran dimulai", style = MaterialTheme.typography.titleSmall)
                    }
                    is LoadState.Success -> {
                        Icon(Icons.Outlined.LocalShipping, contentDescription = null)
                        Text("Kurir belum mengirim lokasi", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingStatusSheet(
    order: Order?,
    tracking: OrderTracking?,
    orderState: LoadState<Order>,
    trackingState: LoadState<OrderTracking>,
    refreshError: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = order?.orderStatus
    val activeStep = status.toDeliveryStep()
    val cancelled = status == OrderStatus.DIBATALKAN

    Surface(
        modifier = modifier.shadow(12.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 20.dp, bottomEnd = 20.dp)),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Grabber dekoratif memberi kesan bottom sheet, tetapi panel tidak menghalangi tombol Android.
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (status == OrderStatus.SELESAI) Icons.Outlined.CheckCircle else Icons.Outlined.LocalShipping,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = when {
                            cancelled -> "Pesanan dibatalkan"
                            status == OrderStatus.SELESAI -> "Galon telah tiba"
                            status == OrderStatus.DALAM_PENGANTARAN -> "Galon sedang diantar"
                            status == null -> "Memuat status pesanan"
                            else -> "Galon sedang dipesan"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            cancelled -> "Pengantaran untuk pesanan ini dibatalkan."
                            status == OrderStatus.SELESAI -> "Pesanan telah selesai diantarkan."
                            status == OrderStatus.DALAM_PENGANTARAN -> "Kurir sedang menuju alamat tujuan Anda."
                            status == OrderStatus.DITUGASKAN -> "Pesanan sudah ditugaskan kepada kurir."
                            status == null -> "Status akan muncul setelah data diterima."
                            else -> "Pesanan sedang menunggu proses dari Berkah Water."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                cancelled -> Text("Status: DIBATALKAN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                status != null -> DeliveryProgress(activeStep = activeStep)
                orderState is LoadState.Error -> Text(orderState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else -> Text("Mengambil informasi pesanan…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when {
                refreshError != null && tracking != null -> Text("Posisi terakhir mungkin belum terbaru.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                trackingState is LoadState.Error -> Text("Posisi kurir belum tersedia. Anda dapat mencoba memuat ulang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                trackingState is LoadState.Empty && status != OrderStatus.SELESAI && status != OrderStatus.DIBATALKAN -> Text("Tracking akan tersedia saat pengantaran aktif.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (orderState is LoadState.Error || trackingState is LoadState.Error || refreshError != null) {
                TextButton(onClick = onRefresh, modifier = Modifier.align(Alignment.End)) { Text("Coba lagi") }
            }
        }
    }
}

@Composable
private fun DeliveryProgress(activeStep: Int) {
    val steps = listOf("Galon sedang dipesan", "Galon sedang diantar", "Galon telah tiba")
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        steps.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.size(25.dp),
                    shape = CircleShape,
                    color = if (index <= activeStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (index < activeStep) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "Selesai", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                        } else {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = if (index == activeStep) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    text = label,
                    style = if (index == activeStep) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodySmall,
                    color = if (index <= activeStep) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 7.dp)
                )
            }
            if (index < steps.lastIndex) {
                Box(modifier = Modifier.padding(start = 12.dp).size(width = 1.dp, height = 9.dp).background(if (index < activeStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

/** Kelompok status backend ke salah satu dari tiga tahap bisnis yang diminta. */
private fun OrderStatus?.toDeliveryStep(): Int = when (this) {
    OrderStatus.SELESAI -> 2
    OrderStatus.DALAM_PENGANTARAN -> 1
    OrderStatus.DIBATALKAN, null -> 0
    else -> 0
}
