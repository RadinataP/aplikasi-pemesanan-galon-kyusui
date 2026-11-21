package com.kyusui.app.ui.notifications

import com.kyusui.app.domain.model.NotificationEvent
import com.kyusui.app.domain.model.NotificationPayload
import com.kyusui.app.domain.model.NotificationResourceType
import com.kyusui.app.domain.model.UserRole
import com.kyusui.app.ui.navigation.RoutePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test notification tap routing untuk NOTIF-001 sampai NOTIF-010.
 *
 * Setiap event type harus menghasilkan rute yang benar berdasarkan
 * role user. Payload hanya membawa routing context — Android
 * menentukan tujuan berdasarkan role dan tipe event.
 */
class NotificationTapRouterTest {

    // --- NOTIF-001: Order Created ---

    @Test
    fun `NOTIF-001 order created routes customer to order detail`() {
        val payload = createPayload(NotificationEvent.ORDER_CREATED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, payload)
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", "1001"),
            route
        )
    }

    @Test
    fun `NOTIF-001 order created routes owner to order detail`() {
        val payload = createPayload(NotificationEvent.ORDER_CREATED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.OWNER, payload)
        assertEquals(
            RoutePaths.OWNER_ORDER_DETAIL.replace("{orderId}", "1001"),
            route
        )
    }

    // --- NOTIF-002: Order Processed ---

    @Test
    fun `NOTIF-002 order processed routes customer to order detail`() {
        val payload = createPayload(NotificationEvent.ORDER_PROCESSED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, payload)
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", "1001"),
            route
        )
    }

    @Test
    fun `NOTIF-002 order processed does not route owner`() {
        val payload = createPayload(NotificationEvent.ORDER_PROCESSED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.OWNER, payload)
        assertNull(route)
    }

    // --- NOTIF-003: QRIS Proof Uploaded ---

    @Test
    fun `NOTIF-003 QRIS proof uploaded routes owner to order detail`() {
        val payload = createPayload(NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.OWNER, payload)
        assertEquals(
            RoutePaths.OWNER_ORDER_DETAIL.replace("{orderId}", "1001"),
            route
        )
    }

    @Test
    fun `NOTIF-003 QRIS proof uploaded does not route customer`() {
        val payload = createPayload(NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, payload)
        assertNull(route)
    }

    // --- NOTIF-004: QRIS Approved ---

    @Test
    fun `NOTIF-004 QRIS approved routes customer to payment`() {
        val payload = createPayload(NotificationEvent.PAYMENT_QRIS_APPROVED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, payload)
        assertEquals(
            RoutePaths.CUSTOMER_PAYMENT_SELECTION.replace("{orderId}", "1001"),
            route
        )
    }

    // --- NOTIF-005: QRIS Rejected ---

    @Test
    fun `NOTIF-005 QRIS rejected routes customer to payment`() {
        val payload = createPayload(NotificationEvent.PAYMENT_QRIS_REJECTED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, payload)
        assertEquals(
            RoutePaths.CUSTOMER_PAYMENT_SELECTION.replace("{orderId}", "1001"),
            route
        )
    }

    // --- NOTIF-006: Cash Confirmed ---

    @Test
    fun `NOTIF-006 cash confirmed routes customer to payment`() {
        val payload = createPayload(NotificationEvent.PAYMENT_CASH_CONFIRMED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, payload)
        assertEquals(
            RoutePaths.CUSTOMER_PAYMENT_SELECTION.replace("{orderId}", "1001"),
            route
        )
    }

    // --- NOTIF-007: Unauthorized Recipient ---

    @Test
    fun `NOTIF-007 unauthorized recipient gets no route`() {
        // Customer tidak menerima notification untuk event yang
        // hanya untuk Owner, dan sebaliknya.
        val ownerPayload = createPayload(NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED, orderId = "1001")
        assertNull(NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, ownerPayload))

        val customerPayload = createPayload(NotificationEvent.PAYMENT_QRIS_APPROVED, orderId = "1001")
        assertNull(NotificationTapRouter.resolveRoute(UserRole.OWNER, customerPayload))
    }

    // --- NOTIF-008: Courier Assignment ---

    @Test
    fun `NOTIF-008 courier assigned routes customer to order detail`() {
        val payload = createPayload(NotificationEvent.COURIER_ASSIGNED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, payload)
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", "1001"),
            route
        )
    }

    @Test
    fun `NOTIF-008 courier assigned routes courier to delivery detail`() {
        val payload = createPayload(
            NotificationEvent.COURIER_ASSIGNED,
            orderId = "1001",
            assignmentId = "7"
        )
        val route = NotificationTapRouter.resolveRoute(UserRole.COURIER, payload)
        assertEquals(
            RoutePaths.COURIER_DELIVERY_DETAIL.replace("{assignmentId}", "7"),
            route
        )
    }

    // --- NOTIF-009: Delivery Started ---

    @Test
    fun `NOTIF-009 delivery started routes customer to tracking`() {
        val payload = createPayload(NotificationEvent.DELIVERY_STARTED, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, payload)
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_TRACKING.replace("{orderId}", "1001"),
            route
        )
    }

    // --- NOTIF-010: Tracking Available ---

    @Test
    fun `NOTIF-010 tracking available routes customer to tracking`() {
        val payload = createPayload(NotificationEvent.TRACKING_AVAILABLE, orderId = "1001")
        val route = NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, payload)
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_TRACKING.replace("{orderId}", "1001"),
            route
        )
    }

    // --- Payload Validation ---

    @Test
    fun `payload without order id is invalid`() {
        val payload = createPayload(NotificationEvent.ORDER_CREATED, orderId = null)
        assertTrue(!NotificationTapRouter.isValidPayload(payload))
    }

    @Test
    fun `payload without notification id is invalid`() {
        val payload = createPayload(NotificationEvent.ORDER_CREATED, orderId = "1001")
            .copy(notificationId = "")
        assertTrue(!NotificationTapRouter.isValidPayload(payload))
    }

    @Test
    fun `payload with order id and notification id is valid`() {
        val payload = createPayload(NotificationEvent.ORDER_CREATED, orderId = "1001")
        assertTrue(NotificationTapRouter.isValidPayload(payload))
    }

    // --- Helper ---

    private fun createPayload(
        type: NotificationEvent,
        orderId: String?,
        assignmentId: String? = null
    ): NotificationPayload = NotificationPayload(
        notificationId = "notif-001",
        type = type,
        resourceType = when (type) {
            NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED,
            NotificationEvent.PAYMENT_QRIS_APPROVED,
            NotificationEvent.PAYMENT_QRIS_REJECTED,
            NotificationEvent.PAYMENT_CASH_CONFIRMED -> NotificationResourceType.PAYMENT
            NotificationEvent.COURIER_ASSIGNED,
            NotificationEvent.TRACKING_AVAILABLE -> NotificationResourceType.DELIVERY_ASSIGNMENT
            else -> NotificationResourceType.ORDER
        },
        orderId = orderId,
        assignmentId = assignmentId
    )
}