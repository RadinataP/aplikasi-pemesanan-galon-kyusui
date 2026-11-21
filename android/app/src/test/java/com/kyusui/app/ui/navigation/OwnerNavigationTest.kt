package com.kyusui.app.ui.navigation

import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.ui.owner.orders.OwnerOrderFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test rute navigasi Owner.
 *
 * Yang dijaga test ini:
 *
 * - setiap rute Owner dibangun dari `RoutePaths`, bukan dari path yang diketik
 *   ulang di layar;
 * - `orderId` dan `status` diteruskan apa adanya dari argumen rute, sehingga
 *   detail order Owner selalu meminta order yang sedang dibuka;
 * - rute antrean verifikasi tidak membawa `orderId`, dan rute verifikasi per
 *   order tetap membawanya. Kedua bentuk ini sengaja dipisah;
 * - `ALL` tidak mengirim query `status`, karena "tanpa filter" bukan "filter
 *   kosong".
 */
class OwnerNavigationTest {

    @Test
    fun `owner home route matches the declared path`() {
        assertEquals(RoutePaths.OWNER_HOME, AppRoute.OwnerHome.toRouteString())
    }

    @Test
    fun `order list without status omits the query`() {
        // Section 10.1 menerima `status` opsional. Mengirim string kosong akan
        // dibaca backend sebagai filter, bukan sebagai "semua".
        assertEquals(
            RoutePaths.OWNER_ORDER_LIST.substringBefore('?'),
            AppRoute.OwnerOrderList(status = null).toRouteString()
        )
        assertEquals(
            RoutePaths.OWNER_ORDER_LIST.substringBefore('?'),
            AppRoute.OwnerOrderList(status = "   ").toRouteString()
        )
    }

    @Test
    fun `order list with status is sent through the status argument`() {
        // `Uri.encode` adalah API Android sehingga tidak bisa dijalankan pada
        // unit test JVM. Yang diperiksa di sini adalah sumber nilainya: filter
        // Owner hanya boleh memakai nilai `OrderStatus` canonical, dan template
        // route menyediakan argumen `status`.
        val route = RoutePaths.OWNER_ORDER_LIST
        assertTrue(route.contains("{${RoutePaths.ARG_STATUS}}"))
        assertEquals(
            OrderStatus.DIPROSES.apiValue,
            OwnerOrderFilter.DIPROSES.status?.apiValue
        )
        assertEquals("DIPROSES", OrderStatus.DIPROSES.apiValue)
    }

    @Test
    fun `order filters use distinct canonical statuses`() {
        val values = OwnerOrderFilter.entries.mapNotNull { it.status?.apiValue }

        // Dua filter dengan nilai yang sama membuat Owner melihatorder yang
        // sama untuk dua pilihan berbeda.
        assertEquals(values.size, values.distinct().size)
        values.forEach { value ->
            assertTrue(
                "Nilai filter harus berasal dari enum canonical: $value",
                OrderStatus.entries.any { it.apiValue == value }
            )
        }
    }

    @Test
    fun `order detail route carries the order id`() {
        val route = AppRoute.OwnerOrderDetail("1001").toRouteString()

        assertEquals("owner_orders/1001", route)
    }

    @Test
    fun `qris verification route carries the order id`() {
        val route = AppRoute.OwnerQrisVerification("1001").toRouteString()

        assertEquals("owner_orders/1001/qris_verification", route)
    }

    @Test
    fun `qris queue route has no order id`() {
        // Antrean `GET /owner/payments/pending` tidak berparameter order. Jika
        // route ini memakai placeholder `orderId`, layar harus menebak nilai
        // yang tidak ada.
        assertEquals("owner/qris_verification", RoutePaths.OWNER_QRIS_QUEUE)
    }

    @Test
    fun `qris queue and per order verification are different routes`() {
        assertNotEquals(RoutePaths.OWNER_QRIS_QUEUE, RoutePaths.OWNER_QRIS_VERIFICATION)
    }

    @Test
    fun `qris settings route has no arguments`() {
        assertEquals(RoutePaths.OWNER_QRIS_SETTINGS, AppRoute.OwnerQrisSettings.toRouteString())
    }

    @Test
    fun `owner routes do not overlap with customer routes`() {
        val ownerRoutes = listOf(
            RoutePaths.OWNER_HOME,
            RoutePaths.OWNER_ORDER_LIST.substringBefore('?'),
            RoutePaths.OWNER_ORDER_DETAIL,
            RoutePaths.OWNER_QRIS_QUEUE,
            RoutePaths.OWNER_QRIS_VERIFICATION,
            RoutePaths.OWNER_QRIS_SETTINGS
        )
        val customerRoutes = listOf(
            RoutePaths.CUSTOMER_HOME,
            RoutePaths.CUSTOMER_ORDER_LIST,
            RoutePaths.CUSTOMER_ORDER_DETAIL
        )

        // Path yang sama antara role Owner dan customer akan membuat navigasi
        // salah peran, jadi keduanya harus terpisah.
        assertTrue(ownerRoutes.none { customerRoutes.contains(it) })
    }

    @Test
    fun `all order filter maps to no backend status`() {
        // `ALL` berarti tidak mengirim query `status` sama sekali.
        assertNull(OwnerOrderFilter.ALL.status)
        assertEquals(
            RoutePaths.OWNER_ORDER_LIST.substringBefore('?'),
            AppRoute.OwnerOrderList(status = OwnerOrderFilter.ALL.status?.apiValue).toRouteString()
        )
    }

    @Test
    fun `every order filter maps to a canonical order status`() {
        OwnerOrderFilter.entries.forEach { filter ->
            if (filter == OwnerOrderFilter.ALL) {
                assertNull(filter.status)
            } else {
                val status = filter.status
                assertTrue(
                    "Filter ${filter.name} harus memakai status canonical",
                    status != null && OrderStatus.entries.contains(status)
                )
            }
        }
    }

    @Test
    fun `no order filter uses a payment status`() {
        // `GET /owner/orders` hanya menerima `order_status`. Mengirim status
        // pembayaran ke sini akan ditolak atau diabaikan backend.
        OwnerOrderFilter.entries.forEach { filter ->
            val value = filter.status?.apiValue.orEmpty()
            if (value.isNotEmpty()) {
                assertTrue(
                    "Filter ${filter.name} bukan order status",
                    value != "PENDING" && value != "WAITING_VERIFICATION" && value != "PAID"
                )
            }
        }
    }
}