package com.kyusui.app.ui.owner.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.core.userMessage
import com.kyusui.app.domain.model.Product
import com.kyusui.app.domain.repository.OwnerRepository
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.toLoadError
import com.kyusui.app.ui.state.toPagedListLoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerProductsUiState(
    val productsState: LoadState<List<Product>> = LoadState.Loading,
    val isMutating: Boolean = false,
    val message: String? = null,
    val mutationError: String? = null,
    val currentPage: Int = 1,
    val totalProducts: Int = 0,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null
) {
    val products: List<Product> get() = (productsState as? LoadState.Success)?.data.orEmpty()
}

@HiltViewModel
class OwnerProductsViewModel @Inject constructor(
    private val ownerRepository: OwnerRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OwnerProductsUiState())
    val uiState: StateFlow<OwnerProductsUiState> = _uiState.asStateFlow()

    init { loadProducts() }

    fun loadProducts() {
        _uiState.update { it.copy(productsState = LoadState.Loading, currentPage = 1,
            totalProducts = 0, hasMore = false, isLoadingMore = false,
            loadMoreError = null, mutationError = null, message = null) }
        viewModelScope.launch {
            when (val result = ownerRepository.getProducts(page = FIRST_PAGE, perPage = PER_PAGE)) {
                is Result.Success -> _uiState.update { state ->
                    state.copy(productsState = result.toPagedListLoadState(EMPTY_PRODUCTS_MESSAGE),
                        currentPage = result.data.currentPage, totalProducts = result.data.total,
                        hasMore = result.data.hasMore)
                }
                is Result.Failure -> _uiState.update { it.copy(productsState = result.toLoadError()) }
            }
        }
    }

    fun loadMore() {
        val snapshot = _uiState.value
        if (!snapshot.hasMore || snapshot.isLoadingMore || snapshot.isMutating ||
            snapshot.productsState !is LoadState.Success) return
        val requestedPage = snapshot.currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            when (val result = ownerRepository.getProducts(page = requestedPage, perPage = PER_PAGE)) {
                is Result.Success -> _uiState.update { state ->
                    val old = state.products
                    val merged = (old + result.data.data).distinctBy { it.id }
                    state.copy(productsState = if (merged.isEmpty()) LoadState.Empty(EMPTY_PRODUCTS_MESSAGE)
                        else LoadState.Success(merged), currentPage = result.data.currentPage,
                        totalProducts = result.data.total, hasMore = result.data.hasMore,
                        isLoadingMore = false, loadMoreError = null)
                }
                is Result.Failure -> _uiState.update { it.copy(isLoadingMore = false,
                    loadMoreError = result.exception.userMessage()) }
            }
        }
    }

    fun activate(product: Product) = mutate("Produk diaktifkan kembali.") {
        ownerRepository.updateProduct(product.id, null, null, null, true)
    }

    fun deactivate(product: Product) = mutate("Produk dinonaktifkan.") {
        ownerRepository.deleteProduct(product.id)
    }

    private fun mutate(successMessage: String, action: suspend () -> Result<*>) {
        if (_uiState.value.isMutating) return
        _uiState.update { it.copy(isMutating = true, message = null, mutationError = null) }
        viewModelScope.launch {
            when (val result = action()) {
                is Result.Success -> {
                    _uiState.update { it.copy(isMutating = false, message = successMessage) }
                    refreshAfterMutation()
                }
                is Result.Failure -> _uiState.update { it.copy(isMutating = false,
                    mutationError = result.exception.userMessage()) }
            }
        }
    }

    private suspend fun refreshAfterMutation() {
        when (val result = ownerRepository.getProducts(FIRST_PAGE, PER_PAGE)) {
            is Result.Success -> _uiState.update { state ->
                state.copy(productsState = result.toPagedListLoadState(EMPTY_PRODUCTS_MESSAGE),
                    currentPage = result.data.currentPage, totalProducts = result.data.total,
                    hasMore = result.data.hasMore, isLoadingMore = false, loadMoreError = null)
            }
            is Result.Failure -> _uiState.update { it.copy(mutationError = result.exception.userMessage()) }
        }
    }

    companion object {
        const val FIRST_PAGE = 1
        /** Backward-compatible alias used by existing unit tests. */
        const val PAGE = FIRST_PAGE
        const val PER_PAGE = 50
        const val EMPTY_PRODUCTS_MESSAGE = "Belum ada produk pada katalog."
    }
}
