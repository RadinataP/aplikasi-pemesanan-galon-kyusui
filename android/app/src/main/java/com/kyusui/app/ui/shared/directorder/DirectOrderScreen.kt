package com.kyusui.app.ui.shared.directorder

import com.kyusui.app.ui.courier.design.CourierPageHeader
import com.kyusui.app.ui.courier.design.CourierStatusTone
import com.kyusui.app.ui.courier.design.CourierStatusBadge
import com.kyusui.app.ui.courier.design.CourierSectionHeader
import com.kyusui.app.ui.courier.design.CourierSecondaryButton
import com.kyusui.app.ui.courier.design.CourierPrimaryButton
import com.kyusui.app.ui.courier.design.CourierCard
import com.kyusui.app.ui.courier.design.CourierSpacing
import com.kyusui.app.ui.courier.design.CourierColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyusui.app.domain.model.Product

/**
 * Form pesanan langsung Owner/Kurir.
 *
 * Katalog punya keadaan loading, error, kosong, dan berhasil yang berbeda.
 * Tombol simpan tidak aktif saat katalog tidak tersedia untuk mencegah order
 * tanpa item produk yang berasal dari server.
 */
@Composable
fun DirectOrderScreen(
    state: DirectOrderUiState,
    onBack: () -> Unit,
    vm: DirectOrderViewModel
) {
    Scaffold(
        containerColor = CourierColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pesanan Langsung")
                        Text(
                            "Catat transaksi di luar pesanan aplikasi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Kembali") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CourierSpacing.Screen)
                .padding(top = CourierSpacing.Lg, bottom = CourierSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(CourierSpacing.Md)
        ) {
            CourierPageHeader(
                eyebrow = "Transaksi / Input langsung",
                title = "Pesanan langsung",
                subtitle = "Catat pesanan pelanggan dengan produk aktif dari katalog."
            )
            CourierSectionHeader(
                title = "Informasi Pelanggan",
                supportingText = "Input transaksi oleh ${if (state.role == "OWNER") "Owner" else "Kurir"}"
            )
            CourierCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CourierStatusBadge(
                            text = "CASH",
                            tone = CourierStatusTone.SUCCESS
                        )
                        Text(
                            "Pembayaran tunai diterima langsung",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "Harga produk dan total akhir tetap dihitung serta divalidasi oleh server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = state.customerName,
                onValueChange = vm::setCustomerName,
                label = { Text("Nama pelanggan (opsional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.customerPhone,
                onValueChange = vm::setCustomerPhone,
                label = { Text("Nomor telepon (opsional)") },
                modifier = Modifier.fillMaxWidth()
            )

            CourierSectionHeader(
                title = "Metode Pemenuhan",
                supportingText = "Pilih apakah pesanan diantar atau diambil di depot"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.fulfillmentType == "DELIVERY") {
                    CourierPrimaryButton(
                        text = "Diantar",
                        onClick = { vm.setFulfillment("DELIVERY") },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    CourierSecondaryButton(
                        text = "Diantar",
                        onClick = { vm.setFulfillment("DELIVERY") },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.fulfillmentType == "PICKUP") {
                    CourierPrimaryButton(
                        text = "Ambil di depot",
                        onClick = { vm.setFulfillment("PICKUP") },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    CourierSecondaryButton(
                        text = "Ambil di depot",
                        onClick = { vm.setFulfillment("PICKUP") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (state.fulfillmentType == "DELIVERY") {
                OutlinedTextField(
                    value = state.address,
                    onValueChange = vm::setAddress,
                    label = { Text("Alamat pengantaran") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.latitude,
                    onValueChange = vm::setLatitude,
                    label = { Text("Latitude tujuan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.longitude,
                    onValueChange = vm::setLongitude,
                    label = { Text("Longitude tujuan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Masukkan koordinat tujuan yang benar. Validasi area layanan tetap dilakukan backend.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            CourierSectionHeader(
                title = "Pilih Produk",
                supportingText = "Tambahkan produk dan tentukan jumlah pesanan"
            )

            // Keadaan 1: server sedang memuat katalog.
            if (state.loadingProducts) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Memuat katalog produk dari server…")
                }
            }

            // Keadaan 2: katalog gagal. Pengguna bisa mencoba kembali tanpa mengisi ulang form.
            if (!state.loadingProducts && state.productsError != null) {
                Text(
                    text = "Katalog gagal dimuat: ${state.productsError}",
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = vm::loadProducts,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loadingProducts
                ) {
                    Text("Coba Muat Ulang Katalog")
                }
            }

            // Keadaan 3: server berhasil menjawab tetapi tidak ada produk aktif.
            if (!state.loadingProducts &&
                state.productsError == null &&
                state.products.isEmpty()
            ) {
                Text(
                    "Belum ada produk aktif yang tersedia. Aktifkan produk di menu Kelola Produk.",
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = vm::loadProducts,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Periksa Katalog Lagi")
                }
            }

            // Keadaan 4: tampilkan produk yang benar-benar dikembalikan server.
            state.products.forEach { product ->
                ProductQuantityRow(
                    product = product,
                    quantity = state.quantities[product.id] ?: 0,
                    vm = vm
                )
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = vm::setDescription,
                label = { Text("Catatan pesanan (opsional)") },
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.let {
                CourierCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentPadding = 14.dp
                ) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            state.receipt?.let { receipt ->
                Text(
                    "Pesanan berhasil dicatat: ${receipt.orderNumber ?: "nomor belum tersedia"}",
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Harga produk: Rp ${receipt.subtotal} • Total: Rp ${receipt.total}")
                Text("Status: ${receipt.status ?: "lihat respons server"}")
            }

            CourierPrimaryButton(
                text = if (state.saving) "Menyimpan pesanan…" else "Konfirmasi & Simpan Pesanan",
                onClick = vm::submit,
                enabled = !state.saving &&
                    !state.loadingProducts &&
                    state.products.isNotEmpty(),
                loading = state.saving,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Kontrol kuantitas per produk; tidak menghitung harga akhir pesanan. */
@Composable
private fun ProductQuantityRow(
    product: Product,
    quantity: Int,
    vm: DirectOrderViewModel
) {
    CourierCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Harga satuan: Rp ${product.price}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (quantity > 0) {
                    CourierStatusBadge(
                        text = "$quantity dipilih",
                        tone = CourierStatusTone.INFO
                    )
                } else {
                    Text(
                        "Belum ditambahkan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = { vm.changeQuantity(product.id, -1) },
                    enabled = quantity > 0
                ) { Text("−") }
                Text(
                    quantity.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = { vm.changeQuantity(product.id, 1) },
                    enabled = quantity < 100
                ) { Text("+") }
            }
        }
    }
}
