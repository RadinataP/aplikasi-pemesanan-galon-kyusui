package com.kyusui.app.data.repository

import com.kyusui.app.core.ApiErrorParser
import com.kyusui.app.core.NoNetworkException
import com.kyusui.app.core.Result
import com.kyusui.app.core.ServerException
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.core.ValidationException
import com.kyusui.app.data.api.AuthApi
import com.kyusui.app.data.api.AuthCurrentUserDataDto
import com.kyusui.app.data.api.AuthCurrentUserDto
import com.kyusui.app.data.api.AuthEnvelopeDto
import com.kyusui.app.data.api.AuthLoginRequestDto
import com.kyusui.app.data.api.AuthRegisterRequestDto
import com.kyusui.app.data.api.AuthRoleDto
import com.kyusui.app.data.api.AuthSessionDto
import com.kyusui.app.data.api.AuthUserDto
import com.kyusui.app.data.datastore.PreferencesDataSource
import com.kyusui.app.data.datastore.StoredSession
import com.kyusui.app.domain.model.UserRole
import com.kyusui.app.domain.repository.LoginRequest
import com.kyusui.app.domain.repository.RegisterRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Kontrak diuji mengikuti `06_KYUSUI_API_SPECIFICATION_REBUILT.md` section 8.
 */
class AuthRepositoryImplTest {

    private lateinit var authApi: AuthApi
    private lateinit var preferencesRepository: PreferencesDataSource
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setup() {
        authApi = mockk()
        preferencesRepository = mockk(relaxed = true)
        coEvery { preferencesRepository.saveAuthData(any(), any(), any(), any(), any(), any()) } returns
            Result.success(Unit)
        coEvery { preferencesRepository.clearAuthData() } returns Result.success(Unit)
        coEvery { preferencesRepository.storedSession } returns flowOf(null)
        repository = AuthRepositoryImpl(authApi, preferencesRepository)
    }

    // --- 1. Successful login ---

    @Test
    fun `successful login returns token and persists session`() = runBlocking {
        coEvery { authApi.login(any()) } returns envelope(
            token = "sanctum-token",
            roleName = "CUSTOMER"
        )

        val result = repository.login(LoginRequest(email = "budi@example.com", password = "secret"))

        assertTrue(result.isSuccess)
        val authResult = result.getOrThrow()
        assertEquals("sanctum-token", authResult.token)
        assertEquals("1", authResult.user.id)
        assertEquals("Budi", authResult.user.name)
        assertEquals(UserRole.CUSTOMER, authResult.user.role)
        assertEquals("Pelanggan", authResult.user.roleDisplayName)
        assertTrue(authResult.user.isActive())

        coVerify(exactly = 1) {
            preferencesRepository.saveAuthData(
                accessToken = "sanctum-token",
                userId = "1",
                role = "CUSTOMER",
                email = "budi@example.com",
                name = "Budi",
                status = "ACTIVE"
            )
        }
    }

    /**
     * `AuthController::login` mencari user dengan `where('email', ...)`, jadi
     * nama field harus `email`. Mengirim `login` akan menghasilkan 422.
     */
    @Test
    fun `login sends the trimmed email field required by the backend`() = runBlocking {
        val captured = slot<AuthLoginRequestDto>()
        coEvery { authApi.login(capture(captured)) } returns envelope()

        repository.login(LoginRequest(email = "  budi@example.com  ", password = "secret"))

        assertEquals("budi@example.com", captured.captured.email)
        assertEquals("secret", captured.captured.password)
    }

    // --- 2. Invalid credential ---

    @Test
    fun `invalid credential returns unauthorized failure`() = runBlocking {
        coEvery { authApi.login(any()) } throws UnauthorizedException(
            "Sesi tidak valid atau telah berakhir. Silakan masuk kembali."
        )

        val result = repository.login(LoginRequest(email = "budi@example.com", password = "salah"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull is UnauthorizedException)
        coVerify(exactly = 0) {
            preferencesRepository.saveAuthData(any(), any(), any(), any(), any(), any())
        }
    }

    // --- 3. Validation error ---

    @Test
    fun `validation error keeps per field messages`() = runBlocking {
        coEvery { authApi.register(any()) } throws ValidationException(
            errors = mapOf(
                "email" to listOf("The email has already been taken."),
                "phone" to listOf("The phone has already been taken.")
            ),
            message = "Validation failed."
        )

        val result = repository.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                password = "secret-password",
                passwordConfirmation = "secret-password",
                email = "budi@example.com"
            )
        )

        assertTrue(result.isFailure)
        val validation = result.exceptionOrNull as ValidationException
        assertEquals("The email has already been taken.", validation.errors["email"]?.first())
        assertEquals("The phone has already been taken.", validation.errors["phone"]?.first())
    }

    @Test
    fun `register does not send role because role comes from backend`() = runBlocking {
        val captured = slot<AuthRegisterRequestDto>()
        coEvery { authApi.register(capture(captured)) } returns envelope()

        repository.register(
            RegisterRequest(
                name = " Budi ",
                phone = " 081234567890 ",
                password = "secret-password",
                passwordConfirmation = "secret-password",
                email = " budi@example.com "
            )
        )

        assertEquals("Budi", captured.captured.name)
        assertEquals("081234567890", captured.captured.phone)
        assertEquals("budi@example.com", captured.captured.email)
        assertEquals("secret-password", captured.captured.passwordConfirmation)
    }

    /**
     * Backend memvalidasi `email` dengan `required`. Versi lama membuang email
     * kosong sebelum request, yang membuat 422 menjadi tak terduga bagi pengguna.
     * Repository kini meneruskan apa adanya; penolakan email kosong adalah
     * pekerjaan validasi lokal di ViewModel.
     */
    @Test
    fun `register forwards the email exactly as given instead of dropping it`() = runBlocking {
        val captured = slot<AuthRegisterRequestDto>()
        coEvery { authApi.register(capture(captured)) } returns envelope()

        repository.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                email = "budi@example.com",
                password = "secret-password",
                passwordConfirmation = "secret-password"
            )
        )

        assertEquals("budi@example.com", captured.captured.email)
    }

    // --- 4. Session restoration ---

    @Test
    fun `session restoration returns session built from stored token and current user`() = runBlocking {
        coEvery { preferencesRepository.storedSession } returns flowOf(storedSession())
        coEvery { authApi.currentUser() } returns currentUser(roleName = "OWNER")

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        val session = requireNotNull(result.getOrThrow())
        assertEquals("stored-token", session.token)
        assertEquals("1", session.user.id)
        assertEquals("Budi", session.user.name)
        assertEquals("budi@example.com", session.user.email)
        assertEquals(UserRole.OWNER, session.user.role)
        coVerify(exactly = 1) { authApi.currentUser() }
    }

    @Test
    fun `session restoration repersists the user resolved from the nested data user`() = runBlocking {
        coEvery { preferencesRepository.storedSession } returns flowOf(storedSession())
        coEvery { authApi.currentUser() } returns currentUser(roleName = "CUSTOMER")

        repository.restoreSession()

        coVerify(exactly = 1) {
            preferencesRepository.saveAuthData(
                accessToken = "stored-token",
                userId = "1",
                role = "CUSTOMER",
                email = "budi@example.com",
                name = "Budi",
                status = "ACTIVE"
            )
        }
    }

    @Test
    fun `session restoration returns null when nothing is stored`() = runBlocking {
        coEvery { preferencesRepository.storedSession } returns flowOf(null)

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
        coVerify(exactly = 0) { authApi.currentUser() }
    }

    @Test
    fun `session restoration propagates network failure so user can retry`() = runBlocking {
        coEvery { preferencesRepository.storedSession } returns flowOf(storedSession())
        coEvery { authApi.currentUser() } throws NoNetworkException()

        val result = repository.restoreSession()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull is NoNetworkException)
        coVerify(exactly = 0) { preferencesRepository.clearAuthData() }
    }

    // --- 5. Unauthorized response ---

    @Test
    fun `unauthorized response during restoration clears the local session`() = runBlocking {
        coEvery { preferencesRepository.storedSession } returns flowOf(storedSession())
        coEvery { authApi.currentUser() } throws UnauthorizedException()

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
        coVerify(exactly = 1) { preferencesRepository.clearAuthData() }
    }

    @Test
    fun `HTTP 401 on auth me clears the local session during restoration`() = runBlocking {
        coEvery { preferencesRepository.storedSession } returns flowOf(storedSession())
        coEvery { authApi.currentUser() } throws httpException(401, """{"message":"Unauthenticated."}""")

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
        coVerify(exactly = 1) { preferencesRepository.clearAuthData() }
    }

    @Test
    fun `HTTP 401 on auth me is reported as unauthorized for current user`() = runBlocking {
        coEvery { authApi.currentUser() } throws httpException(401, """{"message":"Unauthenticated."}""")

        val result = repository.currentUser()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull is UnauthorizedException)
        coVerify(exactly = 0) { preferencesRepository.clearAuthData() }
    }

    @Test
    fun `HTTP 500 on auth me propagates as a server failure so the user can retry`() = runBlocking {
        coEvery { preferencesRepository.storedSession } returns flowOf(storedSession())
        coEvery { authApi.currentUser() } throws httpException(500, """{"message":"Server error."}""")

        val result = repository.restoreSession()

        assertTrue(result.isFailure)
        assertEquals(500, (result.exceptionOrNull as ServerException).statusCode)
        coVerify(exactly = 0) { preferencesRepository.clearAuthData() }
    }

    // --- 6. Logout ---

    @Test
    fun `logout clears the local session`() = runBlocking {
        coEvery { authApi.logout() } returns Response.success(Unit)

        val result = repository.logout()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { authApi.logout() }
        coVerify(exactly = 1) { preferencesRepository.clearAuthData() }
    }

    @Test
    fun `logout clears local session even when the server rejects the request`() = runBlocking {
        coEvery { authApi.logout() } returns Response.error(401, "".toResponseBody(null))

        repository.logout()

        coVerify(exactly = 1) { preferencesRepository.clearAuthData() }
    }

    @Test
    fun `logout clears local session even when the server is unreachable`() = runBlocking {
        coEvery { authApi.logout() } throws NoNetworkException()

        repository.logout()

        coVerify(exactly = 1) { preferencesRepository.clearAuthData() }
    }

    // --- 7. Role routing ---

    @Test
    fun `role from backend is preserved for each canonical role`() = runBlocking {
        val expected = mapOf(
            "CUSTOMER" to UserRole.CUSTOMER,
            "OWNER" to UserRole.OWNER,
            "COURIER" to UserRole.COURIER
        )

        expected.forEach { (roleName, expectedRole) ->
            coEvery { authApi.login(any()) } returns envelope(roleName = roleName)

            val result = repository.login(LoginRequest(email = "budi@example.com", password = "secret"))

            assertTrue(result.isSuccess)
            assertEquals(expectedRole, result.getOrThrow().user.role)
        }
    }

    @Test
    fun `unknown role is rejected instead of silently treated as customer`() = runBlocking {
        coEvery { authApi.login(any()) } returns envelope(roleName = "ADMIN")

        val result = repository.login(LoginRequest(email = "budi@example.com", password = "secret"))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) {
            preferencesRepository.saveAuthData(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `current user endpoint is mapped to domain user`() = runBlocking {
        coEvery { authApi.currentUser() } returns currentUser(roleName = "COURIER")

        val result = repository.currentUser()

        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("Kurir", user.roleLabel())
        assertEquals(UserRole.COURIER, user.role)
    }

    /**
     * `UserResource` backend tidak mengirim `display_name`, jadi label role
     * memakai nama bawaan per role. `status` juga tidak dikirim, sehingga
     * `isActive()` false; tidak ada consumer produksi yang bergantung pada ini.
     */
    @Test
    fun `current user from the real user resource maps to the domain model`() = runBlocking {
        coEvery { authApi.currentUser() } returns backendUserResource(roleName = "CUSTOMER")

        val result = repository.currentUser()

        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("5", user.id)
        assertEquals("Andi", user.name)
        assertEquals("andi@example.com", user.email)
        assertEquals(UserRole.CUSTOMER, user.role)
        assertEquals("Pelanggan", user.roleLabel())
    }

    /**
     * Respons yang tidak memuat `data.user` harus ditangani sebagai session
     * tidak berlaku, bukan lewat exception yang membuat layar restore gagal
     * atau user dengan field kosong.
     */
    @Test
    fun `current user without data user fails instead of returning an empty user`() = runBlocking {
        coEvery { authApi.currentUser() } returns AuthCurrentUserDto(data = null, message = "Success.")

        val result = repository.currentUser()

        assertTrue(result.isFailure)
    }

    @Test
    fun `session restoration clears the local session when the response has no user`() = runBlocking {
        coEvery { preferencesRepository.storedSession } returns flowOf(storedSession())
        coEvery { authApi.currentUser() } returns AuthCurrentUserDto(
            data = AuthCurrentUserDataDto(user = null),
            message = "Success."
        )

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
        coVerify(exactly = 1) { preferencesRepository.clearAuthData() }
    }

    // --- 8. HTTP error responses coming from Retrofit ---

    /**
     * Registro regresi untuk crash `FATAL EXCEPTION: OkHttp Dispatcher`.
     *
     * Error HTTP sungguhan tidak pernah sampai sebagai [ValidationException]:
     * Retrofit melempar [retrofit2.HttpException], dan `safeApiCall` yang
     * memetakannya. Test ini memakai bentuk itu, bukan exception yang sudah
     * dipetakan, supaya jalur produksi ikut ter-cover.
     */
    @Test
    fun `HTTP 422 on login becomes a validation failure with field errors`() = runBlocking {
        val body = """
            {
              "message": "The email field is required.",
              "errors": {
                "email": ["The email field is required."],
                "password": ["The password field must be at least 8 characters."]
              }
            }
        """.trimIndent()
        coEvery { authApi.login(any()) } throws httpException(422, body)

        val result = repository.login(LoginRequest(email = "budi@example.com", password = "salah"))

        assertTrue(result.isFailure)
        val validation = result.exceptionOrNull as ValidationException
        assertEquals("Email wajib diisi.", validation.errors["email"]?.first())
        assertEquals("Kata sandi minimal 8 karakter.", validation.errors["password"]?.first())
        coVerify(exactly = 0) {
            preferencesRepository.saveAuthData(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `HTTP 422 on register becomes a validation failure with field errors`() = runBlocking {
        val body = """
            { "message": "The given data was invalid.",
              "errors": { "email": ["The email has already been taken."] } }
        """.trimIndent()
        coEvery { authApi.register(any()) } throws httpException(422, body)

        val result = repository.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                email = "budi@example.com",
                password = "secret-password",
                passwordConfirmation = "secret-password"
            )
        )

        assertTrue(result.isFailure)
        val validation = result.exceptionOrNull as ValidationException
        assertEquals("Email sudah terdaftar.", validation.errors["email"]?.first())
        coVerify(exactly = 0) {
            preferencesRepository.saveAuthData(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `HTTP 422 without field errors keeps the generic validation message`() = runBlocking {
        coEvery { authApi.login(any()) } throws
            httpException(422, """{"message":"The given data was invalid."}""")

        val result = repository.login(LoginRequest(email = "budi@example.com", password = "salah-password"))

        val validation = result.exceptionOrNull as ValidationException
        assertTrue(validation.errors.isEmpty())
        assertEquals(ApiErrorParser.VALIDATION_MESSAGE, validation.message)
    }

    @Test
    fun `HTTP 401 on login becomes an unauthorized failure`() = runBlocking {
        coEvery { authApi.login(any()) } throws
            httpException(401, """{"message":"Email atau password salah."}""")

        val result = repository.login(LoginRequest(email = "budi@example.com", password = "salah-password"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull is UnauthorizedException)
    }

    @Test
    fun `HTTP 500 on register becomes a server failure`() = runBlocking {
        coEvery { authApi.register(any()) } throws
            httpException(500, """{"message":"Registrasi gagal. Silakan coba lagi."}""")

        val result = repository.register(
            RegisterRequest(
                name = "Budi",
                phone = "081234567890",
                password = "secret-password",
                passwordConfirmation = "secret-password",
                email = "budi@example.com"
            )
        )

        assertTrue(result.isFailure)
        val server = result.exceptionOrNull as ServerException
        assertEquals(500, server.statusCode)
        assertEquals("Registrasi gagal. Silakan coba lagi.", server.message)
    }

    @Test
    fun `HTTP 503 on login becomes a server failure and does not persist a session`() = runBlocking {
        coEvery { authApi.login(any()) } throws httpException(503, "")

        val result = repository.login(LoginRequest(email = "budi@example.com", password = "secret-password"))

        assertTrue(result.isFailure)
        assertEquals(503, (result.exceptionOrNull as ServerException).statusCode)
        coVerify(exactly = 0) {
            preferencesRepository.saveAuthData(any(), any(), any(), any(), any(), any())
        }
    }

    // --- Helpers ---

    private fun httpException(code: Int, body: String): HttpException = HttpException(
        Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType()))
    )

    private fun envelope(
        token: String = "sanctum-token",
        roleName: String = "CUSTOMER"
    ) = AuthEnvelopeDto(
        data = AuthSessionDto(
            user = authUser(roleName = roleName),
            token = token
        ),
        message = "Login successful."
    )

    /**
 * Bentuk literal `AuthController::me`: user berada satu level dalam di bawah
 * `data`, bukan langsung di `data`.
 */
private fun currentUser(roleName: String) = AuthCurrentUserDto(
        data = AuthCurrentUserDataDto(user = authUser(roleName = roleName)),
        message = "Data pengguna berhasil dimuat."
    )

    /**
     * Bentuk `UserResource` yang sebenarnya dari backend: `role` hanya berisi
     * `id` + `name`, dan `status` tidak pernah dikirim.
     */
    private fun backendUserResource(roleName: String) = AuthCurrentUserDto(
        data = AuthCurrentUserDataDto(
            user = AuthUserDto(
                id = 5L,
                name = "Andi",
                email = "andi@example.com",
                phone = null,
                role = AuthRoleDto(name = roleName, display_name = null),
                status = null
            )
        ),
        message = "Data pengguna berhasil dimuat."
    )

    private fun authUser(roleName: String) = AuthUserDto(
        id = 1L,
        name = "Budi",
        email = "budi@example.com",
        phone = "081234567890",
        role = AuthRoleDto(name = roleName, display_name = displayNameOf(roleName)),
        status = "ACTIVE"
    )

    private fun displayNameOf(roleName: String) = when (roleName) {
        "OWNER" -> "Pemilik Toko"
        "COURIER" -> "Kurir"
        else -> "Pelanggan"
    }

    private fun storedSession() = StoredSession(
        token = "stored-token",
        userId = "1",
        role = "CUSTOMER",
        name = "Budi",
        email = "budi@example.com",
        status = "ACTIVE"
    )
}
