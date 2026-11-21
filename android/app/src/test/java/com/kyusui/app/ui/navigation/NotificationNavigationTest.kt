package com.kyusui.app.ui.navigation

import com.kyusui.app.domain.model.NotificationEvent
import com.kyusui.app.domain.model.NotificationPayload
import com.kyusui.app.domain.model.NotificationResourceType
import com.kyusui.app.domain.model.UserRole
import com.kyusui.app.ui.notifications.NotificationTapRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test integrasi navigasi notifikasi.
 *
 * Memverifikasi [NotificationTapRouter.resolveRoute] menghasilkan rute
 * yang cocok dengan `RoutePaths` yang terdaftar di [NavigationHost].
 * Mencakup NOTIF-001 sampai NOTIF-010.
 */
class NotificationNavigationTest {

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

    @Test
    fun `NOTIF-001 order created navigates to customer and owner order detail`() {
        val p = payload(NotificationEvent.ORDER_CREATED)
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p)
        )
        assertEquals(
            RoutePaths.OWNER_ORDER_DETAIL.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.OWNER, p)
        )
        assertNull(NotificationTapRouter.resolveRoute(UserRole.COURIER, p))
    }

    @Test
    fun `NOTIF-002 order processed navigates customer only`() {
        val p = payload(NotificationEvent.ORDER_PROCESSED)
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p)
        )
        assertNull(NotificationTapRouter.resolveRoute(UserRole.OWNER, p))
        assertNull(NotificationTapRouter.resolveRoute(UserRole.COURIER, p))
    }

    @Test
    fun `NOTIF-003 QRIS proof uploaded navigates owner only`() {
        val p = payload(NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED)
        assertEquals(
            RoutePaths.OWNER_ORDER_DETAIL.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.OWNER, p)
        )
        assertNull(NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p))
        assertNull(NotificationTapRouter.resolveRoute(UserRole.COURIER, p))
    }

    @Test
    fun `NOTIF-004 QRIS approved navigates customer to payment`() {
        val p = payload(NotificationEvent.PAYMENT_QRIS_APPROVED)
        assertEquals(
            RoutePaths.CUSTOMER_PAYMENT_SELECTION.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p)
        )
        assertNull(NotificationTapRouter.resolveRoute(UserRole.OWNER, p))
        assertNull(NotificationTapRouter.resolveRoute(UserRole.COURIER, p))
    }

    @Test
    fun `NOTIF-005 QRIS rejected navigates customer to payment`() {
        val p = payload(NotificationEvent.PAYMENT_QRIS_REJECTED)
        assertEquals(
            RoutePaths.CUSTOMER_PAYMENT_SELECTION.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p)
        )
    }

    @Test
    fun `NOTIF-006 cash confirmed navigates customer to payment`() {
        val p = payload(NotificationEvent.PAYMENT_CASH_CONFIRMED)
        assertEquals(
            RoutePaths.CUSTOMER_PAYMENT_SELECTION.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p)
        )
    }

    @Test
    fun `NOTIF-007 unauthorized recipient gets no route`() {
        // Owner event tidak bisa dibuka customer
        val ownerEvent = payload(NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED)
        assertNull(NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, ownerEvent))

        // Customer event tidak bisa dibuka owner
        val customerEvent = payload(NotificationEvent.PAYMENT_QRIS_APPROVED)
        assertNull(NotificationTapRouter.resolveRoute(UserRole.OWNER, customerEvent))
    }

    @Test
    fun `NOTIF-008 courier assigned navigates customer to order and courier to delivery`() {
        val p = payload(NotificationEvent.COURIER_ASSIGNED, assignmentId = "7")
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p)
        )
        assertEquals(
            RoutePaths.COURIER_DELIVERY_DETAIL.replace("{assignmentId}", "7"),
            NotificationTapRouter.resolveRoute(UserRole.COURIER, p)
        )
        assertNull(NotificationTapRouter.resolveRoute(UserRole.OWNER, p))
    }

    @Test
    fun `NOTIF-009 delivery started navigates customer to tracking`() {
        val p = payload(NotificationEvent.DELIVERY_STARTED)
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_TRACKING.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p)
        )
        assertNull(NotificationTapRouter.resolveRoute(UserRole.OWNER, p))
        assertNull(NotificationTapRouter.resolveRoute(UserRole.COURIER, p))
    }

    @Test
    fun `NOTIF-010 tracking available navigates customer to tracking`() {
        val p = payload(NotificationEvent.TRACKING_AVAILABLE)
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_TRACKING.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p)
        )
    }

    @Test
    fun `ORDER_COMPLETED navigates all three roles`() {
        val p = payload(NotificationEvent.ORDER_COMPLETED, assignmentId = "7")
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p)
        )
        assertEquals(
            RoutePaths.OWNER_ORDER_DETAIL.replace("{orderId}", "1001"),
            NotificationTapRouter.resolveRoute(UserRole.OWNER, p)
        )
        assertEquals(
            RoutePaths.COURIER_DELIVERY_DETAIL.replace("{assignmentId}", "7"),
            NotificationTapRouter.resolveRoute(UserRole.COURIER, p)
        )
    }

    @Test
    fun `payload without orderId returns null`() {
        val p = payload(NotificationEvent.ORDER_CREATED, orderId = null)
        assertNull(NotificationTapRouter.resolveRoute(UserRole.CUSTOMER, p))
    }
}