package com.kyusui.app.data.api

import com.kyusui.app.data.mapper.toDomain
import com.kyusui.app.data.mapper.toDomainOrNull
import com.kyusui.app.data.mapper.toVerificationResult
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OwnerVerificationAction
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture di test ini diambil bentuk literal dari
 * `06_KYUSUI_API_SPECIFICATION_REBUILT.md` section 7.4, 10.2, 12.1, 12.2, 12.3,
 * 13.1, dan 13.2.
 *
 * Test ini menjaga DTO Owner tetap sinkron dengan specification, khususnya tiga
 * hal yang paling mudah rusak:
 *
 * 1. nominal dan status hanya dibaca dari backend, tidak pernah dihitung atau
 *    ditebak di Android;
 * 2. response verifikasi membalut payment di dalam objek `payment`, dan status
 *    setelah approve/reject dibaca apa adanya dari response;
 * 3. bentuk `assignment` tidak didefinisikan di API specification, sehingga test
 *    ini memverifikasi bahwa assignment tanpa `courier_id` tidak ditampilkan
 *    sebagai assignment yang sah.
 */
class OwnerApiContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }


    @Test
    fun `owner dashboard decodes financial summary and six month cash flow`() {
        val body = """
            {"data":{
              "summary":{"total_orders":18,"waiting_process":3,"completed_orders":12,
                "waiting_qris_verification":2,"active_deliveries":1,"available_products":5,
                "today_revenue":25000.0,"month_revenue":350000.0,"month_expense":75000.0,
                "month_net":275000.0,"avg_order_value":29000.0,"completion_rate":66.7},
              "revenue_chart":[
                {"month":"2026-06","label":"Jun","income":100000.0,"expense":20000.0},
                {"month":"2026-07","label":"Jul","income":120000.0,"expense":25000.0}
              ]
            }}
        """.trimIndent()

        val dashboard = requireNotNull(
            json.decodeFromString<EnvelopeDto<OwnerDashboardDto>>(body).data
        ).toDomain()

        assertEquals(18, dashboard.totalOrders)
        assertEquals("350000.0", dashboard.monthRevenue)
        assertEquals("75000.0", dashboard.monthExpense)
        assertEquals("275000.0", dashboard.monthNet)
        assertEquals(2, dashboard.revenueChart.size)
        assertEquals(100000.0, dashboard.revenueChart.first().income, 0.0)
    }

    // --- 10.2 Owner Order Detail ---

    @Test
    fun `owner order detail decodes amounts and status from the server`() {
        val body = """
            {
              "data": {
                "id": 1001,
                "order_number": "ORD-20260930-0001",
                "customer_id": 10,
                "customer": { "id": 10, "name": "Budi" },
                "items": [
                  {
                    "product_id": 1,
                    "product_name": "Air Galon",
                    "quantity": 2,
                    "unit_price": "8000.00",
                    "line_total": "16000.00"
                  }
                ],
                "subtotal_amount": "16000.00",
                "delivery_fee": "5000.00",
                "total_amount": "21000.00",
                "order_status": "MENUNGGU_DIPROSES",
                "payment": {
                  "payment_method": "QRIS",
                  "payment_status": "PAID",
                  "amount": "21000.00"
                },
                "delivery_location": {
                  "latitude": -6.2,
                  "longitude": 106.8,
                  "address": "Jl. Merdeka 1"
                },
                "assignment": null
              },
              "message": "Success."
            }
        """.trimIndent()

        val order = requireNotNull(
            json.decodeFromString<EnvelopeDto<OwnerOrderDto>>(body).data
        ).toDomain()

        assertEquals("1001", order.id)
        assertEquals("ORD-20260930-0001", order.orderNumber)
        assertEquals("21000.00", order.totalAmount)
        assertEquals("5000.00", order.deliveryFee)
        assertEquals(OrderStatus.MENUNGGU_DIPROSES, order.orderStatus)
        assertEquals(PaymentMethod.QRIS, order.payment?.paymentMethod)
        assertEquals(PaymentStatus.PAID, order.payment?.paymentStatus)
        assertEquals("Budi", order.customer?.name)
        assertEquals("10", order.customer?.id)
        assertEquals("Jl. Merdeka 1", order.deliveryAddress)
        assertEquals(2, order.totalQuantity)
        assertNull(order.assignment)
    }

    @Test
    fun `unknown order status maps to null instead of a guessed status`() {
        val body = """{"data":{"id":1,"order_number":"ORD-1","order_status":"SOMETHING_NEW"}}"""

        val order = requireNotNull(
            json.decodeFromString<EnvelopeDto<OwnerOrderDto>>(body).data
        ).toDomain()

        assertNull(order.orderStatus)
        // Status yang tidak dikenal tidak boleh membuka tombol aksi apa pun.
        assertTrue(order.availableStatusActions.isEmpty())
    }

    @Test
    fun `missing delivery location leaves address and coordinates empty`() {
        val body = """{"data":{"id":1,"order_number":"ORD-1"}}"""

        val order = requireNotNull(
            json.decodeFromString<EnvelopeDto<OwnerOrderDto>>(body).data
        ).toDomain()

        assertNull(order.deliveryAddress)
        assertNull(order.deliveryLocation)
    }

    // --- 6.6 Courier Assignment ---

    @Test
    fun `assignment decodes the courier_assignments columns`() {
        val body = """
            {
              "data": {
                "id": 1001,
                "order_number": "ORD-20260930-0001",
                "assignment": {
                  "id": 77,
                  "courier_id": 20,
                  "status": "ASSIGNED",
                  "assigned_at": "2026-09-30T10:00:00Z"
                }
              }
            }
        """.trimIndent()

        val order = requireNotNull(
            json.decodeFromString<EnvelopeDto<OwnerOrderDto>>(body).data
        ).toDomain()

        val assignment = requireNotNull(order.assignment)
        assertEquals("77", assignment.id)
        assertEquals("20", assignment.courierId)
        assertEquals(AssignmentStatus.ASSIGNED, assignment.status)
        assertEquals("2026-09-30T10:00:00Z", assignment.assignedAt)
    }

    @Test
    fun `assignment without courier id is not exposed as a valid assignment`() {
        // API specification tidak mendefinisikan isi objek `assignment`. Bila
        // backend mengirim assignment tanpa `courier_id`, Owner tidak boleh melihat
        // "sudah ditugaskan" tanpa tahu ke siapa.
        val dto = OrderAssignmentDto(id = 77, courier_id = null, status = "ASSIGNED")

        assertNull(dto.toDomainOrNull())
    }

    @Test
    fun `unknown assignment status is not mapped to completed`() {
        val body = """
            {"data":{"id":1,"order_number":"ORD-1",
             "assignment":{"id":77,"courier_id":20,"status":"CANCELLED"}}}
        """.trimIndent()

        val order = requireNotNull(
            json.decodeFromString<EnvelopeDto<OwnerOrderDto>>(body).data
        ).toDomain()

        assertNull(order.assignment?.status)
    }

    // --- 12.1 List Pending QRIS Verification ---

    @Test
    fun `pending payment queue decodes the documented fields`() {
        val body = """
            {
              "data": [
                {
                  "id": 7001,
                  "order_id": 1001,
                  "order_number": "ORD-20260930-0001",
                  "customer": { "id": 10, "name": "Budi" },
                  "payment_method": "QRIS",
                  "payment_status": "WAITING_VERIFICATION",
                  "amount": "21000.00",
                  "proof": { "available": true },
                  "created_at": "2026-09-30T09:30:00Z",
                  "updated_at": "2026-09-30T09:31:00Z"
                }
              ],
              "meta": { "current_page": 1, "per_page": 20, "total": 1 }
            }
        """.trimIndent()

        val page = json.decodeFromString<PageDto<OwnerPendingPaymentDto>>(body)
        val payment = requireNotNull(page.data).single().toDomain()

        assertEquals("7001", payment.paymentId)
        assertEquals("1001", payment.orderId)
        assertEquals(PaymentMethod.QRIS, payment.paymentMethod)
        assertEquals(PaymentStatus.WAITING_VERIFICATION, payment.paymentStatus)
        assertEquals("21000.00", payment.amount)
        assertTrue(payment.hasProof)
        assertTrue(payment.isAwaitingVerification)
        assertEquals(1, requireNotNull(page.meta).total)
    }

    @Test
    fun `pending payment without proof is not treated as verified`() {
        val body = """
            {"data":[{"id":7001,"order_id":1001,"payment_method":"QRIS",
              "payment_status":"WAITING_VERIFICATION","amount":"21000.00"}],
             "meta":{"current_page":1,"per_page":20,"total":1}}
        """.trimIndent()

        val page = json.decodeFromString<PageDto<OwnerPendingPaymentDto>>(body)
        val payment = requireNotNull(page.data).single().toDomain()

        // Tidak ada `proof` pada response, jadi `hasProof` false. Ini laporan
        // backend, bukan penilaian Android, dan tidak mengubah status payment.
        assertFalse(payment.hasProof)
        assertEquals(PaymentStatus.WAITING_VERIFICATION, payment.paymentStatus)
    }

    // --- 12.2 View Payment Proof ---

    @Test
    fun `payment proof decodes availability and authorized url`() {
        val body = """
            {
              "data": {
                "order_id": 1001,
                "payment_id": 7001,
                "payment_method": "QRIS",
                "payment_status": "WAITING_VERIFICATION",
                "proof": {
                  "available": true,
                  "url": "http://10.0.2.2:8000/api/v1/owner/orders/1001/payment/proof/file"
                }
              },
              "message": "Success."
            }
        """.trimIndent()

        val proof = requireNotNull(
            json.decodeFromString<EnvelopeDto<OwnerPaymentProofDto>>(body).data
        ).toDomain()

        assertEquals("1001", proof.orderId)
        assertEquals("7001", proof.paymentId)
        assertTrue(proof.hasProof)
        assertTrue(proof.canDisplayImage)
    }

    @Test
    fun `payment proof without url is reported as not displayable`() {
        // Backend boleh melaporkan keberadaan tanpa URL. Android tidak menyusun
        // URL bukti sendiri.
        val body = """
            {"data":{"order_id":1001,"payment_method":"QRIS",
             "payment_status":"WAITING_VERIFICATION","proof":{"available":true}}}
        """.trimIndent()

        val proof = requireNotNull(
            json.decodeFromString<EnvelopeDto<OwnerPaymentProofDto>>(body).data
        ).toDomain()

        assertTrue(proof.hasProof)
        assertFalse(proof.canDisplayImage)
        assertNull(proof.proofUrl)
    }

    // --- 12.3 Approve / Reject ---

    @Test
    fun `approve response reports paid exactly as the server sent it`() {
        val body = """
            {
              "data": {
                "payment": {
                  "id": 7001,
                  "order_id": 1001,
                  "payment_method": "QRIS",
                  "payment_status": "PAID",
                  "amount": "21000.00",
                  "verified_by": "5",
                  "verified_at": "2026-09-30T10:15:00Z"
                }
              },
              "message": "Payment approved."
            }
        """.trimIndent()

        val result = requireNotNull(
            json.decodeFromString<EnvelopeDto<PaymentVerificationEnvelopeDto>>(body).data
        ).toVerificationResult(OwnerVerificationAction.APPROVE)

        // PAID di sini dibaca dari response, bukan ditulis Android.
        assertEquals(PaymentStatus.PAID, result.payment.paymentStatus)
        assertEquals(OwnerVerificationAction.APPROVE, result.action)
        assertEquals("5", result.payment.verifiedBy)
    }

    @Test
    fun `reject response reports pending exactly as the server sent it`() {
        val body = """
            {
              "data": {
                "payment": {
                  "id": 7001,
                  "order_id": 1001,
                  "payment_method": "QRIS",
                  "payment_status": "PENDING",
                  "amount": "21000.00",
                  "verified_by": null,
                  "verified_at": null
                }
              },
              "message": "Payment rejected."
            }
        """.trimIndent()

        val result = requireNotNull(
            json.decodeFromString<EnvelopeDto<PaymentVerificationEnvelopeDto>>(body).data
        ).toVerificationResult(OwnerVerificationAction.REJECT)

        // Reject mengembalikan ke PENDING, bukan ke status REJECTED yang tidak
        // ada pada contract.
        assertEquals(PaymentStatus.PENDING, result.payment.paymentStatus)
        assertNull(result.payment.verifiedBy)
        assertNull(result.payment.verifiedAt)
    }

    @Test
    fun `verification response without payment is rejected instead of guessing`() {
        val body = """{"data":{}}"""

        val envelope = json.decodeFromString<EnvelopeDto<PaymentVerificationEnvelopeDto>>(body)

        val failure = runCatching {
            requireNotNull(envelope.data).toVerificationResult(OwnerVerificationAction.APPROVE)
        }.exceptionOrNull()

        assertNotNull(failure)
    }

    // --- 13.1 / 13.2 Owner QRIS Configuration ---

    @Test
    fun `active qris decodes image path and update metadata`() {
        val body = """
            {
              "data": {
                "qris_image": "/storage/qris/active-qris.png",
                "updated_at": "2026-09-30T09:00:00Z",
                "updated_by": 5
              },
              "message": "Active QRIS retrieved."
            }
        """.trimIndent()

        val config = requireNotNull(
            json.decodeFromString<EnvelopeDto<OwnerActiveQrisDto>>(body).data
        ).toDomain()

        assertEquals("/storage/qris/active-qris.png", config.qrisImage)
        assertEquals("2026-09-30T09:00:00Z", config.updatedAt)
        assertEquals("5", config.updatedBy)
        assertTrue(config.isDisplayable)
    }

    @Test
    fun `active qris without image is reported as not displayable`() {
        val body = """{"data":{"qris_image":null,"updated_at":null,"updated_by":null}}"""

        val config = requireNotNull(
            json.decodeFromString<EnvelopeDto<OwnerActiveQrisDto>>(body).data
        ).toDomain()

        assertNull(config.qrisImage)
        assertFalse(config.isDisplayable)
    }

    // --- 10.3 / 10.4 Request bodies ---

    @Test
    fun `status update request uses the canonical status value`() {
        val request = UpdateOwnerOrderStatusRequestDto(
            status = OrderStatus.DIPROSES.apiValue,
            note = "Pesanan mulai diproses."
        )

        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"status\":\"DIPROSES\""))
        assertTrue(encoded.contains("\"note\":\"Pesanan mulai diproses.\""))
    }

    @Test
    fun `assignment request carries only courier id`() {
        val encoded = json.encodeToString(AssignCourierRequestDto(courier_id = 20))

        assertTrue(encoded.contains("\"courier_id\":20"))
    }

    @Test
    fun `verification request sends the action from the canonical enum`() {
        val approve = json.encodeToString(
            PaymentVerificationRequestDto(action = OwnerVerificationAction.APPROVE.apiValue)
        )
        val reject = json.encodeToString(
            PaymentVerificationRequestDto(
                action = OwnerVerificationAction.REJECT.apiValue,
                note = "Bukti tidak terbaca."
            )
        )

        assertTrue(approve.contains("\"action\":\"APPROVE\""))
        assertTrue(reject.contains("\"action\":\"REJECT\""))
        assertTrue(reject.contains("\"note\":\"Bukti tidak terbaca.\""))
    }

    // --- Preserved: GET /owner/couriers ---

    @Test
    fun `courier list decodes id and user name from the raw model`() {
        val body = """
            {
              "data": [
                {
                  "id": 20,
                  "user_id": 5,
                  "phone": "0812000000",
                  "vehicle": "Motor",
                  "user": { "id": 5, "name": "Budi Kurir" }
                }
              ],
              "message": "Data kurir tersedia berhasil dimuat."
            }
        """.trimIndent()

        val couriers = requireNotNull(
            json.decodeFromString<EnvelopeDto<List<OwnerCourierDto>>>(body).data
        ).map { it.toDomain() }

        assertEquals(1, couriers.size)
        assertEquals("20", couriers.first().id)
        assertEquals("Budi Kurir", couriers.first().displayLabel)
    }

    @Test
    fun `courier without a name keeps the id as its label`() {
        val body = """{"data":[{"id":21,"user_id":5}],"message":"ok"}"""

        val courier = requireNotNull(
            json.decodeFromString<EnvelopeDto<List<OwnerCourierDto>>>(body).data
        ).single().toDomain()

        // Tidak ada nama pada response, jadi label memakai id, bukan tebakan.
        assertEquals("Kurir #21", courier.displayLabel)
    }

    // --- Preserved: owner products CRUD ---

    @Test
    fun `owner product page decodes products including unavailable ones`() {
        val body = """
            {
              "data": [
                {
                  "id": 1001,
                  "name": "Air Galon",
                  "description": "Air galon isi 19 liter.",
                  "price": "18000.00",
                  "is_available": false
                }
              ],
              "meta": { "current_page": 1, "per_page": 20, "total": 1 },
              "message": "Data semua produk berhasil diambil."
            }
        """.trimIndent()

        val page = json.decodeFromString<PageDto<OwnerProductDto>>(body)
        val product = requireNotNull(page.data).single().toDomain()

        assertEquals("1001", product.id)
        assertEquals("Air Galon", product.name)
        assertEquals("18000.00", product.price)
        // Produk nonaktif tetap datang dari backend, bukan disembunyikan Android.
        assertFalse(product.isAvailable)
        assertEquals(1, requireNotNull(page.meta).total)
    }

    @Test
    fun `create product request uses the availability field from the backend`() {
        val request = CreateOwnerProductRequestDto(
            name = "Air Galon",
            description = null,
            price = "18000.00",
            availability = true
        )

        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"name\":\"Air Galon\""))
        assertTrue(encoded.contains("\"price\":\"18000.00\""))
        // Backend memvalidasi `availability`, bukan `is_available`.
        assertTrue(encoded.contains("\"availability\":true"))
        assertFalse(encoded.contains("is_available"))
    }

    @Test
    fun `update request sends only the fields provided`() {
        val request = UpdateOwnerProductRequestDto(
            name = null,
            description = "Diperbarui",
            price = null,
            availability = false
        )

        val encoded = json.encodeToString(request)

        assertFalse(encoded.contains("\"name\""))
        assertFalse(encoded.contains("\"price\""))
        assertTrue(encoded.contains("\"description\":\"Diperbarui\""))
        assertTrue(encoded.contains("\"availability\":false"))
    }

    @Test
    fun `product without description is decoded as null`() {
        val body = """
            {"data":[{"id":1,"name":"Air","price":"1000.00","is_available":true}],
             "meta":{"current_page":1,"per_page":20,"total":1}}
        """.trimIndent()

        val product = requireNotNull(
            json.decodeFromString<PageDto<OwnerProductDto>>(body).data
        ).single().toDomain()

        assertNull(product.description)
        assertTrue(product.isAvailable)
    }
}