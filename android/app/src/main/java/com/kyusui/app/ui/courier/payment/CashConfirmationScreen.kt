package com.kyusui.app.ui.courier.payment

import com.kyusui.app.ui.courier.design.CourierCard

import com.kyusui.app.ui.courier.design.CourierPageIntro

import com.kyusui.app.ui.courier.design.CourierSpacing
import com.kyusui.app.ui.courier.design.CourierColors
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyusui.app.core.Money
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.PaymentStatusChip
import com.kyusui.app.ui.components.PrimaryButton
import com.kyusui.app.ui.state.LoadState

/**
 * Konfirmasi "Uang Diterima" untuk pembayaran tunai.
 *
 * Layar ini punya satu aksi. Android hanya mengirim aksi ke
 * `POST courier/assignments/{assignment}/cash/confirm`, yang tidak punya body.
 * Backend yang memeriksa apakah kurir benar pemilik assignment, apakah order itu
 * milik assignment tersebut, apakah delivery aktif, apakah metode pembayarannya
 * `CASH`, dan apakah pembayarannya belum lunas.
 *
 * Karena itu layar ini tidak punya apa pun untuk dikirim selain konfirmasi itu
 * sendiri: tidak ada nominal yang bisa diedit, tidak ada nomor rekening, tidak
 * ada bukti, dan tidak ada pilihan status pembayaran. Nominal yang ditampilkan
 * dibaca dari backend, dan status lunas hanya ditampilkan setelah server
 * mengatakannya.
 */
@Composable
fun CashConfirmationScreen(
    uiState: CashConfirmationUiState,
    onConfirmCashReceived: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = CourierColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(text = "Konfirmasi Tunai") },
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
                message = "Memuat data pembayaran..."
            )

            is LoadState.Error -> ErrorState(
                modifier = Modifier.padding(innerPadding),
                message = state.message,
                isRetryable = state.isRetryable,
                onRetry = if (state.isRetryable) onRetry else null
            )

            is LoadState.Empty -> LoadingState(
                modifier = Modifier.padding(innerPadding),
                message = "Memuat data pembayaran..."
            )

            is LoadState.Success -> CashContent(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                onConfirmCashReceived = onConfirmCashReceived,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun CashContent(
    uiState: CashConfirmationUiState,
    onConfirmCashReceived: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val delivery = uiState.delivery ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CourierSpacing.Screen)
            .padding(top = CourierSpacing.Lg, bottom = CourierSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(CourierSpacing.Lg)
    ) {
        CourierPageIntro(
            eyebrow = "Operasional / Pembayaran",
            title = "Konfirmasi pembayaran",
            description = "Pastikan nominal tagihan dan status pembayaran sebelum mengonfirmasi."
        )
        AmountCard(uiState = uiState)

        uiState.receipt?.let { receipt ->
            ReceiptCard(
                receiptPaymentId = receipt.paymentId,
                receiptMessage = uiState.message,
                isPaid = uiState.isConfirmedPaid
            )
        }

        ConfirmSection(
            uiState = uiState,
            canConfirm = delivery.canConfirmCash,
            isPaymentMethodCash = delivery.order?.payment?.paymentMethod == PaymentMethod.CASH,
            onConfirmCashReceived = onConfirmCashReceived,
            onRetry = onRetry
        )
    }
}

/**
 * Nominal yang harus diterima.
 *
 * Nilai ini disalin dari `total` pada Assignment Resource. Android tidak
 * menghitung ulang, tidak membulatkan, dan tidak menambahkan biaya apa pun.
 */
@Composable
private fun AmountCard(uiState: CashConfirmationUiState, modifier: Modifier = Modifier) {
    val order = uiState.delivery?.order ?: return

    CourierCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Total Pembayaran Tunai",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = Money.format(order.totalAmount),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Nominal dihitung oleh server dari pesanan #${order.id}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            order.customerName?.let { customerName ->
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Pelanggan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = customerName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                order.payment?.paymentStatus?.let { paymentStatus ->
                    PaymentStatusChip(status = paymentStatus)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = order.payment?.paymentStatus?.displayName ?: STATUS_TIDAK_TERSEDIA,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Bukti konfirmasi dari server.
 *
 * [receiptPaymentId] adalah id payment yang dikirim backend, bukan id yang
 * dibuat Android. Kartu ini hanya muncul setelah response diterima.
 */
@Composable
private fun ReceiptCard(
    receiptPaymentId: String,
    receiptMessage: String?,
    isPaid: Boolean,
    modifier: Modifier = Modifier
) {
    CourierCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (isPaid) {
                        CourierColors.Success
                    } else {
                        CourierColors.Info
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Status Konfirmasi",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = receiptMessage ?: KONFIRMASI_DITERIMA_NOTE,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Referensi pembayaran #$receiptPaymentId dari server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Area aksi konfirmasi.
 *
 * Tombol hanya muncul ketika [canConfirm] benar, yaitu assignment aktif, metode
 * `CASH`, dan pembayaran masih belum lunas. Pemeriksaan itu menentukan tampilan
 * tombol saja; backend tetap memeriksa ulang dan dapat menolak dengan `403` atau
 * `409`.
 *
 * Bila kondisinya belum terpenuhi, alasannya ditulis di layar supaya kurir tahu
 * apa yang harus diperbaiki, bukan hanya melihat tombol yang tidak ada.
 */
@Composable
private fun ConfirmSection(
    uiState: CashConfirmationUiState,
    canConfirm: Boolean,
    isPaymentMethodCash: Boolean,
    onConfirmCashReceived: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    CourierCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Tindakan Pembayaran", style = MaterialTheme.typography.titleLarge)

            if (canConfirm) {
                PrimaryButton(
                    text = "Uang Diterima",
                    onClick = onConfirmCashReceived,
                    isLoading = uiState.isSubmitting,
                    icon = Icons.Outlined.AccountBalanceWallet
                )
                Text(
                    text = CONFIRM_ACTION_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = if (isPaymentMethodCash) {
                        CASH_NOT_AVAILABLE_NOTE
                    } else {
                        NOT_CASH_NOTE
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.errorMessage?.let { message ->
                InlineError(message = message, onRetry = onRetry)
            }
        }
    }
}

private const val STATUS_TIDAK_TERSEDIA = "Status tidak tersedia"
private const val KONFIRMASI_DITERIMA_NOTE = "Konfirmasi diterima server."
private const val CONFIRM_ACTION_NOTE =
    "Kirim hanya setelah uang benar-benar diterima. Server yang memastikan konfirmasi ini sah."

private const val CASH_NOT_AVAILABLE_NOTE =
    "Konfirmasi tunai tidak tersedia. Pengantaran harus berjalan, metode pembayaran harus " +
        "Tunai, dan pembayaran belum berstatus Sudah Dibayar."

private const val NOT_CASH_NOTE =
    "Pesanan ini dibayar bukan dengan Tunai. Konfirmasi tunai hanya untuk pembayaran tunai."