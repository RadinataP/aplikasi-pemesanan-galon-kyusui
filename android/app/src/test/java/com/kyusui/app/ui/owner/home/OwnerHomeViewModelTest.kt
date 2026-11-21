package com.kyusui.app.ui.owner.home

import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.OwnerDashboardData
import com.kyusui.app.domain.model.OwnerRevenuePoint
import com.kyusui.app.domain.repository.OwnerRepository
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerHomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: OwnerRepository

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.getDashboard() } returns Result.Success(dashboard())
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `dashboard state is mapped from backend repository`() = runTest(dispatcher) {
        val vm = OwnerHomeViewModel(repository)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasAnySummary)
        assertEquals(42, state.dashboard?.totalOrders)
        assertEquals("125000.0", state.dashboard?.monthRevenue)
        assertEquals(6, state.dashboard?.revenueChart?.size)
        coVerify(exactly = 1) { repository.getDashboard() }
    }

    @Test fun `dashboard failure exposes retryable error state`() = runTest(dispatcher) {
        coEvery { repository.getDashboard() } returns Result.failure(ForbiddenException())
        val vm = OwnerHomeViewModel(repository)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.hasAnySummary)
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test fun `retry reloads dashboard summary`() = runTest(dispatcher) {
        coEvery { repository.getDashboard() } returnsMany listOf(
            Result.failure(ForbiddenException()), Result.Success(dashboard())
        )
        val vm = OwnerHomeViewModel(repository)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasAnySummary)
        vm.loadSummary()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasAnySummary)
        coVerify(exactly = 2) { repository.getDashboard() }
    }

    private fun dashboard() = OwnerDashboardData(
        totalOrders = 42, waitingProcess = 5, completedOrders = 30,
        waitingQris = 7, activeDeliveries = 2, availableProducts = 4,
        todayRevenue = "25000.0", monthRevenue = "125000.0",
        monthExpense = "25000.0", monthNet = "100000.0",
        averageOrderValue = "50000.0", completionRate = 71.4,
        revenueChart = (1..6).map { OwnerRevenuePoint("2026-%02d".format(it), "M$it", it * 1000.0, it * 300.0) }
    )
}
