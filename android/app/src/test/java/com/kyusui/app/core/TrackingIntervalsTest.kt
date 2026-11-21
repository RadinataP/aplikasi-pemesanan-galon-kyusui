package com.kyusui.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Batas konfigurasi interval tracking.
 *
 * Test ini mengunci dua hal yang berbeda.
 *
 * Pertama, **lantai yang dipaksakan backend**. `GET orders/{order}/tracking`
 * berada di dalam middleware `['role:CUSTOMER', 'throttle:5,1']` pada
 * `backend/routes/api.php`, jadi backend hanya menerima 5 permintaan per 60
 * detik untuk endpoint itu. Angka 12.000 ms di bawah bukan preferensi, itu
 * hasil bagi 60.000 dengan 5. Mempercepatnya akan menghasilkan HTTP
 * 429, bukan data.
 *
 * Kedua, **status belum selesai**. `UD-TRACK-01` dan `UD-TRACK-02` masih
 * `UNRESOLVED`, jadi angka sementara di sini harus tetap ditandai sebagai
 * sementara. Kalau suatu saat spesifikasi mengunci angka, `IS_UNRESOLVED`
 * berubah menjadi false di satu tempat dan test ini ikut berubah.
 */
class TrackingIntervalsTest {

    @Test
    fun `the customer read floor is the arithmetic result of the backend throttle`() {
        // 60 detik dibagi 5 permintaan per menit.
        assertEquals(60_000L / 5, TrackingIntervals.CUSTOMER_READ_MINIMUM_MS)
        assertEquals(12_000L, TrackingIntervals.CUSTOMER_READ_MINIMUM_MS)
    }

    @Test
    fun `the customer polling interval never goes below the backend floor`() {
        assertTrue(
            "customer polling at ${TrackingIntervals.CUSTOMER_READ_INTERVAL_MS} ms " +
                "is faster than the backend floor of ${TrackingIntervals.CUSTOMER_READ_MINIMUM_MS} ms",
            TrackingIntervals.CUSTOMER_READ_INTERVAL_MS >= TrackingIntervals.CUSTOMER_READ_MINIMUM_MS
        )
    }

    @Test
    fun `the customer polling interval stays inside the per-minute quota`() {
        // Dengan interval 15 detik, satu menit berisi 4 permintaan, jadi masih
        // ada sisa satu permintaan untuk aksi manual pengguna.
        val requestsPerMinute = 60_000L / TrackingIntervals.CUSTOMER_READ_INTERVAL_MS
        assertTrue(
            "polling would issue $requestsPerMinute requests per minute, over the backend limit of 5",
            requestsPerMinute <= 5
        )
    }

    @Test
    fun `the courier submission interval is a positive duration`() {
        // `POST courier/assignments/{assignment}/location` tidak memakai
        // throttle, jadi tidak ada lantai dari backend. Yang ada hanya syarat
        // bahwa intervalnya durasi yang masuk akal.
        assertTrue(TrackingIntervals.COURIER_SUBMISSION_INTERVAL_MS > 0)
    }

    @Test
    fun `the unresolved status is still declared`() {
        assertTrue(TrackingIntervals.IS_UNRESOLVED)
    }

    @Test
    fun `the unresolved notice names both open decisions`() {
        val notice = TrackingIntervals.UNRESOLVED_NOTICE
        assertTrue("notice does not mention UD-TRACK-01", notice.contains("UD-TRACK-01"))
        assertTrue("notice does not mention UD-TRACK-02", notice.contains("UD-TRACK-02"))
    }

    @Test
    fun `the production schedule reads the unresolved boundary rather than copying it`() {
        val schedule = TrackingIntervalsSchedule()

        assertEquals(
            TrackingIntervals.COURIER_SUBMISSION_INTERVAL_MS,
            schedule.courierSubmissionIntervalMillis
        )
        assertEquals(
            TrackingIntervals.CUSTOMER_READ_INTERVAL_MS,
            schedule.customerReadIntervalMillis
        )
    }

    @Test
    fun `no tracking view model hardcodes an interval literal`() {
        // Specification tracking menyatakan interval belum boleh dikunci, jadi
        // tidak ada angka interval yang boleh muncul sebagai literal di
        // ViewModel. Angka itu hanya boleh datang dari TrackingSchedule.
        // Test ini menjaga aturan itu tetap berlaku saat file diedit kemudian.
        val offenders = TRACKING_VIEW_MODEL_FILES.flatMap { path ->
            val source = File(path)
            require(source.exists()) { "expected tracking source file at $path" }

            source.readLines()
                .withIndex()
                .filter { (_, line) ->
                    HARD_CODED_DELAY.containsMatchIn(line) ||
                        HARDCODED_INTERVAL_CONSTANT.containsMatchIn(line)
                }
                .map { (index, line) -> "$path:${index + 1}: ${line.trim()}" }
        }

        assertEquals(
            "tracking intervals must come from TrackingSchedule, not literals:",
            emptyList<String>(),
            offenders
        )
    }

    private companion object {
        /** Lokasi relatif terhadap direktori modul `app` saat test berjalan. */
        val TRACKING_VIEW_MODEL_FILES = listOf(
            "src/main/java/com/kyusui/app/ui/customer/tracking/TrackingViewModel.kt",
            "src/main/java/com/kyusui/app/ui/courier/tracking/CourierTrackingViewModel.kt"
        )

        /** `delay(<angka>)`, yaitu interval yang ditulis langsung. */
        val HARD_CODED_DELAY = Regex("""delay\(\s*\d""")

        /** `..._MS` yang dipakai langsung, bukan lewat batas konfigurasi. */
        val HARDCODED_INTERVAL_CONSTANT = Regex("""\b\d[\d_]*L?\b""")
            .let { numberLiteral -> Regex("""_INTERVAL[A-Z_]*\s*=\s*$numberLiteral""") }
    }
}
