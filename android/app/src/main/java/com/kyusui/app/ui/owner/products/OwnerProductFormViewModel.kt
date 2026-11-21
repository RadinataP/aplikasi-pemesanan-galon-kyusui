package com.kyusui.app.ui.owner.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.core.userMessage
import com.kyusui.app.domain.repository.OwnerRepository
import com.kyusui.app.ui.navigation.RoutePaths
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State form tambah/ubah produk Owner.
 *
 * `saved` dipakai sekali untuk memicu navigasi kembali; layar memanggil
 * [onSavedConsumed] setelah menanganinya supaya navigasi tidak berulang saat
 * recomposition.
 */
data class OwnerProductFormUiState(
    val isEdit: Boolean = false,
    val productId: String = "",
    val name: String = "",
    val price: String = "",
    val description: String = "",
    val isAvailable: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

/**
 * Form produk Owner.
 *
 * Mode ubah tidak melakukan request detail karena contract tidak menyediakan
 * `GET /owner/products/{id}` (spec hanya punya `GET /products/{id}` untuk
 * customer). Nilai awal diteruskan lewat argumen rute, mengikuti pola yang sudah
 * dipakai `CUSTOMER_PRODUCT_DETAIL`. Penyimpanan tetap divalidasi dan
 * diotorisasi backend; setelah sukses, daftar dimuat ulang dari server.
 */
@HiltViewModel
class OwnerProductFormViewModel @Inject constructor(
    private val ownerRepository: OwnerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(OwnerProductFormUiState())
    val uiState: StateFlow<OwnerProductFormUiState> = _uiState.asStateFlow()

    init {
        val productId = savedStateHandle.get<String>(RoutePaths.ARG_PRODUCT_ID).orEmpty()
        _uiState.value = OwnerProductFormUiState(
            isEdit = productId.isNotBlank(),
            productId = productId,
            name = savedStateHandle.get<String>(RoutePaths.ARG_PRODUCT_NAME).orEmpty(),
            price = savedStateHandle.get<String>(RoutePaths.ARG_PRODUCT_PRICE).orEmpty(),
            description = savedStateHandle.get<String>(RoutePaths.ARG_PRODUCT_DESCRIPTION)
                .orEmpty(),
            isAvailable = savedStateHandle.get<String>(RoutePaths.ARG_PRODUCT_IS_AVAILABLE)
                ?.toBooleanStrictOrNull()
                ?: true
        )
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, error = null) }

    fun onPriceChange(value: String) = _uiState.update { it.copy(price = value, error = null) }

    fun onDescriptionChange(value: String) =
        _uiState.update { it.copy(description = value, error = null) }

    fun onAvailabilityChange(value: Boolean) = _uiState.update { it.copy(isAvailable = value) }

    fun onSavedConsumed() = _uiState.update { it.copy(saved = false) }

    fun save() {
        val current = _uiState.value
        if (current.isSaving) return

        val name = current.name.trim()
        val price = current.price.trim()

        if (name.isEmpty()) {
            _uiState.update { it.copy(error = "Nama produk tidak boleh kosong.") }
            return
        }

        val priceValue = price.toDoubleOrNull()
        if (priceValue == null || priceValue < 0) {
            _uiState.update { it.copy(error = "Harga harus berupa angka yang valid.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = if (current.isEdit) {
                ownerRepository.updateProduct(
                    productId = current.productId,
                    name = name,
                    description = current.description,
                    price = price,
                    availability = current.isAvailable
                )
            } else {
                ownerRepository.createProduct(
                    name = name,
                    description = current.description,
                    price = price,
                    availability = current.isAvailable
                )
            }

            _uiState.update { state ->
                when (result) {
                    is Result.Success -> state.copy(isSaving = false, saved = true)
                    is Result.Failure -> state.copy(
                        isSaving = false,
                        error = result.exception.userMessage()
                    )
                }
            }
        }
    }
}
