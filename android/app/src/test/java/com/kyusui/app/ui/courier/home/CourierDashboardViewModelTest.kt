package com.kyusui.app.ui.courier.home

import com.kyusui.app.core.ConflictException
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierDashboard
import com.kyusui.app.domain.model.CourierDashboardOrder
import com.kyusui.app.domain.model.CourierDashboardSummary
import com.kyusui.app.domain.model.CourierDeliveryPayment
import com.kyusui.app.domain.model.CourierDutySession
import com.kyusui.app.domain.model.CourierDutyState
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.repository.CourierRepository
import com.kyusui.app.ui.courier.tracking.FakeCourierLocationSharing
import com.kyusui.app.ui.courier.tracking.TrackingStopReason
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test beranda kurir dari `GET dashboard/courier`.
 *
 * Beranda tidak punya aksi tulis apa pun, jadi test ini difokuskan pada dua hal
 * yang paling mudah rusak:
 *
 * 1. Angka ringkasan disalin apa adanya dari backend. Android tidak menghitung
 *    ulang, dan tidak memakai nol sebagai pengganti nilai yang gagal dimuat.
 * 2. Kegagalan muat ulang pada data yang sudah tampil TIDAK menghapus isi layar.
 *    Angkanya ditulis ke `refreshErrorMessage` supaya kurir tidak kehilangan data
 *    yang masih berguna hanya karena satu request gagal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CourierDashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: CourierRepository
    private lateinit var sharing: FakeCourierLocationSharing

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        sharing = FakeCourierLocationSharing()
        // Default supaya test yang tidak menguji sesi bertugas tetap punya
        // jawaban server yang sah (bukan mock Result yang tidak cocok `when`).
        coEvery { repository.getDashboard() } returns Result.Success(dashboard())
        coEvery { repository.getDutyState() } returns Result.Success(CourierDutyState())
        coEvery { repository.getActiveAssignmentIds() } returns Result.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = CourierDashboardViewModel(repository, sharing)

    @Test
    fun `dashboard summary is read from the server response`() = runTest(dispatcher) {
        coEvery { repository.getDashboard() } returns Result.Success(dashboard())

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.dashboardState is LoadState.Success)
        assertEquals(2, state.dashboard?.summary?.todayDeliveries)
        assertEquals(37, state.dashboard?.summary?.monthDeliveries)
        assertEquals(35, state.dashboard?.summary?.completedDeliveries)
        assertEquals(94.59, state.dashboard?.summary?.completionRate ?: 0.0, 1e-9)
        assertEquals(3, state.dashboard?.unreadNotifications)
        assertFalse(state.isRefreshing)
        assertNull(state.refreshErrorMessage)
        coVerify(exactly = 1) { repository.getDashboard() }
    }

    @Test
    fun `initial failure becomes a load error instead of a zero dashboard`() =
        runTest(dispatcher) {
            // Angka nol berarti "kurir tidak pernah mengantar". Kegagalan jaringan
            // tidak boleh terlihat seperti itu.
            coEvery { repository.getDashboard() } returns Result.Failure(
                UnauthorizedException("Token tidak valid.")
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.dashboardState is LoadState.Error)
            assertNull(state.dashboard)
            assertFalse((state.dashboardState as LoadState.Error).isRetryable)
            assertFalse(state.hasActiveDelivery)
        }

    @Test
    fun `refresh failure keeps the data that is already on screen`() = runTest(dispatcher) {
        coEvery { repository.getDashboard() } returnsMany listOf(
            Result.Success(dashboard()),
            Result.Failure(ForbiddenException("Akses ditolak."))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadDashboard(force = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Isi lama tetap ada.
        assertTrue(state.dashboardState is LoadState.Success)
        assertEquals(2, state.dashboard?.summary?.todayDeliveries)
        // Dan pesannya terpisah, bukan menggantikan isi layar.
        // `userMessage()` memetakan `ForbiddenException` ke kalimat aplikasi
        // sendiri, bukan meneruskan pesan mentah dari backend. Layar harus
        // menampilkan kalimat itu, karena pesan backend tidak selalu Bahasa
        // Indonesia dan tidak selalu menyebut apa yang terjadi.
        assertEquals("Akses ditolak", state.refreshErrorMessage)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `successful refresh clears the previous refresh error`() = runTest(dispatcher) {
        coEvery { repository.getDashboard() } returnsMany listOf(
            Result.Success(dashboard()),
            Result.Failure(ForbiddenException("Akses ditolak.")),
            Result.Success(dashboard())
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadDashboard(force = true)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.refreshErrorMessage)

        viewModel.loadDashboard(force = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.refreshErrorMessage)
        assertTrue(viewModel.uiState.value.dashboardState is LoadState.Success)
    }

    @Test
    fun `refresh while a request is running is ignored`() = runTest(dispatcher) {
        coEvery { repository.getDashboard() } returns Result.Success(dashboard())

        val viewModel = createViewModel()
        // Belum ada `advanceUntilIdle()`, jadi request pertama masih berjalan dan
        // `isRefreshing` masih true.
        viewModel.loadDashboard(force = true)
        viewModel.loadDashboard(force = true)
        advanceUntilIdle()

        // Dua request berarti backend bisa dipanggil dua kali untuk aksi yang
        // sama dan layar berkedip dua kali.
        coVerify(exactly = 1) { repository.getDashboard() }
    }

    @Test
    fun `refresh failure without previous data becomes a load error`() = runTest(dispatcher) {
        // Tanpa data yang tampil, tidak ada yang bisa dipertahankan, jadi
        // kegagalan harus menjadi layar error penuh.
        coEvery { repository.getDashboard() } returnsMany listOf(
            Result.Failure(ForbiddenException("Akses ditolak.")),
            Result.Failure(ForbiddenException("Akses ditolak."))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadDashboard(force = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.dashboardState is LoadState.Error)
        assertNull(state.refreshErrorMessage)
    }

    @Test
    fun `dashboard order exposes the assignment id needed to open the detail`() =
        runTest(dispatcher) {
            // Id di dashboard adalah id ORDER, sedangkan endpoint detail kurir
            // bekerja pada id ASSIGNMENT. Layar memakai referensi assignment.
            coEvery { repository.getDashboard() } returns Result.Success(dashboard())

            val viewModel = createViewModel()
            advanceUntilIdle()

            val active = requireNotNull(viewModel.uiState.value.dashboard?.activeDelivery)
            assertEquals("21", active.orderId)
            assertEquals("7", active.assignmentId)
            assertEquals(AssignmentStatus.ACTIVE, active.assignmentStatus)
            assertTrue(active.isDeliverable)
            assertTrue(viewModel.uiState.value.hasActiveDelivery)

            val today = requireNotNull(viewModel.uiState.value.dashboard).todayDeliveries.single()
            assertEquals("22", today.orderId)
            assertEquals("8", today.assignmentId)
            assertEquals(PaymentMethod.QRIS, today.payment?.paymentMethod)
            assertEquals(
                PaymentStatus.WAITING_VERIFICATION,
                today.payment?.paymentStatus
            )
        }

    @Test
    fun `dashboard without summary reports zeroes instead of failing`() = runTest(dispatcher) {
        coEvery { repository.getDashboard() } returns Result.Success(
            CourierDashboard(summary = CourierDashboardSummary(), unreadNotifications = 0)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.dashboardState is LoadState.Success)
        assertEquals(0, state.dashboard?.summary?.todayDeliveries)
        assertNull(state.dashboard?.activeDelivery)
        assertTrue(state.dashboard?.todayDeliveries?.isEmpty() == true)
    }

    @Test
    fun `today order without assignment is marked as not deliverable`() = runTest(dispatcher) {
        // Order tanpa assignment bukan tanggung jawab kurir dan tidak punya
        // endpoint yang bisa dipanggil untuk membukanya.
        coEvery { repository.getDashboard() } returns Result.Success(
            CourierDashboard(
                todayDeliveries = listOf(dashboardOrder(id = 22, assignmentId = null)),
                summary = CourierDashboardSummary(todayDeliveries = 1)
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val today = requireNotNull(viewModel.uiState.value.dashboard).todayDeliveries.single()
        assertNull(today.assignmentId)
        assertFalse(today.isDeliverable)
    }

    @Test
    fun `order status unknown does not crash the dashboard`() = runTest(dispatcher) {
        coEvery { repository.getDashboard() } returns Result.Success(
            CourierDashboard(
                todayDeliveries = listOf(dashboardOrder(id = 22, assignmentId = 8, status = null)),
                summary = CourierDashboardSummary(todayDeliveries = 1)
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val today = requireNotNull(viewModel.uiState.value.dashboard).todayDeliveries.single()
        assertNull(today.status)
        // Baris tanpa order status masih bisa dibuka lewat assignment-nya.
        assertTrue(today.isDeliverable)
    }

    @Test
    fun `summary count and list length are kept separate because the backend caps the list`() =
        runTest(dispatcher) {
            // `DashboardController::courier()` menghitung `summary.today_deliveries`
            // tanpa batas, tetapi `today_assignments` memakai `->limit(20)`. Kalau
            // Android menyamakan keduanya, kurir akan mengira daftar yang tampil
            // sudah lengkap padahal tidak.
            coEvery { repository.getDashboard() } returns Result.Success(
                CourierDashboard(
                    todayDeliveries = listOf(dashboardOrder(id = 22, assignmentId = 8)),
                    summary = CourierDashboardSummary(todayDeliveries = 27),
                    unreadNotifications = 0
                )
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            val dashboard = requireNotNull(viewModel.uiState.value.dashboard)
            // Angka summary bukan turunan dari panjang daftar.
            assertEquals(27, dashboard.summary.todayDeliveries)
            assertEquals(1, dashboard.todayDeliveries.size)
        }

    @Test
    fun `a long today list is not silently reduced to the summary count`() = runTest(dispatcher) {
        // Kebalikannya juga harus berlaku: daftar boleh lebih panjang dari angka
        // summary kalau backendmemberi data seperti itu. Android tidak pernah
        // memotong daftar sendiri.
        val rows = (1..25).map { dashboardOrder(id = 100L + it, assignmentId = it.toLong()) }
        coEvery { repository.getDashboard() } returns Result.Success(
            CourierDashboard(
                todayDeliveries = rows,
                summary = CourierDashboardSummary(todayDeliveries = 3),
                unreadNotifications = 0
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val dashboard = requireNotNull(viewModel.uiState.value.dashboard)
        assertEquals(25, dashboard.todayDeliveries.size)
        assertEquals(3, dashboard.summary.todayDeliveries)
    }

    @Test
    fun `dashboard order helper keeps the backend field names`() {
        // `DashboardOrderResource` memakai `subtotal_amount` dan `total_amount`,
        // bukan `subtotal` dan `total` seperti Assignment Resource.
        val order = dashboardOrder(id = 21, assignmentId = 7)

        assertEquals("16000.00", order.subtotalAmount)
        assertEquals("21000.00", order.totalAmount)
        assertEquals(OrderStatus.DALAM_PENGANTARAN, order.status)
    }

    @Test
    fun `an active duty session on the server is reflected and resumes sharing`() =
        runTest(dispatcher) {
            coEvery { repository.getDashboard() } returns Result.Success(dashboard().copy(activeDelivery = null, activeDeliveries = emptyList()))
        coEvery { repository.getDutyState() } returns Result.Success(
                CourierDutyState(
                    isActive = true,
                    session = CourierDutySession(id = "5", isActive = true)
                )
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isOnDuty)
            // Server sudah menyatakan bertugas, jadi pengiriman dilanjutkan tanpa
            // kurir menekan tombol lagi.
            assertEquals(1, sharing.dutyStartCount)
            assertTrue(sharing.state.value.isSharing)
        }

    @Test
    fun `starting duty posts to the server then starts sharing`() = runTest(dispatcher) {
        coEvery { repository.getDashboard() } returns Result.Success(dashboard().copy(activeDelivery = null, activeDeliveries = emptyList()))
        coEvery { repository.getDutyState() } returnsMany listOf(
            Result.Success(CourierDutyState()),
            Result.Success(CourierDutyState(isActive = true))
        )
        coEvery { repository.startDuty() } returns Result.Success(
            CourierDutySession(id = "9", isActive = true)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(0, sharing.dutyStartCount)

        viewModel.startDuty()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.startDuty() }
        assertEquals(1, sharing.dutyStartCount)
        assertTrue(viewModel.uiState.value.isOnDuty)
        assertFalse(viewModel.uiState.value.isDutyActionInProgress)
    }

    @Test
    fun `starting duty that conflicts surfaces the message and never starts sharing`() =
        runTest(dispatcher) {
            coEvery { repository.startDuty() } returns Result.Failure(
                ConflictException("Sesi bertugas sudah aktif.")
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.startDuty()
            advanceUntilIdle()

            // Tanpa sesi server yang sah, pengiriman tidak boleh dimulai.
            assertEquals(0, sharing.dutyStartCount)
            assertFalse(viewModel.uiState.value.isOnDuty)
            assertNotNull(viewModel.uiState.value.dutyMessage)
            assertFalse(viewModel.uiState.value.isDutyActionInProgress)
        }

    @Test
    fun `stopping duty posts to the server and stops sharing`() = runTest(dispatcher) {
        coEvery { repository.getDashboard() } returns Result.Success(dashboard().copy(activeDelivery = null, activeDeliveries = emptyList()))
        coEvery { repository.getDutyState() } returns Result.Success(
            CourierDutyState(
                isActive = true,
                session = CourierDutySession(id = "5", isActive = true)
            )
        )
        coEvery { repository.stopDuty() } returns Result.Success(
            CourierDutySession(id = "5", isActive = false, endedAt = "2026-01-01T00:00:00Z")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(sharing.state.value.isSharing)

        coEvery { repository.getDutyState() } returns Result.Success(CourierDutyState())
        viewModel.stopDuty()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.stopDuty() }
        assertEquals(listOf(TrackingStopReason.USER_REQUESTED), sharing.stoppedReasons)
        assertFalse(viewModel.uiState.value.isOnDuty)
    }

    // --- Helper ---

    private fun dashboard() = CourierDashboard(
        activeDelivery = dashboardOrder(id = 21, assignmentId = 7),
        // Baris hari ini sengaja memakai QRIS yang masih diverifikasi, supaya
        // test ini juga membuktikan bahwa metode pembayaran lain tidak tertukar
        // dengan CASH saat dipetakan.
        todayDeliveries = listOf(
            dashboardOrder(
                id = 22,
                assignmentId = 8,
                paymentMethod = PaymentMethod.QRIS,
                paymentStatus = PaymentStatus.WAITING_VERIFICATION
            )
        ),
        summary = CourierDashboardSummary(
            todayDeliveries = 2,
            monthDeliveries = 37,
            completedDeliveries = 35,
            completionRate = 94.59
        ),
        unreadNotifications = 3
    )

    private fun dashboardOrder(
        id: Long,
        assignmentId: Long?,
        status: OrderStatus? = OrderStatus.DALAM_PENGANTARAN,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        paymentStatus: PaymentStatus = PaymentStatus.PENDING
    ) = CourierDashboardOrder(
        orderId = id.toString(),
        status = status,
        subtotalAmount = "16000.00",
        totalAmount = "21000.00",
        deliveryAddress = "Jl. Merdeka 1",
        payment = CourierDeliveryPayment(
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus
        ),
        assignmentId = assignmentId?.toString(),
        assignmentStatus = assignmentId?.let { AssignmentStatus.ACTIVE }
    )
}
