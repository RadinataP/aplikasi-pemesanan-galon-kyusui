package com.kyusui.app.ui.courier.assignments

import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeliveryOrder
import com.kyusui.app.domain.model.CourierDeliveryPayment
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.repository.CourierRepository
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
 * Test daftar assignment dari `GET courier/assignments`.
 *
 * Dua hal yang dijaga di sini:
 *
 * 1. Backend tidak menerima query apa pun pada endpoint ini dan tidak
 *    dipaginasi. Mengganti filter TIDAK boleh menyebabkan request baru, karena
 *    tidak ada parameter yang bisa dikirim.
 * 2. `Empty` dan `Error` tetap dibedakan. `Empty` berarti backend bilang kurir
 *    tidak punya penugasan; `Error` berarti backend tidak bisa menjawab. Kalau
 *    keduanya disamakan, kurir melihat "belum ada pengantaran" saat sebenarnya
 *    jaringannya bermasalah.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssignedOrderViewModelTest {

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

    @Test
    fun `assignments are loaded once without any filter parameter`() = runTest(dispatcher) {
        coEvery { repository.getAssignments() } returns Result.Success(
            listOf(
                delivery(id = "7", status = AssignmentStatus.ASSIGNED),
                delivery(id = "8", status = AssignmentStatus.ACTIVE),
                delivery(id = "9", status = AssignmentStatus.COMPLETED)
            )
        )

        val viewModel = AssignedOrderViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.listState is LoadState.Success)
        assertEquals(3, state.deliveries.size)
        assertEquals(CourierAssignmentFilter.ALL, state.filter)
        coVerify(exactly = 1) { repository.getAssignments() }
    }

    @Test
    fun `changing the filter does not call the backend again`() = runTest(dispatcher) {
        // `GET courier/assignments` menerima tanpa query. Filter hanya menyaring
        // data yang sudah diterima, jadi tidak boleh ada request tambahan.
        coEvery { repository.getAssignments() } returns Result.Success(
            listOf(
                delivery(id = "7", status = AssignmentStatus.ASSIGNED),
                delivery(id = "8", status = AssignmentStatus.ACTIVE),
                delivery(id = "9", status = AssignmentStatus.COMPLETED)
            )
        )

        val viewModel = AssignedOrderViewModel(repository)
        advanceUntilIdle()

        viewModel.setFilter(CourierAssignmentFilter.ASSIGNED)
        assertEquals(1, viewModel.uiState.value.visibleDeliveries.size)
        assertEquals("7", viewModel.uiState.value.visibleDeliveries.single().id)

        viewModel.setFilter(CourierAssignmentFilter.ACTIVE)
        assertEquals("8", viewModel.uiState.value.visibleDeliveries.single().id)

        viewModel.setFilter(CourierAssignmentFilter.COMPLETED)
        assertEquals("9", viewModel.uiState.value.visibleDeliveries.single().id)

        viewModel.setFilter(CourierAssignmentFilter.ALL)
        assertEquals(3, viewModel.uiState.value.visibleDeliveries.size)

        coVerify(exactly = 1) { repository.getAssignments() }
    }

    @Test
    fun `filter without rows is reported separately from an empty backend list`() =
        runTest(dispatcher) {
            // Data ada, filter tidak punya baris. Menampilkan "belum ada
            // pengantaran" di sini akan membuat kurir mengira backend tidak
            // mengirim apa pun.
            coEvery { repository.getAssignments() } returns Result.Success(
                listOf(delivery(id = "7", status = AssignmentStatus.ASSIGNED))
            )

            val viewModel = AssignedOrderViewModel(repository)
            advanceUntilIdle()

            viewModel.setFilter(CourierAssignmentFilter.COMPLETED)

            val state = viewModel.uiState.value
            assertTrue(state.listState is LoadState.Success)
            assertEquals(1, state.deliveries.size)
            assertTrue(state.filteredIsEmpty)
            assertTrue(state.visibleDeliveries.isEmpty())
        }

    @Test
    fun `empty list from the backend becomes the empty state`() = runTest(dispatcher) {
        coEvery { repository.getAssignments() } returns Result.Success(emptyList())

        val viewModel = AssignedOrderViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.listState is LoadState.Empty)
        assertEquals(
            AssignedOrderViewModel.EMPTY_MESSAGE,
            (state.listState as LoadState.Empty).message
        )
        assertTrue(state.deliveries.isEmpty())
        // Daftar kosong dari backend bukan error, jadi `filteredIsEmpty` tidak
        // ikut menyala.
        assertFalse(state.filteredIsEmpty)
    }

    @Test
    fun `failure never becomes an empty list`() = runTest(dispatcher) {
        coEvery { repository.getAssignments() } returns Result.Failure(
            UnauthorizedException("Token tidak valid.")
        )

        val viewModel = AssignedOrderViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.listState is LoadState.Error)
        assertFalse(state.listState is LoadState.Empty)
        assertFalse((state.listState as LoadState.Error).isRetryable)
    }

    @Test
    fun `refresh failure keeps the rows that are already on screen`() = runTest(dispatcher) {
        // Pola ini harus sama dengan beranda kurir: kegagalan muat ulang pada
        // daftar yang sudah tampil tidak boleh menghapus daftar itu. Di jaringan
        // seluler kegagalan sesaat sangat mungkin terjadi, dan kurir yang sedang
        // membaca daftarnya tidak boleh kehilangan isinya.
        coEvery { repository.getAssignments() } returnsMany listOf(
            Result.Success(listOf(delivery(id = "7", status = AssignmentStatus.ACTIVE))),
            Result.Failure(ForbiddenException("Anda tidak memiliki akses."))
        )

        val viewModel = AssignedOrderViewModel(repository)
        advanceUntilIdle()

        viewModel.loadAssignments(force = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.listState is LoadState.Success)
        assertEquals("7", state.deliveries.single().id)
        assertFalse(state.isRefreshing)
        // Kegagalan reported terpisah, bukan dengan menghapus isi layar.
        assertEquals("Akses ditolak", state.refreshErrorMessage)
    }

    @Test
    fun `successful refresh clears the previous refresh error`() = runTest(dispatcher) {
        coEvery { repository.getAssignments() } returnsMany listOf(
            Result.Success(listOf(delivery(id = "7", status = AssignmentStatus.ACTIVE))),
            Result.Failure(ForbiddenException("Anda tidak memiliki akses.")),
            Result.Success(listOf(delivery(id = "8", status = AssignmentStatus.ASSIGNED)))
        )

        val viewModel = AssignedOrderViewModel(repository)
        advanceUntilIdle()
        viewModel.loadAssignments(force = true)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.refreshErrorMessage)

        viewModel.loadAssignments(force = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.refreshErrorMessage)
        assertEquals("8", state.deliveries.single().id)
    }

    @Test
    fun `refresh failure without previous rows becomes a full load error`() = runTest(dispatcher) {
        // Tanpa baris yang tampil tidak ada yang bisa dipertahankan, jadi
        // kegagalan harus menjadi layar error penuh.
        coEvery { repository.getAssignments() } returnsMany listOf(
            Result.Failure(ForbiddenException("Anda tidak memiliki akses.")),
            Result.Failure(ForbiddenException("Anda tidak memiliki akses."))
        )

        val viewModel = AssignedOrderViewModel(repository)
        advanceUntilIdle()
        viewModel.loadAssignments(force = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.listState is LoadState.Error)
        assertNull(state.refreshErrorMessage)
    }

    @Test
    fun `refresh while a request is running is ignored`() = runTest(dispatcher) {
        coEvery { repository.getAssignments() } returns Result.Success(
            listOf(delivery(id = "7", status = AssignmentStatus.ASSIGNED))
        )

        val viewModel = AssignedOrderViewModel(repository)
        viewModel.loadAssignments(force = true)
        viewModel.loadAssignments(force = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getAssignments() }
    }

    @Test
    fun `clear filter returns to showing every assignment`() = runTest(dispatcher) {
        coEvery { repository.getAssignments() } returns Result.Success(
            listOf(
                delivery(id = "7", status = AssignmentStatus.ASSIGNED),
                delivery(id = "8", status = AssignmentStatus.COMPLETED)
            )
        )

        val viewModel = AssignedOrderViewModel(repository)
        advanceUntilIdle()

        viewModel.setFilter(CourierAssignmentFilter.ACTIVE)
        viewModel.clearFilter()

        assertEquals(CourierAssignmentFilter.ALL, viewModel.uiState.value.filter)
        assertEquals(2, viewModel.uiState.value.visibleDeliveries.size)
    }

    @Test
    fun `counts come from the same rows the courier sees`() = runTest(dispatcher) {
        // Angka "belum dimulai" dan "sedang diantar" dihitung dari daftar yang
        // sama, sehingga tidak mungkin berbeda dari isi layar.
        coEvery { repository.getAssignments() } returns Result.Success(
            listOf(
                delivery(id = "7", status = AssignmentStatus.ASSIGNED),
                delivery(id = "8", status = AssignmentStatus.ASSIGNED),
                delivery(id = "9", status = AssignmentStatus.ACTIVE),
                delivery(id = "10", status = AssignmentStatus.COMPLETED)
            )
        )

        val viewModel = AssignedOrderViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.pendingStartCount)
        assertEquals(1, state.activeCount)
    }

    @Test
    fun `unknown assignment status is not counted into any bucket`() = runTest(dispatcher) {
        // Status yang tidak dikenal tidak boleh dihitung sebagai ASSIGNED,
        // karena itu akan membuat kurir mengira ada yang perlu dikerjakan.
        coEvery { repository.getAssignments() } returns Result.Success(
            listOf(
                delivery(id = "7", status = null),
                delivery(id = "8", status = AssignmentStatus.ACTIVE)
            )
        )

        val viewModel = AssignedOrderViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.pendingStartCount)
        assertEquals(1, state.activeCount)
        // Baris tetap tampil pada filter ALL, jadi nilainya tidak hilang.
        assertEquals(2, state.visibleDeliveries.size)
        // Tapi tidak masuk filter mana pun yang berbasis status.
        viewModel.setFilter(CourierAssignmentFilter.ASSIGNED)
        assertTrue(viewModel.uiState.value.filteredIsEmpty)
    }

    @Test
    fun `filter matches only the canonical status it declares`() {
        val assigned = delivery(id = "7", status = AssignmentStatus.ASSIGNED)

        assertTrue(CourierAssignmentFilter.ALL.matches(assigned))
        assertTrue(CourierAssignmentFilter.ASSIGNED.matches(assigned))
        assertFalse(CourierAssignmentFilter.ACTIVE.matches(assigned))
        assertFalse(CourierAssignmentFilter.COMPLETED.matches(assigned))
        // Status null tidak cocok dengan filter status apa pun.
        assertTrue(CourierAssignmentFilter.ALL.matches(delivery(id = "8", status = null)))
        assertFalse(CourierAssignmentFilter.ACTIVE.matches(delivery(id = "8", status = null)))
    }

    @Test
    fun `filter labels are Bahasa Indonesia and complete`() {
        val labels = CourierAssignmentFilter.entries.map { it.label }

        assertEquals(CourierAssignmentFilter.entries.size, labels.size)
        assertEquals(CourierAssignmentFilter.entries.size, labels.toSet().size)
        labels.forEach { assertTrue(it.isNotBlank()) }

        // Setiap filter status memakai nilai canonical dari `AssignmentStatus`,
        // bukan string yang diketik di layar, dan tepat satu filter yang tidak
        // menyaring sama sekali.
        assertEquals(1, CourierAssignmentFilter.entries.count { it.status == null })
        assertEquals(
            setOf(
                AssignmentStatus.ASSIGNED,
                AssignmentStatus.ACTIVE,
                AssignmentStatus.COMPLETED
            ),
            CourierAssignmentFilter.entries.mapNotNull { it.status }.toSet()
        )
    }

    // --- Helper ---

    private fun delivery(
        id: String,
        status: AssignmentStatus?,
        paymentStatus: PaymentStatus = PaymentStatus.PENDING
    ) = CourierDelivery(
        id = id,
        status = status,
        order = CourierDeliveryOrder(
            id = id.toInt().toString(),
            status = OrderStatus.DITUGASKAN,
            subtotalAmount = "16000.00",
            totalAmount = "21000.00",
            deliveryAddress = "Jl. Merdeka $id",
            customerName = "Budi $id",
            payment = CourierDeliveryPayment(
                paymentMethod = PaymentMethod.CASH,
                paymentStatus = paymentStatus
            )
        )
    )
}
