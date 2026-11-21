package com.kyusui.app.ui.owner.payment

import androidx.lifecycle.SavedStateHandle
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.NotFoundException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.core.ValidationException
import com.kyusui.app.domain.model.OwnerPaymentProof
import com.kyusui.app.domain.model.OwnerPendingPayment
import com.kyusui.app.domain.model.OwnerVerificationAction
import com.kyusui.app.domain.model.OwnerVerificationResult
import com.kyusui.app.domain.model.Payment
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
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
 * Test antrean verifikasi QRIS Owner.
 *
 * Ini test inti dari aturan pembayaran pada Phase 5:
 *
 * - Android hanya mengirim `action`. Status `PAID` maupun `PENDING` SELALU dibaca
 *   dari response backend, tidak pernah ditulis di Android;
 * - reject tidak memakai status `REJECTED` karena status itu tidak ada pada
 *   contract;
 * - approve dan reject memakai endpoint yang sama dengan `action` berbeda;
 * - aksi kedua untuk aksi yang sama tidak dikirim;
 * - `409` dan `422` dari backend tampil apa adanya dan tidak mengubah antrean.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingQrisVerificationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: OwnerRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Antrean ---

    @Test
    fun `queue is loaded on init`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()

        val viewModel = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.getPendingPayments(
                page = 1,
                perPage = PendingQrisVerificationViewModel.PER_PAGE
            )
        }
        assertEquals(2, viewModel.uiState.value.payments.size)
    }

    @Test
    fun `empty queue is an empty state`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value.paymentsState
        assertTrue(state is LoadState.Empty)
        assertEquals(
            PendingQrisVerificationViewModel.EMPTY_QUEUE_MESSAGE,
            (state as LoadState.Empty).message
        )
    }

    @Test
    fun `unauthorized queue load is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns Result.failure(UnauthorizedException())

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.paymentsState is LoadState.Error)
    }

    @Test
    fun `forbidden queue load is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns Result.failure(ForbiddenException())

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.paymentsState is LoadState.Error)
    }

    @Test
    fun `next queue page is appended`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(page = 1, perPage = any()) } returns queue(
            payments = listOf(payment(paymentId = "7001", orderId = "1001")),
            currentPage = 1,
            perPage = 1,
            total = 2
        )
        coEvery { repository.getPendingPayments(page = 2, perPage = any()) } returns queue(
            payments = listOf(payment(paymentId = "7002", orderId = "1002")),
            currentPage = 2,
            perPage = 1,
            total = 2
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasMore)

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(
            listOf("7001", "7002"),
            viewModel.uiState.value.payments.map { it.paymentId }
        )
    }

    @Test
    fun `failed queue append keeps the loaded rows and stops paging`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(page = 1, perPage = any()) } returns queue(
            payments = listOf(payment(paymentId = "7001", orderId = "1001")),
            currentPage = 1,
            perPage = 1,
            total = 5
        )
        coEvery { repository.getPendingPayments(page = 2, perPage = any()) } returns Result.failure(ForbiddenException())

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.payments.size)
        assertFalse(viewModel.uiState.value.hasMore)
    }

    // --- Bukti pembayaran ---

    @Test
    fun `proof is loaded for the selected order`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()

        assertEquals("1001", viewModel.uiState.value.proofOrderId)
        val state = viewModel.uiState.value.proofState
        assertTrue(state is LoadState.Success)
        assertTrue((state as LoadState.Success).data.canDisplayImage)
    }

    @Test
    fun `proof not found does not remove the queue`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.failure(NotFoundException())

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()

        // 404 pada endpoint bukti berarti tidak ada bukti untuk order itu. Antrean
        // harus tetap utuh supaya Owner bisa memilih pembayaran lain.
        val state = viewModel.uiState.value.proofState
        assertTrue(state is LoadState.Error)
        assertTrue((state as LoadState.Error).isNotFound)
        assertEquals(2, viewModel.uiState.value.payments.size)
    }

    @Test
    fun `blank order id does not trigger a proof request`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("  ")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getPaymentProof(any()) }
        assertNull(viewModel.uiState.value.proofOrderId)
    }

    @Test
    fun `dismissing the proof closes the viewer`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isProofOpen)

        viewModel.dismissProof()

        assertFalse(viewModel.uiState.value.isProofOpen)
        assertNull(viewModel.uiState.value.proofState)
    }

    @Test
    fun `route with order id opens that payment proof directly`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())

        val viewModel = createViewModel(orderId = "1001")
        advanceUntilIdle()

        // dibuka dari detail order, jadi bukti langsung dimuat tanpa menunggu
        // Owner memilih baris antrean.
        coVerify(exactly = 1) { repository.getPaymentProof("1001") }
        assertEquals("1001", viewModel.uiState.value.proofOrderId)
    }

    // --- Approve ---

    @Test
    fun `approve sends only the approve action`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.APPROVE, PaymentStatus.PAID)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        // Approve tidak mengirim alasan. Backend yang memutuskan, jadi Android tidak
        // menambahkan field yang tidak diminta.
        coVerify(exactly = 1) {
            repository.verifyPayment("1001", OwnerVerificationAction.APPROVE, note = null)
        }
    }

    @Test
    fun `approve reports the paid status returned by the server`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.APPROVE, PaymentStatus.PAID)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        // Status PAID hanya boleh muncul karena dibaca dari response.
        val message = requireNotNull(viewModel.uiState.value.verificationMessage)
        assertTrue(message.contains("PAID"))
        assertTrue(viewModel.uiState.value.verificationState is SubmitState.Success)
    }

    @Test
    fun `approve removes the verified payment from the queue`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.APPROVE, PaymentStatus.PAID)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        // `GET /owner/payments/pending` hanya memuat WAITING_VERIFICATION, jadi
        // baris yang sudah diverifikasi tidak lagi ada di antrean. Menahannya
        // akan membuat layar berbeda dari server.
        assertEquals(listOf("1002"), viewModel.uiState.value.payments.map { it.orderId })
    }

    @Test
    fun `approve on the last queue row results in an empty queue`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue(
            listOf(payment(paymentId = "7001", orderId = "1001"))
        )
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.APPROVE, PaymentStatus.PAID)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.paymentsState is LoadState.Empty)
    }

    @Test
    fun `server status after approve wins over the expected status`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        // Backend mengembalikan status lain. Itu keputusan backend dan
        // Android tidak menyalakannya menjadi PAID.
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.APPROVE, PaymentStatus.WAITING_VERIFICATION)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        val message = requireNotNull(viewModel.uiState.value.verificationMessage)
        assertTrue(message.contains("WAITING_VERIFICATION"))
        assertFalse(message.contains("PAID"))
    }

    // --- Reject ---

    @Test
    fun `reject sends the note provided by the owner`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.REJECT, PaymentStatus.PENDING)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.REJECT, note = "Bukti tidak terbaca.")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.verifyPayment(
                "1001",
                OwnerVerificationAction.REJECT,
                note = "Bukti tidak terbaca."
            )
        }
    }

    @Test
    fun `reject reports the pending status returned by the server`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.REJECT, PaymentStatus.PENDING)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.REJECT, note = "Tidak terbaca.")
        advanceUntilIdle()

        // Reject mengembalikan ke PENDING, bukan ke status REJECTED yang tidak ada
        // pada contract.
        val message = requireNotNull(viewModel.uiState.value.verificationMessage)
        assertTrue(message.contains("PENDING"))
        assertFalse(message.contains("REJECTED"))
    }

    @Test
    fun `reject removes the payment from the verification queue`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.REJECT, PaymentStatus.PENDING)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.REJECT, note = "Tidak terbaca.")
        advanceUntilIdle()

        // Pembayaran yang ditolak sudah tidak lagi menunggu verifikasi.
        assertEquals(listOf("1002"), viewModel.uiState.value.payments.map { it.orderId })
    }

    // --- Guard dan error ---

    @Test
    fun `verification is not sent when no proof is open`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        // Tidak ada order yang dipilih berarti tidak ada yang boleh diverifikasi.
        coVerify(exactly = 0) { repository.verifyPayment(any(), any(), any()) }
    }

    @Test
    fun `duplicate approve is sent only once`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.APPROVE, PaymentStatus.PAID)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        // Satu pembayaran tidak boleh terkirim dua kali. Kalau Owner menekan
        // dua kali, backend bisa memproses dua kali.
        coVerify(exactly = 1) { repository.verifyPayment(any(), any(), any()) }
    }

    @Test
    fun `approve then reject in the same frame is not sent twice`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.APPROVE, PaymentStatus.PAID)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        viewModel.verify(OwnerVerificationAction.REJECT, note = "Tidak terbaca.")
        advanceUntilIdle()

        // Aksi yang berlawanan pada frame yang sama juga hanya terkirim satu.
        coVerify(exactly = 1) { repository.verifyPayment(any(), any(), any()) }
        coVerify(exactly = 0) {
            repository.verifyPayment(any(), OwnerVerificationAction.REJECT, any())
        }
    }

    @Test
    fun `conflict on verification keeps the payment in the queue`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.failure(ValidationException(
            message = "Pembayaran sudah diverifikasi."
        ))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        // Penolakan backend tidak boleh menghapus baris antrean atau mengubah
        // status pembayaran di Android.
        assertTrue(viewModel.uiState.value.verificationState is SubmitState.Error)
        assertEquals(2, viewModel.uiState.value.payments.size)
        assertNull(viewModel.uiState.value.verificationMessage)
    }

    @Test
    fun `unprocessable verification is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.failure(ValidationException(
            message = "Bukti tidak tersedia."
        ))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.REJECT, note = "Tidak terbaca.")
        advanceUntilIdle()

        val submit = viewModel.uiState.value.verificationState
        assertTrue(submit is SubmitState.Error)
        assertEquals(2, viewModel.uiState.value.payments.size)
    }

    @Test
    fun `not found verification is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.failure(NotFoundException())

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.verificationState is SubmitState.Error)
    }

    @Test
    fun `forbidden verification is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.failure(ForbiddenException())

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.verificationState is SubmitState.Error)
    }

    @Test
    fun `unauthorized verification is reported as an error`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns Result.failure(UnauthorizedException())

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Sesi habis: antrean tidak termuat dan tidak ada aksi yang bisa dikirim.
        assertTrue(viewModel.uiState.value.paymentsState is LoadState.Error)
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.verifyPayment(any(), any(), any()) }
    }

    @Test
    fun `verification can be retried after a failure`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        val calls = intArrayOf(0)
        coEvery { repository.verifyPayment(any(), any(), any()) } answers {
            calls[0]++
            if (calls[0] == 1) {
                Result.failure<OwnerVerificationResult>(
                    ValidationException(message = "Sesi verifikasi bermasalah.")
                )
            } else {
                Result.Success(
                    verificationResult(OwnerVerificationAction.APPROVE, PaymentStatus.PAID)
                )
            }
        }

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.verificationState is SubmitState.Error)

        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.verificationState is SubmitState.Success)
        coVerify(exactly = 2) { repository.verifyPayment(any(), any(), any()) }
    }

    @Test
    fun `queue reload after verification reflects the server state`() = runTest(dispatcher) {
        coEvery { repository.getPendingPayments(any(), any()) } returns queue()
        coEvery { repository.getPaymentProof("1001") } returns Result.Success(proof())
        coEvery { repository.verifyPayment(any(), any(), any()) } returns Result.Success(
            verificationResult(OwnerVerificationAction.APPROVE, PaymentStatus.PAID)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadProof("1001")
        advanceUntilIdle()
        viewModel.verify(OwnerVerificationAction.APPROVE)
        advanceUntilIdle()

        // Setelah verifikasi, Owner memuat ulang antrean untuk melihat keadaan
        // yang benar-benar ada di server.
        coEvery { repository.getPendingPayments(any(), any()) } returns queue(
            listOf(payment(paymentId = "7002", orderId = "1002"))
        )
        viewModel.loadPendingPayments()
        advanceUntilIdle()

        assertEquals(
            listOf("1002"),
            viewModel.uiState.value.payments.map { it.orderId }
        )
    }

    @Test
    fun `verify action is disabled for a payment that is not awaiting verification`() =
        runTest(dispatcher) {
            val waiting = payment(paymentId = "7001", orderId = "1001")
            val unknown = payment(
                paymentId = "7003",
                orderId = "1003",
                status = PaymentStatus.PENDING
            )

            val state = PendingQrisVerificationUiState(
                paymentsState = LoadState.Success(listOf(waiting, unknown))
            )

            // Backend sudah memfilter antrean, tapi kalau suatu saat mengirim
            // baris lain, aksi tetap tidak dibuka untuknya.
            assertTrue(state.canVerify(waiting))
            assertFalse(state.canVerify(unknown))
        }

    // --- Fixtures ---

    private fun createViewModel(orderId: String? = null) =
        PendingQrisVerificationViewModel(
            ownerRepository = repository,
            savedStateHandle = SavedStateHandle(
                buildMap {
                    orderId?.let {
                        put(PendingQrisVerificationViewModel.ARG_ORDER_ID, it)
                    }
                }
            )
        )

    private fun payment(
        paymentId: String,
        orderId: String,
        status: PaymentStatus = PaymentStatus.WAITING_VERIFICATION
    ) = OwnerPendingPayment(
        paymentId = paymentId,
        orderId = orderId,
        orderNumber = "ORD-$orderId",
        customer = null,
        paymentMethod = PaymentMethod.QRIS,
        paymentStatus = status,
        amount = "21000.00",
        hasProof = true
    )

    private fun queue(
        payments: List<OwnerPendingPayment> = listOf(
            payment(paymentId = "7001", orderId = "1001"),
            payment(paymentId = "7002", orderId = "1002")
        ),
        currentPage: Int = 1,
        perPage: Int = 20,
        total: Int = payments.size
    ): Result<com.kyusui.app.domain.model.PaginatedResult<OwnerPendingPayment>> = Result.Success(
        com.kyusui.app.domain.model.PaginatedResult(
            data = payments,
            currentPage = currentPage,
            perPage = perPage,
            total = total
        )
    )

    private fun proof(
        status: PaymentStatus = PaymentStatus.WAITING_VERIFICATION
    ) = OwnerPaymentProof(
        orderId = "1001",
        paymentId = "7001",
        paymentMethod = PaymentMethod.QRIS,
        paymentStatus = status,
        hasProof = true,
        proofUrl = "http://10.0.2.2:8000/api/v1/owner/orders/1001/payment/proof/file"
    )

    private fun verificationResult(
        action: OwnerVerificationAction,
        status: PaymentStatus
    ) = OwnerVerificationResult(
        payment = Payment(
            id = "7001",
            orderId = "1001",
            paymentMethod = PaymentMethod.QRIS,
            paymentStatus = status,
            amount = "21000.00",
            proof = null,
            verifiedBy = if (status == PaymentStatus.PAID) "5" else null,
            verifiedAt = if (status == PaymentStatus.PAID) "2026-09-30T10:15:00Z" else null,
            createdAt = null,
            updatedAt = null
        ),
        action = action
    )
}
