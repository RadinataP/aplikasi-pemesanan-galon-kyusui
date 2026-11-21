package com.kyusui.app.ui.courier.tracking

import com.kyusui.app.core.ConflictException
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.NoNetworkException
import com.kyusui.app.core.Result
import com.kyusui.app.core.TrackingSchedule
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.domain.repository.CourierRepository
import com.kyusui.app.ui.location.DeviceLocationSource
import com.kyusui.app.ui.location.LocationReadResult
import com.kyusui.app.ui.state.SubmitState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
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
 * Test controller pengiriman posisi kurir (Batch 2).
 *
 * Controller adalah satu-satunya pengirim untuk proses: satu session, satu
 * loop acquisition, satu upload pada satu waktu. Test di sini memeriksa:
 *
 *  - hanya assignment yang benar-benar aktif yang membuka stream lokasi;
 *  - upload satu-per-satu: pembacaan kedua yang mendahului disebar ataupun
 *    datang saat upload sebelumnya belum selesai tidak memunculkan pengirim
 *    kedua;
 *  - session berhenti saat backend menolak (401/403/409), izin hilang,
 *    provider mati, atau sumber lokasi tak tersedia — tapi TIDAK berhenti
 *    untuk gangguan jaringan sementara;
 *  - [CourierLocationServiceLauncher] ikut dihentikan bersama session, dan
 *    listener Fused Location Provider benar-benar dilepas (lewat
 *    [FakeDeviceLocationSource.streamCancelled]);
 *  - start yang gagal (foreground service dilarang) tidak pernah membuka
 *    stream tanpa notifikasi.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CourierLocationShareControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var courierRepository: CourierRepository
    private lateinit var locationSource: FakeDeviceLocationSource
    private lateinit var launcher: FakeCourierLocationServiceLauncher

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        courierRepository = mockk(relaxed = true)
        locationSource = FakeDeviceLocationSource()
        launcher = FakeCourierLocationServiceLauncher()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Session dan interval ---

    @Test
    fun `start opens the stream with the configured interval and the service once`() = runTest(dispatcher) {
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()

        assertEquals(INTERVAL, locationSource.requestedIntervalMillis)
        assertEquals(listOf(ASSIGNMENT_ID), launcher.started)
        assertTrue(controller.state.value.isSharing)
        assertEquals(ASSIGNMENT_ID, controller.state.value.sessionAssignmentId)
    }

    @Test
    fun `a second start while sharing is ignored so only one sender exists`() = runTest(dispatcher) {
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        controller.start(OTHER_ASSIGNMENT_ID)
        runCurrent()

        assertEquals(listOf(ASSIGNMENT_ID), launcher.started)
        assertEquals(ASSIGNMENT_ID, controller.state.value.sessionAssignmentId)
    }

    @Test
    fun `start with a blank id never touches the service`() = runTest(dispatcher) {
        val controller = controller()

        controller.start("")
        runCurrent()

        assertTrue(launcher.started.isEmpty())
        assertFalse(controller.state.value.isSharing)
    }

    @Test
    fun `a failed foreground service start never begins sharing`() = runTest(dispatcher) {
        val failingLauncher = FakeCourierLocationServiceLauncher(startResult = false)
        val controller = controller(launcher = failingLauncher)

        controller.start(ASSIGNMENT_ID)

        assertFalse(controller.state.value.isSharing)
        assertEquals(TrackingStopReason.USER_REQUESTED, controller.state.value.stopReason)
        assertEquals(
            CourierLocationShareController.SERVICE_START_FAILED_MESSAGE,
            controller.state.value.message
        )
        // Tanpa foreground service tidak boleh ada loop acquisition.
        assertNull(locationSource.requestedIntervalMillis)
    }

    // --- Pengiriman posisi ---

    @Test
    fun `a readable fix is uploaded for the session assignment`() = runTest(dispatcher) {
        coEvery { courierRepository.updateLocation(ASSIGNMENT_ID, FIX) } returns Result.success(SERVER_LOCATION)
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()

        coVerify(exactly = 1) { courierRepository.updateLocation(ASSIGNMENT_ID, FIX) }
    }

    @Test
    fun `the position shown as official is the one the backend returned`() = runTest(dispatcher) {
        // `FIX` dan `SERVER_LOCATION` sengaja berbeda. Yang boleh tampil sebagai
        // posisi resmi adalah nilai server, bukan pembacaan perangkat.
        coEvery { courierRepository.updateLocation(ASSIGNMENT_ID, FIX) } returns Result.success(SERVER_LOCATION)
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()

        val state = controller.state.value
        assertEquals(SERVER_LOCATION, state.lastReportedLocation)
        assertEquals(FIX, state.currentFix)
        assertEquals(LocationReadStatus.Ready, state.locationReadStatus)
        assertTrue(state.submitState is SubmitState.Success)
    }

    @Test
    fun `a second rapid fix does not create a second upload`() = runTest(dispatcher) {
        coEvery { courierRepository.updateLocation(any(), any()) } returns Result.success(SERVER_LOCATION)
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()

        locationSource.emit(LocationReadResult.Fix(FIX))
        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()

        coVerify(exactly = 1) { courierRepository.updateLocation(any(), any()) }
    }

    @Test
    fun `a fix arriving while the previous upload is in flight is dropped`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { courierRepository.updateLocation(any(), any()) } coAnswers {
            gate.await()
            Result.success(SERVER_LOCATION)
        }
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()

        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()
        // Upload pertama masih menggantung; session tetap hidup.
        assertTrue(controller.state.value.isSharing)
        assertTrue(controller.state.value.submitState is SubmitState.Submitting)

        // Pembacaan kedua datang saat upload masih berjalan: harus dibuang,
        // bukan memunculkan pengirim kedua.
        locationSource.emit(LocationReadResult.Fix(NEW_FIX))
        runCurrent()

        gate.complete(Unit)
        runCurrent()

        coVerify(exactly = 1) { courierRepository.updateLocation(any(), any()) }
        assertEquals(SERVER_LOCATION, controller.state.value.lastReportedLocation)
        // Pembacaan yang dibuang tidak menimpa posisi terakhir yang valid.
        assertEquals(FIX, controller.state.value.currentFix)
    }

    // --- Gangguan jaringan tetap melanjutkan session ---

    @Test
    fun `a transient network failure is shown but the session keeps sharing`() = runTest(dispatcher) {
        coEvery { courierRepository.updateLocation(any(), any()) } returnsMany listOf(
            Result.failure(NoNetworkException()),
            Result.success(SERVER_LOCATION)
        )
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()

        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()

        val afterFailure = controller.state.value
        assertTrue(afterFailure.isSharing)
        assertTrue(afterFailure.submitState is SubmitState.Error)
        assertFalse(launcher.stopped)
        assertFalse(locationSource.streamCancelled)

        // Pembacaan berikutnya tetap dikirim.
        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()

        assertEquals(SERVER_LOCATION, controller.state.value.lastReportedLocation)
        assertTrue(controller.state.value.submitState is SubmitState.Success)
    }

    // --- Berhenti karena penolakan backend ---

    @Test
    fun `401 stops the session with session expired`() = runTest(dispatcher) {
        coEvery { courierRepository.updateLocation(any(), any()) } returns Result.failure(UnauthorizedException())
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()
        runCurrent()

        val state = controller.state.value
        assertFalse(state.isSharing)
        assertEquals(TrackingStopReason.SESSION_EXPIRED, state.stopReason)
        assertNull(state.lastReportedLocation)
        assertTrue(launcher.stopped)
        assertTrue("location listener was not released", locationSource.streamCancelled)
    }

    @Test
    fun `403 stops the session as delivery not active`() = runTest(dispatcher) {
        coEvery { courierRepository.updateLocation(any(), any()) } returns Result.failure(ForbiddenException())
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()
        runCurrent()

        val state = controller.state.value
        assertFalse(state.isSharing)
        assertEquals(TrackingStopReason.DELIVERY_NOT_ACTIVE, state.stopReason)
        assertTrue(launcher.stopped)
        assertTrue(locationSource.streamCancelled)
    }

    @Test
    fun `409 stops the session as delivery not active`() = runTest(dispatcher) {
        coEvery { courierRepository.updateLocation(any(), any()) } returns Result.failure(ConflictException())
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()
        runCurrent()

        val state = controller.state.value
        assertFalse(state.isSharing)
        assertEquals(TrackingStopReason.DELIVERY_NOT_ACTIVE, state.stopReason)
        assertTrue(launcher.stopped)
    }

    // --- Berhenti karena perangkat / izin ---

    @Test
    fun `permission missing mid stream stops the session and sends nothing`() = runTest(dispatcher) {
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        locationSource.emit(LocationReadResult.PermissionMissing)
        runCurrent()
        runCurrent()

        val state = controller.state.value
        assertFalse(state.isSharing)
        assertEquals(TrackingStopReason.PERMISSION_NOT_GRANTED, state.stopReason)
        assertEquals(LocationReadStatus.PermissionDenied, state.locationReadStatus)
        assertEquals(
            CourierLocationShareController.PERMISSION_MISSING_MESSAGE,
            state.message
        )
        coVerify(exactly = 0) { courierRepository.updateLocation(any(), any()) }
        assertTrue(launcher.stopped)
        assertTrue(locationSource.streamCancelled)
    }

    @Test
    fun `a disabled provider stops the session with an explanation and sends nothing`() = runTest(dispatcher) {
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        locationSource.emit(LocationReadResult.ProviderDisabled)
        runCurrent()
        runCurrent()

        val state = controller.state.value
        assertFalse(state.isSharing)
        assertEquals(TrackingStopReason.PROVIDER_DISABLED, state.stopReason)
        assertEquals(LocationReadStatus.Unavailable, state.locationReadStatus)
        assertEquals(CourierLocationShareController.PROVIDER_DISABLED_MESSAGE, state.message)
        coVerify(exactly = 0) { courierRepository.updateLocation(any(), any()) }
        assertTrue(launcher.stopped)
    }

    @Test
    fun `an unavailable source stops the session with an explanation`() = runTest(dispatcher) {
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        locationSource.emit(LocationReadResult.Unavailable)
        runCurrent()
        runCurrent()

        val state = controller.state.value
        assertFalse(state.isSharing)
        assertEquals(TrackingStopReason.LOCATION_UNAVAILABLE, state.stopReason)
        assertEquals(CourierLocationShareController.LOCATION_UNAVAILABLE_MESSAGE, state.message)
        coVerify(exactly = 0) { courierRepository.updateLocation(any(), any()) }
        assertTrue(launcher.stopped)
    }

    // --- Stop manual ---

    @Test
    fun `stop by hand releases the listener and the service`() = runTest(dispatcher) {
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        assertTrue(controller.state.value.isSharing)

        controller.stop(TrackingStopReason.USER_REQUESTED)
        runCurrent()
        runCurrent()

        val state = controller.state.value
        assertFalse(state.isSharing)
        assertEquals(TrackingStopReason.USER_REQUESTED, state.stopReason)
        assertTrue(launcher.stopped)
        assertTrue("location listener was not released", locationSource.streamCancelled)
    }

    @Test
    fun `stop without an active session is a no-op`() = runTest(dispatcher) {
        val controller = controller()

        controller.stop(TrackingStopReason.USER_REQUESTED)

        assertFalse(launcher.stopped)
        assertEquals(CourierLocationShareState(), controller.state.value)
    }

    @Test
    fun `restart after stop opens a fresh stream`() = runTest(dispatcher) {
        coEvery { courierRepository.updateLocation(any(), any()) } returns Result.success(SERVER_LOCATION)
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        controller.stop(TrackingStopReason.USER_REQUESTED)
        runCurrent()
        runCurrent()
        assertTrue(locationSource.streamCancelled)

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()

        assertEquals(listOf(ASSIGNMENT_ID, ASSIGNMENT_ID), launcher.started)
        assertEquals(INTERVAL, locationSource.requestedIntervalMillis)
        assertFalse(locationSource.streamCancelled)
        coVerify(exactly = 1) { courierRepository.updateLocation(ASSIGNMENT_ID, FIX) }
    }

    @Test
    fun `clearMessage clears the message and submit state but keeps the session`() = runTest(dispatcher) {
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        controller.clearMessage()

        val state = controller.state.value
        assertTrue(state.isSharing)
        assertNull(state.message)
        assertTrue(state.submitState is SubmitState.Idle)
    }

    // --- Sesi bertugas (tanpa assignment) ---

    @Test
    fun `start duty opens the stream and the service once without an assignment`() =
        runTest(dispatcher) {
            val controller = controller()

            controller.startDuty()
            runCurrent()

            assertEquals(INTERVAL, locationSource.requestedIntervalMillis)
            assertEquals(1, launcher.dutyStartCount)
            assertTrue(launcher.started.isEmpty())
            assertTrue(controller.state.value.isOnDuty)
            assertNull(controller.state.value.sessionAssignmentId)
        }

    @Test
    fun `a fix during duty is uploaded through the duty endpoint`() = runTest(dispatcher) {
        coEvery { courierRepository.sendDutyLocation(FIX) } returns Result.success(SERVER_LOCATION)
        val controller = controller()

        controller.startDuty()
        runCurrent()
        locationSource.emit(LocationReadResult.Fix(FIX))
        runCurrent()

        coVerify(exactly = 1) { courierRepository.sendDutyLocation(FIX) }
        coVerify(exactly = 0) { courierRepository.updateLocation(any(), any()) }
        assertEquals(SERVER_LOCATION, controller.state.value.lastReportedLocation)
    }

    @Test
    fun `a second start duty while sharing is ignored`() = runTest(dispatcher) {
        val controller = controller()

        controller.startDuty()
        runCurrent()
        controller.startDuty()
        runCurrent()

        assertEquals(1, launcher.dutyStartCount)
    }

    @Test
    fun `duty and assignment sharing cannot run at the same time`() = runTest(dispatcher) {
        val controller = controller()

        controller.start(ASSIGNMENT_ID)
        runCurrent()
        controller.startDuty()
        runCurrent()

        // Session assignment tetap yang berjalan; satu pengirim saja.
        assertTrue(controller.state.value.isSharingFor(ASSIGNMENT_ID))
        assertEquals(0, launcher.dutyStartCount)
    }

    @Test
    fun `conflict on duty upload stops the session with the duty reason`() =
        runTest(dispatcher) {
            coEvery { courierRepository.sendDutyLocation(FIX) } returns Result.Failure(
                ConflictException("Sesi bertugas sudah berakhir.")
            )
            val controller = controller()

            controller.startDuty()
            runCurrent()
            locationSource.emit(LocationReadResult.Fix(FIX))
            runCurrent()
            runCurrent()

            assertFalse(controller.state.value.isSharing)
            assertEquals(
                TrackingStopReason.DUTY_SESSION_ENDED,
                controller.state.value.stopReason
            )
            assertTrue(launcher.stopped)
        }

    @Test
    fun `a failed foreground service start during duty never begins sharing`() =
        runTest(dispatcher) {
            val failingLauncher = FakeCourierLocationServiceLauncher(startResult = false)
            val controller = controller(launcher = failingLauncher)

            controller.startDuty()
            runCurrent()

            assertFalse(controller.state.value.isSharing)
            assertFalse(controller.state.value.isOnDuty)
            assertEquals(TrackingStopReason.USER_REQUESTED, controller.state.value.stopReason)
            // Tanpa foreground service tidak boleh ada loop acquisition.
            assertNull(locationSource.requestedIntervalMillis)
        }

    // --- Helper ---

    private fun controller(
        launcher: CourierLocationServiceLauncher = this.launcher
    ): CourierLocationShareController = CourierLocationShareController(
        locationSource = locationSource,
        courierRepository = courierRepository,
        trackingSchedule = FakeSchedule(INTERVAL),
        serviceLauncher = launcher
    )

    private companion object {
        const val ASSIGNMENT_ID = "7"
        const val OTHER_ASSIGNMENT_ID = "8"

        /** Interval kecil agar test tidak bergantung pada angka produksi. */
        const val INTERVAL = 1_000L

        val FIX = CourierLocationFix(
            point = GeoPoint(latitude = -6.2, longitude = 100.0),
            accuracyMeters = 8.5
        )

        /** Pembacaan kedua yang berbeda dari [FIX]. */
        val NEW_FIX = CourierLocationFix(
            point = GeoPoint(latitude = -6.21, longitude = 100.01),
            accuracyMeters = 9.0
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

/**
 * Sumber lokasi palsu yang aliran pembacaannya dikendalikan test (salinan
 * lokal; controller memakai coroutine proses sendiri, jadi test ini butuh
 * fake-nya sendiri).
 */
private class FakeDeviceLocationSource : DeviceLocationSource {

    private val readings = MutableSharedFlow<LocationReadResult>(extraBufferCapacity = 32)

    var requestedIntervalMillis: Long? = null
        private set

    var streamCancelled: Boolean = false
        private set

    override suspend fun readOnce(): LocationReadResult = LocationReadResult.Unavailable

    override fun stream(intervalMillis: Long): Flow<LocationReadResult> {
        requestedIntervalMillis = intervalMillis
        streamCancelled = false
        return readings.onCompletion { streamCancelled = true }
    }

    suspend fun emit(result: LocationReadResult) {
        readings.emit(result)
    }
}

/** Launcher palsu yang bisa disetel gagal supaya start FGS bisa diuji. */
private class FakeCourierLocationServiceLauncher(
    private val startResult: Boolean = true
) : CourierLocationServiceLauncher {

    val started = mutableListOf<String>()
    var dutyStartCount = 0
        private set
    var stopped = false
        private set

    override fun start(assignmentId: String): Boolean {
        started += assignmentId
        return startResult
    }

    override fun startDuty(): Boolean {
        dutyStartCount++
        return startResult
    }

    override fun stop() {
        stopped = true
    }
}

/** Jadwal interval palsu yang mengimplementasikan batas konfigurasi penuh. */
private class FakeSchedule(override val courierSubmissionIntervalMillis: Long) : TrackingSchedule {
    override val customerReadIntervalMillis: Long = courierSubmissionIntervalMillis
}