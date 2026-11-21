package com.kyusui.app.ui.courier.payment

import androidx.lifecycle.SavedStateHandle
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.NotFoundException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.core.ValidationException
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierCashReceipt
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeliveryOrder
import com.kyusui.app.domain.model.CourierDeliveryPayment
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.repository.CourierRepository
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
 * Test konfirmasi "Uang Diterima" untuk role COURIER.
 *
 * Test ini adalahpadanan Android dari CASH-001 sampai CASH-004 pada
 * `backend/tests/Feature/CourierApiTest.php`:
 *
 * - CASH-001 kurir yang benar-benar ditugaskan: aksi dikirim ke assignment miliknya
 *   dan backend-lah yang menyatakan pembayaran lunas;
 * - CASH-002 kurir yang tidak berhak: `403` dari backend ditampilkan sebagai
 *   penolakan, dan Android tidak mencari jalan lain;
 * - CASH-003 konfirmasi tunai yang sah: assignment aktif, metode `CASH`, dan
 *   pembayaran `PENDING`, sehingga aksi dikirim dan hasilnya dibaca apa adanya;
 * - CASH-004 konfirmasi tunai yang tidak sah: `409` dari backend ditampilkan apa
 *   adanya dan status pembayaran di layar tidak berubah.
 *
 * Batas yang dijaga bersama di seluruh test ini:
 *
 * - tidak ada satu pun test yang membuat assignment palsu atau payment palsu.
 *   Semua penolakan adalah exception dari backend, bukan exception buatan Android;
 * - tidak ada jalur di Android yang menulis `PAID`. Nilai `PAID` pada test hanya
 *   muncul di `Result.Success` yang berasal dari response backend;
 * - Android hanya mengirim aksi. Tidak ada nominal, id kurir, id order, atau bukti
 *   yang dikirim, dan tidak ada pemeriksaan otorisasi Android yang dianggap
 *   menentukan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CashConfirmationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: CourierRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- CASH-003: konfirmasi tunai yang sah ---

    @Test
    fun `CASH-003 assigned courier sends the action and shows the receipt from the server`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Success(
                cashPendingDelivery()
            )
            coEvery { repository.confirmCashReceived("7") } returns Result.Success(
                paidReceipt()
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()
            viewModel.confirmCashReceived()
            advanceUntilIdle()

            // Aksi dikirim ke assignment yang sedang dibuka, tanpa argumen lain.
            coVerify(exactly = 1) { repository.confirmCashReceived("7") }

            val state = viewModel.uiState.value
            assertTrue(state.submitState is SubmitState.Success)
            // `PAID` pada receipt berasal dari response backend.
            assertEquals(PaymentStatus.PAID, state.receipt?.paymentStatus)
            assertTrue(state.isConfirmedPaid)
            assertNotNull(state.message)
        }

    @Test
    fun `CASH-003 assignment is reloaded after a successful confirmation`() = runTest(dispatcher) {
        // Setelah konfirmasi, isi layar dimuat ulang supaya status pembayaran
        // yang tampil adalah nilai terbaru dari server, bukan asumsi dari aksi.
        coEvery { repository.getAssignment("7") } returnsMany listOf(
            Result.Success(cashPendingDelivery()),
            Result.Success(cashPaidDelivery())
        )
        coEvery { repository.confirmCashReceived("7") } returns Result.Success(paidReceipt())

        val viewModel = createViewModel("7")
        advanceUntilIdle()
        viewModel.confirmCashReceived()
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.getAssignment("7") }
        assertEquals(PaymentStatus.PAID, viewModel.uiState.value.delivery?.order?.payment?.paymentStatus)
    }

    @Test
    fun `CASH-003 receipt survives a failed reload so the courier is not misled`() =
        runTest(dispatcher) {
            // Muat ulang gagal bukan berarti konfirmasi tunai gagal. Receipt yang
            // sudah diterima dari server harus tetap ditampilkan.
            coEvery { repository.getAssignment("7") } returnsMany listOf(
                Result.Success(cashPendingDelivery()),
                Result.Failure(NotFoundException("Penugasan tidak ditemukan."))
            )
            coEvery { repository.confirmCashReceived("7") } returns Result.Success(paidReceipt())

            val viewModel = createViewModel("7")
            advanceUntilIdle()
            viewModel.confirmCashReceived()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.receipt)
            assertTrue(state.isConfirmedPaid)
            assertNull(state.errorMessage)
        }

    @Test
    fun `CASH-003 second press while submitting does not send the action twice`() =
        runTest(dispatcher) {
            // Guard double submit. Dua request berarti backend bisa dipanggil
            // dua kali untuk aksi yang sama.
            coEvery { repository.getAssignment("7") } returns Result.Success(cashPendingDelivery())
            coEvery { repository.confirmCashReceived("7") } returns Result.Success(paidReceipt())

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            viewModel.confirmCashReceived()
            viewModel.confirmCashReceived()
            viewModel.confirmCashReceived()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.confirmCashReceived("7") }
        }

    // --- CASH-004: konfirmasi tunai yang tidak sah ---

    @Test
    fun `CASH-004 invalid cash confirmation shows the backend conflict and keeps the status`() =
        runTest(dispatcher) {
            // Backend membalas 409 bila delivery belum aktif, metode bukan CASH,
            // atau pembayaran sudah lunas. Android menampilkan apa adanya.
            coEvery { repository.getAssignment("7") } returns Result.Success(cashPendingDelivery())
            coEvery { repository.confirmCashReceived("7") } returns Result.Failure(
                ValidationException(message = "Pembayaran sudah berstatus lunas.")
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()
            viewModel.confirmCashReceived()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.submitState is SubmitState.Error)
            assertEquals("Pembayaran sudah berstatus lunas.", state.errorMessage)
            // Penolakan backend tidak boleh mengubah apa pun di layar.
            assertNull(state.receipt)
            assertFalse(state.isConfirmedPaid)
            assertEquals(
                PaymentStatus.PENDING,
                state.delivery?.order?.payment?.paymentStatus
            )
        }

    @Test
    fun `CASH-004 cash action is not sent when the payment is not pending`() = runTest(dispatcher) {
        // Pembayaran sudah lunas di luar konfirmasi kurir, misalnya Owner sudah
        // memverifikasi. Tombol tidak muncul dan aksi tidak dikirim.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            cashPaidDelivery(assignmentStatus = AssignmentStatus.ACTIVE)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        assertFalse(requireNotNull(viewModel.uiState.value.delivery).canConfirmCash)
        viewModel.confirmCashReceived()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.confirmCashReceived(any()) }
    }

    @Test
    fun `cash action is not sent when the delivery is still only assigned`() = runTest(dispatcher) {
        // Assignment masih ASSIGNED berarti delivery belum aktif. Backend akan
        // menolak dengan 409, jadi Android tidak mengirimnya.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            cashPendingDelivery(assignmentStatus = AssignmentStatus.ASSIGNED)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        assertFalse(requireNotNull(viewModel.uiState.value.delivery).canConfirmCash)
        viewModel.confirmCashReceived()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.confirmCashReceived(any()) }
    }

    @Test
    fun `cash action is not sent when the payment method is not cash`() = runTest(dispatcher) {
        // QRIS diverifikasi Owner, bukan kurir. Tidak ada aksi Android untuk itu.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            cashPendingDelivery(paymentMethod = PaymentMethod.QRIS)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        assertFalse(requireNotNull(viewModel.uiState.value.delivery).canConfirmCash)
        viewModel.confirmCashReceived()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.confirmCashReceived(any()) }
    }

    @Test
    fun `cash action is not sent when the payment status is unknown`() = runTest(dispatcher) {
        // Status yang tidak dikenal tidak boleh diperlakukan sebagai `PENDING`,
        // karena itu akan membuka aksi dengan asumsi.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            cashPendingDelivery(paymentStatus = null)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        assertFalse(requireNotNull(viewModel.uiState.value.delivery).canConfirmCash)
        viewModel.confirmCashReceived()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.confirmCashReceived(any()) }
    }

    // --- CASH-002: kurir yang tidak berhak ---

    @Test
    fun `CASH-002 forbidden courier sees the rejection and no receipt appears`() =
        runTest(dispatcher) {
            // Backend membalas 403 bila kurir bukan pemilik assignment. Android
            // menampilkan penolakan itu dan tidak mencari endpoint lain.
            coEvery { repository.getAssignment("7") } returns Result.Success(cashPendingDelivery())
            coEvery { repository.confirmCashReceived("7") } returns Result.Failure(
                ForbiddenException("Anda tidak memiliki akses ke pengantaran ini.")
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()
            viewModel.confirmCashReceived()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.submitState is SubmitState.Error)
            // `403` dipetakan ke kalimat aplikasi sendiri oleh `userMessage()`,
            // bukan pesan mentah dari backend. Backend juga memakai kalimat yang
            // berbeda untuk tiap sumber `403` (middleware `ensure-role` versus
            // `abort`), jadi menampilkan pesan mentah akan membuat teks di layar
            // bergantung pada tempat penolakan terjadi.
            assertEquals("Akses ditolak", state.errorMessage)
            assertNull(state.receipt)
            assertFalse(state.isConfirmedPaid)
        }

    @Test
    fun `CASH-002 forbidden on loading the assignment blocks the cash action`() =
        runTest(dispatcher) {
            // Bila assignment-nya sendiri tidak boleh dibuka, tidak ada aksi yang
            // boleh dikirim dari layar ini.
            coEvery { repository.getAssignment("7") } returns Result.Failure(
                ForbiddenException("Anda tidak memiliki akses ke pengantaran ini.")
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.deliveryState is LoadState.Error)
            assertNull(state.delivery)
            viewModel.confirmCashReceived()
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.confirmCashReceived(any()) }
        }

    @Test
    fun `unauthorized session is reported as an error and not as a success`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(cashPendingDelivery())
        coEvery { repository.confirmCashReceived("7") } returns Result.Failure(
            UnauthorizedException("Token tidak valid.")
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()
        viewModel.confirmCashReceived()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.submitState is SubmitState.Error)
        // Sesi berakhir harus dipetakan ke kalimat "login kembali", bukan
        // diteruskan sebagai pesan teknis dari server.
        assertEquals("Sesi berakhir, silakan login kembali", state.errorMessage)
        assertNull(state.receipt)
        assertFalse(state.isConfirmedPaid)
    }

    // --- Memuat ulang ---

    @Test
    fun `blank assignment id fails without calling the backend`() = runTest(dispatcher) {
        val viewModel = createViewModel("")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.deliveryState is LoadState.Error)
        assertFalse((state.deliveryState as LoadState.Error).isRetryable)
        coVerify(exactly = 0) { repository.getAssignment(any()) }
    }

    @Test
    fun `reload clears the previous receipt so it is not mistaken for a new action`() =
        runTest(dispatcher) {
            // Receipt milik aksi sebelumnya. Menampilkannya lagi setelah muat
            // ulang membuat kurir mengira konfirmasi lama masih berlaku.
            coEvery { repository.getAssignment("7") } returns Result.Success(cashPendingDelivery())
            coEvery { repository.confirmCashReceived("7") } returns Result.Success(paidReceipt())

            val viewModel = createViewModel("7")
            advanceUntilIdle()
            viewModel.confirmCashReceived()
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.receipt)

            coEvery { repository.getAssignment("7") } returns Result.Success(cashPendingDelivery())
            viewModel.loadDelivery()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.receipt)
            assertNull(state.message)
            assertTrue(state.submitState is SubmitState.Idle)
        }

    @Test
    fun `clear receipt resets the action feedback without dropping the delivery`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Success(cashPendingDelivery())
            coEvery { repository.confirmCashReceived("7") } returns Result.Success(paidReceipt())

            val viewModel = createViewModel("7")
            advanceUntilIdle()
            viewModel.confirmCashReceived()
            advanceUntilIdle()

            viewModel.clearReceipt()
            val state = viewModel.uiState.value
            assertNull(state.receipt)
            assertNull(state.message)
            assertTrue(state.submitState is SubmitState.Idle)
            // Assignment yang sudah termuat tetap tampil supaya kurir tidak
            // kembali ke layar muat.
            assertEquals("7", state.delivery?.id)
        }

    @Test
    fun `cash success message never claims paid for another status`() {
        assertTrue(
            CashConfirmationViewModel.cashSuccessMessage(PaymentStatus.PAID)
                .contains("lunas")
        )
        // Status selain PAID tidak boleh diterjemahkan menjadi lunas.
        val pendingMessage = CashConfirmationViewModel.cashSuccessMessage(PaymentStatus.PENDING)
        assertTrue(pendingMessage.contains(PaymentStatus.PENDING.displayName))
        assertFalse(
            CashConfirmationViewModel.cashSuccessMessage(null).contains("lunas oleh server")
        )
    }

    // --- Helper ---

    private fun createViewModel(assignmentId: String) = CashConfirmationViewModel(
        courierRepository = repository,
        savedStateHandle = SavedStateHandle(
            mapOf(CashConfirmationViewModel.ARG_ASSIGNMENT_ID to assignmentId)
        )
    )

    private fun cashPendingDelivery(
        assignmentStatus: AssignmentStatus = AssignmentStatus.ACTIVE,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        paymentStatus: PaymentStatus? = PaymentStatus.PENDING
    ) = CourierDelivery(
        id = "7",
        status = assignmentStatus,
        order = CourierDeliveryOrder(
            id = "21",
            status = if (assignmentStatus == AssignmentStatus.ACTIVE) {
                OrderStatus.DALAM_PENGANTARAN
            } else {
                OrderStatus.DITUGASKAN
            },
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

    private fun cashPaidDelivery(
        assignmentStatus: AssignmentStatus = AssignmentStatus.ACTIVE
    ) = cashPendingDelivery(assignmentStatus = assignmentStatus, paymentStatus = PaymentStatus.PAID)

    private fun paidReceipt() = CourierCashReceipt(
        paymentId = "9001",
        paymentMethod = PaymentMethod.CASH,
        paymentStatus = PaymentStatus.PAID,
        verifiedAt = "2026-01-01T10:00:00.000000Z"
    )
}
