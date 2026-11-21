
package com.kyusui.app.ui.customer.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyusui.app.core.Money
import com.kyusui.app.domain.model.Product
import com.kyusui.app.ui.components.EmptyState
import com.kyusui.app.ui.components.ErrorState
import com.kyusui.app.ui.components.InlineError
import com.kyusui.app.ui.components.LoadingState
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.customer.CustomerBottomBar
import com.kyusui.app.ui.customer.CustomerTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    uiState: ProductListUiState,
    cartCount: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onGoToCart: () -> Unit,
    onProductClick: (Product) -> Unit,
    onAddToCart: (Product) -> Unit,
    onChangeQuantity: (Product, Int) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateOrders: () -> Unit,
    onNavigateAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    /*
     * Pagination tetap dikendalikan ViewModel dan backend.
     *
     * appendErrorMessage menjadi key remember karena nilai tersebut menentukan
     * apakah infinite scroll boleh meminta halaman berikutnya. Tanpa key ini,
     * lambda derivedStateOf berpotensi membaca nilai error yang sudah kedaluwarsa.
     */
    val shouldLoadMore by remember(
        listState,
        uiState.hasMore,
        uiState.isAppending,
        uiState.appendErrorMessage
    ) {
        derivedStateOf {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = listState.layoutInfo.totalItemsCount

            uiState.hasMore &&
                    !uiState.isAppending &&
                    uiState.appendErrorMessage == null &&
                    totalItems > 0 &&
                    lastVisibleIndex >= totalItems - LOAD_MORE_THRESHOLD
        }
    }

    /*
     * Effect hanya meminta ViewModel memuat halaman berikutnya.
     * Pencegahan request ganda tetap dilakukan oleh ViewModel, bukan UI.
     */
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            CustomerBottomBar(selected = CustomerTab.PRODUCTS, onHome = onNavigateHome, onProducts = {}, onOrders = onNavigateOrders, onAccount = onNavigateAccount)
        },
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Temukan Produk",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Air minum untuk kebutuhan harianmu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    // Keranjang tetap dapat dibuka meskipun masih kosong.
                    TextButton(onClick = onGoToCart) {
                        Text(
                            text = if (cartCount > 0) {
                                "Keranjang ($cartCount)"
                            } else {
                                "Keranjang"
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        when (val state = uiState.productsState) {
            is LoadState.Loading -> {
                LoadingState(
                    modifier = Modifier.padding(innerPadding),
                    message = "Memuat katalog produk..."
                )
            }

            is LoadState.Empty -> {
                EmptyState(
                    modifier = Modifier.padding(innerPadding),
                    icon = Icons.Outlined.WaterDrop,
                    title = "Produk Belum Tersedia",
                    message = state.message,
                    actionText = "Muat Ulang",
                    onActionClick = onRetry
                )
            }

            is LoadState.Error -> {
                ErrorState(
                    modifier = Modifier.padding(innerPadding),
                    message = state.message,
                    isRetryable = state.isRetryable,
                    onRetry = onRetry
                )
            }

            is LoadState.Success -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "catalog_heading") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Produk pilihan Berkah Water",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Pilih produk yang kamu butuhkan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(
                        items = state.data,
                        key = { product -> product.id }
                    ) { product ->
                        ProductCard(
                            product = product,
                            onClick = { onProductClick(product) },
                            onAddToCart = { onAddToCart(product) },
                            onRemoveFromCart = {
                                onChangeQuantity(product, -1)
                            }
                        )
                    }

                    /*
                     * Kegagalan pagination tidak menghapus produk yang sudah
                     * tampil. Tombol retry hanya mengulang request halaman berikutnya.
                     */
                    if (uiState.appendErrorMessage != null) {
                        item(key = "pagination_error") {
                            InlineError(
                                modifier = Modifier.fillMaxWidth(),
                                message = uiState.appendErrorMessage,
                                onRetry = onRetryLoadMore
                            )
                        }
                    }

                    if (uiState.isAppending) {
                        item(key = "pagination_loading") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )

                                Text(
                                    text = "Memuat produk berikutnya...",
                                    modifier = Modifier.padding(start = 12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    onAddToCart: () -> Unit,
    onRemoveFromCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            /*
             * Placeholder ikon digunakan karena kode yang tersedia belum
             * menunjukkan bahwa model Product menjamin URL gambar produk.
             */
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(92.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val description = product.description
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (product.isAvailable) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = if (product.isAvailable) {
                            "Tersedia"
                        } else {
                            "Tidak tersedia"
                        },
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 5.dp
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (product.isAvailable) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = Money.format(product.price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (product.isAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onRemoveFromCart,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "Kurangi jumlah ${product.name}"
                            )
                        }

                        IconButton(
                            onClick = onAddToCart,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Tambah ${product.name} ke keranjang",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Lihat detail ${product.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(18.dp)
            )
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 3
