package com.kyusui.app.ui.customer.orders

import androidx.lifecycle.SavedStateHandle
import com.kyusui.app.core.ConflictException
import com.kyusui.app.core.NoNetworkException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.Order
import com.kyusui.app.domain.model.OrderItem
import com.kyusui.app.domain.model.OrderPayment
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.repository.CustomerRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModelTest {

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
    fun `detail is loading before the response arrives`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.success(order())

        val viewModel = viewModel("1001")

        assertTrue(viewModel.uiState.value is LoadState.Loading)
        advanceUntilIdle()
    }

    @Test
    fun `order totals come straight from the backend`() = runTest(dispatcher) {
        val backendOrder = order(
            subtotal = "16000.00",
            deliveryFee = "5000.00",
            total = "21000.00"
        )
        coEvery { repository.getOrder("1001") } returns Result.success(backendOrder)

        val viewModel = viewModel("1001")
        advanceUntilIdle()

        val state = viewModel.uiState.value as LoadState.Success
        assertEquals("16000.00", state.data.subtotalAmount)
        assertEquals("5000.00", state.data.deliveryFee)
        assertEquals("21000.00", state.data.totalAmount)
    }

    @Test
    fun `network failure becomes a retryable error`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.failure(NoNetworkException())

        val viewModel = viewModel("1001")
        advanceUntilIdle()

        val error = viewModel.uiState.value as LoadState.Error
        assertTrue(error.isRetryable)
        assertFalse(error.isUnauthorized)
    }

    @Test
    fun `unauthorized detail is flagged and not retryable`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.failure(UnauthorizedException())

        val viewModel = viewModel("1001")
        advanceUntilIdle()

        val error = viewModel.uiState.value as LoadState.Error
        assertTrue(error.isUnauthorized)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `retry requests the same order again`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns Result.failure(NoNetworkException())

        val viewModel = viewModel("1001")
        advanceUntilIdle()

        coEvery { repository.getOrder("1001") } returns Result.success(order())
        viewModel.loadOrder()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LoadState.Success)
        coVerify(exactly = 2) { repository.getOrder("1001") }
    }

    @Test
    fun `blank order id fails fast without hitting the backend`() = runTest(dispatcher) {
        val viewModel = viewModel("")

        val error = viewModel.uiState.value as LoadState.Error
        assertFalse(error.isRetryable)
        coVerify(exactly = 0) { repository.getOrder(any()) }
    }

    @Test
    fun `unknown backend status is not coerced into a business status`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns
            Result.success(order(status = "STATUS_BARU_DARIAN_BACKEND"))

        val viewModel = viewModel("1001")
        advanceUntilIdle()

        val state = viewModel.uiState.value as LoadState.Success
        assertNull(state.data.orderStatus)
    }

    @Test
    fun `unknown payment status is not coerced either`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns
            Result.success(order(paymentStatus = "PROCESSING"))

        val viewModel = viewModel("1001")
        advanceUntilIdle()

        val state = viewModel.uiState.value as LoadState.Success
        assertNull(state.data.payment?.paymentStatus)
    }

    // --- Pembatalan order ---

    @Test
    fun `cancel is sent while the order is still waiting for payment`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns
            Result.success(order(status = "MENUNGGU_PEMBAYARAN"))
        coEvery { repository.cancelOrder("1001") } returns
            Result.success(order(status = "DIBATALKAN"))

        val viewModel = viewModel("1001")
        advanceUntilIdle()

        viewModel.cancelOrder()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.cancelOrder("1001") }
        assertTrue(viewModel.cancelState.value is SubmitState.Success)
        // Status `DIBATALKAN` berasal dari response, bukan ditulis Android.
        val state = viewModel.uiState.value as LoadState.Success
        assertEquals(OrderStatus.DIBATALKAN, state.data.orderStatus)
    }

    @Test
    fun `cancel status is taken from the server response`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns
            Result.success(order(status = "MENUNGGU_PEMBAYARAN"))
        // Backend boleh menjawab dengan status lain; Android mengikuti response.
        coEvery { repository.cancelOrder("1001") } returns
            Result.success(order(status = "MENUNGGU_PEMBAYARAN"))

        val viewModel = viewModel("1001")
        advanceUntilIdle()
        viewModel.cancelOrder()
        advanceUntilIdle()

        val state = viewModel.uiState.value as LoadState.Success
        assertEquals(OrderStatus.MENUNGGU_PEMBAYARAN, state.data.orderStatus)
    }

    @Test
    fun `cancel is not sent once the order leaves the waiting payment stage`() =
        runTest(dispatcher) {
            coEvery { repository.getOrder("1001") } returns
                Result.success(order(status = "DIPROSES"))

            val viewModel = viewModel("1001")
            advanceUntilIdle()
            viewModel.cancelOrder()
            advanceUntilIdle()

            // Pembatalan hanya diizinkan backend pada `MENUNGGU_PEMBAYARAN`;
            // Android tidak mengirim request yang pasti ditolak.
            coVerify(exactly = 0) { repository.cancelOrder(any()) }
            assertEquals(SubmitState.Idle, viewModel.cancelState.value)
        }

    @Test
    fun `duplicate cancel is sent only once`() = runTest(dispatcher) {
        coEvery { repository.getOrder("1001") } returns
            Result.success(order(status = "MENUNGGU_PEMBAYARAN"))
        coEvery { repository.cancelOrder("1001") } returns
            Result.success(order(status = "DIBATALKAN"))

        val viewModel = viewModel("1001")
        advanceUntilIdle()
        viewModel.cancelOrder()
        viewModel.cancelOrder()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.cancelOrder("1001") }
    }

    @Test
    fun `conflict on cancel is shown as an error and keeps the order`() =
        runTest(dispatcher) {
            coEvery { repository.getOrder("1001") } returns
                Result.success(order(status = "MENUNGGU_PEMBAYARAN"))
            coEvery { repository.cancelOrder("1001") } returns
                Result.failure(ConflictException(message = "Pesanan tidak dapat dibatalkan."))

            val viewModel = viewModel("1001")
            advanceUntilIdle()
            viewModel.cancelOrder()
            advanceUntilIdle()

            assertTrue(viewModel.cancelState.value is SubmitState.Error)
            // Order tetap menampilkan status terakhir dari server; Android tidak
            // menebak `DIBATALKAN` setelah backend menolak.
            val state = viewModel.uiState.value as LoadState.Success
            assertEquals(OrderStatus.MENUNGGU_PEMBAYARAN, state.data.orderStatus)
        }

    private fun viewModel(orderId: String) = OrderDetailViewModel(
        customerRepository = repository,
        savedStateHandle = SavedStateHandle(mapOf(OrderDetailViewModel.ARG_ORDER_ID to orderId))
    )

    private fun order(
        subtotal: String = "16000.00",
        deliveryFee: String = "0.00",
        total: String = "16000.00",
        status: String = "MENUNGGU_DIPROSES",
        paymentStatus: String = "PENDING"
    ) = Order(
        id = "1001",
        orderNumber = "ORD-20260930-0001",
        customerId = "10",
        items = listOf(
            OrderItem(
                productId = "1",
                productName = "Air Galon",
                quantity = 2,
                unitPrice = "8000.00",
                lineTotal = "16000.00"
            )
        ),
        subtotalAmount = subtotal,
        deliveryFee = deliveryFee,
        totalAmount = total,
        orderStatus = OrderStatus.fromApiValue(status),
        payment = OrderPayment(
            id = "7001",
            paymentMethod = PaymentMethod.CASH,
            paymentStatus = PaymentStatus.fromApiValue(paymentStatus),
            amount = total
        )
    )
}