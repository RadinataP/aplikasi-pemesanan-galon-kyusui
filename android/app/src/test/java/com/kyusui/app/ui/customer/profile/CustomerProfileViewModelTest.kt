package com.kyusui.app.ui.customer.profile

import com.kyusui.app.core.NoNetworkException
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.CustomerProfile
import com.kyusui.app.domain.repository.CustomerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: CustomerRepository

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
    fun `profile is loaded and fills the editable fields`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.success(profile())

        val viewModel = CustomerProfileViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Budi", state.name)
        assertEquals("08123456789", state.phone)
        assertEquals("budi@example.com", state.email)
        assertEquals("Jl. Merdeka No. 10", state.address)
        assertNull(state.error)
    }

    @Test
    fun `failed load keeps profile empty and exposes an error`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.failure(NoNetworkException())

        val viewModel = CustomerProfileViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.profile)
        assertNotNull(state.error)
    }

    @Test
    fun `successful save refreshes the profile from the server`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.success(profile(name = "Budi"))

        val viewModel = CustomerProfileViewModel(repository)
        advanceUntilIdle()

        // Backend mengembalikan body PUT yang berbeda dengan hasil GET
        // berikutnya; layar harus memakai nilai GET (authoritative).
        coEvery { repository.updateProfile(any(), any(), any(), any()) } returns
            Result.success(profile(name = "Budi (PUT)"))
        coEvery { repository.getProfile() } returns Result.success(profile(name = "Budi (GET)"))

        viewModel.onNameChange("Budi")
        viewModel.saveProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Budi (GET)", state.profile?.name)
        assertEquals("Budi (GET)", state.name)
        assertEquals("Profil berhasil diperbarui.", state.message)
        assertFalse(state.isSaving)
        assertNull(state.error)
        coVerify(exactly = 1) { repository.updateProfile(any(), any(), any(), any()) }
        coVerify(exactly = 2) { repository.getProfile() }
    }

    @Test
    fun `save keeps the PUT response when the refresh fails`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.success(profile(name = "Budi"))

        val viewModel = CustomerProfileViewModel(repository)
        advanceUntilIdle()

        coEvery { repository.updateProfile(any(), any(), any(), any()) } returns
            Result.success(profile(name = "Budi Baru"))
        coEvery { repository.getProfile() } returns Result.failure(NoNetworkException())

        viewModel.onNameChange("Budi Baru")
        viewModel.saveProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Simpan berhasil; kegagalan refresh tidak boleh mengubahnya menjadi
        // error dan tidak boleh menghapus data yang sudah dikonfirmasi server.
        assertEquals("Budi Baru", state.profile?.name)
        assertEquals("Profil berhasil diperbarui.", state.message)
        assertFalse(state.isSaving)
        assertNull(state.error)
    }

    @Test
    fun `failed save keeps the existing profile and exposes an error`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.success(profile(name = "Budi"))

        val viewModel = CustomerProfileViewModel(repository)
        advanceUntilIdle()

        coEvery { repository.updateProfile(any(), any(), any(), any()) } returns
            Result.failure(NoNetworkException())

        viewModel.onNameChange("Budi Baru")
        viewModel.saveProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Budi", state.profile?.name)
        assertFalse(state.isSaving)
        assertNotNull(state.error)
        coVerify(exactly = 1) { repository.getProfile() }
    }

    @Test
    fun `blank name never reaches the backend`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.success(profile())

        val viewModel = CustomerProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.onNameChange("   ")
        viewModel.saveProfile()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateProfile(any(), any(), any(), any()) }
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `invalid email never reaches the backend`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.success(profile())

        val viewModel = CustomerProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.onEmailChange("bukan-email")
        viewModel.saveProfile()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateProfile(any(), any(), any(), any()) }
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `valid email is sent to the backend on save`() = runTest(dispatcher) {
        coEvery { repository.getProfile() } returns Result.success(profile())
        coEvery { repository.updateProfile(any(), any(), any(), any()) } returns
            Result.success(profile())

        val viewModel = CustomerProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.onEmailChange("baru@example.com")
        viewModel.saveProfile()
        advanceUntilIdle()

        // `capture` MockK hanya menerima tipe non-null (`T : Any`), jadi slot
        // memakai `String`; email yang benar-benar terkirim sudah non-null.
        val emailSlot = slot<String>()
        coVerify(exactly = 1) {
            repository.updateProfile(any(), any(), capture(emailSlot), any())
        }
        assertEquals("baru@example.com", emailSlot.captured)
    }

    private fun profile(
        name: String = "Budi",
        phone: String? = "08123456789",
        email: String? = "budi@example.com",
        address: String? = "Jl. Merdeka No. 10"
    ) = CustomerProfile(
        id = "1",
        userId = "10",
        name = name,
        phone = phone,
        email = email,
        defaultAddress = address
    )
}
