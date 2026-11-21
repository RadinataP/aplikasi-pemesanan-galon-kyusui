package com.kyusui.app.ui.courier.location

import androidx.lifecycle.SavedStateHandle
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.Result
import com.kyusui.app.core.ValidationException
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeliveryOrder
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.domain.model.OrderStatus
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
 * Test layar lokasi pelanggan.
 *
 * Yang dijaga di test ini adalah batas yang sering dilanggar di layar peta:
 *
 * - Backend tidak mengirim koordinat tujuan untuk kurir, hanya alamat teks.
 *   Karena itu state ini tidak punya koordinat tujuan sama sekali, dan tidak
 *   ada nilai yang mengarang titik asal.
 * - Lokasi yang ditampilkan SELALU lokasi yang tercatat server, baik dari
 *   `latest_location` pada Assignment Resource maupun dari response aksi kirim.
 *   Nilai perangkat hanya dipakai sebagai argumen kirim, tidak pernah ditampilkan
 *   seolah sudah disimpan.
 * - Backend yang memutuskan sah atau tidaknya lokasi. `409` dan `422` dari
 *   backend ditampilkan apa adanya dan tidak diubah di Android.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerLocationViewModelTest {

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
    fun `destination address and customer name come from the assignment resource`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Success(
                delivery(status = AssignmentStatus.ACTIVE)
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Jl. Merdeka 1", state.destinationAddress)
            assertEquals("Budi", state.customerName)
            assertTrue(state.canSendLocation)
        }

    @Test
    fun `no destination coordinates are invented when the address is missing`() = runTest(dispatcher) {
        // Backend hanya mengirim `delivery_address` berupa teks. Menampilkan
        // koordinat nol akan terlihat seperti titik tujuan yang diketahui.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ACTIVE, address = null)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.destinationAddress)
    }

    @Test
    fun `last known location is read from the server not from the device`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Success(
                delivery(
                    status = AssignmentStatus.ACTIVE,
                    latestLocation = CourierDeviceLocation(
                        latitude = -0.948,
                        longitude = 100.418,
                        accuracyMeters = 8.5,
                        recordedAt = "2026-01-01T09:30:00.000000Z"
                    )
                )
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            val location = requireNotNull(viewModel.uiState.value.latestLocation)
            assertEquals(-0.948, requireNotNull(location.latitude), 1e-9)
            assertEquals(100.418, requireNotNull(location.longitude), 1e-9)
            assertEquals("2026-01-01T09:30:00.000000Z", location.recordedAt)
            assertTrue(location.hasCoordinates)
        }

    @Test
    fun `missing location is reported as not sent instead of a fake point`() =
        runTest(dispatcher) {
            coEvery { repository.getAssignment("7") } returns Result.Success(
                delivery(status = AssignmentStatus.ACTIVE, latestLocation = null)
            )

            val viewModel = createViewModel("7")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            // Tidak ada titik asal sebagai pengganti: kurir melihat "belum ada
            // lokasi yang dikirim", bukan koordinat yang mengarang asal-usulnya.
            assertNull(state.latestLocation)
            assertTrue(CustomerLocationViewModel.NO_LOCATION_YET_MESSAGE.isNotBlank())
            assertEquals(
                CourierLocationReadStatus.Unknown,
                state.locationReadStatus
            )
        }

    @Test
    fun `successful send shows the location stored by the backend`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ACTIVE)
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
        viewModel.submitFix(fix())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.submitState is SubmitState.Success)
        assertEquals(CustomerLocationViewModel.LOCATION_SENT_MESSAGE, state.message)
        assertEquals(CourierLocationReadStatus.Ready, state.locationReadStatus)
        // Nilai yang tampil adalah milik server, bukan pembacaan perangkat.
        assertEquals("2026-01-01T09:30:00.000000Z", state.latestLocation?.recordedAt)
    }

    @Test
    fun `unprocessable movement is shown as a backend rejection`() = runTest(dispatcher) {
        // Backend membalas 422 bila jarak versus waktu tidak masuk akal, dengan
        // batas 150 km/jam. Android tidak mengoreksi koordinat supaya lolos.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ACTIVE)
        )
        coEvery { repository.updateLocation(any(), any()) } returns Result.Failure(
            ValidationException(message = "Perpindahan lokasi terlalu cepat.")
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()
        viewModel.submitFix(fix())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.submitState is SubmitState.Error)
        assertEquals("Perpindahan lokasi terlalu cepat.", state.errorMessage)
        // Tidak ada lokasi baru yang ditampilkan karena server menolak.
        assertNull(state.latestLocation)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `second press while sending does not post the location twice`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ACTIVE)
        )
        coEvery { repository.updateLocation(any(), any()) } returns Result.Success(
            CourierDeviceLocation(
                latitude = -0.948,
                longitude = 100.418,
                accuracyMeters = 8.5
            )
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        viewModel.submitFix(fix())
        viewModel.submitFix(fix())
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateLocation("7", any()) }
    }

    @Test
    fun `location is not sent when the delivery is not active`() = runTest(dispatcher) {
        // Backend membalas 409 bila assignment belum ACTIVE, jadi Android tidak
        // mengirimnya sejak awal.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ASSIGNED)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canSendLocation)
        viewModel.submitFix(fix())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateLocation(any(), any()) }
    }

    @Test
    fun `location is not sent when the assignment could not be loaded`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Failure(
            ForbiddenException("Anda tidak memiliki akses ke pengantaran ini.")
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.deliveryState is LoadState.Error)
        viewModel.submitFix(fix())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateLocation(any(), any()) }
    }

    @Test
    fun `permission denied is recorded without sending any coordinate`() = runTest(dispatcher) {
        // Izin ditolak berarti tidak ada koordinat yang dikirim. Mengirim titik
        // asal atau titik tujuan akan berarti mengarang lokasi.
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ACTIVE)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        viewModel.setLocationStatus(CourierLocationReadStatus.PermissionDenied)

        assertEquals(
            CourierLocationReadStatus.PermissionDenied,
            viewModel.uiState.value.locationReadStatus
        )
        coVerify(exactly = 0) { repository.updateLocation(any(), any()) }
    }

    @Test
    fun `unavailable reading is recorded so no wrong coordinate is sent`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ACTIVE)
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()

        viewModel.setLocationStatus(CourierLocationReadStatus.Unavailable)

        assertEquals(
            CourierLocationReadStatus.Unavailable,
            viewModel.uiState.value.locationReadStatus
        )
        coVerify(exactly = 0) { repository.updateLocation(any(), any()) }
    }

    @Test
    fun `every read status has a distinct label and explanation`() {
        val statuses = CourierLocationReadStatus.entries
        val labels = statuses.map { it.label }
        val explanations = statuses.map { it.explanation }

        assertEquals(statuses.size, labels.toSet().size)
        assertEquals(statuses.size, explanations.toSet().size)
        labels.forEach { assertTrue(it.isNotBlank()) }
        explanations.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `blank assignment id fails without calling the backend`() = runTest(dispatcher) {
        val viewModel = createViewModel("")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.deliveryState is LoadState.Error)
        assertFalse((state.deliveryState as LoadState.Error).isRetryable)

        viewModel.submitFix(fix())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getAssignment(any()) }
        coVerify(exactly = 0) { repository.updateLocation(any(), any()) }
    }

    @Test
    fun `clear message drops the feedback without dropping the delivery`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ACTIVE)
        )
        coEvery { repository.updateLocation(any(), any()) } returns Result.Success(
            CourierDeviceLocation(
                latitude = -0.948,
                longitude = 100.418,
                accuracyMeters = 8.5
            )
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()
        viewModel.submitFix(fix())
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.message)

        viewModel.clearMessage()

        val state = viewModel.uiState.value
        assertNull(state.message)
        assertTrue(state.submitState is SubmitState.Idle)
        // Assignment yang sudah termuat tetap tampil.
        assertEquals("7", state.delivery?.id)
    }

    @Test
    fun `reload clears the previous submit feedback`() = runTest(dispatcher) {
        coEvery { repository.getAssignment("7") } returns Result.Success(
            delivery(status = AssignmentStatus.ACTIVE)
        )
        coEvery { repository.updateLocation(any(), any()) } returns Result.Success(
            CourierDeviceLocation(
                latitude = -0.948,
                longitude = 100.418,
                accuracyMeters = 8.5
            )
        )

        val viewModel = createViewModel("7")
        advanceUntilIdle()
        viewModel.submitFix(fix())
        advanceUntilIdle()

        viewModel.loadDelivery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.submitState is SubmitState.Idle)
        assertNull(state.message)
    }

    // --- Helper ---

    private fun createViewModel(assignmentId: String) = CustomerLocationViewModel(
        courierRepository = repository,
        savedStateHandle = SavedStateHandle(
            mapOf(CustomerLocationViewModel.ARG_ASSIGNMENT_ID to assignmentId)
        )
    )

    private fun fix() = CourierLocationFix(
        point = GeoPoint(-0.948, 100.418),
        accuracyMeters = 8.5
    )

    private fun delivery(
        status: AssignmentStatus?,
        address: String? = "Jl. Merdeka 1",
        latestLocation: CourierDeviceLocation? = null
    ) = CourierDelivery(
        id = "7",
        status = status,
        latestLocation = latestLocation,
        order = CourierDeliveryOrder(
            id = "21",
            status = OrderStatus.DALAM_PENGANTARAN,
            subtotalAmount = "16000.00",
            totalAmount = "21000.00",
            deliveryAddress = address,
            customerName = "Budi"
        )
    )
}
