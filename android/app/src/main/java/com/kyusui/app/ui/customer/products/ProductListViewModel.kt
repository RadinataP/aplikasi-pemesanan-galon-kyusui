
package com.kyusui.app.ui.customer.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.Product
import com.kyusui.app.domain.repository.CustomerRepository
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.toPagedListLoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductListUiState(
    val productsState: LoadState<List<Product>> = LoadState.Loading,
    val page: Int = FIRST_PAGE,
    val hasMore: Boolean = false,
    val isAppending: Boolean = false,
    val appendErrorMessage: String? = null
) {
    /**
     * Shortcut untuk membaca produk yang sudah berhasil dimuat.
     */
    val products: List<Product>
        get() = (productsState as? LoadState.Success)?.data.orEmpty()

    companion object {
        const val FIRST_PAGE = 1
        const val DEFAULT_PER_PAGE = 20

        const val EMPTY_PRODUCTS_MESSAGE =
            "Belum ada produk yang tersedia."

        const val APPEND_ERROR_MESSAGE =
            "Produk berikutnya gagal dimuat. Periksa koneksi lalu coba lagi."
    }
}

@HiltViewModel
class ProductListViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductListUiState())
    val uiState: StateFlow<ProductListUiState> = _uiState.asStateFlow()

    private var loadProductsJob: Job? = null
    private var loadNextPageJob: Job? = null

    init {
        loadProducts()
    }

    /**
     * Memuat ulang halaman pertama dari API.
     */
    fun loadProducts() {
        if (loadProductsJob?.isActive == true) return

        loadNextPageJob?.cancel()
        loadNextPageJob = null

        _uiState.update {
            it.copy(
                productsState = LoadState.Loading,
                page = ProductListUiState.FIRST_PAGE,
                hasMore = false,
                isAppending = false,
                appendErrorMessage = null
            )
        }

        loadProductsJob = viewModelScope.launch {
            try {
                val result = customerRepository.getProducts(
                    page = ProductListUiState.FIRST_PAGE,
                    perPage = ProductListUiState.DEFAULT_PER_PAGE
                )

                _uiState.update { current ->
                    current.copy(
                        productsState = result.toPagedListLoadState(
                            ProductListUiState.EMPTY_PRODUCTS_MESSAGE
                        ),
                        page = ProductListUiState.FIRST_PAGE,
                        hasMore = (result as? Result.Success)
                            ?.data
                            ?.hasMore ?: false,
                        isAppending = false,
                        appendErrorMessage = null
                    )
                }
            } finally {
                loadProductsJob = null
            }
        }
    }

    /**
     * Memuat halaman selanjutnya jika masih tersedia.
     */
    fun loadNextPage() {
        val current = _uiState.value

        if (
            loadProductsJob?.isActive == true ||
            loadNextPageJob?.isActive == true ||
            !current.hasMore ||
            current.isAppending ||
            current.productsState !is LoadState.Success
        ) {
            return
        }

        val nextPage = current.page + 1

        _uiState.update {
            it.copy(
                isAppending = true,
                appendErrorMessage = null
            )
        }

        loadNextPageJob = viewModelScope.launch {
            try {
                when (
                    val result = customerRepository.getProducts(
                        page = nextPage,
                        perPage = ProductListUiState.DEFAULT_PER_PAGE
                    )
                ) {
                    is Result.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                productsState = LoadState.Success(
                                    mergeDistinct(
                                        existing = state.products,
                                        incoming = result.data.data
                                    )
                                ),
                                page = nextPage,
                                hasMore = result.data.hasMore,
                                isAppending = false,
                                appendErrorMessage = null
                            )
                        }
                    }

                    is Result.Failure -> {
                        _uiState.update {
                            it.copy(
                                isAppending = false,
                                appendErrorMessage =
                                    ProductListUiState.APPEND_ERROR_MESSAGE
                            )
                        }
                    }
                }
            } finally {
                loadNextPageJob = null
            }
        }
    }

    /**
     * Mencoba kembali memuat halaman berikutnya setelah gagal.
     */
    fun retryLoadNextPage() {
        if (_uiState.value.appendErrorMessage == null) return
        loadNextPage()
    }

    private fun mergeDistinct(
        existing: List<Product>,
        incoming: List<Product>
    ): List<Product> {
        return (existing + incoming).distinctBy { it.id }
    }
}
