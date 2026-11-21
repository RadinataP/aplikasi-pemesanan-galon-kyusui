package com.kyusui.app.ui.owner.orders

import androidx.lifecycle.SavedStateHandle
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.NotFoundException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.core.ValidationException
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierSummary
import com.kyusui.app.domain.model.OrderAssignment
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OwnerOrder
import com.kyusui.app.domain.repository.OwnerRepository
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.SubmitState
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test detail order Owner, termasuk aksi proses dan penugasan kurir.
 *
 * Yang dijaga test ini, sesuai aturan Phase 5:
 *
 * - status pada order hanya berasal dari response backend. Setelah aksi sukses,
 *   order yang tampil adalah order dari response, bukan object lama yang diubah
 *   statusnya di Android;
 * - transition yang tidak valid tidak pernah dikirim. Backend tetap boleh
 *   menolak dengan `409`/`422`, dan penolakan itu tampil apa adanya;
 * - aksi kedua untuk aksi yang sama tidak dikirim;
 * - order yang belum `DIPROSES` atau sudah punya assignment tidak bisa
 *   ditugaskan dari Android.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerOrderDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: OwnerRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        // Detail order yang bisa ditugaskan memuat daftar kurir. Default-nya
        // kosong supaya test yang tidak peduli daftar kurir tetap fokus.
        coEvery { repository.getCouriers() } returns Result.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Pemuatan detail ---

    @Test
    fun `order is loaded by id on init`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(order())

        val viewModel = createViewModel("1001")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getOrder("1001") }
        assertEquals("1001", viewModel.uiState.value.order?.id)
        assertTrue(viewModel.uiState.value.orderState is LoadState.Success)
    }

    @Test
    fun `blank order id fails without calling the backend`() = runTest(dispatcher) {
        val viewModel = createViewModel("")
        advanceUntilIdle()

        // Route tanpa `orderId` tidak boleh memicu request yang tidak mungkin.
        coVerify(exactly = 0) { repository.getOrder(any()) }

        val state = viewModel.uiState.value.orderState
        assertTrue(state is LoadState.Error)
        assertEquals(
            OwnerOrderDetailViewModel.MISSING_ORDER_ID_MESSAGE,
            (state as LoadState.Error).message
        )
        // Route rusak bukan kondisi jaringan, jadi tidak ada tombol coba lagi.
        assertFalse((state as LoadState.Error).isRetryable)
    }

    @Test
    fun `not found detail is reported as a non retryable error`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.failure(NotFoundException())

        val viewModel = createViewModel("1001")
        advanceUntilIdle()

        val state = viewModel.uiState.value.orderState
        assertTrue(state is LoadState.Error)
        assertTrue((state as LoadState.Error).isNotFound)
    }

    @Test
    fun `unauthorized detail is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.failure(UnauthorizedException())

        val viewModel = createViewModel("1001")
        advanceUntilIdle()

        val state = viewModel.uiState.value.orderState
        assertTrue(state is LoadState.Error)
        assertTrue((state as LoadState.Error).isUnauthorized)
    }

    @Test
    fun `forbidden detail is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.failure(ForbiddenException())

        val viewModel = createViewModel("1001")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.orderState is LoadState.Error)
    }

    @Test
    fun `retry after failure loads the order again`() = runTest(dispatcher) {
        val calls = intArrayOf(0)
        coEvery { repository.getOrder("1001") } answers {
            calls[0]++
            if (calls[0] == 1) Result.failure(NotFoundException()) else Result.Success(order())
        }

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.orderState is LoadState.Error)

        viewModel.loadOrder()
        advanceUntilIdle()

        assertEquals("1001", viewModel.uiState.value.order?.id)
        coVerify(exactly = 2) { repository.getOrder("1001") }
    }

    // --- Aksi perubahan status ---

    @Test
    fun `status update sends the next status from the lifecycle`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )
        coEvery { repository.updateOrderStatus(any(), any(), any()) } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.updateStatus(OrderStatus.DIPROSES)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.updateOrderStatus("1001", OrderStatus.DIPROSES, null)
        }
    }

    @Test
    fun `status after update is taken from the server response`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )
        // Backend mengembalikan status yang berbeda dari yang diminta, misalnya
        // karena ada proses lain yang lebih dulu menyelesaikan order.
        coEvery { repository.updateOrderStatus(any(), any(), any()) } returns Result.Success(
            order(status = OrderStatus.SELESAI)
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.updateStatus(OrderStatus.DIPROSES)
        advanceUntilIdle()

        // Android menampilkan apa yang server kirim, bukan nilai yang diminta.
        assertEquals(OrderStatus.SELESAI, viewModel.uiState.value.order?.orderStatus)
        assertTrue(viewModel.uiState.value.statusSubmitState is SubmitState.Success)
    }

    @Test
    fun `status update is not sent for a transition outside the lifecycle`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.updateStatus(OrderStatus.SELESAI)
        advanceUntilIdle()

        // Menunggu diproses ke selesai bukan next state. Android menahan request
        // yang pasti ditolak backend.
        coVerify(exactly = 0) { repository.updateOrderStatus(any(), any(), any()) }
    }

    @Test
    fun `status update is not sent when the order is still waiting for payment`() =
        runTest(dispatcher) {
            coEvery { repository.getOrder("1001") } returns Result.Success(
                order(status = OrderStatus.MENUNGGU_PEMBAYARAN)
            )

            val viewModel = createViewModel("1001")
            advanceUntilIdle()
            OrderStatus.entries.forEach { viewModel.updateStatus(it) }
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.updateOrderStatus(any(), any(), any()) }
        }

    @Test
    fun `status update is not sent for an unknown order status`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(order(status = null))

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        OrderStatus.entries.forEach { viewModel.updateStatus(it) }
        advanceUntilIdle()

        // Status yang tidak dimengerti tidak boleh diterjemahkan menjadi tebakan
        // status berikutnya.
        coVerify(exactly = 0) { repository.updateOrderStatus(any(), any(), any()) }
    }

    @Test
    fun `duplicate status submit is sent only once`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )
        coEvery { repository.updateOrderStatus(any(), any(), any()) } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.updateStatus(OrderStatus.DIPROSES)
        viewModel.updateStatus(OrderStatus.DIPROSES)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateOrderStatus(any(), any(), any()) }
    }

    @Test
    fun `conflict on status update is shown as an error and keeps the order`() =
        runTest(dispatcher) {
            coEvery { repository.getOrder("1001") } returns Result.Success(
                order(status = OrderStatus.MENUNGGU_DIPROSES)
            )
            coEvery { repository.updateOrderStatus(any(), any(), any()) } returns Result.failure(ValidationException(message = "Transisi status tidak valid."))

            val viewModel = createViewModel("1001")
            advanceUntilIdle()
            viewModel.updateStatus(OrderStatus.DIPROSES)
            advanceUntilIdle()

            val submit = viewModel.uiState.value.statusSubmitState
            assertTrue(submit is SubmitState.Error)
            // Order tetap menampilkan status terakhir dari server; Android tidak
            // menebak status baru setelah penolakan.
            assertEquals(
                OrderStatus.MENUNGGU_DIPROSES,
                viewModel.uiState.value.order?.orderStatus
            )
            assertFalse(viewModel.uiState.value.isUpdatingStatus)
        }

    @Test
    fun `unprocessable status update is shown as an error`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )
        coEvery { repository.updateOrderStatus(any(), any(), any()) } returns Result.failure(ValidationException(message = "Status tidak dapat diubah."))

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.updateStatus(OrderStatus.DIPROSES)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.statusSubmitState is SubmitState.Error)
    }

    @Test
    fun `unauthorized status update is shown as an error`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )
        coEvery { repository.updateOrderStatus(any(), any(), any()) } returns Result.failure(UnauthorizedException())

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.updateStatus(OrderStatus.DIPROSES)
        advanceUntilIdle()

        val submit = viewModel.uiState.value.statusSubmitState
        assertTrue(submit is SubmitState.Error)
        assertTrue((submit as SubmitState.Error).isUnauthorized)
    }

    @Test
    fun `status note is forwarded when the owner provides one`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )
        coEvery { repository.updateOrderStatus(any(), any(), any()) } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.updateStatus(OrderStatus.DIPROSES, note = "Stok siap.")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.updateOrderStatus(
                orderId = "1001",
                status = OrderStatus.DIPROSES,
                note = "Stok siap."
            )
        }
    }

    @Test
    fun `reloading the order resets the previous submit state`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )
        coEvery { repository.updateOrderStatus(any(), any(), any()) } returns Result.failure(ValidationException(message = "Ditolak."))

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.updateStatus(OrderStatus.DIPROSES)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.statusSubmitState is SubmitState.Error)

        viewModel.loadOrder()
        advanceUntilIdle()

        // Aksi lama sudah tidak berlaku, jadi tidak lagi ditampilkan sebagai
        // tombol yang ter-disable.
        assertEquals(SubmitState.Idle, viewModel.uiState.value.statusSubmitState)
    }

    // --- Aksi penugasan kurir ---

    @Test
    fun `courier assignment is sent for a processed order`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )
        coEvery { repository.assignCourier(any(), any()) } returns Result.Success(
            order(status = OrderStatus.DIPROSES, assignment = assignment())
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.assignCourier("20")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.assignCourier("1001", "20") }
    }

    @Test
    fun `assignment result is read from the server response`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )
        coEvery { repository.assignCourier(any(), any()) } returns Result.Success(
            order(status = OrderStatus.DIPROSES, assignment = assignment(courierId = "20"))
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.assignCourier("20")
        advanceUntilIdle()

        assertEquals("20", viewModel.uiState.value.order?.assignment?.courierId)
        assertTrue(viewModel.uiState.value.assignmentSubmitState is SubmitState.Success)
    }

    @Test
    fun `blank courier id is rejected without calling the backend`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.assignCourier("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.assignCourier(any(), any()) }
        val submit = viewModel.uiState.value.assignmentSubmitState
        assertTrue(submit is SubmitState.Error)
        assertEquals(
            OwnerOrderDetailViewModel.EMPTY_COURIER_ID_MESSAGE,
            (submit as SubmitState.Error).message
        )
    }

    @Test
    fun `assignment is not sent for an order that is not being processed`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.assignCourier("20")
        advanceUntilIdle()

        // Penugasan hanya relevan setelah order masuk tahap proses. Backend
        // tetap validator terakhir.
        coVerify(exactly = 0) { repository.assignCourier(any(), any()) }
    }

    @Test
    fun `assignment is not sent when the order already has an assignment`() =
        runTest(dispatcher) {
            coEvery { repository.getOrder("1001") } returns Result.Success(
                order(status = OrderStatus.DIPROSES, assignment = assignment())
            )

            val viewModel = createViewModel("1001")
            advanceUntilIdle()
            viewModel.assignCourier("30")
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.assignCourier(any(), any()) }
        }

    @Test
    fun `duplicate courier assignment is sent only once`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )
        coEvery { repository.assignCourier(any(), any()) } returns Result.Success(
            order(status = OrderStatus.DIPROSES, assignment = assignment())
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.assignCourier("20")
        viewModel.assignCourier("20")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.assignCourier(any(), any()) }
    }

    @Test
    fun `conflicting assignment is shown as an error`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )
        coEvery { repository.assignCourier(any(), any()) } returns Result.failure(ValidationException(
            message = "Order sudah punya kurir aktif."
        ))

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.assignCourier("20")
        advanceUntilIdle()

        val submit = viewModel.uiState.value.assignmentSubmitState
        assertTrue(submit is SubmitState.Error)
        // Assignment lokal tidak dibuat-buat setelah penolakan backend.
        assertNull(viewModel.uiState.value.order?.assignment)
        assertFalse(viewModel.uiState.value.isAssigningCourier)
    }

    @Test
    fun `forbidden assignment is shown as an error`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )
        coEvery { repository.assignCourier(any(), any()) } returns Result.failure(ForbiddenException())

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.assignCourier("20")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.assignmentSubmitState is SubmitState.Error)
    }

    @Test
    fun `assignment without courier id in the response is not displayed as assigned`() =
        runTest(dispatcher) {
            coEvery { repository.getOrder("1001") } returns Result.Success(
                order(status = OrderStatus.DIPROSES)
            )
            // Backend menerima request tetapi tidak mengirim assignment pada
            // response. Android tidak mengarang kurir dari nilai yang dikirim.
            coEvery { repository.assignCourier(any(), any()) } returns Result.Success(
                order(status = OrderStatus.DIPROSES, assignment = null)
            )

            val viewModel = createViewModel("1001")
            advanceUntilIdle()
            viewModel.assignCourier("20")
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.order?.assignment)
            assertTrue(viewModel.uiState.value.assignmentSubmitState is SubmitState.Success)
        }

    @Test
    fun `clearing the assignment message resets the submit state`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )
        coEvery { repository.assignCourier(any(), any()) } returns Result.Success(
            order(status = OrderStatus.DIPROSES, assignment = assignment())
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.assignCourier("20")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.assignmentMessage != null)

        viewModel.clearAssignmentMessage()

        assertNull(viewModel.uiState.value.assignmentMessage)
        assertEquals(SubmitState.Idle, viewModel.uiState.value.assignmentSubmitState)
    }

    @Test
    fun `status and assignment submit states are independent`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )
        coEvery { repository.updateOrderStatus(any(), any(), any()) } returns Result.failure(ValidationException(message = "Ditolak."))

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        viewModel.updateStatus(OrderStatus.DIPROSES)
        advanceUntilIdle()

        // Penolakan aksi status tidak boleh ikut mengosongkan state aksi kurir,
        // karena keduanya request terpisah.
        assertEquals(SubmitState.Idle, viewModel.uiState.value.assignmentSubmitState)
        assertTrue(viewModel.uiState.value.statusSubmitState is SubmitState.Error)
    }

    // --- Daftar kurir tersedia ---

    @Test
    fun `couriers are loaded for an order that can be assigned`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )
        coEvery { repository.getCouriers() } returns Result.Success(
            listOf(
                courier(id = "20", name = "Budi"),
                courier(id = "21", name = "Sari")
            )
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getCouriers() }
        assertTrue(viewModel.uiState.value.couriersState is LoadState.Success)
        assertEquals(2, viewModel.uiState.value.couriers.size)
        assertEquals("Budi", viewModel.uiState.value.couriers.first().displayLabel)
    }

    @Test
    fun `couriers are not loaded for an order that cannot be assigned`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.MENUNGGU_DIPROSES)
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()

        // Order belum masuk tahap proses: daftar kurir tidak relevan, jadi
        // Android tidak memuatnya.
        coVerify(exactly = 0) { repository.getCouriers() }
    }

    @Test
    fun `empty courier list is shown as an empty state`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )
        coEvery { repository.getCouriers() } returns Result.Success(emptyList())

        val viewModel = createViewModel("1001")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.couriersState is LoadState.Empty)
        assertTrue(viewModel.uiState.value.couriers.isEmpty())
    }

    @Test
    fun `courier list failure is shown as an error and can be retried`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.Success(
            order(status = OrderStatus.DIPROSES)
        )
        coEvery { repository.getCouriers() } returnsMany listOf(
            Result.failure(ValidationException(message = "Gagal memuat kurir.")),
            Result.Success(listOf(courier(id = "20", name = "Budi")))
        )

        val viewModel = createViewModel("1001")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.couriersState is LoadState.Error)

        viewModel.loadCouriers()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.couriersState is LoadState.Success)
        coVerify(exactly = 2) { repository.getCouriers() }
    }

    // --- Fixtures ---

    private fun createViewModel(orderId: String) = OwnerOrderDetailViewModel(
        ownerRepository = repository,
        savedStateHandle = SavedStateHandle(
            mapOf(OwnerOrderDetailViewModel.ARG_ORDER_ID to orderId)
        )
    )

    private fun order(
        status: OrderStatus? = OrderStatus.MENUNGGU_DIPROSES,
        assignment: OrderAssignment? = null
    ) = OwnerOrder(
        id = "1001",
        orderNumber = "ORD-20260930-0001",
        customerId = "10",
        items = emptyList(),
        subtotalAmount = "16000.00",
        deliveryFee = "5000.00",
        totalAmount = "21000.00",
        orderStatus = status,
        assignment = assignment
    )

    private fun assignment(
        courierId: String = "20",
        status: AssignmentStatus? = AssignmentStatus.ASSIGNED
    ) = OrderAssignment(
        id = "77",
        courierId = courierId,
        status = status,
        assignedAt = "2026-09-30T10:00:00Z"
    )

    private fun courier(
        id: String,
        name: String = "",
        phone: String? = null,
        vehicle: String? = null
    ) = CourierSummary(
        id = id,
        name = name,
        phone = phone,
        vehicle = vehicle
    )
}