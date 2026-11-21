package com.kyusui.app.ui.customer.home

import com.kyusui.app.core.NoNetworkException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.Order
import com.kyusui.app.domain.model.OrderItem
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.PaginatedResult
import com.kyusui.app.domain.repository.CustomerRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerHomeViewModelTest {

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
    fun `home is loading before the response arrives`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns
            Result.success(page(listOf(order(id = "1001"))))

        val viewModel = CustomerHomeViewModel(repository)

        assertTrue(viewModel.uiState.value.ordersState is LoadState.Loading)
        advanceUntilIdle()
    }

    @Test
    fun `only unfinished orders are exposed as active`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns Result.success(
            page(
                listOf(
                    order(id = "1001", OrderStatus.MENUNGGU_DIPROSES),
                    order(id = "1002", OrderStatus.SELESAI)
                )
            )
        )

        val viewModel = CustomerHomeViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("1001"), viewModel.uiState.value.activeOrders.map { it.id })
    }

    @Test
    fun `order with unknown status stays active`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns
            Result.success(page(listOf(order(id = "1001", null))))

        val viewModel = CustomerHomeViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.activeOrders.size)
    }

    @Test
    fun `active delivery is detected only for trackable statuses`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns Result.success(
            page(
                listOf(
                    order(id = "1001", OrderStatus.DALAM_PENGANTARAN),
                    order(id = "1002", OrderStatus.MENUNGGU_DIPROSES)
                )
            )
        )

        val viewModel = CustomerHomeViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasActiveDelivery)
        assertEquals("1001", state.trackableOrderId)
    }

    @Test
    fun `waiting order has no trackable delivery`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns
            Result.success(page(listOf(order(id = "1001", OrderStatus.MENUNGGU_DIPROSES))))

        val viewModel = CustomerHomeViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasActiveDelivery)
        assertNull(viewModel.uiState.value.trackableOrderId)
    }

    @Test
    fun `empty order list becomes empty state`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns Result.success(page(emptyList()))

        val viewModel = CustomerHomeViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value.ordersState
        assertTrue(state is LoadState.Empty)
        assertEquals(CustomerHomeUiState.EMPTY_MESSAGE, (state as LoadState.Empty).message)
    }

    @Test
    fun `network failure becomes a retryable error`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns
            Result.failure(NoNetworkException())

        val viewModel = CustomerHomeViewModel(repository)
        advanceUntilIdle()

        val error = viewModel.uiState.value.ordersState as LoadState.Error
        assertTrue(error.isRetryable)
        assertFalse(error.isUnauthorized)
    }

    @Test
    fun `unauthorized home error is flagged and not retryable`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns
            Result.failure(UnauthorizedException())

        val viewModel = CustomerHomeViewModel(repository)
        advanceUntilIdle()

        val error = viewModel.uiState.value.ordersState as LoadState.Error
        assertTrue(error.isUnauthorized)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `retry requests the home summary again`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns
            Result.failure(NoNetworkException())

        val viewModel = CustomerHomeViewModel(repository)
        advanceUntilIdle()

        coEvery { repository.getOrders(any(), any(), any()) } returns
            Result.success(page(listOf(order(id = "1001", OrderStatus.MENUNGGU_DIPROSES))))
        viewModel.loadHome()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.activeOrders.size)
        coVerify(exactly = 2) { repository.getOrders(any(), any()) }
    }

    private fun page(orders: List<Order>) = PaginatedResult(
        data = orders,
        currentPage = CustomerHomeViewModel.FIRST_PAGE,
        perPage = CustomerHomeViewModel.PER_PAGE,
        total = orders.size
    )

    private fun order(id: String, status: OrderStatus? = OrderStatus.SELESAI) = Order(
        id = id,
        orderNumber = "ORD-20260930-$id",
        customerId = "10",
        items = listOf(
            OrderItem(
                productId = "1",
                productName = "Air Galon",
                quantity = 1,
                unitPrice = "8000.00",
                lineTotal = "8000.00"
            )
        ),
        subtotalAmount = "8000.00",
        deliveryFee = "0.00",
        totalAmount = "8000.00",
        orderStatus = status,
        payment = null
    )
}
