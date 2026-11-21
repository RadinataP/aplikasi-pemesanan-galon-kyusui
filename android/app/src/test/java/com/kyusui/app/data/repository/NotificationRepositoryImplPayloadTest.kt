package com.kyusui.app.data.repository

import com.kyusui.app.core.Result
import com.kyusui.app.data.api.NotificationApi
import com.kyusui.app.domain.model.NotificationEvent
import com.kyusui.app.domain.model.NotificationPayload
import com.kyusui.app.domain.model.NotificationResourceType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test parsing payload FCM di [NotificationRepositoryImpl].
 *
 * Dipisah dari tap-router test supaya logika parsing (bukan routing)
 * teruji terpisah. Mencakup NOTIF-001 sampai NOTIF-010.
 */
class NotificationRepositoryImplPayloadTest {

    private val mockApi = mockk<NotificationApi>()
    private val repo = NotificationRepositoryImpl(mockApi)

    private fun dataOf(vararg pairs: Pair<String, String>): Map<String, String> =
        pairs.toMap()

    // --- Valid payloads per event ---

    @Test
    fun `parse valid ORDER_CREATED payload`() {
        val data = dataOf(
            "schema_version" to "1",
            "notification_id" to "n-1",
            "type" to "ORDER_CREATED",
            "resource_type" to "ORDER",
            "resource_id" to "1001",
            "order_id" to "1001",
            "created_at" to "2026-01-01T10:00:00Z"
        )

        val result = repo.parsePayload(data)
        assertTrue(result is Result.Success)

        val p = (result as Result.Success).data
        assertEquals(1, p.schemaVersion)
        assertEquals("n-1", p.notificationId)
        assertEquals(NotificationEvent.ORDER_CREATED, p.type)
        assertEquals(NotificationResourceType.ORDER, p.resourceType)
        assertEquals("1001", p.resourceId)
        assertEquals("1001", p.orderId)
        assertNull(p.assignmentId)
        assertEquals("2026-01-01T10:00:00Z", p.createdAt)
    }

    @Test
    fun `parse valid COURIER_ASSIGNED with assignment_id`() {
        val data = dataOf(
            "type" to "COURIER_ASSIGNED",
            "resource_type" to "DELIVERY_ASSIGNMENT",
            "notification_id" to "n-2",
            "order_id" to "1001",
            "assignment_id" to "7",
            "resource_id" to "7"
        )

        val p = (repo.parsePayload(data) as Result.Success).data
        assertEquals(NotificationEvent.COURIER_ASSIGNED, p.type)
        assertEquals(NotificationResourceType.DELIVERY_ASSIGNMENT, p.resourceType)
        assertEquals("7", p.assignmentId)
    }

    @Test
    fun `parse valid PAYMENT_QRIS_APPROVED with payment fields`() {
        val data = dataOf(
            "type" to "PAYMENT_QRIS_APPROVED",
            "resource_type" to "PAYMENT",
            "notification_id" to "n-3",
            "order_id" to "1001",
            "order_status" to "PAID",
            "payment_method" to "QRIS",
            "payment_status" to "APPROVED"
        )

        val p = (repo.parsePayload(data) as Result.Success).data
        assertEquals(NotificationEvent.PAYMENT_QRIS_APPROVED, p.type)
        assertEquals(NotificationResourceType.PAYMENT, p.resourceType)
        assertEquals("PAID", p.orderStatus)
        assertEquals("QRIS", p.paymentMethod)
        assertEquals("APPROVED", p.paymentStatus)
    }

    // --- Missing required fields ---

    @Test
    fun `missing type field fails`() {
        val data = dataOf("resource_type" to "ORDER", "notification_id" to "n")
        val result = repo.parsePayload(data)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception.message?.contains("type") ?: false)
    }

    @Test
    fun `unknown type fails`() {
        val data = dataOf("type" to "UNKNOWN_EVENT", "resource_type" to "ORDER", "notification_id" to "n")
        val result = repo.parsePayload(data)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception.message?.contains("tidak dikenal") ?: false)
    }

    @Test
    fun `missing resource_type fails`() {
        val data = dataOf("type" to "ORDER_CREATED", "notification_id" to "n")
        val result = repo.parsePayload(data)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception.message?.contains("resource_type") ?: false)
    }

    @Test
    fun `unknown resource_type fails`() {
        val data = dataOf("type" to "ORDER_CREATED", "resource_type" to "UNKNOWN", "notification_id" to "n")
        val result = repo.parsePayload(data)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception.message?.contains("tidak dikenal") ?: false)
    }

    @Test
    fun `missing notification_id fails`() {
        val data = dataOf("type" to "ORDER_CREATED", "resource_type" to "ORDER")
        val result = repo.parsePayload(data)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception.message?.contains("notification_id") ?: false)
    }

    // --- Default / optional fields ---

    @Test
    fun `schema_version defaults to 1 when absent`() {
        val data = dataOf("type" to "ORDER_CREATED", "resource_type" to "ORDER", "notification_id" to "n")
        val p = (repo.parsePayload(data) as Result.Success).data
        assertEquals(1, p.schemaVersion)
    }

    @Test
    fun `schema_version parses integer`() {
        val data = dataOf(
            "type" to "ORDER_CREATED",
            "resource_type" to "ORDER",
            "notification_id" to "n",
            "schema_version" to "2"
        )
        val p = (repo.parsePayload(data) as Result.Success).data
        assertEquals(2, p.schemaVersion)
    }

    @Test
    fun `all optional fields null when absent`() {
        val data = dataOf("type" to "ORDER_CREATED", "resource_type" to "ORDER", "notification_id" to "n")
        val p = (repo.parsePayload(data) as Result.Success).data
        assertNull(p.resourceId)
        assertNull(p.orderId)
        assertNull(p.assignmentId)
        assertNull(p.orderStatus)
        assertNull(p.paymentMethod)
        assertNull(p.paymentStatus)
        assertNull(p.targetRoute)
        assertNull(p.createdAt)
    }
}