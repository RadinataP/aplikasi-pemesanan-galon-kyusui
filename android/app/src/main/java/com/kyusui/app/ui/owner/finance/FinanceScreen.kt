package com.kyusui.app.ui.owner.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kyusui.app.domain.model.FinancialTransaction
import com.kyusui.app.ui.owner.components.OwnerBottomNavigation
import com.kyusui.app.ui.owner.components.OwnerMainTab
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Buku kas Owner. Semua angka ringkasan dan baris transaksi berasal dari API. */
@Composable
fun FinanceScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    vm: FinanceViewModel,
    onSelectMainTab: (OwnerMainTab) -> Unit
) {
    var showEntryForm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Finance", fontWeight = FontWeight.Bold)
                        Text("Buku kas BERKAH WATER", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Kembali") } },
                actions = { TextButton(onClick = vm::load, enabled = !state.loading) { Text("Muat ulang") } }
            )
        },
        bottomBar = {
            OwnerBottomNavigation(
                selectedTab = OwnerMainTab.FINANCE,
                onSelect = onSelectMainTab
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ringkasan keuangan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Pantau uang masuk dan keluar depot dari catatan server.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ARUS KAS BERSIH", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f))
                    Text(
                        state.book?.netCashFlow?.let(::formatRupiah) ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("Berdasarkan periode filter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard(
                    title = "Pemasukan",
                    value = state.book?.income?.let(::formatRupiah) ?: "—",
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.primary
                )
                SummaryCard(
                    title = "Pengeluaran",
                    value = state.book?.expense?.let(::formatRupiah) ?: "—",
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.error
                )
            }

            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("Memuat data pembukuan…")
                }
            }
            state.error?.let { MessageCard(it, isError = true) }
            state.success?.let { MessageCard(it, isError = false) }

            SectionHeading("Filter riwayat", "Ringkasan mengikuti periode tanggal; filter jenis dan sumber hanya menyaring daftar transaksi.")
            OutlinedTextField(
                value = state.fromDate,
                onValueChange = vm::setFromDate,
                label = { Text("Dari tanggal") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.toDate,
                onValueChange = vm::setToDate,
                label = { Text("Sampai tanggal") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text("Jenis transaksi", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Semua", state.typeFilter.isBlank()) { vm.setTypeFilter("") }
                ChoiceChip("Pemasukan", state.typeFilter == "INCOME") { vm.setTypeFilter("INCOME") }
                ChoiceChip("Pengeluaran", state.typeFilter == "EXPENSE") { vm.setTypeFilter("EXPENSE") }
            }
            Text("Sumber transaksi", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Semua", state.sourceFilter.isBlank()) { vm.setSourceFilter("") }
                ChoiceChip("Pembayaran aplikasi", state.sourceFilter == "APP_PAYMENT") { vm.setSourceFilter("APP_PAYMENT") }
                ChoiceChip("Pesanan langsung", state.sourceFilter == "DIRECT_ORDER") { vm.setSourceFilter("DIRECT_ORDER") }
                ChoiceChip("Manual", state.sourceFilter == "MANUAL") { vm.setSourceFilter("MANUAL") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = vm::load, enabled = !state.loading, modifier = Modifier.weight(1f)) { Text("Terapkan") }
                OutlinedButton(onClick = vm::clearFilters, enabled = !state.loading) { Text("Reset") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Catat transaksi manual", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Untuk transaksi di luar pesanan yang sudah tercatat otomatis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showEntryForm = !showEntryForm }) {
                    Text(if (showEntryForm) "Tutup" else "Tambah")
                }
            }
            if (showEntryForm) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("Pemasukan", state.entryType == "INCOME") { vm.setEntryType("INCOME") }
                            ChoiceChip("Pengeluaran", state.entryType == "EXPENSE") { vm.setEntryType("EXPENSE") }
                        }
                        OutlinedTextField(
                            value = state.category,
                            onValueChange = vm::setCategory,
                            label = { Text("Kategori") },
                            placeholder = { Text(if (state.entryType == "INCOME") "Contoh: Penjualan di luar aplikasi" else "Contoh: Listrik depot") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.amount,
                            onValueChange = vm::setAmount,
                            label = { Text("Nominal (rupiah)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Text("Metode pembayaran", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("Tunai", state.paymentMethod == "CASH") { vm.setPaymentMethod("CASH") }
                            ChoiceChip("QRIS", state.paymentMethod == "QRIS") { vm.setPaymentMethod("QRIS") }
                            ChoiceChip("Lainnya", state.paymentMethod == "OTHER") { vm.setPaymentMethod("OTHER") }
                        }
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = vm::setDescription,
                            label = { Text("Keterangan (opsional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Text(
                            "Jangan catat ulang pembayaran pesanan aplikasi atau pesanan langsung. Keduanya sudah masuk buku kas melalui proses pembayaran.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = vm::saveTransaction, enabled = !state.submitting, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                when {
                                    state.submitting -> "Menyimpan…"
                                    state.entryType == "INCOME" -> "Simpan pemasukan"
                                    else -> "Simpan pengeluaran"
                                }
                            )
                        }
                    }
                }
            }

            SectionHeading("Riwayat transaksi", "Data transaksi ditampilkan sesuai hasil API.")
            val transactions = state.book?.transactions.orEmpty()
            if (transactions.isEmpty() && !state.loading) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Text("Belum ada transaksi pada periode dan filter ini.", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            transactions.forEach { TransactionCard(it) }
            state.book?.let { book ->
                Text(
                    "Menampilkan ${book.transactions.size} dari ${book.total} transaksi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.appendError?.let { MessageCard(it, isError = true) }
            if (state.canLoadMore || state.appending) {
                OutlinedButton(
                    onClick = vm::loadNextPage,
                    enabled = !state.appending,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.appending) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp).height(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(" Memuat transaksi…")
                    } else {
                        Text("Muat transaksi berikutnya")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier, valueColor: androidx.compose.ui.graphics.Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = color, contentColor = content, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TransactionCard(item: FinancialTransaction) {
    val isIncome = item.type == "INCOME"
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(sourceLabel(item.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    (if (isIncome) "+ " else "− ") + formatRupiah(item.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isIncome) "Pemasukan" else "Pengeluaran", style = MaterialTheme.typography.labelSmall)
                Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(paymentLabel(item.paymentMethod), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.orderId?.let { Text("ID pesanan: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item.description?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            item.occurredAt?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun sourceLabel(source: String?): String = when (source) {
    "APP_PAYMENT" -> "Pembayaran aplikasi"
    "DIRECT_ORDER" -> "Pesanan langsung"
    "MANUAL" -> "Pencatatan manual"
    else -> "Sumber tidak tersedia"
}

private fun paymentLabel(method: String?): String = when (method) {
    "CASH" -> "Tunai"
    "QRIS" -> "QRIS"
    "OTHER" -> "Lainnya"
    else -> "Metode tidak tersedia"
}

private fun formatRupiah(raw: String): String {
    val amount = raw.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    formatter.maximumFractionDigits = 0
    formatter.minimumFractionDigits = 0
    return formatter.format(amount)
}
