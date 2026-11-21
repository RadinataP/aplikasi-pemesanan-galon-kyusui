package com.kyusui.app.ui.owner.products

import androidx.lifecycle.SavedStateHandle
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.Product
import com.kyusui.app.domain.repository.OwnerRepository
import com.kyusui.app.ui.navigation.RoutePaths
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
 * Test form tambah/ubah produk Owner.
 *
 * Yang dijaga test ini:
 *
 * - nilai awal mode ubah datang dari argumen rute, bukan endpoint yang tidak ada;
 * - validasi lokal hanya mencegah request yang jelas tidak valid; keputusan
 *   akhir tetap milik backend;
 * - tambah memakai `createProduct` dan ubah memakai `updateProduct`;
 * - keberhasilan ditandai `saved` untuk memicu navigasi kembali.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerProductFormViewModelTest {

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
    fun `create mode starts empty`() = runTest(dispatcher) {
        val viewModel = OwnerProductFormViewModel(repository, SavedStateHandle())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEdit)
        assertEquals("", state.name)
        assertEquals("", state.price)
        assertTrue(state.isAvailable)
    }

    @Test
    fun `edit mode is prefilled from route arguments`() = runTest(dispatcher) {
        val viewModel = OwnerProductFormViewModel(repository, editHandle())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEdit)
        assertEquals("1001", state.productId)
        assertEquals("Air Galon", state.name)
        assertEquals("18000.00", state.price)
        assertEquals("Air galon isi 19 liter.", state.description)
        assertFalse(state.isAvailable)
    }

    @Test
    fun `create sends the form values to the create endpoint`() = runTest(dispatcher) {
        coEvery { repository.createProduct(any(), any(), any(), any()) } returns
            Result.Success(product())

        val viewModel = OwnerProductFormViewModel(repository, SavedStateHandle())
        viewModel.onNameChange("Air Botol")
        viewModel.onPriceChange("5000.00")
        viewModel.onDescriptionChange("600ml")
        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.createProduct(
                name = "Air Botol",
                description = "600ml",
                price = "5000.00",
                availability = true
            )
        }
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `edit sends the form values to the update endpoint`() = runTest(dispatcher) {
        coEvery { repository.updateProduct(any(), any(), any(), any(), any()) } returns
            Result.Success(product())

        val viewModel = OwnerProductFormViewModel(repository, editHandle())
        viewModel.onNameChange("Air Galon Baru")
        viewModel.onAvailabilityChange(true)
        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.updateProduct(
                productId = "1001",
                name = "Air Galon Baru",
                description = "Air galon isi 19 liter.",
                price = "18000.00",
                availability = true
            )
        }
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `blank name is rejected before calling the backend`() = runTest(dispatcher) {
        val viewModel = OwnerProductFormViewModel(repository, SavedStateHandle())
        viewModel.onPriceChange("5000.00")
        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.createProduct(any(), any(), any(), any()) }
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.saved)
    }

    @Test
    fun `invalid price is rejected before calling the backend`() = runTest(dispatcher) {
        val viewModel = OwnerProductFormViewModel(repository, SavedStateHandle())
        viewModel.onNameChange("Air Botol")
        viewModel.onPriceChange("gratis")
        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.createProduct(any(), any(), any(), any()) }
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a failed save keeps the form open and shows the error`() = runTest(dispatcher) {
        coEvery { repository.createProduct(any(), any(), any(), any()) } returns
            Result.Failure(UnauthorizedException())

        val viewModel = OwnerProductFormViewModel(repository, SavedStateHandle())
        viewModel.onNameChange("Air Botol")
        viewModel.onPriceChange("5000.00")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.saved)
        assertFalse(state.isSaving)
        assertNotNull(state.error)
    }

    // --- Fixtures ---

    private fun editHandle() = SavedStateHandle(
        mapOf(
            RoutePaths.ARG_PRODUCT_ID to "1001",
            RoutePaths.ARG_PRODUCT_NAME to "Air Galon",
            RoutePaths.ARG_PRODUCT_PRICE to "18000.00",
            RoutePaths.ARG_PRODUCT_DESCRIPTION to "Air galon isi 19 liter.",
            RoutePaths.ARG_PRODUCT_IS_AVAILABLE to "false"
        )
    )

    private fun product() = Product(
        id = "1001",
        name = "Air Galon",
        description = "Air galon isi 19 liter.",
        price = "18000.00",
        isAvailable = true
    )
}
