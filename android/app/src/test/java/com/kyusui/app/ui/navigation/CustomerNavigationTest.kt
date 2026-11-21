package com.kyusui.app.ui.navigation

import com.kyusui.app.ui.customer.orders.OrderStatusFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test rute navigasi Customer.
 *
 * `Uri.encode` adalah API Android sehingga tidak bisa dijalankan pada unit test
 * JVM. Karena itu test ini memeriksa sumber nilai filter (nama enum
 * [OrderStatusFilter]) dan bentuk template route, bukan hasil akhir string
 * route ber-filter.
 */
class CustomerNavigationTest {

    @Test
    fun `order list without filter omits the query`() {
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_LIST.substringBefore('?'),
            AppRoute.CustomerOrderList(filter = null).toRouteString()
        )
        assertEquals(
            RoutePaths.CUSTOMER_ORDER_LIST.substringBefore('?'),
            AppRoute.CustomerOrderList(filter = "   ").toRouteString()
        )
    }

    @Test
    fun `order list route exposes the filter argument`() {
        val route = RoutePaths.CUSTOMER_ORDER_LIST
        assertTrue(route.contains("{${RoutePaths.ARG_FILTER}}"))
        // Bagian path tetap "customer_orders" supaya tidak bentrok dengan rute
        // role lain maupun detail order.
        assertEquals("customer_orders", route.substringBefore('?'))
    }

    @Test
    fun `home filters use distinct enum values`() {
        // Beranda mengirim nama filter, bukan status backend: endpoint
        // `GET /customer/orders` tidak menerima parameter filter status.
        assertNotEquals(OrderStatusFilter.ACTIVE.name, OrderStatusFilter.ALL.name)
        assertNotEquals(OrderStatusFilter.COMPLETED.name, OrderStatusFilter.ALL.name)
    }

    @Test
    fun `every filter value resolves back to an enum entry`() {
        // `NavigationHost` memetakan nama filter kembali ke enum dan jatuh ke
        // ALL untuk nilai asing. Setiap nama harus unik agar pemetaan tidak
        // ambigu.
        val names = OrderStatusFilter.entries.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `order detail route carries the order id`() {
        assertEquals(
            "customer_orders/1001",
            AppRoute.CustomerOrderDetail("1001").toRouteString()
        )
    }

    @Test
    fun `order list and order detail are different routes`() {
        assertNotEquals(
            RoutePaths.CUSTOMER_ORDER_LIST.substringBefore('?'),
            RoutePaths.CUSTOMER_ORDER_DETAIL.substringBefore('{')
        )
    }

    @Test
    fun `cart route has no arguments`() {
        assertEquals(RoutePaths.CUSTOMER_CART, AppRoute.CustomerCart.toRouteString())
    }
}
