package com.kyusui.app.ui.notifications

import com.kyusui.app.core.Result
import com.kyusui.app.data.api.NotificationApi
import com.kyusui.app.data.repository.NotificationRepositoryImpl
import com.kyusui.app.domain.model.UserRole
import com.kyusui.app.ui.navigation.RoutePaths
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test rantai lengkap Intent notification sampai rute: extras payload ->
 * parse oleh repository -> [NotificationTapRouter] dengan role nyata.
 *
 * Test ini menjaga dua hal yang tidak boleh terpisah:
 * - payload system notification diparse dengan kontrak yang sama seperti
 *   notifikasi in-app;
 * - tujuan ditentukan oleh role sesi, bukan default CUSTOMER.
 */
class NotificationDeepLinkRoutingTest {

    private val repository = NotificationRepositoryImpl(mockk<NotificationApi>())

    private fun extras(vararg pairs: Pair<String, String>): Map<String, String> =
        pairs.toMap()

    private fun routeFor(role: UserRole, vararg pairs: Pair<String, String>): String? {
        val payload = (repository.parsePayload(extras(*pairs)) as Result.Success).data
        return NotificationTapRouter.resolveRoute(role, payload)
    }

    @Test
    fun `order created from a notification routes owner to owner order detail`() {
        val route = routeFor(
            UserRole.OWNER,
            "type" to "ORDER_CREATED",
            "resource_type" to "ORDER",
            "notification_id" to "n-1",
            "order_id" to "1001"
        )

        assertEquals(RoutePaths.OWNER_ORDER_DETAIL.replace("{orderId}", "1001"), route)
    }

    @Test
    fun `order created does not route courier through the customer role`() {
        val route = routeFor(
            UserRole.COURIER,
            "type" to "ORDER_CREATED",
            "resource_type" to "ORDER",
            "notification_id" to "n-1",
            "order_id" to "1001"
        )

        assertNull(route)
    }

    @Test
    fun `courier assignment routes courier to delivery detail using assignment id`() {
        val route = routeFor(
            UserRole.COURIER,
            "type" to "COURIER_ASSIGNED",
            "resource_type" to "DELIVERY_ASSIGNMENT",
            "notification_id" to "n-2",
            "order_id" to "1001",
            "assignment_id" to "7"
        )

        assertEquals(RoutePaths.COURIER_DELIVERY_DETAIL.replace("{assignmentId}", "7"), route)
    }

    @Test
    fun `qris proof uploaded routes owner only`() {
        val route = routeFor(
            UserRole.OWNER,
            "type" to "PAYMENT_QRIS_PROOF_UPLOADED",
            "resource_type" to "PAYMENT",
            "notification_id" to "n-3",
            "order_id" to "1001"
        )
        assertEquals(RoutePaths.OWNER_ORDER_DETAIL.replace("{orderId}", "1001"), route)
    }

    @Test
    fun `qris proof uploaded never opens a customer screen`() {
        val route = routeFor(
            UserRole.CUSTOMER,
            "type" to "PAYMENT_QRIS_PROOF_UPLOADED",
            "resource_type" to "PAYMENT",
            "notification_id" to "n-3",
            "order_id" to "1001"
        )

        assertNull(route)
    }

    @Test
    fun `a payload without an order id never produces a route`() {
        // Payload yang tidak lengkap tidak boleh membuat Android menebak
        // tujuan. Router mengembalikan null.
        val route = routeFor(
            UserRole.CUSTOMER,
            "type" to "ORDER_CREATED",
            "resource_type" to "ORDER",
            "notification_id" to "n-4"
        )

        assertNull(route)
    }

    @Test
    fun `an unknown event type is not parsed into a payload`() {
        val result = repository.parsePayload(
            extras(
                "type" to "SOMETHING_NEW",
                "resource_type" to "ORDER",
                "notification_id" to "n-5",
                "order_id" to "1001"
            )
        )

        assertTrue(result is Result.Failure)
    }
}
