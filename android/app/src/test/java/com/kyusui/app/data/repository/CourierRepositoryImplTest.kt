package com.kyusui.app.data.repository

import com.kyusui.app.core.ConflictException
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.NotFoundException
import com.kyusui.app.core.ParsingException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.core.ValidationException
import com.kyusui.app.data.api.CourierApi
import com.kyusui.app.data.api.CourierAssignmentDto
import com.kyusui.app.data.api.CourierAssignmentOrderDto
import com.kyusui.app.data.api.CourierAssignmentPaymentDto
import com.kyusui.app.data.api.CourierCashReceiptDto
import com.kyusui.app.data.api.CourierDashboardDto
import com.kyusui.app.data.api.CourierDutySessionDto
import com.kyusui.app.data.api.CourierDutyStateDto
import com.kyusui.app.data.api.CourierLocationAcceptedDto
import com.kyusui.app.data.api.CourierDashboardSummaryDto
import com.kyusui.app.data.api.CourierLocationDto
import com.kyusui.app.data.api.CourierLocationRequestDto
import com.kyusui.app.data.api.EnvelopeDto
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Test repository Courier.
 *
 * Fokus test ini adalah apa yang repository KIRIM dan apa yang repository
 * KEMBALIKAN, memakai endpoint yang benar-benar ada di `backend/routes/api.php`:
 *
 * - `GET courier/assignments` dan `GET courier/assignments/{assignment}` mengembalikan
 *   assignment apa adanya;
 * - aksi `start`, `complete`, `location`, dan `cash/confirm` mengembalikan
 *   Assignment Resource atau receipt dari backend, dan status pada hasil dibaca
 *   dari response itu;
 * - `cash/confirm` dikirim tanpa body apa pun, karena backend yang menentukan
 *   apakah kurir berhak mengonfirmasi;
 * - error HTTP non-2xx dilempar sebagai exception sehingga `403`, `409`, dan `422`
 *   tidak pernah terlihat sebagai aksi yang berhasil.
 *
 * Yang paling penting dari test ini: tidak ada satu pun test yang membuat
 * assignment palsu, payment palsu, atau status `PAID` palsu. Semua fixture adalah
 * response backend.
 */
class CourierRepositoryImplTest {

    private lateinit var api: CourierApi
    private lateinit var repository: CourierRepositoryImpl

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        repository = CourierRepositoryImpl(api)
    }

    // --- GET courier/assignments ---

    @Test
    fun `assignment list is read without any pagination parameter`() = runTest {
        coEvery { api.assignments() } returns EnvelopeDto(
            data = listOf(
                assignmentDto(status = "ASSIGNED"),
                assignmentDto(id = 8, status = "ACTIVE")
            )
        )

        val deliveries = (repository.getAssignments() as Result.Success).data

        assertEquals(2, deliveries.size)
        // Endpoint ini menerima tanpa parameter apa pun. Mengirim halaman hanya
        // akan membuat backend mengabaikan atau menolak request.
        coVerify(exactly = 1) { api.assignments() }
    }

    @Test
    fun `assignment list maps status and payment from the response`() = runTest {
        coEvery { api.assignments() } returns EnvelopeDto(
            data = listOf(
                assignmentDto(
                    status = "ACTIVE",
                    order = orderDto(
                        status = "DALAM_PENGANTARAN",
                        payment = CourierAssignmentPaymentDto(method = "CASH", status = "PENDING")
                    )
                )
            )
        )

        val delivery = (repository.getAssignments() as Result.Success).data.single()

        assertEquals(AssignmentStatus.ACTIVE, delivery.status)
        assertEquals(OrderStatus.DALAM_PENGANTARAN, delivery.order?.status)
        assertEquals(PaymentMethod.CASH, delivery.order?.payment?.paymentMethod)
        assertEquals(PaymentStatus.PENDING, delivery.order?.payment?.paymentStatus)
        assertTrue(delivery.canConfirmCash)
    }

    @Test
    fun `empty assignment list is a success with an empty list`() = runTest {
        // Backend membalas array kosong kalau kurir belum punya penugasan. Itu
        // kondisi bisnis yang sah, bukan error.
        coEvery { api.assignments() } returns EnvelopeDto(data = emptyList())

        val result = repository.getAssignments()

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun `missing data in the list envelope is reported as a failure`() = runTest {
        // Backend tidak mungkin mengirim `data: null` pada list, tapi kalau
        // terjadi Android harus gagal dengan jujur, bukan menganggap daftar kosong
        // sebagai "kurir tidak punya penugasan".
        coEvery { api.assignments() } returns EnvelopeDto(data = null)

        val result = repository.getAssignments()

        assertTrue(result is Result.Failure)
        assertTrue(
            "Respons 2xx tanpa payload harus jadi ParsingException, bukan UnknownException " +
                "atau, lebih buruk, dianggap berhasil.",
            result.exceptionOrNull is ParsingException
        )
    }

    @Test
    fun `interceptor rejection on the list endpoint is reported as a failure`() = runTest {
        // Response non-2xx diteruskan interceptor ke Retrofit, yang melempar
        // `HttpException`. Itulah yang diteruskan ke `safeApiCall` lalu dipetakan ke
        // exception domain.
        coEvery { api.assignments() } throws HttpException(errorResponse<Any>(FORBIDDEN_CODE))

        val result = repository.getAssignments()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is ForbiddenException)
    }

    // --- GET courier/assignments/{assignment} ---

    @Test
    fun `assignment detail is requested with the assignment id`() = runTest {
        coEvery { api.assignment(any()) } returns EnvelopeDto(data = assignmentDto())

        val delivery = (repository.getAssignment("7") as Result.Success).data

        assertEquals("7", delivery.id)
        coVerify(exactly = 1) { api.assignment("7") }
    }

    @Test
    fun `forbidden on someone else's assignment is reported as forbidden`() = runTest {
        // Backend membalas 403 dengan pesan "Anda tidak memiliki akses ke
        // pengantaran ini." bila assignment ini milik kurir lain.
        coEvery { api.assignment(any()) } throws HttpException(
            errorResponse<Any>(FORBIDDEN_CODE, FORBIDDEN_BODY)
        )

        val result = repository.getAssignment("7")

        assertTrue(result is Result.Failure)
        // 403 harus tetap exception yang terpetakan, bukan HttpException mentah,
        // supaya UI bisa membedakan "bukan milikmu" dari "server salah".
        val exception = (result as Result.Failure).exception
        assertFalse(exception is HttpException)
        assertTrue(exception is ForbiddenException)
    }

    @Test
    fun `missing assignment is reported as not found`() = runTest {
        coEvery { api.assignment(any()) } throws HttpException(errorResponse<Any>(NOT_FOUND_CODE))

        val result = repository.getAssignment("7")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is NotFoundException)
    }

    // --- POST courier/assignments/{assignment}/start (DEL-001) ---

    @Test
    fun `start delivery posts to the start action without a body`() = runTest {
        coEvery { api.startDelivery(any()) } returns Response.success(
            EnvelopeDto(
                data = assignmentDto(
                    status = "ACTIVE",
                    startedAt = "2026-01-01T09:00:00.000000Z",
                    order = orderDto(status = "DALAM_PENGANTARAN")
                )
            )
        )

        val delivery = (repository.startDelivery("7") as Result.Success).data

        // Status `ACTIVE` hanya boleh muncul karena backend mengatakannya lewat
        // response. Repository tidak menulisnya sendiri.
        assertEquals(AssignmentStatus.ACTIVE, delivery.status)
        assertEquals("2026-01-01T09:00:00.000000Z", delivery.startedAt)
        coVerify(exactly = 1) { api.startDelivery("7") }
    }

    @Test
    fun `conflict on starting a delivery that is not assigned is a failure`() = runTest {
        // Backend membalas 409 bila status assignment bukan ASSIGNED, order belum
        // DITUGASKAN, atau kurir masih punya assignment ACTIVE lain.
        coEvery { api.startDelivery(any()) } returns errorResponse(CONFLICT_CODE, CONFLICT_BODY)

        val result = repository.startDelivery("7")

        assertTrue(result is Result.Failure)
        val exception = (result as Result.Failure).exception
        assertFalse(exception is HttpException)
        assertTrue(exception is ValidationException)
    }

    @Test
    fun `unauthorized start delivery is reported as unauthorized`() = runTest {
        coEvery { api.startDelivery(any()) } returns errorResponse(UNAUTHORIZED_CODE, ERROR_BODY)

        val result = repository.startDelivery("7")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is UnauthorizedException)
    }

    @Test
    fun `start delivery on a successful response without assignment is a failure`() = runTest {
        // Aksi 2xx tanpa assignment tidak boleh dianggap berhasil, karena kurir
        // tetap perlu tahu status sebenarnya dari server.
        coEvery { api.startDelivery(any()) } returns Response.success(EnvelopeDto(data = null))

        val result = repository.startDelivery("7")

        assertTrue(result is Result.Failure)
        assertTrue(
            "Respons 2xx tanpa payload harus jadi ParsingException, bukan UnknownException " +
                "atau, lebih buruk, dianggap berhasil.",
            result.exceptionOrNull is ParsingException
        )
    }

    // --- POST courier/assignments/{assignment}/location ---

    @Test
    fun `location request carries the fix and a generated idempotency key`() = runTest {
        coEvery { api.updateLocation(any(), any()) } returns Response.success(
            EnvelopeDto(
                data = CourierLocationAcceptedDto(
                    accepted = true,
                    location = CourierLocationDto(
                        id = 55,
                        assignment_id = 7,
                        courier_id = 4,
                        latitude = -0.948,
                        longitude = 100.418,
                        accuracy_meters = 8.5,
                        recorded_at = "2026-01-01T09:30:00.000000Z"
                    )
                )
            )
        )

        val location = (
            repository.updateLocation(
                "7",
                CourierLocationFix(point = GeoPoint(-0.948, 100.418), accuracyMeters = 8.5)
            ) as Result.Success
            ).data

        val requestSlot = slot<CourierLocationRequestDto>()
        coVerify(exactly = 1) { api.updateLocation("7", capture(requestSlot)) }

        assertEquals(-0.948, requestSlot.captured.latitude, 1e-9)
        assertEquals(100.418, requestSlot.captured.longitude, 1e-9)
        assertEquals(8.5, requestSlot.captured.accuracy_meters, 1e-9)

        // `recorded_at` tidak dikirim: backend memakainya untuk menghitung
        // kecepatan perpindahan lokasi, dan jam perangkat bisa tertinggal dari
        // jam server sehingga justru memicu penolakan 422.
        assertEquals(null, requestSlot.captured.recorded_at)

        // Idempotency key dibuat client karena backend menerima field opsional ini
        // untuk mencegah lokasi yang sama tersimpan dua kali.
        val key = requireNotNull(requestSlot.captured.idempotency_key)
        assertTrue(key.isNotBlank())
        assertTrue(
            "Idempotency key harus di bawah batas 64 karakter backend.",
            key.length <= 64
        )

        // Nilai yang dikembalikan adalah lokasi dari server, bukan echo request.
        assertEquals(-0.948, requireNotNull(location.latitude), 1e-9)
        assertEquals("2026-01-01T09:30:00.000000Z", location.recordedAt)
    }

    @Test
    fun `two location requests use different idempotency keys`() = runTest {
        val capturedRequests = mutableListOf<CourierLocationRequestDto>()
        coEvery {
            api.updateLocation("7", capture(capturedRequests))
        } returns Response.success(
            EnvelopeDto(
                data = CourierLocationAcceptedDto(
                    location = CourierLocationDto(latitude = -0.948, longitude = 100.418)
                )
            )
        )

        val fix = CourierLocationFix(point = GeoPoint(-0.948, 100.418), accuracyMeters = 8.5)
        repository.updateLocation("7", fix)
        repository.updateLocation("7", fix)

        coVerify(exactly = 2) { api.updateLocation("7", any()) }

        val keys = capturedRequests.map { request -> requireNotNull(request.idempotency_key) }

        // Dua pengiriman berbeda adalah dua lokasi berbeda, jadi keduanya memakai
        // key berbeda. Key yang sama membuat backend membuang lokasi kedua.
        assertEquals(2, keys.toSet().size)
    }

    @Test
    fun `unprocessable location is reported as a failure`() = runTest {
        // Backend membalas 422 bila jarak versus waktu tidak masuk akal, dengan
        // batas 150 km/jam.
        coEvery { api.updateLocation(any(), any()) } returns errorResponse(
            UNPROCESSABLE_CODE,
            UNPROCESSABLE_BODY
        )

        val result = repository.updateLocation(
            "7",
            CourierLocationFix(point = GeoPoint(-0.948, 100.418), accuracyMeters = 8.5)
        )

        assertTrue(result is Result.Failure)
        val exception = (result as Result.Failure).exception
        assertFalse(exception is HttpException)
        assertTrue(exception is ValidationException)
    }

    @Test
    fun `conflict when the delivery is not active is reported as a failure`() = runTest {
        // Backend membalas 409 bila assignment belum ACTIVE atau order belum
        // DALAM_PENGANTARAN. Pada endpoint lokasi, 409 selalu berarti delivery
        // sudah tidak aktif, jadi pemetaannya ditangani endpoint ini secara
        // khusus (ConflictException) supaya pengirim posisi bisa berhenti.
        // Endpoint lain tetap memakai pemetaan global 409 -> ValidationException.
        coEvery { api.updateLocation(any(), any()) } returns errorResponse(CONFLICT_CODE)

        val result = repository.updateLocation(
            "7",
            CourierLocationFix(point = GeoPoint(-0.948, 100.418), accuracyMeters = 8.5)
        )

        assertTrue(result is Result.Failure)
        val exception = (result as Result.Failure).exception
        assertFalse(exception is HttpException)
        assertTrue(exception is ConflictException)
    }

    @Test
    fun `location response without coordinates is a failure`() = runTest {
        // Backend tidak mungkin mengirim lokasi tanpa koordinat, tapi kalau
        // terjadi Android tidak boleh menampilkan titik asal tanpa koordinat.
        coEvery { api.updateLocation(any(), any()) } returns Response.success(
            EnvelopeDto(
                data = CourierLocationAcceptedDto(
                    location = CourierLocationDto(id = 55, recorded_at = null)
                )
            )
        )

        val result = repository.updateLocation(
            "7",
            CourierLocationFix(point = GeoPoint(-0.948, 100.418), accuracyMeters = 8.5)
        )

        assertTrue(result is Result.Failure)
        assertTrue(
            "Respons 2xx tanpa payload harus jadi ParsingException, bukan UnknownException " +
                "atau, lebih buruk, dianggap berhasil.",
            result.exceptionOrNull is ParsingException
        )
    }

    // --- POST courier/assignments/{assignment}/cash/confirm ---

    @Test
    fun `cash confirmation sends only the assignment id and nothing else`() = runTest {
        coEvery { api.confirmCashReceived(any()) } returns Response.success(
            EnvelopeDto(
                data = CourierCashReceiptDto(
                    id = 9001,
                    payment_method = "CASH",
                    payment_status = "PAID",
                    verified_at = "2026-01-01T10:00:00.000000Z"
                )
            )
        )

        val receipt = (repository.confirmCashReceived("7") as Result.Success).data

        // Konfirmasi tunai hanya membawa aksi. Tidak ada nominal, tidak ada id
        // kurir, tidak ada id order, tidak ada bukti. Backend yang memeriksa
        // semuanya.
        coVerify(exactly = 1) { api.confirmCashReceived("7") }

        assertEquals("9001", receipt.paymentId)
        assertEquals(PaymentMethod.CASH, receipt.paymentMethod)
        // `PAID` di sini adalah nilai dari response backend, bukan nilai yang
        // ditulis Android.
        assertEquals(PaymentStatus.PAID, receipt.paymentStatus)
        assertEquals("2026-01-01T10:00:00.000000Z", receipt.verifiedAt)
    }

    @Test
    fun `cash confirmation on a conflict is a failure and not a receipt`() = runTest {
        // Backend membalas 409 bila delivery belum aktif, metode pembayaran bukan
        // CASH, atau pembayaran sudah lunas.
        coEvery { api.confirmCashReceived(any()) } returns errorResponse(
            CONFLICT_CODE,
            CONFLICT_BODY
        )

        val result = repository.confirmCashReceived("7")

        assertTrue(result is Result.Failure)
        val exception = (result as Result.Failure).exception
        assertFalse(exception is HttpException)
        assertTrue(exception is ValidationException)
    }

    @Test
    fun `cash confirmation for an unauthorized courier is reported as forbidden`() = runTest {
        coEvery { api.confirmCashReceived(any()) } returns errorResponse(
            FORBIDDEN_CODE,
            FORBIDDEN_BODY
        )

        val result = repository.confirmCashReceived("7")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is ForbiddenException)
    }

    @Test
    fun `cash confirmation with missing receipt data is a failure`() = runTest {
        // Android tidak pernah menganggap aksi sukses berarti pembayaran sudah
        // lunas. Tanpa receipt dari server, hasilnya adalah kegagalan.
        coEvery { api.confirmCashReceived(any()) } returns Response.success(
            EnvelopeDto(data = null)
        )

        val result = repository.confirmCashReceived("7")

        assertTrue(result is Result.Failure)
        assertTrue(
            "Respons 2xx tanpa payload harus jadi ParsingException, bukan UnknownException " +
                "atau, lebih buruk, dianggap berhasil.",
            result.exceptionOrNull is ParsingException
        )
    }

    @Test
    fun `cash confirmation keeps a non paid status from the server`() = runTest {
        // Bila backend membalas 2xx dengan status selain PAID, Android tidak boleh
        // menafsirkannya menjadi lunas.
        coEvery { api.confirmCashReceived(any()) } returns Response.success(
            EnvelopeDto(
                data = CourierCashReceiptDto(
                    id = 9001,
                    payment_method = "CASH",
                    payment_status = "PENDING",
                    verified_at = null
                )
            )
        )

        val receipt = (repository.confirmCashReceived("7") as Result.Success).data

        assertEquals(PaymentStatus.PENDING, receipt.paymentStatus)
    }

    // --- POST courier/assignments/{assignment}/complete (DEL-002) ---

    @Test
    fun `complete delivery reads the finished assignment from the response`() = runTest {
        coEvery { api.completeDelivery(any()) } returns Response.success(
            EnvelopeDto(
                data = assignmentDto(
                    status = "COMPLETED",
                    completedAt = "2026-01-01T11:00:00.000000Z",
                    order = orderDto(
                        status = "SELESAI",
                        payment = CourierAssignmentPaymentDto(method = "CASH", status = "PAID")
                    )
                )
            )
        )

        val delivery = (repository.completeDelivery("7") as Result.Success).data

        assertEquals(AssignmentStatus.COMPLETED, delivery.status)
        assertEquals(OrderStatus.SELESAI, delivery.order?.status)
        assertTrue(delivery.isFinished)
        coVerify(exactly = 1) { api.completeDelivery("7") }
    }

    @Test
    fun `conflict on completing a delivery whose payment is not paid is a failure`() = runTest {
        // Backend membalas 409 bila delivery belum ACTIVE atau payment belum PAID.
        coEvery { api.completeDelivery(any()) } returns errorResponse(CONFLICT_CODE, CONFLICT_BODY)

        val result = repository.completeDelivery("7")

        assertTrue(result is Result.Failure)
        val exception = (result as Result.Failure).exception
        assertFalse(exception is HttpException)
        assertTrue(exception is ValidationException)
    }

    @Test
    fun `forbidden on completing someone else's delivery is reported as forbidden`() = runTest {
        coEvery { api.completeDelivery(any()) } returns errorResponse(FORBIDDEN_CODE, FORBIDDEN_BODY)

        val result = repository.completeDelivery("7")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is ForbiddenException)
    }

    @Test
    fun `successful complete response without assignment data is a failure`() = runTest {
        coEvery { api.completeDelivery(any()) } returns Response.success(EnvelopeDto(data = null))

        val result = repository.completeDelivery("7")

        assertTrue(result is Result.Failure)
        assertTrue(
            "Respons 2xx tanpa payload harus jadi ParsingException, bukan UnknownException " +
                "atau, lebih buruk, dianggap berhasil.",
            result.exceptionOrNull is ParsingException
        )
    }

    // --- GET dashboard/courier ---

    @Test
    fun `dashboard is requested once and mapped from the response`() = runTest {
        coEvery { api.dashboard() } returns EnvelopeDto(
            data = CourierDashboardDto(
                summary = CourierDashboardSummaryDto(
                    today_deliveries = 2,
                    month_deliveries = 37,
                    completed_deliveries = 35,
                    completion_rate = 94.59
                ),
                unread_notifications = 3
            )
        )

        val dashboard = (repository.getDashboard() as Result.Success).data

        coVerify(exactly = 1) { api.dashboard() }
        assertEquals(2, dashboard.summary.todayDeliveries)
        assertEquals(37, dashboard.summary.monthDeliveries)
        assertEquals(35, dashboard.summary.completedDeliveries)
        assertEquals(94.59, dashboard.summary.completionRate, 1e-9)
        assertEquals(3, dashboard.unreadNotifications)
        assertTrue(dashboard.activeDelivery == null)
    }

    @Test
    fun `dashboard failure is reported as a failure`() = runTest {
        coEvery { api.dashboard() } throws HttpException(
            errorResponse<Any>(UNAUTHORIZED_CODE, ERROR_BODY)
        )

        val result = repository.getDashboard()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is UnauthorizedException)
    }

    // --- Sesi bertugas (courier/duty) ---

    @Test
    fun `duty state is read from the server including the latest location`() = runTest {
        coEvery { api.dutyState() } returns EnvelopeDto(
            data = CourierDutyStateDto(
                active = true,
                session = CourierDutySessionDto(
                    id = 5,
                    started_at = "2026-01-01T07:00:00.000000Z",
                    ended_at = null,
                    is_active = true
                ),
                latest_location = CourierLocationDto(
                    id = 88,
                    latitude = -0.948,
                    longitude = 100.418,
                    accuracy_meters = 6.0,
                    recorded_at = "2026-01-01T07:05:00.000000Z"
                )
            )
        )

        val duty = (repository.getDutyState() as Result.Success).data

        coVerify(exactly = 1) { api.dutyState() }
        assertTrue(duty.isActive)
        assertEquals("5", duty.session?.id)
        assertEquals("2026-01-01T07:00:00.000000Z", duty.session?.startedAt)
        assertEquals(-0.948, requireNotNull(duty.latestLocation?.latitude), 1e-9)
    }

    @Test
    fun `start duty posts to the server and maps the returned session`() = runTest {
        coEvery { api.startDuty() } returns Response.success(
            EnvelopeDto(
                data = CourierDutySessionDto(
                    id = 9,
                    started_at = "2026-01-01T07:00:00.000000Z",
                    ended_at = null,
                    is_active = true
                )
            )
        )

        val session = (repository.startDuty() as Result.Success).data

        coVerify(exactly = 1) { api.startDuty() }
        assertEquals("9", session.id)
        assertTrue(session.isActive)
    }

    @Test
    fun `stop duty posts to the server and maps the ended session`() = runTest {
        coEvery { api.stopDuty() } returns Response.success(
            EnvelopeDto(
                data = CourierDutySessionDto(
                    id = 9,
                    started_at = "2026-01-01T07:00:00.000000Z",
                    ended_at = "2026-01-01T09:00:00.000000Z",
                    is_active = false
                )
            )
        )

        val session = (repository.stopDuty() as Result.Success).data

        coVerify(exactly = 1) { api.stopDuty() }
        assertEquals("2026-01-01T09:00:00.000000Z", session.endedAt)
        assertFalse(session.isActive)
    }

    @Test
    fun `start duty without session data is a failure`() = runTest {
        coEvery { api.startDuty() } returns Response.success(EnvelopeDto(data = null))

        val result = repository.startDuty()

        assertTrue(result is Result.Failure)
        assertTrue(result.exceptionOrNull is ParsingException)
    }

    @Test
    fun `duty location carries the fix without an idempotency key`() = runTest {
        coEvery { api.sendDutyLocation(any()) } returns Response.success(
            EnvelopeDto(
                data = CourierLocationAcceptedDto(
                    accepted = true,
                    location = CourierLocationDto(
                        id = 91,
                        latitude = -0.948,
                        longitude = 100.418,
                        accuracy_meters = 8.5,
                        recorded_at = "2026-01-01T07:05:00.000000Z"
                    )
                )
            )
        )

        val location = (
            repository.sendDutyLocation(
                CourierLocationFix(point = GeoPoint(-0.948, 100.418), accuracyMeters = 8.5)
            ) as Result.Success
            ).data

        val requestSlot = slot<CourierLocationRequestDto>()
        coVerify(exactly = 1) { api.sendDutyLocation(capture(requestSlot)) }

        assertEquals(-0.948, requestSlot.captured.latitude, 1e-9)
        assertEquals(100.418, requestSlot.captured.longitude, 1e-9)
        assertEquals(8.5, requestSlot.captured.accuracy_meters, 1e-9)
        // Tabel `courier_duty_locations` tidak punya kolom idempotency di
        // backend, jadi tidak ada yang dikirim.
        assertNull(requestSlot.captured.recorded_at)
        assertNull(requestSlot.captured.idempotency_key)
        assertEquals(-0.948, requireNotNull(location.latitude), 1e-9)
    }

    @Test
    fun `conflict on duty location is reported as a conflict so the sender stops`() = runTest {
        // 409 berarti tidak ada sesi aktif atau sesi yang dipegang klien sudah
        // berakhir. Mengirim ulang tidak akan berhasil.
        coEvery { api.sendDutyLocation(any()) } returns errorResponse(
            CONFLICT_CODE,
            CONFLICT_BODY
        )

        val result = repository.sendDutyLocation(
            CourierLocationFix(point = GeoPoint(-0.948, 100.418), accuracyMeters = 8.5)
        )

        assertTrue(result is Result.Failure)
        val exception = (result as Result.Failure).exception
        assertFalse(exception is HttpException)
        assertTrue(exception is ConflictException)
    }

    @Test
    fun `duty location without a location payload is a failure`() = runTest {
        coEvery { api.sendDutyLocation(any()) } returns Response.success(
            EnvelopeDto(data = CourierLocationAcceptedDto(location = null))
        )

        val result = repository.sendDutyLocation(
            CourierLocationFix(point = GeoPoint(-0.948, 100.418), accuracyMeters = 8.5)
        )

        assertTrue(result is Result.Failure)
        assertTrue(result.exceptionOrNull is ParsingException)
    }

    // --- Helper ---

    private fun <T> errorResponse(
        code: Int,
        body: String = ERROR_BODY
    ): Response<T> = Response.error(code, body.toResponseBody(JSON_MEDIA_TYPE.toMediaType()))

    private fun assignmentDto(
        id: Long = 7,
        status: String = "ASSIGNED",
        assignedAt: String? = "2026-01-01T08:00:00.000000Z",
        startedAt: String? = null,
        completedAt: String? = null,
        order: CourierAssignmentOrderDto = orderDto()
    ) = CourierAssignmentDto(
        id = id,
        status = status,
        assigned_at = assignedAt,
        started_at = startedAt,
        completed_at = completedAt,
        order = order,
        latest_location = null
    )

    private fun orderDto(
        status: String = "DITUGASKAN",
        payment: CourierAssignmentPaymentDto = CourierAssignmentPaymentDto(
            method = "CASH",
            status = "PENDING"
        )
    ) = CourierAssignmentOrderDto(
        id = 21,
        status = status,
        subtotal = "16000.00",
        total = "21000.00",
        delivery_address = "Jl. Merdeka 1",
        customer = null,
        items = emptyList(),
        payment = payment
    )

    private companion object {
        const val UNAUTHORIZED_CODE = 401
        const val FORBIDDEN_CODE = 403
        const val NOT_FOUND_CODE = 404
        const val CONFLICT_CODE = 409
        const val UNPROCESSABLE_CODE = 422

        const val JSON_MEDIA_TYPE = "application/json"
        const val ERROR_BODY = """{"message":"Permintaan ditolak."}"""
        const val FORBIDDEN_BODY =
            """{"message":"Anda tidak memiliki akses ke pengantaran ini."}"""
        const val CONFLICT_BODY =
            """{"message":"Pembayaran tunai hanya bisa dikonfirmasi saat pesanan sedang diantar."}"""
        const val UNPROCESSABLE_BODY =
            """{"message":"Perpindahan lokasi tidak masuk akal."}"""
    }
}
