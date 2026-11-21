package com.kyusui.app.ui.courier.tracking

import androidx.lifecycle.SavedStateHandle
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.domain.repository.CourierRepository
import com.kyusui.app.ui.location.LocationPermissionState
import com.kyusui.app.ui.state.LoadState
import com.kyusui.app.ui.state.SubmitState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
 * TRK-003 dan TRK-005 untuk sisi kurir, plus batas keamanan assignment.
 *
 * Sejak Batch 2, loop acquisition dan pengiriman posisi hidup di
 * [CourierLocationShareController] dan diuji di [CourierLocationShareControllerTest].
 * ViewModel di sini hanya menguji delegasi: meneruskan perintah ke
 * [CourierLocationSharing], menghentikan session saat backend atau izin
 * menyatakannya tidak sah, dan mencerminkan state controller ke layar — hanya
 * untuk session milik assignment layar ini.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CourierTrackingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var courierRepository: CourierRepository
    private lateinit var sharing: FakeCourierLocationSharing

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        courierRepository = mockk(relaxed = true)
        sharing = FakeCourierLocationSharing()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- TRK-003: pengiriman posisi (delegasi) ---

    @Test
    fun `startTracking for an active delivery starts sharing for the assignment in the route`() = runTest(dispatcher) {
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.success(activeDelivery())

        val viewModel = viewModel()
        runCurrent()
        viewModel.startTracking()
        runCurrent()

        assertEquals(listOf(ASSIGNMENT_ID), sharing.started)
        assertTrue(viewModel.uiState.value.isTracking)
    }

    @Test
    fun `an assignment that was never active cannot start sharing`() = runTest(dispatcher) {
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.success(assignedDelivery())

        val viewModel = viewModel()
        runCurrent()
        viewModel.startTracking()
        runCurrent()

        assertTrue(sharing.started.isEmpty())
        assertFalse(viewModel.uiState.value.isTracking)
    }

    @Test
    fun `a delivery that fails to load does not start sharing`() = runTest(dispatcher) {
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.failure(ForbiddenException())

        val viewModel = viewModel()
        runCurrent()
        viewModel.startTracking()
        runCurrent()

        val state = viewModel.uiState.value.deliveryState
        assertTrue(state is LoadState.Error)
        assertFalse(viewModel.uiState.value.isTracking)
        assertTrue(sharing.started.isEmpty())
    }

    // --- State controller dicerminkan ke layar ---

    @Test
    fun `sharing state for this assignment is mirrored into the screen state`() = runTest(dispatcher) {
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.success(activeDelivery())

        val viewModel = viewModel()
        runCurrent()

        sharing.publish(
            CourierLocationShareState(
                sessionAssignmentId = ASSIGNMENT_ID,
                isSharing = true,
                locationReadStatus = LocationReadStatus.Ready,
                submitState = SubmitState.Success(ASSIGNMENT_ID),
                currentFix = FIX,
                lastReportedLocation = SERVER_LOCATION
            )
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isTracking)
        assertEquals(LocationReadStatus.Ready, state.locationReadStatus)
        assertEquals(FIX, state.currentFix)
        // Posisi yang ditampilkan adalah milik server, bukan pembacaan lokal.
        assertEquals(SERVER_LOCATION, state.latestLocation)
    }

    @Test
    fun `a refused submission from the controller is shown as the official state`() = runTest(dispatcher) {
        // Backend menolak dengan 403 bila assignment bukan milik kurir yang
        // sedang login. Controller yang menangani penolakan; ViewModel hanya
        // mencerminkan apa adanya.
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.success(activeDelivery())

        val viewModel = viewModel()
        runCurrent()

        sharing.publish(
            CourierLocationShareState(
                sessionAssignmentId = ASSIGNMENT_ID,
                isSharing = false,
                stopReason = TrackingStopReason.DELIVERY_NOT_ACTIVE,
                submitState = SubmitState.Error(message = "Akses ditolak", isRetryable = false)
            )
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.lastReportedLocation)
        val error = state.submitState as SubmitState.Error
        assertEquals("Akses ditolak", error.message)
        assertFalse(error.isRetryable)
        assertEquals(TrackingStopReason.DELIVERY_NOT_ACTIVE, state.stopReason)
    }

    @Test
    fun `sharing state for another assignment never leaks into this screen`() = runTest(dispatcher) {
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.success(activeDelivery())

        val viewModel = viewModel()
        runCurrent()
        viewModel.startTracking()
        runCurrent()
        assertTrue(viewModel.uiState.value.isTracking)

        // Sesuatu (assignment lain) menyalakan session 8. Layar assignment 7
        // tidak boleh ikut menampilkannya.
        sharing.publish(
            CourierLocationShareState(
                sessionAssignmentId = OTHER_ASSIGNMENT_ID,
                isSharing = true,
                locationReadStatus = LocationReadStatus.Locating
            )
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.isTracking)
        assertEquals(ASSIGNMENT_ID, sharing.started.singleOrNull())
    }

    // --- TRK-005: tracking berhenti ---

    @Test
    fun `stopTracking by hand delegates the user request and mirrors it`() = runTest(dispatcher) {
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.success(activeDelivery())

        val viewModel = viewModel()
        runCurrent()
        viewModel.startTracking()
        runCurrent()
        assertTrue(viewModel.uiState.value.isTracking)

        viewModel.stopTracking()
        runCurrent()

        assertEquals(listOf(TrackingStopReason.USER_REQUESTED), sharing.stoppedReasons)
        assertFalse(viewModel.uiState.value.isTracking)
        assertEquals(TrackingStopReason.USER_REQUESTED, viewModel.uiState.value.stopReason)
    }

    @Test
    fun `a completed delivery stops sharing with delivery not active`() = runTest(dispatcher) {
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.success(activeDelivery())

        val viewModel = viewModel()
        runCurrent()
        viewModel.startTracking()
        runCurrent()
        assertTrue(viewModel.uiState.value.isTracking)

        // Backend sekarang melaporkan assignment sudah selesai.
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.success(completedDelivery())
        viewModel.loadDelivery()
        runCurrent()

        assertEquals(listOf(TrackingStopReason.DELIVERY_NOT_ACTIVE), sharing.stoppedReasons)
        assertFalse(viewModel.uiState.value.isTracking)
        assertEquals(TrackingStopReason.DELIVERY_NOT_ACTIVE, viewModel.uiState.value.stopReason)
    }

    // --- GPS-002 / GPS-003 pada sisi kurir ---

    @Test
    fun `a permanently denied permission stops sharing`() = runTest(dispatcher) {
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.success(activeDelivery())

        val viewModel = viewModel()
        runCurrent()
        viewModel.startTracking()
        runCurrent()
        assertTrue(viewModel.uiState.value.isTracking)

        viewModel.onPermissionStateChanged(LocationPermissionState.DENIED_PERMANENTLY)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(LocationPermissionState.DENIED_PERMANENTLY, state.permissionState)
        assertFalse(state.isTracking)
        assertEquals(listOf(TrackingStopReason.PERMISSION_NOT_GRANTED), sharing.stoppedReasons)
    }

    @Test
    fun `denied state also stops sharing when acquisition is not allowed`() = runTest(dispatcher) {
        coEvery { courierRepository.getAssignment(ASSIGNMENT_ID) } returns Result.success(activeDelivery())

        val viewModel = viewModel()
        runCurrent()
        viewModel.startTracking()
        runCurrent()

        viewModel.onPermissionStateChanged(LocationPermissionState.DENIED)
        runCurrent()

        assertEquals(listOf(TrackingStopReason.PERMISSION_NOT_GRANTED), sharing.stoppedReasons)
    }

    // --- Batas keamanan assignment ---

    @Test
    fun `a route without an assignment id never contacts the backend nor starts sharing`() = runTest(dispatcher) {
        val viewModel = viewModel(assignmentId = "")
        runCurrent()

        val state = viewModel.uiState.value.deliveryState
        assertTrue(state is LoadState.Error)
        assertEquals(
            CourierTrackingViewModel.MISSING_ASSIGNMENT_ID_MESSAGE,
            (state as LoadState.Error).message
        )

        viewModel.startTracking()
        runCurrent()

        coVerify(exactly = 0) { courierRepository.getAssignment(any()) }
        assertTrue(sharing.started.isEmpty())
    }

    @Test
    fun `the assignment id sent to the sharing controller always comes from the route`() = runTest(dispatcher) {
        coEvery { courierRepository.getAssignment(OTHER_ASSIGNMENT_ID) } returns Result.success(activeDelivery())

        val viewModel = viewModel(assignmentId = OTHER_ASSIGNMENT_ID)
        runCurrent()
        viewModel.startTracking()
        runCurrent()

        // ViewModel tidak pernah menyusun id penugasan sendiri.
        assertEquals(listOf(OTHER_ASSIGNMENT_ID), sharing.started)
    }

    // --- Helper ---

    private fun viewModel(assignmentId: String = ASSIGNMENT_ID): CourierTrackingViewModel =
        CourierTrackingViewModel(
            courierRepository = courierRepository,
            sharing = sharing,
            savedStateHandle = SavedStateHandle(
                mapOf(CourierTrackingViewModel.ARG_ASSIGNMENT_ID to assignmentId)
            )
        )

    private fun activeDelivery(): CourierDelivery = CourierDelivery(
        id = ASSIGNMENT_ID,
        status = AssignmentStatus.ACTIVE,
        startedAt = "2026-03-01T09:00:00.000000Z"
    )

    private fun assignedDelivery(): CourierDelivery = CourierDelivery(
        id = ASSIGNMENT_ID,
        status = AssignmentStatus.ASSIGNED,
        assignedAt = "2026-03-01T08:00:00.000000Z"
    )

    private fun completedDelivery(): CourierDelivery = CourierDelivery(
        id = ASSIGNMENT_ID,
        status = AssignmentStatus.COMPLETED,
        startedAt = "2026-03-01T09:00:00.000000Z",
        completedAt = "2026-03-01T11:00:00.000000Z"
    )

    private companion object {
        const val ASSIGNMENT_ID = "7"
        const val OTHER_ASSIGNMENT_ID = "8"

        val FIX = CourierLocationFix(
            point = GeoPoint(latitude = -6.2, longitude = 100.0),
            accuracyMeters = 8.5
        )

        /** Nilai berbeda dari [FIX] supaya sumbernya bisa dibedakan. */
        val SERVER_LOCATION = CourierDeviceLocation(
            latitude = -6.19,
            longitude = 100.01,
            accuracyMeters = 7.0,
            recordedAt = "2026-03-01T10:30:00.000000Z"
        )
    }
}