package com.kyusui.app.fcm

import com.kyusui.app.domain.model.NotificationEvent
import com.kyusui.app.domain.model.NotificationPayload
import com.kyusui.app.domain.model.NotificationResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test untuk [NotificationContent] — murni Kotlin, tanpa Android
 * framework, sehingga berjalan di JVM unit test.
 *
 * Mencakup NOTIF-001 sampai NOTIF-010.
 */
class NotificationContentTest {

    private fun payload(
        type: NotificationEvent,
        orderId: String? = "1001",
        assignmentId: String? = null
    ): NotificationPayload = NotificationPayload(
        notificationId = "notif-$type",
        type = type,
        resourceType = when (type) {
            NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED,
            NotificationEvent.PAYMENT_QRIS_APPROVED,
            NotificationEvent.PAYMENT_QRIS_REJECTED,
            NotificationEvent.PAYMENT_CASH_CONFIRMED -> NotificationResourceType.PAYMENT
            NotificationEvent.COURIER_ASSIGNED,
            NotificationEvent.DELIVERY_STARTED,
            NotificationEvent.TRACKING_AVAILABLE -> NotificationResourceType.DELIVERY_ASSIGNMENT
            else -> NotificationResourceType.ORDER
        },
        orderId = orderId,
        assignmentId = assignmentId
    )

    // --- NOTIF-001: ORDER_CREATED ---
    @Test
    fun `NOTIF-001 order created title and body`() {
        val p = payload(NotificationEvent.ORDER_CREATED)
        assertEquals("Pesanan Baru", NotificationContent.titleFor(p))
        assertEquals("Pesanan #1001 sudah masuk dan menunggu diproses.", NotificationContent.bodyFor(p))
    }

    // --- NOTIF-002: ORDER_PROCESSED ---
    @Test
    fun `NOTIF-002 order processed title and body`() {
        val p = payload(NotificationEvent.ORDER_PROCESSED)
        assertEquals("Pesanan Diproses", NotificationContent.titleFor(p))
        assertEquals("Pesanan #1001 sedang diproses.", NotificationContent.bodyFor(p))
    }

    // --- NOTIF-003: QRIS_PROOF_UPLOADED ---
    @Test
    fun `NOTIF-003 QRIS proof uploaded title and body`() {
        val p = payload(NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED)
        assertEquals("Bukti QRIS Menunggu Verifikasi", NotificationContent.titleFor(p))
        assertEquals("Bukti pembayaran QRIS untuk pesanan #1001 menunggu verifikasi.", NotificationContent.bodyFor(p))
    }

    // --- NOTIF-004: QRIS_APPROVED ---
    @Test
    fun `NOTIF-004 QRIS approved title and body`() {
        val p = payload(NotificationEvent.PAYMENT_QRIS_APPROVED)
        assertEquals("Pembayaran Disetujui", NotificationContent.titleFor(p))
        assertEquals("Pembayaran QRIS untuk pesanan #1001 telah disetujui.", NotificationContent.bodyFor(p))
    }

    // --- NOTIF-005: QRIS_REJECTED ---
    @Test
    fun `NOTIF-005 QRIS rejected title and body`() {
        val p = payload(NotificationEvent.PAYMENT_QRIS_REJECTED)
        assertEquals("Pembayaran Ditolak", NotificationContent.titleFor(p))
        assertEquals("Pembayaran QRIS untuk pesanan #1001 ditolak. Silakan lakukan pembayaran ulang.", NotificationContent.bodyFor(p))
    }

    // --- NOTIF-006: CASH_CONFIRMED ---
    @Test
    fun `NOTIF-006 cash confirmed title and body`() {
        val p = payload(NotificationEvent.PAYMENT_CASH_CONFIRMED)
        assertEquals("Pembayaran Dikonfirmasi", NotificationContent.titleFor(p))
        assertEquals("Pembayaran tunai untuk pesanan #1001 telah dikonfirmasi kurir.", NotificationContent.bodyFor(p))
    }

    // --- NOTIF-007: Unauthorized recipient (not a separate event, but coverage) ---
    @Test
    fun `NOTIF-007 different event types have distinct titles`() {
        val titles = NotificationEvent.values().map { NotificationContent.titleFor(payload(it)) }
        assertEquals(titles.size, titles.distinct().size)
    }

    // --- NOTIF-008: COURIER_ASSIGNED ---
    @Test
    fun `NOTIF-008 courier assigned title and body`() {
        val p = payload(NotificationEvent.COURIER_ASSIGNED)
        assertEquals("Kurir Ditugaskan", NotificationContent.titleFor(p))
        assertEquals("Kurir sudah ditugaskan untuk pesanan #1001.", NotificationContent.bodyFor(p))
    }

    // --- NOTIF-009: DELIVERY_STARTED ---
    @Test
    fun `NOTIF-009 delivery started title and body`() {
        val p = payload(NotificationEvent.DELIVERY_STARTED)
        assertEquals("Pesanan Sedang Diantar", NotificationContent.titleFor(p))
        assertEquals("Pengantar sedang menuju alamat Anda untuk pesanan #1001.", NotificationContent.bodyFor(p))
    }

    // --- NOTIF-010: TRACKING_AVAILABLE ---
    @Test
    fun `NOTIF-010 tracking available title and body`() {
        val p = payload(NotificationEvent.TRACKING_AVAILABLE)
        assertEquals("Lokasi Pengantar Tersedia", NotificationContent.titleFor(p))
        assertEquals("Lokasi pengantar untuk pesanan #1001 sudah dapat dilacak.", NotificationContent.bodyFor(p))
    }

    // --- Edge: missing orderId ---
    @Test
    fun `body without orderId does not contain placeholder`() {
        val p = payload(NotificationEvent.ORDER_CREATED, orderId = null)
        val body = NotificationContent.bodyFor(p)
        assertTrue(body.contains("Pesanan"))
        assertTrue(!body.contains("#"))
    }

    // --- Edge: ORDER_COMPLETED ---
    @Test
    fun `ORDER_COMPLETED title and body`() {
        val p = payload(NotificationEvent.ORDER_COMPLETED)
        assertEquals("Pesanan Selesai", NotificationContent.titleFor(p))
        assertEquals("Pesanan #1001 sudah selesai diantar.", NotificationContent.bodyFor(p))
    }
}