package com.kyusui.app.ui.owner.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyusui.app.core.Money
import com.kyusui.app.domain.model.CourierSummary
import com.kyusui.app.domain.model.OrderItem
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OwnerOrder
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InfoRow
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.PaymentStatusChip
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.components.SecondaryButton
import com.kyusui.app.ui.components.StatusChip
import com.kyusui.app.ui.state.LoadState

private const val UNKNOWN_STATUS_LABEL = "Status tidak dikenal"
private const val UNKNOWN_PAYMENT_LABEL = "Belum ada pembayaran"

/**
 * Detail order Owner dari `GET /owner/orders/{order}` (specification 10.2).
 *
 * Layar ini juga menjadi tempat dua aksi Owner dijalankan:
 *
 * - `PATCH /owner/orders/{order}/status` (10.3) untuk memproses pesanan
 * - `POST /owner/orders/{order}/assignment` (10.4) untuk menugaskan kurir
 *
 * Dua hal yang dijaga tampilan:
 *
 * 1. Hanya next state dari lifecycle canonical yang ditawarkan. Backend tetap
 *    memvalidasi transition, dan penolakan `409`/`422` ditampilkan apa adanya.
 * 2. `courier_id` dipilih dari daftar `GET /owner/couriers`, bukan diketik
 *    manual. Backend hanya mengirim kurir yang tidak sedang mengantar, sehingga
 *    Android tidak memvalidasi ketersediaan kurir secara lokal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerOrderDetailScreen(
    uiState: OwnerOrderDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpdateStatus: (OrderStatus, String?) -> Unit,
    onAssignCourier: (String) -> Unit,
    onRetryCouriers: () -> Unit,
    onOpenQrisVerification: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.order?.orderNumber?.takeIf { it.isNotBlank() }
                            ?: "Detail Pesanan"
                    )
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
        when (val state = uiState.orderState) {
            is LoadState.Loading -> LoadingState(
                modifier = Modifier.padding(innerPadding),
                message = "Memuat detail pesanan..."
            )

            is LoadState.Error -> ErrorState(
                modifier = Modifier.padding(innerPadding),
                message = state.message,
                isRetryable = state.isRetryable,
                onRetry = onRetry
            )

            is LoadState.Empty -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ErrorState(
                    message = state.message,
                    isRetryable = false,
                    onRetry = onRetry
                )
            }

            is LoadState.Success -> OrderDetailContent(
                order = state.data,
                uiState = uiState,
                onUpdateStatus = onUpdateStatus,
                onAssignCourier = onAssignCourier,
                onRetryCouriers = onRetryCouriers,
                onOpenQrisVerification = onOpenQrisVerification,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun OrderDetailContent(
    order: OwnerOrder,
    uiState: OwnerOrderDetailUiState,
    onUpdateStatus: (OrderStatus, String?) -> Unit,
    onAssignCourier: (String) -> Unit,
    onRetryCouriers: () -> Unit,
    onOpenQrisVerification: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Status dan pembayaran -------------------------------------------
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

                order.payment?.paymentMethod?.let { method ->
                    InfoRow(label = "Metode pembayaran", value = method.displayName)
                }
            }
        }

        // --- Pelanggan -------------------------------------------------------
        CustomerSection(order = order)

        // --- Item ------------------------------------------------------------
        ItemsSection(items = order.items)

        // --- Nominal ---------------------------------------------------------
        AmountSection(order = order)

        // --- Pengiriman ------------------------------------------------------
        DeliverySection(order = order)

        // --- Penugasan kurir -------------------------------------------------
        CourierAssignmentSection(
            order = order,
            couriersState = uiState.couriersState,
            isSubmitting = uiState.isAssigningCourier,
            errorMessage = uiState.assignmentErrorMessage,
            successMessage = uiState.assignmentMessage,
            onRetryCouriers = onRetryCouriers,
            onAssign = onAssignCourier
        )

        // --- Aksi proses -----------------------------------------------------
        StatusActionSection(
            order = order,
            isSubmitting = uiState.isUpdatingStatus,
            errorMessage = uiState.statusErrorMessage,
            successMessage = uiState.statusMessage,
            onUpdateStatus = onUpdateStatus
        )

        // --- Verifikasi QRIS -------------------------------------------------
        if (order.payment?.paymentMethod == PaymentMethod.QRIS) {
            SecondaryButton(
                text = "Buka Verifikasi QRIS",
                onClick = { onOpenQrisVerification(order.id) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CustomerSection(order: OwnerOrder) {
    SectionCard(
        icon = Icons.Outlined.Person,
        title = "Pelanggan"
    ) {
        val name = order.customer?.name
        if (name.isNullOrBlank()) {
            // Section 10.2 menyebut customer context bersifat opsional. Kalau
            // backend tidak mengirimnya, Android tidak mengarang nama pelanggan.
            Text(
                text = "Data pelanggan tidak disertakan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            InfoRow(label = "Nama", value = name)
            order.customer?.id?.let { InfoRow(label = "ID Pelanggan", value = it) }
            order.customerId?.let { InfoRow(label = "Customer ID", value = it) }
        }
    }
}

@Composable
private fun ItemsSection(items: List<OrderItem>) {
    SectionCard(
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        title = "Item Pesanan"
    ) {
        if (items.isEmpty()) {
            Text(
                text = "Daftar item tidak disertakan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.quantity}x ${item.productName}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = Money.format(item.lineTotal),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountSection(order: OwnerOrder) {
    SectionCard(
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        title = "Rincian Biaya"
    ) {
        // Seluruh nominal disalin apa adanya dari backend. Android tidak
        // menjumlahkan ulang subtotal, ongkir, maupun total.
        InfoRow(label = "Harga produk", value = Money.format(order.subtotalAmount))
        // Tampilkan biaya historis hanya jika memang tersimpan pada order lama.
        if (order.deliveryFee.toBigDecimalOrNull()?.compareTo(java.math.BigDecimal.ZERO) == 1) {
            InfoRow(label = "Ongkos kirim", value = Money.format(order.deliveryFee))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(
            label = "Total",
            value = Money.format(order.totalAmount),
            valueStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun DeliverySection(order: OwnerOrder) {
    SectionCard(
        icon = Icons.Outlined.LocationOn,
        title = "Alamat Pengiriman"
    ) {
        val address = order.deliveryAddress
        if (address.isNullOrBlank()) {
            Text(
                text = "Alamat pengiriman tidak disertakan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(text = address, style = MaterialTheme.typography.bodyMedium)
        }

        order.deliveryLocation?.let { point ->
            InfoRow(
                label = "Koordinat",
                value = "${point.latitude}, ${point.longitude}"
            )
        }
    }
}

/**
 * Bagian penugasan kurir.
 *
 * Pilihan kurir dimuat dari `GET /owner/couriers`. Backend hanya mengirim kurir
 * yang tidak sedang mengantarkan order lain, jadi Android menampilkan apa yang
 * diterima tanpa menghitung ulang ketersediaan.
 *
 * Bila order sudah punya assignment, bagian ini hanya menampilkan assignment
 * yang backend kirim, tanpa menyediakan form untuk menimpa.
 */
@Composable
private fun CourierAssignmentSection(
    order: OwnerOrder,
    couriersState: LoadState<List<CourierSummary>>,
    isSubmitting: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onRetryCouriers: () -> Unit,
    onAssign: (String) -> Unit
) {
    SectionCard(
        icon = Icons.Outlined.LocationOn,
        title = "Penugasan Kurir"
    ) {
        val assignment = order.assignment

        if (assignment != null) {
            InfoRow(label = "ID Kurir", value = assignment.courierId)
            InfoRow(label = "Status", value = assignment.status.ownerLabel())
            assignment.assignedAt?.let { InfoRow(label = "Ditugaskan", value = it) }
            return@SectionCard
        }

        if (!order.canRequestCourierAssignment) {
            Text(
                text = "Order belum dapat ditugaskan pada tahap ini. Backend " +
                    "memvalidasi apakah order dapat ditugaskan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        when (val state = couriersState) {
            is LoadState.Loading -> Text(
                text = "Memuat daftar kurir...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            is LoadState.Error -> InlineError(
                message = state.message,
                onRetry = onRetryCouriers
            )

            is LoadState.Empty -> Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            is LoadState.Success -> CourierPicker(
                couriers = state.data,
                isSubmitting = isSubmitting,
                onAssign = onAssign
            )
        }

        errorMessage?.let { InlineError(message = it) }

        if (successMessage != null) {
            Text(
                text = successMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Daftar pilihan kurir.
 *
 * Owner memilih satu kurir sebelum menugaskan. Tombol kirim tidak aktif sampai
 * ada pilihan, sehingga tidak ada `courier_id` yang dikirim tanpa dasar.
 */
@Composable
private fun CourierPicker(
    couriers: List<CourierSummary>,
    isSubmitting: Boolean,
    onAssign: (String) -> Unit
) {
    var selectedCourierId by rememberSaveable { mutableStateOf<String?>(null) }

    Text(
        text = "Pilih kurir yang akan ditugaskan.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    couriers.forEach { courier ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isSubmitting) { selectedCourierId = courier.id },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedCourierId == courier.id,
                onClick = { selectedCourierId = courier.id },
                enabled = !isSubmitting
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = courier.displayLabel,
                    style = MaterialTheme.typography.bodyLarge
                )
                val detail = listOfNotNull(courier.vehicle, courier.phone)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(separator = " • ")
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    PrimaryButton(
        text = "Tugaskan Kurir",
        onClick = { selectedCourierId?.let(onAssign) },
        modifier = Modifier.fillMaxWidth(),
        // Loading state sekaligus jadi guard double submit: tombol tidak bisa
        // ditekan ulang saat request yang sama masih berjalan.
        enabled = selectedCourierId != null && !isSubmitting,
        isLoading = isSubmitting
    )
}

/**
 * Bagian aksi perubahan status.
 *
 * Tombol hanya untuk next state dari lifecycle canonical. Bila status saat ini
 * tidak dikenal, tidak ada tombol sama sekali: Android tidak menebak status
 * berikutnya dari status yang tidak dimengerti.
 */
@Composable
private fun StatusActionSection(
    order: OwnerOrder,
    isSubmitting: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onUpdateStatus: (OrderStatus, String?) -> Unit
) {
    SectionCard(
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        title = "Proses Pesanan"
    ) {
        val nextStatuses = order.availableStatusActions

        if (nextStatuses.isEmpty()) {
            Text(
                text = when (order.orderStatus) {
                    OrderStatus.SELESAI -> "Pesanan sudah selesai."
                    OrderStatus.MENUNGGU_PEMBAYARAN ->
                        "Pesanan masih menunggu pembayaran dari pelanggan."

                    else -> "Status pesanan tidak dikenali. Muat ulang untuk " +
                        "mengambil status terbaru dari server."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        nextStatuses.forEach { next ->
            PrimaryButton(
                text = next.ownerActionLabel(),
                onClick = { onUpdateStatus(next, null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
                isLoading = isSubmitting
            )
            Spacer(Modifier.height(8.dp))
        }

        errorMessage?.let { InlineError(message = it) }

        if (successMessage != null) {
            Text(
                text = successMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Kartu bagian dengan judul dan ikon.
 *
 * Dibuat di file ini karena seluruh bagian detail order memakai pola yang sama
 * dan tidak pernah keluar dari layout ini.
 */
@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall)
            }

            content()
        }
    }
}

/**
 * Label aksi untuk next status.
 *
 * Teks memakai nilai canonical backend, bukan istilah baru, supaya Owner melihat
 * nilai yang sama dengan yang tercatat di server.
 */
internal fun OrderStatus.ownerActionLabel(): String = when (this) {
    OrderStatus.DIPROSES -> "Mulai Proses"
    OrderStatus.DITUGASKAN -> "Tandai Sudah Ditugaskan"
    OrderStatus.DALAM_PENGANTARAN -> "Tandai Sedang Diantar"
    OrderStatus.SELESAI -> "Tandai Selesai"
    OrderStatus.MENUNGGU_PEMBAYARAN -> "Tunggu Pembayaran"
    OrderStatus.MENUNGGU_DIPROSES -> "Tunggu Diproses"
    // `DIBATALKAN` tidak pernah menjadi aksi lanjutan Owner, tetapi cabang ini
    // diperlukan supaya `when` tetap lengkap untuk seluruh status canonical.
    OrderStatus.DIBATALKAN -> "Pesanan Dibatalkan"
}