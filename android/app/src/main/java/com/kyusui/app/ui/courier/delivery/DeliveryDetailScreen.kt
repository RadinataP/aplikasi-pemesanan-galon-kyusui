package com.kyusui.app.ui.courier.delivery

import com.kyusui.app.ui.courier.design.CourierCard

import com.kyusui.app.ui.courier.design.CourierPageIntro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyusui.app.core.Money
import com.kyusui.app.ui.courier.design.CourierColors
import com.kyusui.app.ui.courier.design.CourierSpacing
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeliveryItem
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.ui.maps.CourierDeliveryMap
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.OutlinedButton
import com.kyusui.app.ui.components.PaymentStatusChip
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.components.StatusChip
import com.kyusui.app.ui.state.LoadState

/**
 * Detail pengantaran untuk satu assignment.
 *
 * Layar ini menampilkan data pengantaran apa adanya dari backend dan menyediakan
 * aksi yang memang ada endpointnya: Mulai Pengantaran, Kirim Lokasi, dan
 * Selesaikan Pengantaran. Konfirmasi tunai berada di layar sendiri.
 *
 * Aturan yang dijaga di layar:
 *
 * - Setiap aksi punya tombol yang hanya muncul bila [CourierDelivery] menyatakan
 *   kondisinya terpenuhi. Itu hanya penyaringan tampilan, bukan otorisasi.
 *   Backend tetap memeriksa ulang sebelum menerima aksi.
 * - Setiap aksi punya status sendiri, jadi satu aksi yang gagal tidak mematikan
 *   aksi lain dan pesan errornya terlihat tepat di bawah aksi yang gagal.
 * - Tidak ada status, nominal, atau tanda pembayaran yang dihitung di Android.
 */
@Composable
fun DeliveryDetailScreen(
    uiState: DeliveryDetailUiState,
    onStartDelivery: () -> Unit,
    onCompleteDelivery: () -> Unit,
    onOpenCustomerLocation: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenCashConfirmation: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenQrisVerification: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Pesan aksi ditampilkan lewat snackbar lalu dibersihkan, supaya aksi
    // berikutnya tidak mewarisi pesan aksi sebelumnya.
    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        modifier = modifier,
        containerColor = CourierColors.Background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.delivery
                            ?.order
                            ?.let { "Pesanan #${it.id}" }
                            ?: "Detail Pengantaran"
                    )
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState.deliveryState) {
            is LoadState.Loading -> LoadingState(
                modifier = Modifier.padding(innerPadding),
                message = "Memuat detail pengantaran..."
            )

            is LoadState.Error -> ErrorState(
                modifier = Modifier.padding(innerPadding),
                message = state.message,
                isRetryable = state.isRetryable,
                onRetry = if (state.isRetryable) onRetry else null
            )

            is LoadState.Empty -> LoadingState(
                modifier = Modifier.padding(innerPadding),
                message = "Memuat detail pengantaran..."
            )

            is LoadState.Success -> DeliveryContent(
                modifier = Modifier.padding(innerPadding),
                delivery = state.data,
                uiState = uiState,
                onStartDelivery = onStartDelivery,
                onCompleteDelivery = onCompleteDelivery,
                onOpenCustomerLocation = onOpenCustomerLocation,
                onOpenTracking = onOpenTracking,
                onOpenCashConfirmation = onOpenCashConfirmation,
                onOpenQrisVerification = onOpenQrisVerification,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun DeliveryContent(
    delivery: CourierDelivery,
    uiState: DeliveryDetailUiState,
    onStartDelivery: () -> Unit,
    onCompleteDelivery: () -> Unit,
    onOpenCustomerLocation: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenCashConfirmation: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenQrisVerification: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        CourierPageIntro(
            eyebrow = "Operasional / Pesanan",
            title = "Detail pengantaran",
            description = "Periksa informasi pesanan sebelum melanjutkan tindakan."
        )
        StatusHeader(delivery = delivery)

        OrderCard(delivery = delivery)

        delivery.order?.let { order ->
            CourierCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Peta Pengantaran", style = MaterialTheme.typography.titleMedium)
                    CourierDeliveryMap(
                        destination = order.deliveryLocation?.point,
                        courier = delivery.latestLocation?.let { location ->
                            val lat = location.latitude
                            val lng = location.longitude
                            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) GeoPoint(lat, lng) else null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        delivery.order?.items?.takeIf { it.isNotEmpty() }?.let { items ->
            ItemListCard(items = items)
        }

        ActionSection(
            delivery = delivery,
            uiState = uiState,
            onStartDelivery = onStartDelivery,
            onCompleteDelivery = onCompleteDelivery,
            onOpenCustomerLocation = onOpenCustomerLocation,
            onOpenTracking = onOpenTracking,
                onOpenCashConfirmation = onOpenCashConfirmation
        )

        if (delivery.order?.payment?.paymentMethod == com.kyusui.app.domain.model.PaymentMethod.QRIS &&
            delivery.order?.payment?.paymentStatus == com.kyusui.app.domain.model.PaymentStatus.WAITING_VERIFICATION) {
            PrimaryButton(text = "Verifikasi QRIS", onClick = onOpenQrisVerification)
        }

        OutlinedButton(
            text = "Muat Ulang",
            onClick = onRetry,
            enabled = !uiState.isBusy,
            isLoading = uiState.isBusy,
            icon = Icons.Outlined.Refresh
        )
    }
}

@Composable
private fun StatusHeader(delivery: CourierDelivery) {
    CourierCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Status Pengantaran",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = delivery.status?.displayName ?: STATUS_TIDAK_DIKETAHUI,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                delivery.order?.status?.let { orderStatus ->
                    StatusChip(status = orderStatus)
                }
            }

            delivery.assignedAt?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ditugaskan: ${formatTimestamp(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            delivery.startedAt?.let {
                Text(
                    text = "Dimulai: ${formatTimestamp(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            delivery.completedAt?.let {
                Text(
                    text = "Selesai: ${formatTimestamp(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (delivery.status == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = STATUS_TIDAK_DIKETAHUI_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun OrderCard(delivery: CourierDelivery) {
    val order = delivery.order ?: return

    CourierCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "Informasi Pengiriman", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            order.customerName?.let { customerName ->
                LabeledValueRow(
                    icon = Icons.Outlined.Person,
                    label = "Pelanggan",
                    value = customerName
                )
            }

            LabeledValueRow(
                icon = Icons.Outlined.LocationOn,
                label = "Alamat Tujuan",
                value = order.deliveryAddress ?: ALAMAT_TIDAK_TERSEDIA
            )

            LabeledValueRow(
                icon = Icons.Outlined.AccountBalanceWallet,
                label = "Total",
                value = Money.format(order.totalAmount)
            )

            order.payment?.let { payment ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Metode: ${payment.paymentMethod?.displayName ?: METODE_TIDAK_TERSEDIA}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    payment.paymentStatus?.let { paymentStatus ->
                        Spacer(Modifier.width(8.dp))
                        PaymentStatusChip(status = paymentStatus)
                    }
                }
            }

            if (order.payment?.paymentMethod == PaymentMethod.CASH &&
                order.payment.paymentStatus == PaymentStatus.PENDING
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = CASH_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ItemListCard(items: List<CourierDeliveryItem>) {
    CourierCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "Barang yang Diantar", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.productName.ifBlank { NAMA_PRODUK_TIDAK_TERSEDIA },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${item.quantity} x ${Money.format(item.unitPrice)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = Money.format(item.lineTotal),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/**
 * Bagian aksi.
 *
 * Urutan tombol mengikuti urutan kerja kurir: mulai, lihat lokasi, konfirmasi
 * tunai, lalu selesaikan. Tombol yang kondisinya belum terpenuhi tidak
 * ditampilkan, dan alasannya ditulis sebagai catatan supaya kurir tahu kenapa
 * aksi itu belum bisa dilakukan.
 */
@Composable
private fun ActionSection(
    delivery: CourierDelivery,
    uiState: DeliveryDetailUiState,
    onStartDelivery: () -> Unit,
    onCompleteDelivery: () -> Unit,
    onOpenCustomerLocation: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenCashConfirmation: () -> Unit
) {
    CourierCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Tindakan Pengantaran", style = MaterialTheme.typography.titleLarge)

            if (delivery.canStartDelivery) {
                PrimaryButton(
                    text = "Mulai Pengantaran",
                    onClick = onStartDelivery,
                    isLoading = uiState.isStarting,
                    icon = Icons.Outlined.LocalShipping
                )
                Text(
                    text = START_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.startErrorMessage?.let { message ->
                InlineError(message = message, onRetry = onStartDelivery)
            }

            OutlinedButton(
                text = "Lokasi Pelanggan",
                onClick = onOpenCustomerLocation,
                icon = Icons.Outlined.LocationOn
            )

            OutlinedButton(
                text = "Status Tracking Otomatis",
                onClick = onOpenTracking,
                icon = Icons.Outlined.MyLocation
            )

            if (delivery.canConfirmCash) {
                PrimaryButton(
                    text = "Uang Diterima",
                    onClick = onOpenCashConfirmation,
                    icon = Icons.Outlined.AccountBalanceWallet
                )
                Text(
                    text = CASH_ACTION_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (delivery.canCompleteDelivery) {
                PrimaryButton(
                    text = "Selesaikan Pengantaran",
                    onClick = onCompleteDelivery,
                    isLoading = uiState.isCompleting
                )
            }

            uiState.completeErrorMessage?.let { message ->
                InlineError(message = message, onRetry = onCompleteDelivery)
            }

            if (delivery.isFinished) {
                Text(
                    text = FINISHED_NOTE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LabeledValueRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Menampilkan nilai waktu dari backend apa adanya.
 *
 * Nilai ini adalah timestamp server. Android tidak memformatnya ke zona waktu
 * lokal karena itu akan mengubah waktu yang tercatat server, dan kurir membandingkan
 * waktu ini dengan waktu yang sama di layar lain.
 */
private fun formatTimestamp(raw: String): String = raw

private const val ALAMAT_TIDAK_TERSEDIA = "Alamat tidak tersedia"
private const val STATUS_TIDAK_DIKETAHUI = "Status tidak diketahui"
private const val STATUS_TIDAK_DIKETAHUI_NOTE =
    "Backend mengirim status penugasan yang tidak dikenali. Hubungi pemilik toko."
private const val METODE_TIDAK_TERSEDIA = "tidak tersedia"
private const val NAMA_PRODUK_TIDAK_TERSEDIA = "Nama produk tidak tersedia"
private const val CASH_NOTE =
    "Pembayaran tunai dikonfirmasi kurir saat uang diterima, bukan oleh pemilik toko."
private const val START_NOTE =
    "Backend akan menolak bila pesanan ini belum siap atau Anda masih mengantar pesanan lain."
private const val CASH_ACTION_NOTE =
    "Kirim konfirmasi setelah menerima uang. Server yang memutuskan apakah konfirmasi ini sah."
private const val FINISHED_NOTE =
    "Pengantaran sudah selesai. Backend tidak menerima aksi kurir lagi untuk penugasan ini."
