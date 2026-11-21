package com.kyusui.app.ui.customer.tracking

import androidx.lifecycle.SavedStateHandle
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.NoNetworkException
import com.kyusui.app.core.Result
import com.kyusui.app.core.TrackingSchedule
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierLocationPoint
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.domain.model.Order
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OrderTracking
import com.kyusui.app.domain.model.OrderTrackingResult
import com.kyusui.app.domain.repository.CustomerRepository
import com.kyusui.app.ui.state.LoadState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
 * TRK-001, TRK-002, TRK-004, dan TRK-005 untuk sisi customer.
 *
 * Test memakai [FakeTrackingSchedule] dengan interval kecil dan waktu virtual,
 * jadi tidak ada satu pun test yang bergantung pada angka interval produksi.
 * Batas interval itu sendiri diuji di `TrackingIntervalsTest`.
 *
 * ## Kenapa tidak memakai `advanceUntilIdle`
 *
 * Polling di `TrackingViewModel` berjalan tanpa batas akhir, seperti di
 * aplikasi. Di akhir `runTest`, Gradle menjalankan semua tugas yang masih
 * terjadwal sampai tidak ada lagi. Loop polling selalu menjadwalkan tugas
 * berikutnya, jadi langkah akhir itu tidak pernah selesai dan JVM kehabisan
 * memori sebelum batas waktu test tercapai.
 *
 * Karena itu test memakai [runCurrent] untuk menjalankan pekerjaan yang sudah
 * dijadwalkan pada waktu virtual sekarang, dan [trackingTest] menutup polling
 * sebelum test selesai.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {

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

    // --- TRK-001: pengiriman aktif ---

    @Test
    fun `TRK-001 active delivery is shown as a success with the courier position`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()

            val state = viewModel.uiState.value.trackingState
            assertTrue("expected Success but was $state", state is LoadState.Success)

            val data = (state as LoadState.Success).data
            assertEquals(AssignmentStatus.ACTIVE, data.assignmentStatus)
            assertNotNull(data.latestLocation)
        }

    @Test
    fun `TRK-001 delivery status is read from the order endpoint because tracking does not carry it`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            coEvery {
                repository.getOrder(ORDER_ID)
            } returns Result.success(order(status = "DALAM_PENGANTARAN"))

            runCurrent()

            // `GET orders/{order}/tracking` tidak memuat status order, jadi
            // status canonical harus datang dari `GET orders/{order}`.
            coVerify(exactly = 1) { repository.getOrder(ORDER_ID) }
            val orderState = viewModel.uiState.value.orderState as LoadState.Success
            assertEquals(OrderStatus.DALAM_PENGANTARAN, orderState.data.orderStatus)
        }

    @Test
    fun `TRK-001 active delivery without any position is a success and not an error`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking(locations = emptyList())))
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()

            val state = viewModel.uiState.value.trackingState
            assertTrue("expected Success but was $state", state is LoadState.Success)
            assertNull((state as LoadState.Success).data.latestLocation)
            assertFalse((state as LoadState.Success).data.hasPositions)
        }

    // --- TRK-002: tracking tidak berhak ---

    @Test
    fun `TRK-002 tracking another customer order is refused with the mapped forbidden copy`() =
        trackingTest { viewModel ->
            // Backend menjawab 403 lewat `abort_unless`, jadi Android tidak
            // pernah menampilkan posisi milik orang lain.
            coEvery { repository.getTracking(ORDER_ID) } returns Result.failure(ForbiddenException())
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()

            val state = viewModel.uiState.value.trackingState
            assertTrue("expected Error but was $state", state is LoadState.Error)

            val error = state as LoadState.Error
            assertEquals("Akses ditolak", error.message)
            // Menolak akses tidak akan berubah dengan mencoba ulang.
            assertFalse(error.isRetryable)
            assertNull(viewModel.uiState.value.confirmedTracking)
        }

    @Test
    fun `TRK-002 expired session is reported as unauthorized rather than forbidden`() =
        trackingTest { viewModel ->
            coEvery { repository.getTracking(ORDER_ID) } returns Result.failure(UnauthorizedException())
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()

            val error = viewModel.uiState.value.trackingState as LoadState.Error
            assertTrue(error.isUnauthorized)
            assertFalse(error.isRetryable)
            assertEquals("Sesi berakhir, silakan login kembali", error.message)
        }

    // --- TRK-004: rendering posisi ---

    @Test
    fun `TRK-004 latest position is the newest point the backend returned`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(
                OrderTrackingResult.Active(tracking(locations = listOf(newestPoint, olderPoint)))
            )
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()

            val data = (viewModel.uiState.value.trackingState as LoadState.Success).data
            // Backend memuat relasi dengan `latest('recorded_at')->limit(10)`,
            // jadi indeks 0 adalah yang terbaru dan Android tidak mengurutkan
            // ulang.
            assertEquals(newestPoint, data.latestLocation)
            assertEquals(2, data.locations.size)
        }

    @Test
    fun `TRK-004 coordinates and accuracy are taken verbatim from the backend`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(
                OrderTrackingResult.Active(
                    tracking(
                        locations = listOf(
                            CourierLocationPoint(
                                point = GeoPoint(latitude = -6.175392, longitude = 106.827153),
                                accuracyMeters = 8.5,
                                recordedAt = RECORDED_AT
                            )
                        )
                    )
                )
            )
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()

            val latest = (viewModel.uiState.value.trackingState as LoadState.Success).data.latestLocation!!
            assertEquals(-6.175392, latest.point.latitude, 1e-9)
            assertEquals(106.827153, latest.point.longitude, 1e-9)
            assertEquals(8.5, latest.accuracyMeters!!, 1e-9)
            // Timestamp ditampilkan apa adanya. Android tidak mengonversi zona
            // waktu karena backend tidak memberi tahu zona waktu perangkat.
            assertEquals(RECORDED_AT, latest.recordedAt)
        }

    @Test
    fun `TRK-004 a point without accuracy is rendered without inventing a value`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(
                OrderTrackingResult.Active(
                    tracking(
                        locations = listOf(
                            CourierLocationPoint(
                                point = GeoPoint(latitude = -6.2, longitude = 100.0),
                                accuracyMeters = null,
                                recordedAt = null
                            )
                        )
                    )
                )
            )
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()

            val latest = (viewModel.uiState.value.trackingState as LoadState.Success).data.latestLocation!!
            // Layar hanya menampilkan akurasi bila backend mengirimnya.
            // Menampilkan 0.00 m akan mengklaim pengukuran yang tidak pernah
            // dilakukan.
            assertNull(latest.accuracyMeters)
            assertNull(latest.recordedAt)
        }

    // --- TRK-005: tracking berhenti ---

    @Test
    fun `TRK-005 null data from the backend means tracking finished and is not an error`() =
        trackingTest { viewModel ->
            // Backend hanya memuat assignment berstatus ACTIVE. Setelah delivery
            // selesai, `data` menjadi null dan itu jawaban normal.
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.NoActiveDelivery)
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order(status = "SELESAI"))

            runCurrent()

            val state = viewModel.uiState.value.trackingState
            assertTrue("expected Empty but was $state", state is LoadState.Empty)

            val empty = state as LoadState.Empty
            assertEquals(TrackingViewModel.NO_ACTIVE_DELIVERY_MESSAGE, empty.message)
            assertNull(viewModel.uiState.value.confirmedTracking)
            assertNull(viewModel.uiState.value.refreshErrorMessage)
        }

    @Test
    fun `TRK-005 a network failure during polling keeps the position already on screen`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()
            val positionBefore = viewModel.uiState.value.confirmedTracking
            assertNotNull(positionBefore)

            coEvery { repository.getTracking(ORDER_ID) } returns Result.failure(NoNetworkException())
            advanceTimeBy(INTERVAL)
            runCurrent()

            // Posisi yang masih berlaku lebih berguna daripada layar kosong
            // karena satu permintaan gagal.
            assertEquals(positionBefore, viewModel.uiState.value.confirmedTracking)
            assertEquals(
                "Tidak ada koneksi internet",
                viewModel.uiState.value.refreshErrorMessage
            )
        }

    @Test
    fun `TRK-005 a polling success clears the previous refresh error`() =
        trackingTest { viewModel ->
            coEvery { repository.getTracking(ORDER_ID) } returns Result.failure(NoNetworkException())
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()
            val errorAfterFirstLoad = viewModel.uiState.value.trackingState as LoadState.Error
            // Pemuatan awal yang gagal harus terlihat sebagai error, bukan diam.
            assertEquals("Tidak ada koneksi internet", errorAfterFirstLoad.message)

            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            advanceTimeBy(INTERVAL)
            runCurrent()

            assertTrue(viewModel.uiState.value.trackingState is LoadState.Success)
            // Pesan error lama tidak boleh tetap menempel di state sukses.
            assertNull(viewModel.uiState.value.refreshErrorMessage)
        }

    // --- Tahap E: berhenti polling saat order tidak lagi dapat dilacak ---

    @Test
    fun `a final order does not keep polling after the initial load`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.NoActiveDelivery)
            coEvery {
                repository.getOrder(ORDER_ID)
            } returns Result.success(order(status = "SELESAI"))

            runCurrent()

            assertFalse(viewModel.uiState.value.isPolling)
            coVerify(exactly = 1) { repository.getTracking(ORDER_ID) }

            advanceTimeBy(INTERVAL * 5)
            runCurrent()
            // Order final tidak akan punya assignment aktif lagi, jadi tidak
            // ada permintaan tambahan.
            coVerify(exactly = 1) { repository.getTracking(ORDER_ID) }
        }

    @Test
    fun `startPolling refuses to restart once the order is final`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.NoActiveDelivery)
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order(status = "SELESAI"))

            runCurrent()
            assertFalse(viewModel.uiState.value.isPolling)

            // Layar kembali STARTED dan meminta polling lagi; karena order
            // sudah final, polling harus tetap mati.
            viewModel.startPolling()
            runCurrent()
            assertFalse(viewModel.uiState.value.isPolling)

            advanceTimeBy(INTERVAL * 5)
            runCurrent()
            coVerify(exactly = 1) { repository.getTracking(ORDER_ID) }
        }

    @Test
    fun `tracking that finishes while polling stops the loop and refreshes the final status`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()
            assertTrue(viewModel.uiState.value.isPolling)

            // Assignment yang tadi aktif berakhir di backend.
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.NoActiveDelivery)
            coEvery {
                repository.getOrder(ORDER_ID)
            } returns Result.success(order(status = "SELESAI"))

            advanceTimeBy(INTERVAL + 1)
            runCurrent()

            assertFalse(viewModel.uiState.value.isPolling)
            assertTrue(viewModel.uiState.value.trackingState is LoadState.Empty)
            val orderState = viewModel.uiState.value.orderState as LoadState.Success
            assertEquals(OrderStatus.SELESAI, orderState.data.orderStatus)
            // Satu permintaan dari load dan satu permintaan poll penutup, lalu
            // tidak ada lagi.
            coVerify(exactly = 2) { repository.getTracking(ORDER_ID) }

            advanceTimeBy(INTERVAL * 5)
            runCurrent()
            coVerify(exactly = 2) { repository.getTracking(ORDER_ID) }
        }

    // --- Tahap E: membedakan posisi belum ada, stale, dan error ---

    @Test
    fun `a failed poll after a position is shown marks the position stale`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()
            assertFalse(viewModel.uiState.value.isPositionStale)

            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.failure(NoNetworkException())
            advanceTimeBy(INTERVAL + 1)
            runCurrent()

            // Posisi lama masih ditampilkan, tetapi pembaruan terakhir gagal.
            assertNotNull(viewModel.uiState.value.confirmedTracking)
            assertTrue(viewModel.uiState.value.isPositionStale)

            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            advanceTimeBy(INTERVAL + 1)
            runCurrent()

            // Pembaruan berhasil lagi, jadi posisi bukan lagi stale.
            assertFalse(viewModel.uiState.value.isPositionStale)
        }

    @Test
    fun `a failure before any position is an error and not a stale position`() =
        trackingTest { viewModel ->
            coEvery { repository.getTracking(ORDER_ID) } returns Result.failure(NoNetworkException())
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()
            advanceTimeBy(INTERVAL + 1)
            runCurrent()

            // Tidak ada posisi untuk ditandai stale; ini murni kegagalan muat.
            assertNull(viewModel.uiState.value.confirmedTracking)
            assertFalse(viewModel.uiState.value.isPositionStale)
            assertTrue(viewModel.uiState.value.trackingState is LoadState.Error)
        }

    // --- Interval dari batas konfigurasi ---

    @Test
    fun `polling waits for the configured interval before requesting tracking again`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()
            // Satu permintaan dari pemuatan awal.
            coVerify(exactly = 1) { repository.getTracking(ORDER_ID) }

            advanceTimeBy(INTERVAL - 1)
            runCurrent()
            coVerify(exactly = 1) { repository.getTracking(ORDER_ID) }

            advanceTimeBy(2)
            runCurrent()
            coVerify(exactly = 2) { repository.getTracking(ORDER_ID) }
        }

    @Test
    fun `a manual refresh does not stack on top of a running poll`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()
            coVerify(exactly = 1) { repository.getTracking(ORDER_ID) }

            viewModel.refresh()
            runCurrent()
            // Permintaan manual menggantikan polling yang sedang berjalan, bukan
            // menambahkannya, supaya kuota throttle tidak terbuang percuma.
            coVerify(exactly = 2) { repository.getTracking(ORDER_ID) }

            advanceTimeBy(INTERVAL + 1)
            runCurrent()
            coVerify(exactly = 3) { repository.getTracking(ORDER_ID) }
        }

    @Test
    fun `stopPolling ends the automatic requests`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()
            coVerify(exactly = 1) { repository.getTracking(ORDER_ID) }

            viewModel.stopPolling()
            advanceTimeBy(INTERVAL * 10)
            runCurrent()

            coVerify(exactly = 1) { repository.getTracking(ORDER_ID) }
        }

    @Test
    fun `isPolling follows start and stop so the screen knows whether polling runs`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()
            // Pemuatan awal menyalakan polling dan mencerminkannya ke state.
            assertTrue(viewModel.uiState.value.isPolling)

            viewModel.stopPolling()
            runCurrent()
            assertFalse(viewModel.uiState.value.isPolling)

            // Menyalakan lagi setelah berhenti tetap membuka polling.
            viewModel.startPolling()
            runCurrent()
            assertTrue(viewModel.uiState.value.isPolling)

            advanceTimeBy(INTERVAL + 1)
            runCurrent()
            coVerify(exactly = 2) { repository.getTracking(ORDER_ID) }
        }

    @Test
    fun `a repeated startPolling does not create a second polling loop`() =
        trackingTest { viewModel ->
            coEvery {
                repository.getTracking(ORDER_ID)
            } returns Result.success(OrderTrackingResult.Active(tracking()))
            coEvery { repository.getOrder(ORDER_ID) } returns Result.success(order())

            runCurrent()
            coVerify(exactly = 1) { repository.getTracking(ORDER_ID) }

            // Polling sudah menyala dari load(); panggilan keduabelas ini harus
            // diabaikan, bukan membuat loop kedua.
            viewModel.startPolling()
            runCurrent()
            coVerify(exactly = 1) { repository.getTracking(ORDER_ID) }

            advanceTimeBy(INTERVAL + 1)
            runCurrent()
            // Kalau panggilan kedua membuat loop kedua, akan ada 3 permintaan.
            coVerify(exactly = 2) { repository.getTracking(ORDER_ID) }
        }

    @Test
    fun `a blank order id fails without calling the backend`() =
        trackingTest(orderId = "") { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value.orderState
            assertTrue(state is LoadState.Error)
            assertEquals(TrackingViewModel.MISSING_ORDER_ID_MESSAGE, (state as LoadState.Error).message)
            assertFalse(state.isRetryable)

            coVerify(exactly = 0) { repository.getTracking(any()) }
            coVerify(exactly = 0) { repository.getOrder(any()) }
        }

    // --- Helper ---

    /**
     * Membuat ViewModel dan memastikan polling ditutup sebelum test selesai.
     *
     * Satu-satunya cara membuat ViewModel di test ini adalah lewat fungsi ini,
     * supaya tidak ada test yang lupa menutup loop polling.
     */
    private fun trackingTest(
        orderId: String = ORDER_ID,
        body: suspend TestScope.(TrackingViewModel) -> Unit
    ) = runTest(dispatcher) {
        val viewModel = TrackingViewModel(
            customerRepository = repository,
            trackingSchedule = FakeTrackingSchedule(INTERVAL),
            savedStateHandle = SavedStateHandle(mapOf(TrackingViewModel.ARG_ORDER_ID to orderId))
        )
        try {
            body(viewModel)
        } finally {
            viewModel.stopPolling()
        }
    }

    private fun tracking(
        locations: List<CourierLocationPoint> = listOf(newestPoint)
    ): OrderTracking = OrderTracking(
        assignmentId = "7",
        orderId = ORDER_ID,
        assignmentStatus = AssignmentStatus.ACTIVE,
        startedAt = "2026-03-01T09:00:00.000000Z",
        completedAt = null,
        locations = locations
    )

    private fun order(status: String = "DALAM_PENGANTARAN"): Order = Order(
        id = ORDER_ID,
        orderNumber = "ORD-2026-0001",
        customerId = "10",
        items = emptyList(),
        subtotalAmount = "16000.00",
        deliveryFee = "5000.00",
        totalAmount = "21000.00",
        orderStatus = OrderStatus.fromApiValue(status),
        payment = null
    )

    private companion object {
        const val ORDER_ID = "1001"

        /** Interval kecil agar test tidak menunggu waktu produksi. */
        const val INTERVAL = 1_000L

        const val RECORDED_AT = "2026-03-01T10:30:00.000000Z"

        val newestPoint = CourierLocationPoint(
            point = GeoPoint(latitude = -6.175392, longitude = 106.827153),
            accuracyMeters = 8.5,
            recordedAt = RECORDED_AT
        )

        val olderPoint = CourierLocationPoint(
            point = GeoPoint(latitude = -6.17, longitude = 106.82),
            accuracyMeters = 12.0,
            recordedAt = "2026-03-01T10:00:00.000000Z"
        )
    }
}

/**
 * Jadwal interval yang dipakai test.
 *
 * Mengimplementasikan [TrackingSchedule] penuh supaya test benar-benar memakai
 * batas konfigurasi, bukan daftar angka yang disalin ke test.
 */
private class FakeTrackingSchedule(
    override val customerReadIntervalMillis: Long
) : TrackingSchedule {
    override val courierSubmissionIntervalMillis: Long = customerReadIntervalMillis
}