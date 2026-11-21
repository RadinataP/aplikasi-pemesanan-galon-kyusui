package com.kyusui.app.ui.courier.delivery

import androidx.lifecycle.SavedStateHandle
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.NotFoundException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.core.ValidationException
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeliveryOrder
import com.kyusui.app.domain.model.CourierDeliveryPayment
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.repository.CourierRepository
import com.kyusui.app.ui.courier.tracking.FakeCourierLocationSharing
import com.kyusui.app.ui.courier.tracking.TrackingStopReason
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.SubmitState
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
 * Test detail pengantaran: DEL-001 (mulai) dan DEL-002 (selesai).
 *
 * Padanan Android dari `backend/tests/Feature/CourierApiTest.php`:
 *
 * - DEL-001 kurir yang benar-benar ditugaskan memulai pengantaran, dan status
 *   `ACTIVE` pada layar hanya muncul karena backend mengirimkannya;
 * - DEL-001 ditolak dengan `409` bila assignment belum `ASSIGNED`, order belum
 *   `DITUGASKAN`, atau kurir masih mengantar yang lain. Dua syarat terakhir tidak
 *   bisa dicek dari Assignment Resource, jadi Android tidak mengarang bisa;
 * - DEL-002 pengantaran selesai setelah pembayaran `PAID`, dan status `COMPLETED`
 *   dibaca dari response backend;
 * - DEL-002 ditolak dengan `409` bila delivery belum aktif atau pembayaran belum
 *   lunas.
 *
 * Batas yang dijaga bersama di test ini: tidak ada penugasan palsu, tidak ada
    // --- Aksi yang saling independen ---
    // --- Aksi yang saling independen ---
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: CourierRepository
    private lateinit var sharing: FakeCourierLocationSharing

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        sharing = FakeCourierLocationSharing()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- DEL-001: mulai pengantaran ---

    @Test
    fun `DEL-001 assigned courier starts the delivery and shows the server result`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Success(
                delivery(status = AssignmentStatus.ASSIGNED, orderStatus = OrderStatus.DITUGASKAN)
            )
            coEvery { repository.startDelivery("7") } returns Result.Success(
                delivery(
                    status = AssignmentStatus.ACTIVE,
                    orderStatus = OrderStatus.DALAM_PENGANTARAN,
                    startedAt = "2026-01-01T09:00:00.000000Z"
                )
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            assertTrue(requireNotNull(viewModel.uiState.value.delivery).canStartDelivery)

            viewModel.startDelivery()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.startDelivery("7") }

            val state = viewModel.uiState.value
            assertTrue(state.startState is SubmitState.Success)
            // Status `ACTIVE` berasal dari response backend, bukan ditulis Android.
            assertEquals(AssignmentStatus.ACTIVE, state.delivery?.status)
            assertEquals(OrderStatus.DALAM_PENGANTARAN, state.delivery?.order?.status)
            assertEquals("2026-01-01T09:00:00.000000Z", state.delivery?.startedAt)
            assertEquals(DeliveryDetailViewModel.START_SUCCESS_MESSAGE, state.actionMessage)
    // --- Aksi yang saling independen ---
            assertFalse(requireNotNull(state.delivery).canStartDelivery)
        }

    @Test
    fun `DEL-001 second press while submitting does not start the delivery twice`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Success(
                delivery(status = AssignmentStatus.ASSIGNED, orderStatus = OrderStatus.DITUGASKAN)
            )
            coEvery { repository.startDelivery("7") } returns Result.Success(
                delivery(status = AssignmentStatus.ACTIVE, orderStatus = OrderStatus.DALAM_PENGANTARAN)
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            viewModel.startDelivery()
            viewModel.startDelivery()
            viewModel.startDelivery()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.startDelivery("7") }
        }

    @Test
    fun `DEL-001 conflict from the backend is shown and changes nothing`() = runTest(dispatcher) {
        // Backend membalas 409 antara lain bila kurir masih punya assignment
        // ACTIVE lain. Syarat itu tidak ada di Assignment Resource, jadi Android
        // hanya menampilkan penolakan itu apa adanya.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ASSIGNED, orderStatus = OrderStatus.DITUGASKAN)
        )
        coEvery { repository.startDelivery("7") } returns Result.Failure(
            ValidationException(message = "You already have an active delivery.")
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()
        viewModel.startDelivery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.startState is SubmitState.Error)
        assertEquals("You already have an active delivery.", state.startErrorMessage)
    // --- Aksi yang saling independen ---
        assertEquals(AssignmentStatus.ASSIGNED, state.delivery?.status)
        assertTrue(requireNotNull(state.delivery).canStartDelivery)
        assertNull(state.actionMessage)
    }

    @Test
    fun `DEL-001 start is not sent when the assignment is already active`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ACTIVE, orderStatus = OrderStatus.DALAM_PENGANTARAN)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        assertFalse(requireNotNull(viewModel.uiState.value.delivery).canStartDelivery)
        viewModel.startDelivery()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.startDelivery(any()) }
    }

    @Test
    fun `DEL-001 start is not sent when the assignment status is unknown`() = runTest(dispatcher) {
        // Status yang tidak dikenal tidak boleh diperlakukan sebagai ASSIGNED.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = null, orderStatus = OrderStatus.DITUGASKAN)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        assertFalse(requireNotNull(viewModel.uiState.value.delivery).canStartDelivery)
        viewModel.startDelivery()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.startDelivery(any()) }
    }

    @Test
    fun `DEL-001 forbidden courier never reaches the start action`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Failure(
            ForbiddenException("Anda tidak memiliki akses ke pengantaran ini.")
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.deliveryState is LoadState.Error)
        assertFalse((state.deliveryState as LoadState.Error).isRetryable)
        viewModel.startDelivery()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.startDelivery(any()) }
    }

    // --- DEL-002: selesaikan pengantaran ---

    @Test
    fun `DEL-002 active delivery with paid payment is completed from the server result`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Success(
                delivery(
                    status = AssignmentStatus.ACTIVE,
                    orderStatus = OrderStatus.DALAM_PENGANTARAN,
                    paymentStatus = PaymentStatus.PAID
                )
            )
            coEvery { repository.completeDelivery("7") } returns Result.Success(
                delivery(
                    status = AssignmentStatus.COMPLETED,
                    orderStatus = OrderStatus.SELESAI,
                    paymentStatus = PaymentStatus.PAID,
                    completedAt = "2026-01-01T11:00:00.000000Z"
                )
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            assertTrue(requireNotNull(viewModel.uiState.value.delivery).canCompleteDelivery)

            viewModel.completeDelivery()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.completeDelivery("7") }

            val state = viewModel.uiState.value
            assertTrue(state.completeState is SubmitState.Success)
            assertEquals(AssignmentStatus.COMPLETED, state.delivery?.status)
            assertEquals(OrderStatus.SELESAI, state.delivery?.order?.status)
            assertEquals("2026-01-01T11:00:00.000000Z", state.delivery?.completedAt)
            assertTrue(state.isCompleted)
            assertEquals(
                DeliveryDetailViewModel.COMPLETE_SUCCESS_MESSAGE,
                state.actionMessage
            )
    // --- Aksi yang saling independen ---
            assertFalse(requireNotNull(state.delivery).canCompleteDelivery)
            assertFalse(requireNotNull(state.delivery).canConfirmCash)
            assertFalse(requireNotNull(state.delivery).canStartDelivery)
        }

    @Test
    fun `completing a delivery stops location sharing so no background sender survives`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Success(
                delivery(
                    status = AssignmentStatus.ACTIVE,
                    orderStatus = OrderStatus.DALAM_PENGANTARAN,
                    paymentStatus = PaymentStatus.PAID
                )
            )
            coEvery { repository.completeDelivery("7") } returns Result.Success(
                delivery(
                    status = AssignmentStatus.COMPLETED,
                    orderStatus = OrderStatus.SELESAI,
                    paymentStatus = PaymentStatus.PAID,
                    completedAt = "2026-01-01T11:00:00.000000Z"
                )
            )
            // Kurir sedang mengirim posisi untuk assignment ini.
            sharing.start("7")

            val viewModel = createViewModel("7")
            advanceUntilIdle()
            viewModel.completeDelivery()
            advanceUntilIdle()

            assertEquals(
                listOf(TrackingStopReason.DELIVERY_NOT_ACTIVE),
                sharing.stoppedReasons
            )
            assertTrue(sharing.started.isNotEmpty())
        }

    @Test
    fun `DEL-002 completion is blocked while the cash payment is still pending`() =
        runTest(dispatcher) {
            // Pembayaran CASH baru lunas setelah kurir mengonfirmasi uang
            // diterima. Backend menolak dengan 409, jadi Android tidak mengirim.
            coEvery { repository.getAssignment("7") } returns Result.Success(
                delivery(
                    status = AssignmentStatus.ACTIVE,
                    orderStatus = OrderStatus.DALAM_PENGANTARAN,
                    paymentStatus = PaymentStatus.PENDING
                )
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            assertFalse(requireNotNull(viewModel.uiState.value.delivery).canCompleteDelivery)
            viewModel.completeDelivery()
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.completeDelivery(any()) }
        }

    @Test
    fun `DEL-002 conflict from the backend is shown and the delivery stays active`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Success(
                delivery(
                    status = AssignmentStatus.ACTIVE,
                    orderStatus = OrderStatus.DALAM_PENGANTARAN,
                    paymentStatus = PaymentStatus.PAID
                )
            )
            coEvery { repository.completeDelivery("7") } returns Result.Failure(
                ValidationException(message = "Pembayaran belum lunas.")
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()
            viewModel.completeDelivery()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.completeState is SubmitState.Error)
            assertEquals("Pembayaran belum lunas.", state.completeErrorMessage)
            // Penolakan tidak boleh mengubah status di layar menjadi selesai.
            assertEquals(AssignmentStatus.ACTIVE, state.delivery?.status)
            assertFalse(state.isCompleted)
        }

    @Test
    fun `DEL-002 second press while completing does not complete twice`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(
                status = AssignmentStatus.ACTIVE,
                orderStatus = OrderStatus.DALAM_PENGANTARAN,
                paymentStatus = PaymentStatus.PAID
            )
        )
        coEvery { repository.completeDelivery("7") } returns Result.Success(
            delivery(
                status = AssignmentStatus.COMPLETED,
                orderStatus = OrderStatus.SELESAI,
                paymentStatus = PaymentStatus.PAID
            )
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        viewModel.completeDelivery()
        viewModel.completeDelivery()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.completeDelivery("7") }
    }

    @Test
    fun `DEL-002 completion is not sent for a qris payment that is still verifying`() =
        runTest(dispatcher) {
            // QRIS diverifikasi Owner, bukan kurir. Status WAITING_VERIFICATION
            // bukan berarti belum ada pembayaran.
            coEvery { repository.getAssignment("7") } returns Result.Success(
                delivery(
                    status = AssignmentStatus.ACTIVE,
                    orderStatus = OrderStatus.DALAM_PENGANTARAN,
                    paymentMethod = PaymentMethod.QRIS,
                    paymentStatus = PaymentStatus.WAITING_VERIFICATION
                )
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            assertFalse(requireNotNull(viewModel.uiState.value.delivery).canCompleteDelivery)
            viewModel.completeDelivery()
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.completeDelivery(any()) }
        }

    // --- Aksi yang saling independen ---

    @Test
    fun `a failed location post does not block the complete action`() = runTest(dispatcher) {
    // --- Aksi yang saling independen ---
        // dikirim tidak boleh membuat kurir tidak bisa menyelesaikan pengantaran.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(
                status = AssignmentStatus.ACTIVE,
                orderStatus = OrderStatus.DALAM_PENGANTARAN,
                paymentStatus = PaymentStatus.PAID
            )
        )
        coEvery { repository.updateLocation(any(), any()) } returns Result.Failure(
            ValidationException(message = "Perpindahan lokasi tidak masuk akal.")
        )
        coEvery { repository.completeDelivery("7") } returns Result.Success(
            delivery(
                status = AssignmentStatus.COMPLETED,
                orderStatus = OrderStatus.SELESAI,
                paymentStatus = PaymentStatus.PAID
            )
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        viewModel.postLocation(fix())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.locationState is SubmitState.Error)
        assertFalse(viewModel.uiState.value.isBusy)

        viewModel.completeDelivery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.completeState is SubmitState.Success)
        assertEquals(AssignmentStatus.COMPLETED, state.delivery?.status)
    }

    @Test
    fun `location is not posted when the delivery is not active`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ASSIGNED, orderStatus = OrderStatus.DITUGASKAN)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        assertFalse(requireNotNull(viewModel.uiState.value.delivery).canPostLocation)
        viewModel.postLocation(fix())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateLocation(any(), any()) }
    }

    @Test
    fun `location post shows the location stored by the backend`() = runTest(dispatcher) {
        // Nilai yang tampil setelah pengiriman adalah lokasi dari server, bukan
        // pembacaan lokal perangkat.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ACTIVE, orderStatus = OrderStatus.DALAM_PENGANTARAN)
        )
        coEvery { repository.updateLocation("7", any()) } returns Result.Success(
            CourierDeviceLocation(
                latitude = -0.948,
                longitude = 100.418,
                accuracyMeters = 8.5,
                recordedAt = "2026-01-01T09:30:00.000000Z"
            )
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()
        viewModel.postLocation(fix())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.locationState is SubmitState.Success)
        assertEquals("2026-01-01T09:30:00.000000Z", state.latestLocation?.recordedAt)
        assertEquals(DeliveryDetailViewModel.LOCATION_SUCCESS_MESSAGE, state.actionMessage)
    }

    // --- Muat ulang dan argumen rute ---

    @Test
    fun `blank assignment id fails without calling the backend`() = runTest(dispatcher) {
        val viewModel = createViewModel("")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.deliveryState is LoadState.Error)
        assertFalse((state.deliveryState as LoadState.Error).isRetryable)

        viewModel.startDelivery()
        viewModel.completeDelivery()
        viewModel.postLocation(fix())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getAssignment(any()) }
        coVerify(exactly = 0) { repository.startDelivery(any()) }
        coVerify(exactly = 0) { repository.completeDelivery(any()) }
        coVerify(exactly = 0) { repository.updateLocation(any(), any()) }
    }

    @Test
    fun `reload resets every action state`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ASSIGNED, orderStatus = OrderStatus.DITUGASKAN)
        )
        coEvery { repository.startDelivery("7") } returns Result.Failure(
            ValidationException(message = "Pengantaran sudah berjalan.")
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()
        viewModel.startDelivery()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.startState is SubmitState.Error)

        viewModel.loadDelivery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.startState is SubmitState.Idle)
        assertTrue(state.completeState is SubmitState.Idle)
        assertTrue(state.locationState is SubmitState.Idle)
        assertNull(state.actionMessage)
        assertNull(state.startErrorMessage)
    }

    @Test
    fun `clear action message drops every action feedback`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(
                status = AssignmentStatus.ACTIVE,
                orderStatus = OrderStatus.DALAM_PENGANTARAN,
                paymentStatus = PaymentStatus.PAID
            )
        )
        coEvery { repository.completeDelivery("7") } returns Result.Success(
            delivery(
                status = AssignmentStatus.COMPLETED,
                orderStatus = OrderStatus.SELESAI,
                paymentStatus = PaymentStatus.PAID
            )
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()
        viewModel.completeDelivery()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.actionMessage)

        viewModel.clearActionMessage()

        val state = viewModel.uiState.value
        assertNull(state.actionMessage)
        assertTrue(state.completeState is SubmitState.Idle)
    // --- Aksi yang saling independen ---
        // sebenarnya dari server.
        assertEquals(AssignmentStatus.COMPLETED, state.delivery?.status)
    }

    @Test
    fun `missing assignment is reported as not found and is not retryable`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Failure(
                NotFoundException("Penugasan tidak ditemukan.")
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.deliveryState is LoadState.Error)
            assertTrue((state.deliveryState as LoadState.Error).isNotFound)
        }

    @Test
    fun `unauthorized load marks the state as unauthorized`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Failure(
            UnauthorizedException("Token tidak valid.")
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue((state.deliveryState as LoadState.Error).isUnauthorized)
        // Sesi yang sudah tidak valid tidak akan berubah dengan tombol coba lagi.
        assertFalse((state.deliveryState as LoadState.Error).isRetryable)
    }

    @Test
    fun `cash success message never claims paid for another status`() {
        assertTrue(
            DeliveryDetailViewModel.cashSuccessMessage(PaymentStatus.PAID).contains("lunas")
        )
        val pendingMessage = DeliveryDetailViewModel.cashSuccessMessage(PaymentStatus.PENDING)
        assertTrue(pendingMessage.contains(PaymentStatus.PENDING.displayName))
        assertFalse(
            DeliveryDetailViewModel.cashSuccessMessage(null).contains("lunas oleh server")
        )
    }

    // --- Helper ---

    private fun createViewModel(assignmentId: String) = DeliveryDetailViewModel(
        courierRepository = repository,
        sharing = sharing,
        savedStateHandle = SavedStateHandle(
            mapOf(DeliveryDetailViewModel.ARG_ASSIGNMENT_ID to assignmentId)
        )
    )

    private fun fix() = CourierLocationFix(
        point = GeoPoint(-0.948, 100.418),
        accuracyMeters = 8.5
    )

    private fun delivery(
        status: AssignmentStatus?,
        orderStatus: OrderStatus,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        paymentStatus: PaymentStatus = PaymentStatus.PENDING,
        startedAt: String? = null,
        completedAt: String? = null
    ) = CourierDelivery(
        id = "7",
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        order = CourierDeliveryOrder(
            id = "21",
            status = orderStatus,
            subtotalAmount = "16000.00",
            totalAmount = "21000.00",
            deliveryAddress = "Jl. Merdeka 1",
            customerName = "Budi",
            payment = CourierDeliveryPayment(
                paymentMethod = paymentMethod,
                paymentStatus = paymentStatus
            )
        )
    )
}
