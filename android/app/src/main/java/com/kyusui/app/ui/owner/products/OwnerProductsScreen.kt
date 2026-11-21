package com.kyusui.app.ui.owner.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyusui.app.core.Money
import com.kyusui.app.domain.model.Product
import com.kyusui.app.ui.components.EmptyState
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.components.OutlinedButton
import com.kyusui.app.ui.components.SecondaryButton
import com.kyusui.app.ui.state.LoadState

/**
 * Katalog produk Owner (`GET /owner/products`).
 *
 * Daftar memuat seluruh produk, termasuk yang tidak tersedia. Aksi aktif/nonaktif
 * mengirim perubahan ke backend, lalu daftar dibaca ulang dari server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerProductsScreen(
    state: OwnerProductsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (Product) -> Unit,
    onActivateProduct: (Product) -> Unit,
    onDeactivateProduct: (Product) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Produk") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Muat ulang"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProduct) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Tambah produk"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val productsState = state.productsState) {
                LoadState.Loading -> LoadingState(message = "Memuat produk...")

                is LoadState.Empty -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "Belum Ada Produk",
                    message = productsState.message,
                    actionText = "Tambah Produk",
                    onActionClick = onAddProduct
                )

                is LoadState.Error -> ErrorState(
                    message = productsState.message,
                    isRetryable = productsState.isRetryable,
                    onRetry = onRefresh
                )

                is LoadState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pengantar ini membantu Owner memahami bahwa katalog
                    // menampilkan produk aktif maupun nonaktif dari server.
                    item {
                        Text(
                            text = "Kelola informasi produk dan atur apakah produk dapat dipesan pelanggan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.message != null) {
                        item {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    state.mutationError?.let { error ->
                        item { InlineError(message = error, onRetry = onRefresh) }
                    }
                    items(items = productsState.data, key = { it.id }) { product ->
                        OwnerProductCard(
                            product = product,
                            isMutating = state.isMutating,
                            onEdit = { onEditProduct(product) },
                            onActivate = { onActivateProduct(product) },
                            onDeactivate = { onDeactivateProduct(product) }
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Menampilkan ${productsState.data.size} dari ${state.totalProducts} produk",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            state.loadMoreError?.let { InlineError(message = it) }
                            if (state.hasMore) {
                                Button(
                                    onClick = onLoadMore,
                                    enabled = !state.isLoadingMore && !state.isMutating
                                ) {
                                    if (state.isLoadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(end = 8.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text("Memuat...")
                                    } else Text("Muat Lebih Banyak")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerProductCard(
    product: Product,
    isMutating: Boolean,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                text = Money.format(product.price),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            product.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Availability adalah boolean, bukan jumlah stok. Label ini
            // sengaja tidak menampilkan kuantitas inventaris.
            Surface(
                shape = RoundedCornerShape(50),
                color = if (product.isAvailable) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = if (product.isAvailable) "Tersedia untuk dipesan" else "Tidak tersedia",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (product.isAvailable) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    text = "Ubah",
                    onClick = onEdit,
                    enabled = !isMutating,
                    fillWidth = false
                )
                if (product.isAvailable) {
                    SecondaryButton(
                        text = "Nonaktifkan",
                        onClick = onDeactivate,
                        enabled = !isMutating,
                        fillWidth = false
                    )
                } else {
                    SecondaryButton(
                        text = "Aktifkan",
                        onClick = onActivate,
                        enabled = !isMutating,
                        fillWidth = false
                    )
                }
            }
        }
    }
}
