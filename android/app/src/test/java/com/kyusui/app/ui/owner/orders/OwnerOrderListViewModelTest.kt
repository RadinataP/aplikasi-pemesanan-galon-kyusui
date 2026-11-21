package com.kyusui.app.ui.owner.orders

import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OwnerOrder
import com.kyusui.app.domain.model.PaginatedResult
import com.kyusui.app.domain.repository.OwnerRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test daftar order Owner.
 *
 * Yang dijaga test ini:
 *
 * - filter diteruskan sebagai query `status` apa adanya dari enum canonical;
 * - `ALL` berarti tidak mengirim query `status` sama sekali;
 * - order yang tampil tidak pernah difilter ulang di Android;
 * - halaman yang gagal tidak menghapus order yang sudah tampil;
 * - `401` dan `403` dibedakan agar layar bisa mengarahkan ke login hanya pada
 *   `401`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerOrderListViewModelTest {

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

    @Test
    fun `first page is requested on init without a status filter`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns pageOfOrders()

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.getOrders(
                page = 1,
                perPage = OwnerOrderListViewModel.PER_PAGE,
                status = null
            )
        }
        assertEquals(2, viewModel.uiState.value.orders.size)
        assertFalse(viewModel.uiState.value.isUnauthorized)
    }

    @Test
    fun `all filter sends no status query`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns pageOfOrders()

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()
        viewModel.setStatusFilter(OwnerOrderFilter.ALL)
        advanceUntilIdle()

        coVerify(exactly = 2) {
            repository.getOrders(any(), any(), null)
        }
        assertEquals(OwnerOrderFilter.ALL, viewModel.uiState.value.activeFilter)
    }

    @Test
    fun `status filter is forwarded to the backend unchanged`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns pageOfOrders()

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()
        viewModel.setStatusFilter(OwnerOrderFilter.MENUNGGU_DIPROSES)
        advanceUntilIdle()

        // Filter adalah query backend, bukan filter lokal. Nilai dikirim apa
        // adanya dari enum canonical.
        coVerify(exactly = 1) {
            repository.getOrders(any(), any(), OrderStatus.MENUNGGU_DIPROSES)
        }
        assertEquals(OwnerOrderFilter.MENUNGGU_DIPROSES, viewModel.uiState.value.activeFilter)
    }

    @Test
    fun `changing filter reloads from the first page`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns pageOfOrders()

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()
        viewModel.setStatusFilter(OwnerOrderFilter.DIPROSES)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.getOrders(page = 1, perPage = any(), status = OrderStatus.DIPROSES)
        }
        assertEquals(1, viewModel.uiState.value.page)
    }

    @Test
    fun `orders from the backend are displayed without local filtering`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns pageOfOrders(
            orders = listOf(
                order(id = "1", status = OrderStatus.SELESAI),
                order(id = "2", status = OrderStatus.MENUNGGU_DIPROSES),
                order(id = "3", status = null)
            )
        )

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()

        // Order dengan status tidak dikenal tetap tampil. Android tidak
        // menyembunyikan order hanya karena statusnya tidak dimengerti.
        assertEquals(listOf("1", "2", "3"), viewModel.uiState.value.orders.map { it.id })
    }

    @Test
    fun `empty first page is an empty state with a message`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns pageOfOrders(emptyList())

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value.ordersState
        assertTrue(state is LoadState.Empty)
        assertEquals(
            OwnerOrderListViewModel.EMPTY_ORDERS_MESSAGE,
            (state as LoadState.Empty).message
        )
    }

    @Test
    fun `unauthorized load is reported as an error with unauthorized flag`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns Result.failure(UnauthorizedException())

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value.ordersState
        assertTrue(state is LoadState.Error)
        assertTrue((state as LoadState.Error).isUnauthorized)
    }

    @Test
    fun `forbidden load is reported as an error that is not unauthorized`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns Result.failure(ForbiddenException())

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value.ordersState
        assertTrue(state is LoadState.Error)
        // 403 dan 401 dibedakan supaya layar bisa mengarahkan Owner ke login
        // hanya pada 401.
        assertFalse((state as LoadState.Error).isUnauthorized)
    }

    @Test
    fun `load failure after success replaces the list with an error`() = runTest(dispatcher) {
        // Panggilan pertama (dipicu init) sukses, berikutnya ditolak backend.
        // Owner menekan tombol muat ulang, jadi error baru harus tampil dan
        // bukan memakai order lama yang sudah tidak berlaku.
        val calls = intArrayOf(0)
        coEvery { repository.getOrders(any(), any(), any()) } answers {
            calls[0]++
            if (calls[0] == 1) pageOfOrders() else Result.failure(ForbiddenException())
        }

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.orders.size)

        viewModel.loadOrders()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.ordersState is LoadState.Error)
        assertTrue(viewModel.uiState.value.orders.isEmpty())
    }

    @Test
    fun `next page is appended and merged`() = runTest(dispatcher) {
        coEvery { repository.getOrders(page = 1, perPage = any(), status = null) } returns
            pageOfOrders(listOf(order(id = "1")), currentPage = 1, perPage = 1, total = 3)
        coEvery { repository.getOrders(page = 2, perPage = any(), status = null) } returns
            pageOfOrders(listOf(order(id = "2")), currentPage = 2, perPage = 1, total = 3)

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasMore)

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("1", "2"), viewModel.uiState.value.orders.map { it.id })
        assertEquals(2, viewModel.uiState.value.page)
    }

    @Test
    fun `next page is not requested when there is no more data`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns pageOfOrders(
            listOf(order(id = "1")),
            currentPage = 1,
            perPage = 20,
            total = 1
        )

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasMore)

        viewModel.loadNextPage()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getOrders(any(), any(), any()) }
    }

    @Test
    fun `duplicate rows across pages are merged by order id`() = runTest(dispatcher) {
        coEvery { repository.getOrders(page = 1, perPage = any(), status = null) } returns
            pageOfOrders(listOf(order(id = "1")), currentPage = 1, perPage = 1, total = 2)
        // Backend mengirim ulang order yang sama, misalnya karena data berubah
        // di antara dua request.
        coEvery { repository.getOrders(page = 2, perPage = any(), status = null) } returns
            pageOfOrders(listOf(order(id = "1"), order(id = "2")), currentPage = 2, perPage = 2, total = 3)

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("1", "2"), viewModel.uiState.value.orders.map { it.id })
    }

    @Test
    fun `failed append keeps the loaded orders and stops further pages`() = runTest(dispatcher) {
        coEvery { repository.getOrders(page = 1, perPage = any(), status = null) } returns
            pageOfOrders(listOf(order(id = "1")), currentPage = 1, perPage = 1, total = 5)
        coEvery { repository.getOrders(page = 2, perPage = any(), status = null) } returns Result.failure(ForbiddenException())

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        // Order yang sudah tampil tidak dihapus karena satu halaman gagal, dan
        // hasMore dimatikan supaya scroll tidak mengulang request yang sama.
        assertEquals(listOf("1"), viewModel.uiState.value.orders.map { it.id })
        assertFalse(viewModel.uiState.value.hasMore)
        assertFalse(viewModel.uiState.value.isAppending)
        assertTrue(viewModel.uiState.value.ordersState is LoadState.Success)
    }

    @Test
    fun `append is ignored while another append is running`() = runTest(dispatcher) {
        coEvery { repository.getOrders(page = 1, perPage = any(), status = null) } returns
            pageOfOrders(listOf(order(id = "1")), currentPage = 1, perPage = 1, total = 5)

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()

        viewModel.loadNextPage()
        viewModel.loadNextPage()
        advanceUntilIdle()

        // Dua panggilan loadNextPage pada frame yang sama harus menghasilkan satu
        // request saja untuk halaman yang sama.
        coVerify(exactly = 1) { repository.getOrders(page = 2, perPage = any(), status = null) }
    }

    @Test
    fun `append is ignored while the list is still loading`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns Result.failure(ForbiddenException())

        val viewModel = OwnerOrderListViewModel(repository)
        viewModel.loadNextPage()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getOrders(any(), any(), any()) }
    }

    @Test
    fun `amounts shown in the list come from the backend`() = runTest(dispatcher) {
        coEvery { repository.getOrders(any(), any(), any()) } returns pageOfOrders(
            listOf(order(id = "1", total = "21000.00", deliveryFee = "5000.00"))
        )

        val viewModel = OwnerOrderListViewModel(repository)
        advanceUntilIdle()

        assertEquals("21000.00", viewModel.uiState.value.orders.single().totalAmount)
        assertEquals("5000.00", viewModel.uiState.value.orders.single().deliveryFee)
    }

    // --- Fixtures ---

    private fun order(
        id: String,
        status: OrderStatus? = OrderStatus.MENUNGGU_DIPROSES,
        total: String = "16000.00",
        deliveryFee: String = "0.00"
    ) = OwnerOrder(
        id = id,
        orderNumber = "ORD-$id",
        customerId = "10",
        items = emptyList(),
        subtotalAmount = total,
        deliveryFee = deliveryFee,
        totalAmount = total,
        orderStatus = status
    )

    private fun pageOfOrders(
        orders: List<OwnerOrder> = listOf(order("1"), order("2")),
        currentPage: Int = 1,
        perPage: Int = 20,
        total: Int = orders.size
    ): Result<PaginatedResult<OwnerOrder>> = Result.Success(
        PaginatedResult(
            data = orders,
            currentPage = currentPage,
            perPage = perPage,
            total = total
        )
    )
}