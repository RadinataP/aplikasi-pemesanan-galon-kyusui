package com.kyusui.app.ui.auth

import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.AuthResult
import com.kyusui.app.domain.model.AuthSession
import com.kyusui.app.domain.model.User
import com.kyusui.app.domain.model.UserRole
import com.kyusui.app.domain.repository.AuthRepository
import com.kyusui.app.domain.repository.LoginRequest
import com.kyusui.app.domain.repository.RegisterRequest
import com.kyusui.app.ui.courier.tracking.FakeCourierLocationSharing
import com.kyusui.app.ui.courier.tracking.TrackingStopReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: AuthRepository
    private lateinit var sharing: FakeCourierLocationSharing
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        sharing = FakeCourierLocationSharing()
        coEvery { repository.session } returns flowOf(null)
        coEvery { repository.restoreSession() } returns Result.success(null)
        viewModel = AuthViewModel(repository, sharing)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- 7. Role routing ---

    @Test
    fun `each canonical role maps to its own entry point`() {
        assertEquals(AuthEntryPoint.CUSTOMER_HOME, AuthEntryPoint.fromRole(UserRole.CUSTOMER))
        assertEquals(AuthEntryPoint.OWNER_HOME, AuthEntryPoint.fromRole(UserRole.OWNER))
        assertEquals(AuthEntryPoint.COURIER_HOME, AuthEntryPoint.fromRole(UserRole.COURIER))
    }

    @Test
    fun `login as owner resolves to owner entry point`() = runTest(dispatcher) {
        coEvery { repository.restoreSession() } returns Result.success(null)
        coEvery { repository.login(any()) } returns Result.success(authResult(UserRole.OWNER))

        val viewModel = AuthViewModel(repository, sharing)
        advanceUntilIdle()
        viewModel.login("budi@example.com", "secret")
        advanceUntilIdle()

        assertEquals(AuthEntryPoint.OWNER_HOME, viewModel.entryPoint.value)
        assertEquals(UserRole.OWNER, (viewModel.uiState.value as AuthUiState.Authenticated).user.role)
    }

    @Test
    fun `login as courier resolves to courier entry point`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } returns Result.success(authResult(UserRole.COURIER))

        viewModel.login("budi@example.com", "secret")
        advanceUntilIdle()

        assertEquals(AuthEntryPoint.COURIER_HOME, viewModel.entryPoint.value)
    }

    // --- Session restoration ---

    @Test
    fun `restored session resolves entry point without asking for login again`() = runTest(dispatcher) {
        coEvery { repository.restoreSession() } returns Result.success(
            AuthSession(user = user(UserRole.CUSTOMER), token = "stored-token")
        )

        val viewModel = AuthViewModel(repository, sharing)
        advanceUntilIdle()

        assertEquals(AuthEntryPoint.CUSTOMER_HOME, viewModel.entryPoint.value)
        assertTrue(viewModel.uiState.value is AuthUiState.Authenticated)
    }

    @Test
    fun `empty session leaves the app on login entry point`() = runTest(dispatcher) {
        val viewModel = AuthViewModel(repository, sharing)
        advanceUntilIdle()

        assertEquals(AuthEntryPoint.LOGIN, viewModel.entryPoint.value)
        assertTrue(viewModel.uiState.value is AuthUiState.Unauthenticated)
    }

    @Test
    fun `failed restoration exposes a recoverable state`() = runTest(dispatcher) {
        coEvery { repository.restoreSession() } returns
            Result.failure(com.kyusui.app.core.NoNetworkException())

        val viewModel = AuthViewModel(repository, sharing)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AuthUiState.RestoreFailed)
        assertNull(viewModel.entryPoint.value)
    }

    // --- Loading state ---

    @Test
    fun `login shows submitting state while the request is in flight`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } returns Result.success(authResult(UserRole.CUSTOMER))

        viewModel.login("budi@example.com", "secret")

        assertTrue((viewModel.uiState.value as AuthUiState.Unauthenticated).isSubmitting)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AuthUiState.Authenticated)
    }

    // --- Logout ---

    @Test
    fun `logout returns to login entry point`() = runTest(dispatcher) {
        coEvery { repository.restoreSession() } returns Result.success(
            AuthSession(user = user(UserRole.CUSTOMER), token = "stored-token")
        )
        coEvery { repository.logout() } returns Result.success(Unit)

        val viewModel = AuthViewModel(repository, sharing)
        advanceUntilIdle()
        viewModel.logout()
        advanceUntilIdle()

        assertEquals(AuthEntryPoint.LOGIN, viewModel.entryPoint.value)
        assertTrue(viewModel.uiState.value is AuthUiState.Unauthenticated)
        coVerify(exactly = 1) { repository.logout() }
    }

    @Test
    fun `logout stops location sharing so no background sender survives a logout`() = runTest(dispatcher) {
        coEvery { repository.restoreSession() } returns Result.success(
            AuthSession(user = user(UserRole.COURIER), token = "stored-token")
        )
        coEvery { repository.logout() } returns Result.success(Unit)
        // Kurir sedang mengirim posisi.
        sharing.start("7")

        val viewModel = AuthViewModel(repository, sharing)
        advanceUntilIdle()
        viewModel.logout()
        advanceUntilIdle()

        assertEquals(listOf(TrackingStopReason.SESSION_EXPIRED), sharing.stoppedReasons)
        assertTrue(sharing.started.isNotEmpty())
    }

    // --- Validation ---

    @Test
    fun `blank credentials are rejected without hitting the network`() = runTest(dispatcher) {
        viewModel.login("", "")

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertTrue(state.errorMessage!!.isNotBlank())
        coVerify(exactly = 0) { repository.login(any()) }
    }

    @Test
    fun `short password is rejected before the request`() = runTest(dispatcher) {
        viewModel.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                email = "budi@example.com",
                password = "short",
                passwordConfirmation = "short"
            )
        )

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertEquals(
            "Kata sandi minimal 8 karakter.",
            state.fieldErrors[AuthViewModel.FIELD_PASSWORD]
        )
        coVerify(exactly = 0) { repository.register(any()) }
    }

    @Test
    fun `mismatched confirmation is rejected before the request`() = runTest(dispatcher) {
        viewModel.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                email = "budi@example.com",
                password = "secret-password",
                passwordConfirmation = "different-password"
            )
        )

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertEquals(
            "Konfirmasi kata sandi tidak cocok.",
            state.fieldErrors[AuthViewModel.FIELD_PASSWORD_CONFIRMATION]
        )
        coVerify(exactly = 0) { repository.register(any()) }
    }

    /**
     * Backend memvalidasi `email` dengan `required`, jadi email kosong harus
     * berhenti di Android. Versi lama mengirim request tanpa email dan
     * menunggu 422 yang tidak bisa di perpendiksi.
     */
    @Test
    fun `register rejects an empty email before the request`() = runTest(dispatcher) {
        viewModel.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                email = "",
                password = "secret-password",
                passwordConfirmation = "secret-password"
            )
        )

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertEquals(
            AuthViewModel.EMAIL_REQUIRED_MESSAGE,
            state.fieldErrors[AuthViewModel.FIELD_EMAIL]
        )
        coVerify(exactly = 0) { repository.register(any()) }
    }

    @Test
    fun `register rejects a blank email before the request`() = runTest(dispatcher) {
        viewModel.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                email = "   ",
                password = "secret-password",
                passwordConfirmation = "secret-password"
            )
        )

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertEquals(
            AuthViewModel.EMAIL_REQUIRED_MESSAGE,
            state.fieldErrors[AuthViewModel.FIELD_EMAIL]
        )
        coVerify(exactly = 0) { repository.register(any()) }
    }

    @Test
    fun `register rejects an invalid email before the request`() = runTest(dispatcher) {
        viewModel.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                email = "budi@",
                password = "secret-password",
                passwordConfirmation = "secret-password"
            )
        )

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertEquals(
            AuthViewModel.EMAIL_FORMAT_MESSAGE,
            state.fieldErrors[AuthViewModel.FIELD_EMAIL]
        )
        coVerify(exactly = 0) { repository.register(any()) }
    }

    @Test
    fun `register accepts a valid email and reaches the repository`() = runTest(dispatcher) {
        coEvery { repository.register(any()) } returns Result.success(authResult(UserRole.CUSTOMER))

        viewModel.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                email = "budi@example.com",
                password = "secret-password",
                passwordConfirmation = "secret-password"
            )
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.register(
                RegisterRequest(
                    name = "Budi",
                    phone = "081234567890",
                    email = "budi@example.com",
                    password = "secret-password",
                    passwordConfirmation = "secret-password"
                )
            )
        }
        assertTrue(viewModel.uiState.value is AuthUiState.Authenticated)
    }

    @Test
    fun `backend 422 on register reaches the email field`() = runTest(dispatcher) {
        coEvery { repository.register(any()) } returns Result.failure(
            com.kyusui.app.core.ValidationException(
                errors = mapOf("email" to listOf("The email has already been taken."))
            )
        )

        viewModel.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                email = "budi@example.com",
                password = "secret-password",
                passwordConfirmation = "secret-password"
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertEquals(
            "The email has already been taken.",
            state.fieldErrors[AuthViewModel.FIELD_EMAIL]
        )
        assertTrue(state.errorMessage!!.isNotBlank())
    }

    // --- Login: email sebagai identitas ---

    @Test
    fun `login accepts a valid email`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } returns Result.success(authResult(UserRole.CUSTOMER))

        viewModel.login("budi@example.com", "secret-password")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.login(LoginRequest(email = "budi@example.com", password = "secret-password"))
        }
        assertTrue(viewModel.uiState.value is AuthUiState.Authenticated)
    }

    @Test
    fun `login trims the email before building the request`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } returns Result.success(authResult(UserRole.CUSTOMER))

        viewModel.login("  budi@example.com  ", "secret-password")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.login(LoginRequest(email = "budi@example.com", password = "secret-password"))
        }
    }

    @Test
    fun `login rejects an empty email without hitting the network`() = runTest(dispatcher) {
        viewModel.login("", "secret-password")

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertEquals(
            AuthViewModel.EMAIL_REQUIRED_MESSAGE,
            state.fieldErrors[AuthViewModel.FIELD_EMAIL]
        )
        assertTrue(state.errorMessage!!.isNotBlank())
        coVerify(exactly = 0) { repository.login(any()) }
    }

    @Test
    fun `login rejects an invalid email without hitting the network`() = runTest(dispatcher) {
        viewModel.login("081234567890", "secret-password")

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertEquals(
            AuthViewModel.EMAIL_FORMAT_MESSAGE,
            state.fieldErrors[AuthViewModel.FIELD_EMAIL]
        )
        coVerify(exactly = 0) { repository.login(any()) }
    }

    @Test
    fun `login reports a missing password against the password field`() = runTest(dispatcher) {
        viewModel.login("budi@example.com", "")

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertEquals(
            AuthViewModel.PASSWORD_REQUIRED_MESSAGE,
            state.fieldErrors[AuthViewModel.FIELD_PASSWORD]
        )
        coVerify(exactly = 0) { repository.login(any()) }
    }

    @Test
    fun `network failure on login surfaces an error without crashing`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } returns Result.failure(
            com.kyusui.app.core.NoNetworkException()
        )

        viewModel.login("budi@example.com", "secret-password")
        advanceUntilIdle()

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertTrue(state.errorMessage!!.isNotBlank())
        assertEquals(AuthEntryPoint.LOGIN, viewModel.entryPoint.value)
    }

    /**
 * ViewModel tidak menerjemahkan pesan; terjemahan ke Bahasa Indonesia terjadi
 * di `ApiErrorParser` yang sudah diuji terpisah. Yang diuji di sini adalah
 * pemetaan nama field backend ke key form, karena `email` dari backend harus
 * mendarat di field email pada layar login.
 */
@Test
    fun `server validation errors are mapped to the matching form field`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } returns Result.failure(
            com.kyusui.app.core.ValidationException(
                errors = mapOf("email" to listOf("The email field is required."))
            )
        )

        viewModel.login("budi@example.com", "secret")
        advanceUntilIdle()

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertEquals(
            "The email field is required.",
            state.fieldErrors[AuthViewModel.FIELD_EMAIL]
        )
    }

    @Test
    fun `422 without field errors keeps a readable login message`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } returns Result.failure(
            com.kyusui.app.core.ValidationException(errors = emptyMap())
        )

        viewModel.login("budi@example.com", "secret")
        advanceUntilIdle()

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertTrue(state.fieldErrors.isEmpty())
        assertTrue(state.errorMessage!!.isNotBlank())
    }

    @Test
    fun `unauthorized login surfaces an error without authenticating`() = runTest(dispatcher) {
        coEvery { repository.login(any()) } returns Result.failure(
            com.kyusui.app.core.UnauthorizedException()
        )

        viewModel.login("budi@example.com", "salah-password")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AuthUiState.Unauthenticated)
        assertEquals(AuthEntryPoint.LOGIN, viewModel.entryPoint.value)
    }

    @Test
    fun `clearFieldError only removes the field that is being edited`() = runTest(dispatcher) {
        viewModel.login("", "")
        viewModel.clearFieldError(AuthViewModel.FIELD_EMAIL)

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertNull(state.errorMessage)
        assertTrue(AuthViewModel.FIELD_EMAIL !in state.fieldErrors)
        assertTrue(AuthViewModel.FIELD_PASSWORD in state.fieldErrors)
    }

    @Test
    fun `clearError removes the message and the field errors`() = runTest(dispatcher) {
        viewModel.login("", "")
        viewModel.clearError()

        val state = viewModel.uiState.value as AuthUiState.Unauthenticated
        assertNull(state.errorMessage)
        assertTrue(state.fieldErrors.isEmpty())
    }

    private fun authResult(role: UserRole) = AuthResult(user = user(role), token = "sanctum-token")

    private fun user(role: UserRole) = User(
        id = "1",
        name = "Budi",
        email = "budi@example.com",
        role = role,
        status = "ACTIVE"
    )
}
