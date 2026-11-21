package com.kyusui.app.ui.profile

import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.AccountProfile
import com.kyusui.app.domain.repository.ProfileRepository
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
import org.junit.Before
import org.junit.Test

/**
 * Test profil akun OWNER dan COURIER.
 *
 * Yang dijaga test ini:
 *
 * - profil dibaca dari `GET /profile` pada init;
 * - Owner tidak mengirim phone/vehicle karena backend tidak menyimpannya;
 * - Courier mengirim phone/vehicle;
 * - setelah simpan, layar memakai hasil `GET /profile` sebagai otoritas akhir;
 * - nama kosong dan kegagalan simpan dilaporkan sebagai error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ProfileRepository

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
    fun `profile is loaded on init`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.Success(
            ownerProfile(name = "Warung Kyusui")
        )

        val viewModel = AccountProfileViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Warung Kyusui", state.name)
        assertEquals("Warung Kyusui", state.profile?.name)
        assertFalse(state.isLoading)
        coVerify(exactly = 1) { repository.getProfile() }
    }

    @Test
    fun `load failure is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.Failure(UnauthorizedException())

        val viewModel = AccountProfileViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `owner save sends only the name`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.Success(ownerProfile())
        coEvery { repository.updateProfile(any(), any(), any(), any(), any()) } returns
            Result.Success(ownerProfile(name = "Warung Baru"))

        val viewModel = AccountProfileViewModel(repository)
        advanceUntilIdle()
        viewModel.onNameChange("Warung Baru")
        viewModel.saveProfile()
        advanceUntilIdle()

        // Owner tidak punya kolom phone/vehicle di backend, jadi keduanya null.
        coVerify(exactly = 1) {
            repository.updateProfile(
                name = "Warung Baru",
                phone = null,
                address = null,
                vehicle = null,
                password = null
            )
        }
    }

    @Test
    fun `courier save sends phone and vehicle`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.Success(courierProfile())
        coEvery { repository.updateProfile(any(), any(), any(), any(), any()) } returns
            Result.Success(courierProfile(name = "Budi"))

        val viewModel = AccountProfileViewModel(repository)
        advanceUntilIdle()
        viewModel.onNameChange("Budi")
        viewModel.onPhoneChange("08123456789")
        viewModel.onVehicleChange("Motor")
        viewModel.saveProfile()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.updateProfile(
                name = "Budi",
                phone = "08123456789",
                address = null,
                vehicle = "Motor",
                password = null
            )
        }
    }

    @Test
    fun `save refreshes from the server before showing success`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returnsMany listOf(
            Result.Success(ownerProfile(name = "Lama")),
            Result.Success(ownerProfile(name = "Dari Server"))
        )
        coEvery { repository.updateProfile(any(), any(), any(), any(), any()) } returns
            Result.Success(ownerProfile(name = "Nilai Simpan"))

        val viewModel = AccountProfileViewModel(repository)
        advanceUntilIdle()
        viewModel.onNameChange("Nilai Simpan")
        viewModel.saveProfile()
        advanceUntilIdle()

        // Nilai akhir berasal dari GET /profile, bukan payload PUT.
        assertEquals("Dari Server", viewModel.uiState.value.name)
        assertEquals("Dari Server", viewModel.uiState.value.profile?.name)
        assertNotNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `blank name is rejected before calling the backend`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.Success(ownerProfile(name = "Warung"))
        coEvery { repository.updateProfile(any(), any(), any(), any(), any()) } returns
            Result.Success(ownerProfile())

        val viewModel = AccountProfileViewModel(repository)
        advanceUntilIdle()
        viewModel.onNameChange("   ")
        viewModel.saveProfile()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateProfile(any(), any(), any(), any(), any()) }
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `save failure keeps the form and shows an error`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.Success(ownerProfile())
        coEvery { repository.updateProfile(any(), any(), any(), any(), any()) } returns
            Result.Failure(UnauthorizedException())

        val viewModel = AccountProfileViewModel(repository)
        advanceUntilIdle()
        viewModel.onNameChange("Warung Baru")
        viewModel.saveProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertNotNull(state.error)
    }

    // --- Fixtures ---

    private fun ownerProfile(name: String = "Warung Kyusui") = AccountProfile(
        id = "1",
        name = name,
        role = AccountProfile.ROLE_OWNER
    )

    private fun courierProfile(name: String = "Budi") = AccountProfile(
        id = "2",
        name = name,
        role = AccountProfile.ROLE_COURIER
    )
}
