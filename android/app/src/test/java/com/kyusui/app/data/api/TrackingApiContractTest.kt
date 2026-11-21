package com.kyusui.app.data.api

import com.kyusui.app.data.mapper.toDomain
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.OrderTracking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontrak payload `GET orders/{order}/tracking`.
 *
 * Fixture diambil dari bentuk yang benar-benar dikembalikan backend, yaitu
 * `OrderController::tracking` yang mengembalikan model `CourierAssignment`
 * mentah beserta relasi `locations`. Bentuk itu berbeda dari API Resource mana
 * pun di backend, jadi test ini mengunci perbedaan itu.
 */
class TrackingApiContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `tracking envelope decodes the raw assignment shape`() {
        val body = """
        {
          "data": {
            "id": 7,
            "order_id": 21,
            "courier_id": 4,
            "status": "ACTIVE",
            "assigned_at": "2026-03-01T08:00:00.000000Z",
            "started_at": "2026-03-01T09:00:00.000000Z",
            "completed_at": null,
            "locations": []
          },
          "message": "Data tracking berhasil dimuat."
        }
        """.trimIndent()

        val envelope = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body)

        assertNotNull(envelope.data)
        assertEquals(7L, envelope.data!!.id)
        assertEquals(21L, envelope.data!!.order_id)
        assertEquals(4L, envelope.data!!.courier_id)
        assertEquals("ACTIVE", envelope.data!!.status)
        assertEquals(0, envelope.data!!.locations.size)
    }

    @Test
    fun `tracking response carries courier id which the courier assignment resource omits`() {
        // `CourierAssignmentResource` milik kurir sengaja tidak mengirim
        // `courier_id`, tapi `OrderController::tracking` mengembalikan model
        // Eloquent penuh sehingga field itu ada. Kalau suatu saat backend
        // menyelaraskan keduanya, test ini yang akan memberi tahu.
        val body = """
        {"data":{"id":7,"order_id":21,"courier_id":4,"status":"ACTIVE","locations":[]}}
        """.trimIndent()

        val dto = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body).data

        assertEquals(4L, dto?.courier_id)
    }

    @Test
    fun `location decimals arrive as strings and are parsed to doubles`() {
        // Kolom `courier_locations` di-cast `decimal:7`, jadi JSON berisi
        // string. Serializer harus menerimanya, bukan gagal atau jadi nol.
        val body = """
        {"data":{"id":7,"order_id":21,"status":"ACTIVE","locations":[
          {"id":55,"assignment_id":7,"courier_id":4,
           "latitude":"-0.9480000","longitude":"100.4180000",
           "accuracy_meters":"8.50","recorded_at":"2026-03-01T10:30:00.000000Z"}
        ]}}
        """.trimIndent()

        val tracking = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body)
            .data!!.toDomain()

        val point = tracking.locations.single()
        assertEquals(-0.948, point.point.latitude, 1e-9)
        assertEquals(100.418, point.point.longitude, 1e-9)
        assertEquals(8.5, point.accuracyMeters!!, 1e-9)
    }

    @Test
    fun `latest location is the first element because backend orders by recorded_at desc`() {
        val body = """
        {"data":{"id":7,"order_id":21,"status":"ACTIVE","locations":[
          {"id":55,"assignment_id":7,"latitude":"-0.9480000","longitude":"100.4180000",
           "recorded_at":"2026-03-01T10:30:00.000000Z"},
          {"id":54,"assignment_id":7,"latitude":"-0.9400000","longitude":"100.4100000",
           "recorded_at":"2026-03-01T10:00:00.000000Z"}
        ]}}
        """.trimIndent()

        val tracking = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body)
            .data!!.toDomain()

        // Backend memuat relasi dengan `latest('recorded_at')`, jadi elemen
        // pertama adalah yang waktunya paling baru, bukan yang id-nya paling
        // besar. Yang diuji di sini adalah urutannya, bukan id-nya.
        assertEquals("2026-03-01T10:30:00.000000Z", tracking.latestLocation!!.recordedAt)
        assertEquals(-0.948, tracking.latestLocation!!.point.latitude, 1e-9)
        assertEquals(2, tracking.locations.size)
    }

    @Test
    fun `active delivery with no positions yet is a success without a position`() {
        val body = """
        {"data":{"id":7,"order_id":21,"status":"ACTIVE","locations":[]}}
        """.trimIndent()

        val tracking = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body)
            .data!!.toDomain()

        assertTrue(tracking.hasPositions.not())
        assertNull(tracking.latestLocation)
    }

    @Test
    fun `point without coordinates is dropped rather than plotted at zero`() {
        // Mengganti koordinat hilang dengan 0.0 akan menandai Gulf of Guinea
        // sebagai posisi kurir. Titik seperti itu harus dibuang.
        val body = """
        {"data":{"id":7,"order_id":21,"status":"ACTIVE","locations":[
          {"id":55,"assignment_id":7,"latitude":null,"longitude":null},
          {"id":54,"assignment_id":7,"latitude":"-0.9400000","longitude":"100.4100000"}
        ]}}
        """.trimIndent()

        val tracking = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body)
            .data!!.toDomain()

        assertEquals(1, tracking.locations.size)
        assertEquals(-0.94, tracking.locations.single().point.latitude, 1e-9)
    }

    @Test
    fun `point with only latitude is also dropped`() {
        val body = """
        {"data":{"id":7,"order_id":21,"status":"ACTIVE","locations":[
          {"id":55,"assignment_id":7,"latitude":"-0.9400000","longitude":null}
        ]}}
        """.trimIndent()

        val tracking = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body)
            .data!!.toDomain()

        assertEquals(0, tracking.locations.size)
    }

    @Test
    fun `null data decodes as no active delivery instead of a broken payload`() {
        val body = """
        {"data":null,"message":"Data tracking berhasil dimuat."}
        """.trimIndent()

        val envelope = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body)

        assertNull(envelope.data)
    }

    @Test
    fun `assignment status maps to the known enum values`() {
        val body = """
        {"data":{"id":7,"order_id":21,"status":"ACTIVE","locations":[]}}
        """.trimIndent()

        val tracking = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body)
            .data!!.toDomain()

        assertEquals(AssignmentStatus.ACTIVE, tracking.assignmentStatus)
    }

    @Test
    fun `unknown assignment status becomes null instead of a guessed status`() {
        val body = """
        {"data":{"id":7,"order_id":21,"status":"TELEPORTED","locations":[]}}
        """.trimIndent()

        val tracking = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body)
            .data!!.toDomain()

        assertNull(tracking.assignmentStatus)
    }

    @Test
    fun `tracking dto carries no customer name block because the backend does not send one`() {
        // `OrderController::tracking` tidak memuat relasi order maupun
        // customer, jadi tidak ada field yang boleh dikarang di sini. Yang
        // ada di DTO ini adalah field yang benar-benar ada di model Eloquent.
        val body = """
        {"data":{"id":7,"order_id":21,"courier_id":4,"status":"ACTIVE",
          "assigned_at":null,"started_at":null,"completed_at":null,"locations":[]}}
        """.trimIndent()

        val dto = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body).data

        assertNull(dto!!.assigned_at)
        assertNull(dto.started_at)
        assertNull(dto.completed_at)
        assertNull(dto.order_id.takeIf { it == 0L })
    }

    @Test
    fun `completed assignment timestamp is preserved verbatim`() {
        val body = """
        {"data":{"id":7,"order_id":21,"status":"COMPLETED",
          "completed_at":"2026-03-01T11:00:00.000000Z","locations":[]}}
        """.trimIndent()

        val tracking: OrderTracking = json.decodeFromString<EnvelopeDto<CustomerTrackingDto>>(body)
            .data!!.toDomain()

        assertEquals(AssignmentStatus.COMPLETED, tracking.assignmentStatus)
        assertEquals("2026-03-01T11:00:00.000000Z", tracking.completedAt)
    }
}
