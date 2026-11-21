package com.kyusui.app.data.api

import com.kyusui.app.data.mapper.toDomain
import com.kyusui.app.data.mapper.toDomainOrNull
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontrak DTO Courier.
 *
 * Fixture di test ini diambil bentuk literal dari
 * `backend/app/Http/Controllers/Api/CourierController.php` dan
 * `backend/app/Http/Resources/DashboardOrderResource.php`, bukan dari
 * `06_KYUSUI_API_SPECIFICATION_REBUILT.md` section 15 dan 16. Keduanya berbeda,
 * dan hanya backend yang bisa dipanggil.
 *
 * Yang dijaga test ini:
 *
 * 1. `GET courier/assignments` membalut array penuh tanpa objek `meta`. Fixture
 *    memakai `data` berupa array dan tanpa `meta`; kalau suatu saat DTO berubah
 *    mengira ada paginasi, test ini harus gagal.
 * 2. Bentuk `order` di dalam assignment memakai `subtotal`, `total`, serta
 *    `payment.method` dan `payment.status`. Bentuk itu berbeda dari Order
 *    Resource specification yang memakai `subtotal_amount`, `total_amount`,
 *    `payment_method`, dan `payment_status`. Salah baca akan membuat semua nominal
 *    dan status pembayaran bernilai default.
 * 3. `courier_locations` di-cast `decimal` di backend, jadi latitude, longitude,
 *    dan accuracy datang sebagai string desimal. Serializer harus menerima bentuk
 *    string itu.
 * 4. Status yang tidak dikenal dipetakan ke null, dan status turunan seperti
 *    `canConfirmCash` harus mati. Status yang ditebak di Android akan membuka aksi
 *    yang tidak seharusnya terbuka.
 * 5. Dashboard memakai Order Resource yang tidak memuat nama pelanggan dan tidak
 *    memuat `delivery_location`. Keduanya tidak boleh muncul di domain model
 *    dashboard.
 */
class CourierApiContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    // --- GET courier/assignments ---

    @Test
    fun `assignment list decodes every field from assignmentData`() {
        val body = """
            {
              "data": [
                {
                  "id": 7,
                  "status": "ASSIGNED",
                  "assigned_at": "2026-01-01T08:00:00.000000Z",
                  "started_at": null,
                  "completed_at": null,
                  "order": {
                    "id": 21,
                    "status": "DITUGASKAN",
                    "subtotal": "16000.00",
                    "total": "21000.00",
                    "delivery_address": "Jl. Merdeka 1",
                    "customer": { "name": "Budi" },
                    "items": [
                      {
                        "product_name": "Air Galon",
                        "quantity": 2,
                        "unit_price": "8000.00",
                        "line_total": "16000.00"
                      }
                    ],
                    "payment": { "method": "CASH", "status": "PENDING" }
                  },
                  "latest_location": null
                }
              ],
              "message": "Daftar penugasan berhasil dimuat."
            }
        """.trimIndent()

        val deliveries = json.decodeFromString<EnvelopeDto<List<CourierAssignmentDto>>>(body)
            .requireData()

        assertEquals(1, deliveries.size)

        val delivery = deliveries.first().toDomain()
        assertEquals("7", delivery.id)
        assertEquals(AssignmentStatus.ASSIGNED, delivery.status)
        assertEquals("2026-01-01T08:00:00.000000Z", delivery.assignedAt)
        assertNull(delivery.startedAt)
        assertNull(delivery.completedAt)

        val order = requireNotNull(delivery.order)
        assertEquals("21", order.id)
        assertEquals(OrderStatus.DITUGASKAN, order.status)
        // Nama field backend adalah `subtotal` dan `total`, bukan
        // `subtotal_amount` dan `total_amount`.
        assertEquals("16000.00", order.subtotalAmount)
        assertEquals("21000.00", order.totalAmount)
        assertEquals("Jl. Merdeka 1", order.deliveryAddress)
        assertEquals("Budi", order.customerName)
        assertEquals(PaymentMethod.CASH, order.payment?.paymentMethod)
        assertEquals(PaymentStatus.PENDING, order.payment?.paymentStatus)
        assertEquals(2, order.totalQuantity)
        assertEquals("Air Galon", order.items.single().productName)
        assertNull(delivery.latestLocation)
    }

    @Test
    fun `assignment list has no pagination envelope`() {
        // Backend memakai `get()` untuk daftar assignment, bukan `paginate()`.
        // Fixture ini sengaja tanpa `meta`; kalau DTO mengira ada paginasi,
        // test ini akan gagal karena `meta` tidak pernah dikirim.
        val body = """
            {
              "data": [
                { "id": 1, "status": "ASSIGNED", "order": { "id": 1 } },
                { "id": 2, "status": "ACTIVE", "order": { "id": 2 } },
                { "id": 3, "status": "COMPLETED", "order": { "id": 3 } }
              ],
              "message": "OK"
            }
        """.trimIndent()

        val deliveries = json.decodeFromString<EnvelopeDto<List<CourierAssignmentDto>>>(body)
            .requireData()

        assertEquals(3, deliveries.size)
        assertEquals(
            listOf(
                AssignmentStatus.ASSIGNED,
                AssignmentStatus.ACTIVE,
                AssignmentStatus.COMPLETED
            ),
            deliveries.map { AssignmentStatus.fromApiValue(it.status) }
        )
    }

    // --- GET courier/assignments/{assignment} ---

    @Test
    fun `latest location decodes decimal strings sent by the backend`() {
        // Kolom latitude, longitude, dan accuracy di-cast `decimal`, jadi JSON-nya
        // berisi string seperti "-0.9480000", bukan angka.
        val body = """
            {
              "data": {
                "id": 7,
                "status": "ACTIVE",
                "assigned_at": "2026-01-01T08:00:00.000000Z",
                "started_at": "2026-01-01T09:00:00.000000Z",
                "completed_at": null,
                "order": { "id": 21, "status": "DALAM_PENGANTARAN", "payment": { "method": "CASH", "status": "PENDING" } },
                "latest_location": {
                  "id": 55,
                  "assignment_id": 7,
                  "courier_id": 4,
                  "latitude": "-0.9480000",
                  "longitude": "100.4180000",
                  "accuracy_meters": "8.50",
                  "recorded_at": "2026-01-01T09:30:00.000000Z",
                  "idempotency_key": null
                }
              },
              "message": "Penugasan berhasil dimuat."
            }
        """.trimIndent()

        val delivery = json.decodeFromString<EnvelopeDto<CourierAssignmentDto>>(body)
            .requireData()
            .toDomain()

        val location = requireNotNull(delivery.latestLocation)
        assertEquals(-0.948, requireNotNull(location.latitude), 1e-9)
        assertEquals(100.418, requireNotNull(location.longitude), 1e-9)
        assertEquals(8.50, requireNotNull(location.accuracyMeters), 1e-9)
        assertEquals("2026-01-01T09:30:00.000000Z", location.recordedAt)
        assertTrue(location.hasCoordinates)
    }

    @Test
    fun `latest location also decodes when the backend sends plain numbers`() {
        // Bila cast `decimal` di backend berubah, angka biasa harus tetap bisa
        // dibaca tanpa membuat seluruh layar gagal.
        val body = """
            {
              "data": {
                "id": 7,
                "status": "ACTIVE",
                "latest_location": {
                  "id": 55,
                  "latitude": -0.948,
                  "longitude": 100.418,
                  "accuracy_meters": 8.5
                }
              },
              "message": "OK"
            }
        """.trimIndent()

        val delivery = json.decodeFromString<EnvelopeDto<CourierAssignmentDto>>(body)
            .requireData()
            .toDomain()

        val location = requireNotNull(delivery.latestLocation)
        assertEquals(-0.948, requireNotNull(location.latitude), 1e-9)
        assertEquals(100.418, requireNotNull(location.longitude), 1e-9)
    }

    @Test
    fun `incomplete coordinates are dropped instead of shown as zero`() {
        // Koordinat setengah tidak berguna. Menampilkannya sebagai 0.0 akan terlihat
        // seperti lokasi yang benar-benar diketahui.
        val body = """
            {
              "data": {
                "id": 7,
                "status": "ACTIVE",
                "latest_location": { "id": 55, "latitude": "-0.9480000" }
              },
              "message": "OK"
            }
        """.trimIndent()

        val dto = json.decodeFromString<EnvelopeDto<CourierAssignmentDto>>(body).requireData()

        assertNull(dto.toDomain().latestLocation)
        assertNull(dto.latest_location.toDomainOrNull())
    }

    // --- Aksi: start, location, cash/confirm, complete ---

    @Test
    fun `start response replaces status with the value returned by the backend`() {
        val body = """
            {
              "data": {
                "id": 7,
                "status": "ACTIVE",
                "started_at": "2026-01-01T09:00:00.000000Z",
                "order": { "id": 21, "status": "DALAM_PENGANTARAN", "payment": { "method": "CASH", "status": "PENDING" } }
              },
              "message": "Pengantaran berhasil dimulai."
            }
        """.trimIndent()

        val delivery = json.decodeFromString<EnvelopeDto<CourierAssignmentDto>>(body)
            .requireData()
            .toDomain()

        assertEquals(AssignmentStatus.ACTIVE, delivery.status)
        assertEquals(OrderStatus.DALAM_PENGANTARAN, delivery.order?.status)
        assertFalse(delivery.canStartDelivery)
        assertTrue(delivery.canPostLocation)
        // Status masih PENDING, jadi konfirmasi tunai baru boleh dikirim.
        assertTrue(delivery.canConfirmCash)
        assertFalse(delivery.canCompleteDelivery)
    }

    @Test
    fun `cash confirmation reads payment status from the server response`() {
        val body = """
            {
              "data": {
                "id": 9001,
                "payment_method": "CASH",
                "payment_status": "PAID",
                "verified_at": "2026-01-01T10:00:00.000000Z"
              },
              "message": "Pembayaran tunai berhasil dikonfirmasi."
            }
        """.trimIndent()

        val receipt = json.decodeFromString<EnvelopeDto<CourierCashReceiptDto>>(body)
            .requireData()
            .toDomain()

        // `PAID` di sini datang dari server. Tidak ada nilai default `PAID` di
        // DTO maupun mapper, jadi nilai ini tidak mungkin muncul tanpa backend.
        assertEquals("9001", receipt.paymentId)
        assertEquals(PaymentMethod.CASH, receipt.paymentMethod)
        assertEquals(PaymentStatus.PAID, receipt.paymentStatus)
        assertEquals("2026-01-01T10:00:00.000000Z", receipt.verifiedAt)
    }

    @Test
    fun `cash confirmation does not become paid when the server returns another status`() {
        // Bila backend mengembalikan status selain PAID, Android tidak boleh
        // menafsirkannya menjadi lunas.
        val body = """
            {
              "data": {
                "id": 9001,
                "payment_method": "CASH",
                "payment_status": "PENDING",
                "verified_at": null
              },
              "message": "OK"
            }
        """.trimIndent()

        val receipt = json.decodeFromString<EnvelopeDto<CourierCashReceiptDto>>(body)
            .requireData()
            .toDomain()

        assertEquals(PaymentStatus.PENDING, receipt.paymentStatus)
    }

    @Test
    fun `complete response reports the backend status verbatim`() {
        val body = """
            {
              "data": {
                "id": 7,
                "status": "COMPLETED",
                "completed_at": "2026-01-01T11:00:00.000000Z",
                "order": { "id": 21, "status": "SELESAI", "payment": { "method": "CASH", "status": "PAID" } }
              },
              "message": "Pengantaran berhasil diselesaikan."
            }
        """.trimIndent()

        val delivery = json.decodeFromString<EnvelopeDto<CourierAssignmentDto>>(body)
            .requireData()
            .toDomain()

        assertEquals(AssignmentStatus.COMPLETED, delivery.status)
        assertEquals(OrderStatus.SELESAI, delivery.order?.status)
        assertTrue(delivery.isFinished)
        // Setelah selesai, tidak ada aksi kurir yang boleh dibuka lagi.
        assertFalse(delivery.canStartDelivery)
        assertFalse(delivery.canPostLocation)
        assertFalse(delivery.canConfirmCash)
        assertFalse(delivery.canCompleteDelivery)
    }

    @Test
    fun `location request omits recorded_at so the server clock decides`() {
        // `recorded_at` dipakai backend untuk menghitung kecepatan perpindahan
        // lokasi. Repository tidak mengirimnya supaya jam perangkat yang tertinggal
        // tidak memicu penolakan 422.
        val request = CourierLocationRequestDto(
            latitude = -0.948,
            longitude = 100.418,
            accuracy_meters = 8.5,
            recorded_at = null,
            idempotency_key = "6f1c2b4e-6a2f-4a1d-9d3e-1a2b3c4d5e6f"
        )

        assertNull(request.recorded_at)
        assertEquals(-0.948, request.latitude, 1e-9)
        assertEquals(100.418, request.longitude, 1e-9)
        assertEquals(8.5, request.accuracy_meters, 1e-9)
    }

    @Test
    fun `location response wraps the location in data location as the backend sends`() {
        // `POST courier/assignments/{assignment}/location` menjawab
        // `data: {accepted, location}` (CourierController::location), BUKAN
        // objek lokasi di `data` itu sendiri. Test ini mengunci bentuk literal
        // itu; kalau DTO `data` berubah menjadi CourierLocationDto langsung,
        // seluruh koordinat akan terbaca null dan pengiriman tampak gagal.
        val body = """
            {
              "data": {
                "accepted": true,
                "location": {
                  "id": 55,
                  "assignment_id": 7,
                  "courier_id": 4,
                  "latitude": "-0.9480000",
                  "longitude": "100.4180000",
                  "accuracy_meters": "8.50",
                  "recorded_at": "2026-01-01T09:30:00.000000Z",
                  "idempotency_key": null
                }
              },
              "message": "Lokasi kurir berhasil diperbarui."
            }
        """.trimIndent()

        val accepted = json.decodeFromString<EnvelopeDto<CourierLocationAcceptedDto>>(body)
            .requireData()

        // `accepted` dibaca apa adanya dari backend; Android tidak pernah
        // menulis nilainya.
        assertTrue(accepted.accepted)

        // Lokasi yang sah di mata server berada di dalam `location`, dibaca
        // dengan serializer desimal yang sama seperti daftar lokasi.
        val location = requireNotNull(accepted.location).toDomainOrNull()
        assertEquals(-0.948, requireNotNull(location?.latitude), 1e-9)
        assertEquals(100.418, requireNotNull(location?.longitude), 1e-9)
        assertEquals(8.50, requireNotNull(location?.accuracyMeters), 1e-9)
        assertEquals("2026-01-01T09:30:00.000000Z", location?.recordedAt)

        // Pembuktian kebalikan: membuka `data` sebagai objek lokasi langsung
        // akan membuat semua koordinat null — itulah bug yang dihindari.
        val misread = json.decodeFromString<EnvelopeDto<CourierLocationDto>>(body)
            .requireData()
        assertNull(misread.latitude)
        assertNull(misread.longitude)
    }

    // --- GET dashboard/courier ---

    @Test
    fun `dashboard decodes summary and references without inventing customer data`() {
        val body = """
            {
              "data": {
                "active_assignment": {
                  "id": 21,
                  "status": "DALAM_PENGANTARAN",
                  "subtotal_amount": "16000.00",
                  "total_amount": "21000.00",
                  "delivery_address": "Jl. Merdeka 1",
                  "payment": { "payment_method": "CASH", "payment_status": "PENDING" },
                  "items": [
                    {
                      "product_id": 1,
                      "name": "Air Galon",
                      "quantity": 2,
                      "unit_price": "8000.00",
                      "line_total_amount": "16000.00"
                    }
                  ],
                  "assignment": { "id": 7, "status": "ACTIVE" },
                  "created_at": "2026-01-01T07:30:00.000000Z"
                },
                "today_assignments": [
                  {
                    "id": 22,
                    "status": "DITUGASKAN",
                    "subtotal_amount": "8000.00",
                    "total_amount": "13000.00",
                    "delivery_address": "Jl. Melati 2",
                    "payment": { "payment_method": "QRIS", "payment_status": "WAITING_VERIFICATION" },
                    "items": [],
                    "assignment": { "id": 8, "status": "ASSIGNED" },
                    "created_at": "2026-01-01T09:00:00.000000Z"
                  }
                ],
                "summary": {
                  "today_deliveries": 2,
                  "month_deliveries": 37,
                  "completed_deliveries": 35,
                  "completion_rate": 94.59
                },
                "unread_notifications": 3
              },
              "message": "OK"
            }
        """.trimIndent()

        val dashboard = json.decodeFromString<EnvelopeDto<CourierDashboardDto>>(body)
            .requireData()
            .toDomain()

        assertEquals(2, dashboard.summary.todayDeliveries)
        assertEquals(37, dashboard.summary.monthDeliveries)
        assertEquals(35, dashboard.summary.completedDeliveries)
        assertEquals(94.59, dashboard.summary.completionRate, 1e-9)
        assertEquals(3, dashboard.unreadNotifications)

        val active = requireNotNull(dashboard.activeDelivery)
        // Id di dashboard adalah id order, bukan id assignment.
        assertEquals("21", active.orderId)
        assertEquals("7", active.assignmentId)
        assertEquals(AssignmentStatus.ACTIVE, active.assignmentStatus)
        assertEquals(OrderStatus.DALAM_PENGANTARAN, active.status)
        assertEquals("21000.00", active.totalAmount)
        assertTrue(active.isDeliverable)

        val today = dashboard.todayDeliveries.single()
        assertEquals("22", today.orderId)
        assertEquals("8", today.assignmentId)
        assertEquals(PaymentMethod.QRIS, today.payment?.paymentMethod)
        assertEquals(PaymentStatus.WAITING_VERIFICATION, today.payment?.paymentStatus)
    }

    @Test
    fun `dashboard without summary falls back to zeroes instead of failing`() {
        // `summary` tidak wajib dikirim. Nilai default dipakai supaya layar tidak
        // gagal total, dan angka nol di situ berarti "tidak dilaporkan", bukan
        // "kurir tidak pernah mengantar".
        val body = """
            {
              "data": {
                "active_assignment": null,
                "today_assignments": [],
                "unread_notifications": 0
              },
              "message": "OK"
            }
        """.trimIndent()

        val dashboard = json.decodeFromString<EnvelopeDto<CourierDashboardDto>>(body)
            .requireData()
            .toDomain()

        assertNull(dashboard.activeDelivery)
        assertTrue(dashboard.todayDeliveries.isEmpty())
        assertEquals(0, dashboard.summary.todayDeliveries)
        assertEquals(0, dashboard.unreadNotifications)
    }

    @Test
    fun `dashboard order without assignment is not treated as deliverable`() {
        // Backend hanya memasukkan order yang punya assignment, tapi baris seperti
        // ini tetap harus aman kalau terjadi: tanpa assignment tidak ada endpoint
        // yang bisa dipanggil untuk membukanya.
        val body = """
            {
              "data": {
                "active_assignment": null,
                "today_assignments": [
                  { "id": 22, "status": "DITUGASKAN", "assignment": null }
                ],
                "summary": { "today_deliveries": 1 },
                "unread_notifications": 0
              },
              "message": "OK"
            }
        """.trimIndent()

        val dashboard = json.decodeFromString<EnvelopeDto<CourierDashboardDto>>(body)
            .requireData()
            .toDomain()

        val order = dashboard.todayDeliveries.single()
        assertNull(order.assignmentId)
        assertFalse(order.isDeliverable)
    }

    // --- Sesi bertugas (courier/duty) ---

    @Test
    fun `duty state decodes the active session and the latest stored location`() {
        val body = """
            {
              "data": {
                "active": true,
                "session": {
                  "id": 5,
                  "started_at": "2026-01-01T07:00:00.000000Z",
                  "ended_at": null,
                  "is_active": true
                },
                "latest_location": {
                  "latitude": "-0.9480000",
                  "longitude": "100.4180000",
                  "accuracy_meters": "6.00",
                  "recorded_at": "2026-01-01T07:05:00.000000Z"
                }
              },
              "message": "Status bertugas berhasil dimuat."
            }
        """.trimIndent()

        val duty = json.decodeFromString<EnvelopeDto<CourierDutyStateDto>>(body)
            .requireData()
            .toDomain()

        assertTrue(duty.isActive)
        assertEquals("5", duty.session?.id)
        assertTrue(duty.session?.isActive == true)
        // Lokasi memakai `CourierLocationResource` yang sama dengan jalur
        // assignment, jadi pemetaannya juga sama.
        assertEquals(-0.948, requireNotNull(duty.latestLocation?.latitude), 1e-9)
        assertEquals("2026-01-01T07:05:00.000000Z", duty.latestLocation?.recordedAt)
    }

    @Test
    fun `duty state without an active session is readable`() {
        val body =
            """{"data":{"active":false,"session":null,"latest_location":null},"message":"OK"}"""

        val duty = json.decodeFromString<EnvelopeDto<CourierDutyStateDto>>(body)
            .requireData()
            .toDomain()

        assertFalse(duty.isActive)
        assertNull(duty.session)
        assertNull(duty.latestLocation)
    }

    @Test
    fun `stop duty decodes the ended session from the server`() {
        val body = """
            {
              "data": {
                "id": 5,
                "started_at": "2026-01-01T07:00:00.000000Z",
                "ended_at": "2026-01-01T09:00:00.000000Z",
                "is_active": false
              },
              "message": "Sesi bertugas diakhiri."
            }
        """.trimIndent()

        val session = json.decodeFromString<EnvelopeDto<CourierDutySessionDto>>(body)
            .requireData()
            .toDomain()

        assertEquals("2026-01-01T09:00:00.000000Z", session.endedAt)
        assertFalse(session.isActive)
    }

    @Test
    fun `duty location response has the same accepted location shape as the assignment path`() {
        val body = """
            {
              "data": {
                "accepted": true,
                "location": {
                  "latitude": "-0.9480000",
                  "longitude": "100.4180000",
                  "accuracy_meters": "8.50",
                  "recorded_at": "2026-01-01T07:05:00.000000Z"
                }
              },
              "message": "Lokasi berhasil diperbarui."
            }
        """.trimIndent()

        val accepted = json.decodeFromString<EnvelopeDto<CourierLocationAcceptedDto>>(body)
            .requireData()

        assertTrue(accepted.accepted)
        val location = requireNotNull(accepted.location?.toDomainOrNull())
        assertEquals(-0.948, requireNotNull(location.latitude), 1e-9)
        assertEquals(100.418, requireNotNull(location.longitude), 1e-9)
        assertEquals("2026-01-01T07:05:00.000000Z", location.recordedAt)
    }

    // --- Nilai yang tidak dikenal ---

    @Test
    fun `unknown statuses map to null and close every courier action`() {
        val body = """
            {
              "data": {
                "id": 7,
                "status": "SOMETHING_ELSE",
                "order": {
                  "id": 21,
                  "status": "SOMETHING_ELSE",
                  "payment": { "method": "TRANSFER", "status": "SOMETHING_ELSE" }
                }
              },
              "message": "OK"
            }
        """.trimIndent()

        val delivery = json.decodeFromString<EnvelopeDto<CourierAssignmentDto>>(body)
            .requireData()
            .toDomain()

        assertNull(delivery.status)
        assertNull(delivery.order?.status)
        assertNull(delivery.order?.payment?.paymentMethod)
        assertNull(delivery.order?.payment?.paymentStatus)

        // Status yang tidak dikenal tidak boleh membuka aksi apa pun. Membuka
        // tombol di sini berarti Android mengarang aturan yang tidak ada di
        // backend.
        assertFalse(delivery.canStartDelivery)
        assertFalse(delivery.canPostLocation)
        assertFalse(delivery.canConfirmCash)
        assertFalse(delivery.canCompleteDelivery)
        assertFalse(delivery.isFinished)
    }

    @Test
    fun `blank customer name and address are treated as not sent`() {
        // Nilai kosong berarti backend tidak mengirimnya. Menjadikannya string
        // kosong membuat UI menampilkan baris kosong yang terlihat seperti data.
        val body = """
            {
              "data": {
                "id": 7,
                "status": "ASSIGNED",
                "order": { "id": 21, "delivery_address": "   ", "customer": { "name": "" } }
              },
              "message": "OK"
            }
        """.trimIndent()

        val delivery = json.decodeFromString<EnvelopeDto<CourierAssignmentDto>>(body)
            .requireData()
            .toDomain()

        assertNull(delivery.order?.deliveryAddress)
        assertNull(delivery.order?.customerName)
    }

    @Test
    fun `assignment without order is readable instead of crashing`() {
        val body = """{"data":{"id":7,"status":"ASSIGNED"},"message":"OK"}"""

        val delivery = json.decodeFromString<EnvelopeDto<CourierAssignmentDto>>(body)
            .requireData()
            .toDomain()

        assertNull(delivery.order)
        assertNull(delivery.orderId)
        assertNotNull(delivery.id)
        assertTrue(delivery.canStartDelivery)
    }

    private fun <T> EnvelopeDto<T>.requireData(): T =
        requireNotNull(data) { "Envelope harus punya data untuk test ini." }
}
