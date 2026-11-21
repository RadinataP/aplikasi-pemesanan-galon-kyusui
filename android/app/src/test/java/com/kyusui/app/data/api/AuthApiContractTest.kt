package com.kyusui.app.data.api

import com.kyusui.app.core.ApiErrorParser
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.NotFoundException
import com.kyusui.app.core.ServerException
import com.kyusui.app.core.TimeoutException
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.core.UnknownException
import com.kyusui.app.core.ValidationException
import com.kyusui.app.core.toNetworkException
import com.kyusui.app.domain.model.UserRole
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/**
 * Fixture di test ini diambil bentuk literal dari
 * `06_KYUSUI_API_SPECIFICATION_REBUILT.md` section 4.4, 7.1, dan 8.
 */
class AuthApiContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    // --- 8.1 Register response ---

    @Test
    fun `register response envelope decodes user and token`() {
        val body = """
            {
              "data": {
                "user": {
                  "id": 1,
                  "name": "Budi",
                  "email": "budi@example.com",
                  "phone": "081234567890",
                  "role": { "name": "CUSTOMER", "display_name": "Pelanggan" },
                  "status": "ACTIVE"
                },
                "token": "sanctum-token"
              },
              "message": "Registration successful."
            }
        """.trimIndent()

        val envelope = json.decodeFromString<AuthEnvelopeDto<AuthSessionDto>>(body)

        assertEquals("Registration successful.", envelope.message)
        val session = requireNotNull(envelope.data)
        assertEquals("sanctum-token", session.token)
        assertEquals(1L, session.user.id)
        assertEquals("Budi", session.user.name)
        assertEquals("081234567890", session.user.phone)
        assertEquals("CUSTOMER", session.user.role.name)
        assertEquals("Pelanggan", session.user.role.display_name)
        assertEquals("ACTIVE", session.user.status)
    }

    // --- 8.2 Login request shape ---

    /**
     * Backend `AuthController::login` memvalidasi `email` dengan aturan
     * `required|email|max:255`. Mengirim field `login` menghasilkan 422 yang
     * selalu gagal, jadi nama field di DTO harus sama dengan backend.
     */
    @Test
    fun `login request serializes the email field and never a login field`() {
        val encoded = json.encodeToString(
            AuthLoginRequestDto.serializer(),
            AuthLoginRequestDto(email = "budi@example.com", password = "secret-password")
        )

        assertTrue(encoded.contains("\"email\":\"budi@example.com\""))
        assertTrue(!encoded.contains("\"login\""))
        assertTrue(!encoded.contains("device_token"))
    }

    @Test
    fun `login request does not leak the phone number as an identity field`() {
        val encoded = json.encodeToString(
            AuthLoginRequestDto.serializer(),
            AuthLoginRequestDto(email = "budi@example.com", password = "secret-password")
        )

        assertTrue(!encoded.contains("0812"))
        assertTrue(!encoded.contains("phone"))
    }

    // --- 8.1 Register request shape ---

    /**
     * Backend `AuthController::register` memvalidasi `email` dengan `required`,
     * jadi field ini tidak boleh nullable maupun dilewati saat kosong.
     */
    @Test
    fun `register request always serializes email`() {
        val encoded = json.encodeToString(
            AuthRegisterRequestDto.serializer(),
            AuthRegisterRequestDto(
                name = "Budi",
                phone = "081234567890",
                email = "budi@example.com",
                password = "secret-password",
                passwordConfirmation = "secret-password"
            )
        )

        assertTrue(encoded.contains("\"email\":\"budi@example.com\""))
    }

    @Test
    fun `register request serializes password confirmation and no role`() {
        val encoded = json.encodeToString(
            AuthRegisterRequestDto.serializer(),
            AuthRegisterRequestDto(
                name = "Budi",
                phone = "081234567890",
                email = "budi@example.com",
                password = "secret-password",
                passwordConfirmation = "secret-password"
            )
        )

        assertTrue(encoded.contains("\"password_confirmation\""))
        assertTrue(!encoded.contains("\"role\""))
        assertTrue(!encoded.contains("\"address\""))
    }

    // --- 8.4 Current user ---

    /**
     * `AuthController::me` menjawab `{"data":{"user":{...}}}`. DTO harus membaca
     * `data.user`; kalau `data` dibaca langsung sebagai user, field `user`
     * terabaikan dan `role` yang diwajibkan tidak pernah ditemukan.
     */
    @Test
    fun `current user decodes the nested data user resource`() {
        val body = """
            {
              "data": {
                "user": {
                  "id": 1,
                  "name": "Budi",
                  "email": "budi@example.com",
                  "role": { "id": 2, "name": "OWNER" }
                }
              },
              "message": "Data pengguna berhasil dimuat."
            }
        """.trimIndent()

        val dto = json.decodeFromString<AuthCurrentUserDto>(body)
        val user = dto.resolveUser()

        assertEquals(1L, requireNotNull(user).id)
        assertEquals("Budi", user.name)
        assertEquals("budi@example.com", user.email)
        assertEquals("OWNER", user.role.name)
        assertEquals("Data pengguna berhasil dimuat.", dto.message)
    }

    @Test
    fun `current user without data returns null`() {
        val dto = json.decodeFromString<AuthCurrentUserDto>("""{"message":"Success."}""")

        assertNull(dto.resolveUser())
    }

    @Test
    fun `current user with data but without user returns null`() {
        val dto = json.decodeFromString<AuthCurrentUserDto>("""{"data":{},"message":"Success."}""")

        assertNull(dto.resolveUser())
    }

    @Test
    fun `current user never resolves the data envelope itself as a user`() {
        val body = """{"data":{"user":{"id":1,"name":"Budi","role":{"name":"CUSTOMER"}}}}"""

        val user = json.decodeFromString<AuthCurrentUserDto>(body).resolveUser()

        assertEquals("Budi", requireNotNull(user).name)
        assertEquals(1L, requireNotNull(user).id)
    }

    @Test
    fun `user resource with a string id is still accepted`() {
        val body = """
            { "data": { "user": { "id": "42", "name": "Budi",
              "role": { "name": "COURIER" } } }, "message": "Success." }
        """.trimIndent()

        val user = json.decodeFromString<AuthCurrentUserDto>(body).resolveUser()

        assertEquals(42L, requireNotNull(user).id)
        assertEquals(UserRole.COURIER, UserRole.fromApiValue(requireNotNull(user).role.name))
    }

    // --- 4.4 Error envelope ---

    private val parser = ApiErrorParser(json)

    @Test
    fun `validation error envelope maps field messages`() {
        val body = """
            {
              "message": "Validation failed.",
              "errors": {
                "email": ["The email has already been taken."],
                "password": ["The password field must be at least 8 characters."]
              }
            }
        """.trimIndent()

        val exception = parser.parse(422, body, "Unprocessable Content")

        assertTrue(exception is ValidationException)
        val errors = (exception as ValidationException).errors
        assertEquals("Email sudah terdaftar.", errors["email"]?.first())
        assertEquals("Kata sandi minimal 8 karakter.", errors["password"]?.first())
    }

    @Test
    fun `duplicate identity conflict is reported as a field error`() {
        val body = """
            { "message": "Conflict.", "errors": { "phone": ["The phone has already been taken."] } }
        """.trimIndent()

        val exception = parser.parse(409, body, "Conflict")

        assertTrue(exception is ValidationException)
        assertEquals(
            "Nomor telepon sudah terdaftar.",
            (exception as ValidationException).errors["phone"]?.first()
        )
    }

    @Test
    fun `unknown field message is preserved so server detail is not lost`() {
        val body = """
            { "message": "Validation failed.", "errors": { "phone": ["Format 0812 tidak dikenali."] } }
        """.trimIndent()

        val errors = (parser.parse(422, body, "Unprocessable Content") as ValidationException).errors

        assertEquals("Format 0812 tidak dikenali.", errors["phone"]?.first())
    }

    @Test
    fun `nested error field names are reduced to the leaf name`() {
        val body = """
            { "message": "Validation failed.", "errors": { "data.user.phone": ["The phone has already been taken."] } }
        """.trimIndent()

        val errors = (parser.parse(422, body, "Unprocessable Content") as ValidationException).errors

        assertEquals(
            "Nomor telepon sudah terdaftar.",
            errors["phone"]?.first()
        )
    }

    // --- Status mapping ---

    @Test
    fun `401 maps to unauthorized with an Indonesian message`() {
        val exception = parser.parse(401, """{"message":"Unauthenticated."}""", "Unauthorized")

        assertTrue(exception is UnauthorizedException)
        assertEquals(ApiErrorParser.UNAUTHORIZED_MESSAGE, exception.message)
    }

    @Test
    fun `403 maps to forbidden`() {
        assertTrue(parser.parse(403, null, "Forbidden") is ForbiddenException)
    }

    @Test
    fun `404 maps to not found`() {
        assertTrue(parser.parse(404, null, "Not Found") is NotFoundException)
    }

    @Test
    fun `408 maps to timeout`() {
        assertTrue(parser.parse(408, null, "Request Timeout") is TimeoutException)
    }

    @Test
    fun `500 maps to server exception keeping the status code`() {
        val exception = parser.parse(500, null, "Server Error")

        assertTrue(exception is ServerException)
        assertEquals(500, (exception as ServerException).statusCode)
    }

    @Test
    fun `unexpected status maps to unknown exception`() {
        assertTrue(parser.parse(302, null, "Found") is UnknownException)
    }

    @Test
    fun `non json error body does not crash the parser`() {
        val exception = parser.parse(500, "<html>Bad Gateway</html>", "Bad Gateway")

        assertTrue(exception is ServerException)
    }

    // --- OkHttp chain must never throw ---

    /**
     * Regresi untuk bug crash: `ResponseValidationInterceptor` pernah melempar
     * exception dari dalam chain OkHttp. OkHttp menjalankan interceptor di thread
     * Dispatcher, jadi lemparan itu keluar dari `AsyncCall.execute()`,
     * diteruskan ke uncaught handler, dan menjatuhkan proses pada
     * `FATAL EXCEPTION: OkHttp Dispatcher` untuk setiap HTTP 422.
     */
    @Test
    fun `okhttp client chain has no network interceptor that can throw`() {
        val client = RetrofitModule.provideOkHttpClient(mockk(relaxed = true))

        assertTrue(
            "Interceptor network tidak boleh melempar exception",
            client.networkInterceptors.isEmpty()
        )
    }

    @Test
    fun `failure log interceptor returns a 422 response instead of throwing`() {
        val response = createResponse(422, """{"message":"The email field is required."}""")

        val returned = ApiFailureLogInterceptor(
            errorParser = parser,
            isEnabled = true,
            log = {}
        ).intercept(chainReturning(response))

        assertSame(response, returned)
    }

    @Test
    fun `failure log interceptor reports status path and field names only`() {
        val body = """
            { "message": "The given data was invalid.",
              "errors": { "email": ["The email field is required."] } }
        """.trimIndent()
        val logged = mutableListOf<String>()

        ApiFailureLogInterceptor(parser, isEnabled = true, log = { logged += it })
            .intercept(chainReturning(createResponse(422, body)))

        val line = logged.single()
        assertTrue(line, line.contains("HTTP 422"))
        assertTrue(line, line.contains("fields=[email]"))
        assertTrue(line, line.contains("path=/"))
        // Nilai field, password, dan token tidak boleh bocor ke log.
        assertTrue(line, !line.contains("secret"))
        assertTrue(line, !line.contains("Bearer"))
    }

    @Test
    fun `failure log interceptor stays silent on success responses`() {
        val logged = mutableListOf<String>()

        ApiFailureLogInterceptor(parser, isEnabled = true, log = { logged += it })
            .intercept(chainReturning(createResponse(200)))

        assertTrue(logged.isEmpty())
    }

    // --- Error mapping on the coroutine path (safeApiCall) ---

    @Test
    fun `422 http exception maps to validation exception with field errors`() {
        val body = """
            {
              "message": "The given data was invalid.",
              "errors": {
                "email": ["The email field is required."],
                "password": ["The password field must be at least 8 characters."]
              }
            }
        """.trimIndent()

        val exception = httpException(422, body).toNetworkException(parser)

        assertTrue(exception is ValidationException)
        val errors = (exception as ValidationException).errors
        assertEquals("Email wajib diisi.", errors["email"]?.first())
        assertEquals("Kata sandi minimal 8 karakter.", errors["password"]?.first())
    }

    @Test
    fun `422 without field errors keeps the generic validation message`() {
        val exception = httpException(422, """{"message":"The given data was invalid."}""")
            .toNetworkException(parser)

        assertTrue(exception is ValidationException)
        assertEquals(ApiErrorParser.VALIDATION_MESSAGE, exception.message)
        assertTrue((exception as ValidationException).errors.isEmpty())
    }

    @Test
    fun `409 conflict maps to validation exception so duplicate identity is not swallowed`() {
        val body = """{"message":"Conflict.","errors":{"email":["The email has already been taken."]}}"""

        val exception = httpException(409, body).toNetworkException(parser)

        assertTrue(exception is ValidationException)
        assertEquals(
            "Email sudah terdaftar.",
            (exception as ValidationException).errors["email"]?.first()
        )
    }

    @Test
    fun `401 http exception maps to unauthorized`() {
        val exception = httpException(401, """{"message":"Unauthenticated."}""")
            .toNetworkException(parser)

        assertTrue(exception is UnauthorizedException)
        assertEquals(ApiErrorParser.UNAUTHORIZED_MESSAGE, exception.message)
    }

    @Test
    fun `403 http exception maps to forbidden and keeps the backend message`() {
        val exception = httpException(403, """{"message":"Anda bukan pemilik toko ini."}""")
            .toNetworkException(parser)

        assertTrue(exception is ForbiddenException)
        assertEquals("Anda bukan pemilik toko ini.", exception.message)
    }

    @Test
    fun `500 http exception maps to server exception keeping the status code`() {
        val exception = httpException(500, """{"message":"Registrasi gagal. Silakan coba lagi."}""")
            .toNetworkException(parser)

        assertTrue(exception is ServerException)
        assertEquals(500, (exception as ServerException).statusCode)
    }

    @Test
    fun `http exception without a readable body still maps by status code`() {
        val exception = httpException(422, null).toNetworkException(parser)

        assertTrue(exception is ValidationException)
    }

    // --- Helpers ---

    private fun httpException(code: Int, body: String?): HttpException = HttpException(
        retrofit2.Response.error<Unit>(
            code,
            (body ?: "").toResponseBody("application/json".toMediaType())
        )
    )

    private fun createResponse(code: Int, body: String = "{}"): Response = Response.Builder()
        .request(Request.Builder().url("https://api.test.com").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("HTTP $code")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private fun chainReturning(response: Response): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>(relaxed = true)
        every { chain.proceed(any()) } returns response
        return chain
    }
}
