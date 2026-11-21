package com.kyusui.app.ui.owner.products

import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.PaginatedResult
import com.kyusui.app.domain.model.Product
import com.kyusui.app.domain.repository.OwnerRepository
import com.kyusui.app.ui.state.LoadState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test katalog produk Owner.
 *
 * Yang dijaga test ini:
 *
 * - katalog dibaca dari `GET /owner/products` pada init;
 * - daftar tidak pernah difilter ulang di Android: apa pun yang dikirim backend
 *   ditampilkan;
 * - aksi aktif/nonaktif memanggil endpoint backend yang tepat, bukan mengubah
 *   status secara lokal;
 * - setelah aksi berhasil, daftar dibaca ulang dari server;
 * - aksi gagal tidak menghapus produk yang sudah tampil dan menampilkan pesan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerProductsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: OwnerRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `catalog is requested on init`() = runTest(dispatcher) {
        coEvery { repository.getProducts(any(), any()) } returns pageOfProducts()

        val viewModel = OwnerProductsViewModel(repository)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.getProducts(
                page = OwnerProductsViewModel.PAGE,
                perPage = OwnerProductsViewModel.PER_PAGE
            )
        }
        assertEquals(listOf("1001", "1002"), viewModel.uiState.value.products.map { it.id })
    }

    @Test
    fun `products from the backend are displayed without local filtering`() =
        runTest(dispatcher) {
            coEvery { repository.getProducts(any(), any()) } returns pageOfProducts(
                products = listOf(
                    product(id = "1", isAvailable = false),
                    product(id = "2", isAvailable = true)
                )
            )

            val viewModel = OwnerProductsViewModel(repository)
            advanceUntilIdle()

            // Produk yang dinonaktifkan tetap tampil pada layar Owner.
            assertEquals(listOf("1", "2"), viewModel.uiState.value.products.map { it.id })
        }

    @Test
    fun `empty catalog becomes an empty state with a message`() = runTest(dispatcher) {
        coEvery { repository.getProducts(any(), any()) } returns pageOfProducts(emptyList())

        val viewModel = OwnerProductsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value.productsState
        assertTrue(state is LoadState.Empty)
        assertEquals(
            OwnerProductsViewModel.EMPTY_PRODUCTS_MESSAGE,
            (state as LoadState.Empty).message
        )
    }

    @Test
    fun `catalog failure is reported as a load error`() = runTest(dispatcher) {
        coEvery { repository.getProducts(any(), any()) } returns
            Result.Failure(UnauthorizedException())

        val viewModel = OwnerProductsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value.productsState
        assertTrue(state is LoadState.Error)
    }

    @Test
    fun `deactivating a product calls the disable endpoint and reloads`() =
        runTest(dispatcher) {
            coEvery { repository.getProducts(any(), any()) } returns pageOfProducts()
            coEvery { repository.deleteProduct(any()) } returns Result.Success(Unit)

            val viewModel = OwnerProductsViewModel(repository)
            advanceUntilIdle()
            viewModel.deactivate(product(id = "1001"))
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.deleteProduct("1001") }
            // Satu kali saat init, satu kali setelah aksi berhasil.
            coVerify(exactly = 2) { repository.getProducts(any(), any()) }
            assertNotNull(viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.isMutating)
        }

    @Test
    fun `activating a product sends availability true only`() = runTest(dispatcher) {
        coEvery { repository.getProducts(any(), any()) } returns pageOfProducts(
            products = listOf(product(id = "1001", isAvailable = false))
        )
        coEvery { repository.updateProduct(any(), any(), any(), any(), any()) } returns
            Result.Success(product(id = "1001", isAvailable = true))

        val viewModel = OwnerProductsViewModel(repository)
        advanceUntilIdle()
        viewModel.activate(product(id = "1001", isAvailable = false))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.updateProduct(
                productId = "1001",
                name = null,
                description = null,
                price = null,
                availability = true
            )
        }
    }

    @Test
    fun `a failed mutation keeps the catalog and shows an error`() = runTest(dispatcher) {
        coEvery { repository.getProducts(any(), any()) } returns pageOfProducts()
        coEvery { repository.deleteProduct(any()) } returns
            Result.Failure(UnauthorizedException())

        val viewModel = OwnerProductsViewModel(repository)
        advanceUntilIdle()
        viewModel.deactivate(product(id = "1001"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.mutationError)
        assertFalse(state.isMutating)
        // Daftar tidak dikosongkan dan tidak diubah saat aksi gagal.
        assertEquals(listOf("1001", "1002"), state.products.map { it.id })
        coVerify(exactly = 1) { repository.getProducts(any(), any()) }
    }

    // --- Fixtures ---

    private fun product(
        id: String = "1001",
        name: String = "Air Galon",
        description: String? = null,
        price: String = "18000.00",
        isAvailable: Boolean = true
    ) = Product(
        id = id,
        name = name,
        description = description,
        price = price,
        isAvailable = isAvailable
    )

    private fun pageOfProducts(
        products: List<Product> = listOf(
            product(id = "1001"),
            product(id = "1002", name = "Air Botol")
        )
    ) = Result.Success(
        PaginatedResult(
            data = products,
            currentPage = 1,
            perPage = OwnerProductsViewModel.PER_PAGE,
            total = products.size
        )
    )
}
