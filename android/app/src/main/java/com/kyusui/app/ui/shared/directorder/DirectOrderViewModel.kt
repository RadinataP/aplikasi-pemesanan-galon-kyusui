package com.kyusui.app.ui.shared.directorder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.core.userMessage
import com.kyusui.app.domain.model.DirectOrderDraft
import com.kyusui.app.domain.model.DirectOrderReceipt
import com.kyusui.app.domain.model.Product
import com.kyusui.app.domain.repository.FinanceDirectOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State UI untuk form pesanan langsung.
 *
 * `productsError` dipisahkan dari `error`: kegagalan memuat katalog tidak
 * boleh tertukar dengan validasi submit pesanan.
 */
data class DirectOrderUiState(
    val role: String = "OWNER",
    val loadingProducts: Boolean = false,
    val productsError: String? = null,
    val saving: Boolean = false,
    val products: List<Product> = emptyList(),
    val quantities: Map<String, Int> = emptyMap(),
    val customerName: String = "",
    val customerPhone: String = "",
    val fulfillmentType: String = "DELIVERY",
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val description: String = "",
    val error: String? = null,
    val receipt: DirectOrderReceipt? = null
)

@HiltViewModel
class DirectOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FinanceDirectOrderRepository
) : ViewModel() {

    // Role dibaca dari navigation arguments dan dibatasi pada role yang diizinkan.
    private val role = savedStateHandle.get<String>("role")
        ?.uppercase()
        ?.takeIf { it == "OWNER" || it == "COURIER" }
        ?: "OWNER"

    private val _uiState = MutableStateFlow(DirectOrderUiState(role = role))
    val uiState = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    /**
     * Muat katalog dari server. Fungsi ini bisa dipanggil ulang lewat tombol
     * "Coba Muat Ulang Katalog" tanpa menutup atau menghapus data form.
     */
    fun loadProducts() = viewModelScope.launch {
        // Jangan jalankan dua permintaan katalog secara bersamaan.
        if (_uiState.value.loadingProducts) return@launch

        _uiState.value = _uiState.value.copy(
            loadingProducts = true,
            productsError = null
        )

        when (val result = repository.getAvailableProducts()) {
            is Result.Success -> {
                // Hanya produk aktif/tersedia yang dapat ditambahkan ke pesanan.
                val available = result.data.filter { it.isAvailable }
                _uiState.value = _uiState.value.copy(
                    loadingProducts = false,
                    products = available,
                    productsError = if (available.isEmpty()) {
                        "Belum ada produk aktif yang tersedia di katalog depot."
                    } else {
                        null
                    }
                )
            }

            is Result.Failure -> {
                // Tampilkan pesan jaringan/server yang sudah dipetakan aplikasi.
                _uiState.value = _uiState.value.copy(
                    loadingProducts = false,
                    products = emptyList(),
                    productsError = result.exception.userMessage()
                )
            }
        }
    }

    fun setCustomerName(v: String) {
        _uiState.value = _uiState.value.copy(customerName = v)
    }

    fun setCustomerPhone(v: String) {
        _uiState.value = _uiState.value.copy(customerPhone = v)
    }

    fun setAddress(v: String) {
        _uiState.value = _uiState.value.copy(address = v)
    }

    // Membatasi karakter input koordinat, lalu validasi rentang dilakukan saat submit.
    fun setLatitude(v: String) {
        _uiState.value = _uiState.value.copy(
            latitude = v.filter { it.isDigit() || it == '-' || it == '.' }
        )
    }

    fun setLongitude(v: String) {
        _uiState.value = _uiState.value.copy(
            longitude = v.filter { it.isDigit() || it == '-' || it == '.' }
        )
    }

    fun setDescription(v: String) {
        _uiState.value = _uiState.value.copy(description = v)
    }

    fun setFulfillment(v: String) {
        if (v == "DELIVERY" || v == "PICKUP") {
            _uiState.value = _uiState.value.copy(fulfillmentType = v, error = null)
        }
    }

    /** Tambah/kurangi kuantitas; kuantitas tidak boleh kurang dari 0 atau lebih dari 100. */
    fun changeQuantity(productId: String, delta: Int) {
        val current = _uiState.value.quantities[productId] ?: 0
        val next = (current + delta).coerceIn(0, 100)
        val updated = _uiState.value.quantities.toMutableMap().apply {
            if (next == 0) remove(productId) else put(productId, next)
        }
        _uiState.value = _uiState.value.copy(quantities = updated, error = null)
    }

    /** Validasi form di sisi UI; server tetap menjadi sumber validasi akhir. */
    fun submit() = viewModelScope.launch {
        val s = _uiState.value

        if (s.loadingProducts) {
            _uiState.value = s.copy(error = "Tunggu sampai katalog selesai dimuat.")
            return@launch
        }

        if (s.products.isEmpty()) {
            _uiState.value = s.copy(
                error = "Katalog produk belum tersedia. Muat ulang katalog sebelum menyimpan."
            )
            return@launch
        }

        if (s.quantities.values.sum() == 0) {
            _uiState.value = s.copy(error = "Pilih minimal satu produk.")
            return@launch
        }

        if (s.fulfillmentType == "DELIVERY") {
            val latitude = s.latitude.toDoubleOrNull()
            val longitude = s.longitude.toDoubleOrNull()

            if (s.address.isBlank() || latitude == null || longitude == null) {
                _uiState.value = s.copy(
                    error = "Alamat, latitude, dan longitude wajib diisi untuk pengantaran."
                )
                return@launch
            }

            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                _uiState.value = s.copy(
                    error = "Koordinat berada di luar rentang yang valid."
                )
                return@launch
            }
        }

        _uiState.value = s.copy(saving = true, error = null, receipt = null)

        when (
            val result = repository.createDirectOrder(
                s.role,
                DirectOrderDraft(
                    customerName = s.customerName,
                    customerPhone = s.customerPhone,
                    fulfillmentType = s.fulfillmentType,
                    productQuantities = s.quantities,
                    address = s.address,
                    latitude = s.latitude,
                    longitude = s.longitude,
                    description = s.description
                )
            )
        ) {
            is Result.Success -> {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    receipt = result.data,
                    quantities = emptyMap()
                )
            }

            is Result.Failure -> {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    error = result.exception.userMessage()
                )
            }
        }
    }
}
