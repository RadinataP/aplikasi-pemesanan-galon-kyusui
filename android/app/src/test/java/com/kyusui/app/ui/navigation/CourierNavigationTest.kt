package com.kyusui.app.ui.navigation

import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.ui.courier.assignments.CourierAssignmentFilter
import com.kyusui.app.ui.courier.payment.CashConfirmationViewModel
import com.kyusui.app.ui.courier.delivery.DeliveryDetailViewModel
import com.kyusui.app.ui.courier.location.CustomerLocationViewModel
import com.kyusui.app.ui.courier.tracking.CourierTrackingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test rute navigasi Courier.
 *
 * Yang dijaga test ini:
 *
 * - setiap rute kurir memakai `assignmentId`, bukan `orderId`. Endpoint kurir di
 *   `backend/routes/api.php` bekerja pada `courier_assignments`, jadi mengirim id
 *   order akan membuat backend membalas `404`;
 * - keempat ViewModel kurir membaca argumen rute dengan nama yang sama, sehingga
 *   id yang dibawa rute benar-benar yang dipakai ViewModel;
 * - rute kurir terpisah dari rute Customer dan Owner, supaya navigasi tidak
 *   salah peran;
 * - filter daftar memakai status canonical `AssignmentStatus`, bukan string.
 *
 * Rute dibangun tanpa `Uri.encode` untuk nilai path, jadi `toRouteString()` bisa
 * dijalankan pada unit test JVM dan hasilnya bisa diperiksa langsung.
 */
class CourierNavigationTest {

    @Test
    fun `courier home route matches the declared path`() {
        assertEquals(RoutePaths.COURIER_HOME, AppRoute.CourierHome.toRouteString())
    }

    @Test
    fun `assignment list route has no argument`() {
        // `GET courier/assignments` tidak menerima query apa pun, jadi rute
        // daftar tidak boleh membawa filter di path-nya.
        assertEquals(
            RoutePaths.COURIER_ASSIGNMENT_LIST,
            AppRoute.CourierAssignmentList.toRouteString()
        )
        assertFalse(RoutePaths.COURIER_ASSIGNMENT_LIST.contains('?'))
        assertFalse(RoutePaths.COURIER_ASSIGNMENT_LIST.contains('{'))
    }

    @Test
    fun `delivery detail route carries the assignment id`() {
        assertEquals(
            "courier_assignments/7/delivery",
            AppRoute.CourierDeliveryDetail("7").toRouteString()
        )
    }

    @Test
    fun `customer location route carries the assignment id`() {
        assertEquals(
            "courier_assignments/7/location",
            AppRoute.CourierCustomerLocation("7").toRouteString()
        )
    }

    @Test
    fun `cash confirmation route carries the assignment id`() {
        assertEquals(
            "courier_assignments/7/cash_confirmation",
            AppRoute.CourierCashConfirmation("7").toRouteString()
        )
    }

    @Test
    fun `tracking route carries the assignment id`() {
        assertEquals(
            "courier_assignments/7/tracking",
            AppRoute.CourierTracking("7").toRouteString()
        )
    }

    @Test
    fun `courier action routes never use an order id placeholder`() {
        // Argumen `orderId` tidak boleh muncul di rute yang menjalankan aksi
        // kurir. Backend tidak punya endpoint `courier/orders/{order}`.
        val actionRoutes = listOf(
            RoutePaths.COURIER_DELIVERY_DETAIL,
            RoutePaths.COURIER_CUSTOMER_LOCATION,
            RoutePaths.COURIER_CASH_CONFIRMATION,
            RoutePaths.COURIER_TRACKING
        )

        actionRoutes.forEach { route ->
            assertTrue(
                "Rute $route harus memakai placeholder assignmentId",
                route.contains("{${RoutePaths.ARG_ASSIGNMENT_ID}}")
            )
            assertFalse(
                "Rute $route tidak boleh memakai placeholder orderId",
                route.contains("{${RoutePaths.ARG_ORDER_ID}}")
            )
        }
    }

    @Test
    fun `assignment id and order id are different route arguments`() {
        // Kalau keduanya bernilai sama, salah baca argumen di layar tidak akan
        // terlihat dan backend akan membalas 404.
        assertNotEquals(RoutePaths.ARG_ASSIGNMENT_ID, RoutePaths.ARG_ORDER_ID)
        assertEquals("assignmentId", RoutePaths.ARG_ASSIGNMENT_ID)
    }

    @Test
    fun `every courier ViewModel reads the same route argument name`() {
        // Ketiga ViewModel ini memakai `SavedStateHandle`. Kalau nama argumennya
        // berbeda dari nama placeholder di rute, assignment yang diopened oleh
        // kurir tidak akan terbaca dan layar akan selalu gagal memuat.
        val argumentName = RoutePaths.ARG_ASSIGNMENT_ID

        assertEquals(argumentName, DeliveryDetailViewModel.ARG_ASSIGNMENT_ID)
        assertEquals(argumentName, CustomerLocationViewModel.ARG_ASSIGNMENT_ID)
        assertEquals(argumentName, CashConfirmationViewModel.ARG_ASSIGNMENT_ID)
        assertEquals(argumentName, CourierTrackingViewModel.ARG_ASSIGNMENT_ID)
    }

    @Test
    fun `action routes are distinct so the back stack cannot be confused`() {
        val rendered = listOf(
            AppRoute.CourierDeliveryDetail("7").toRouteString(),
            AppRoute.CourierCustomerLocation("7").toRouteString(),
            AppRoute.CourierCashConfirmation("7").toRouteString(),
            AppRoute.CourierTracking("7").toRouteString()
        )

        // Empat layar, empat rute. Rute yang sama untuk detail dan konfirmasi
        // tunai akan membuat navigasi kembali membuka layar yang salah.
        assertEquals(rendered.size, rendered.toSet().size)
    }

    @Test
    fun `child routes share the prefix of the detail route`() {
        // Rute anak dibangun dari assignment yang sama dengan detail, jadi
        // menambah rute baru tidak boleh mengubah segmen assignment.
        val detail = AppRoute.CourierDeliveryDetail("42").toRouteString()
        val location = AppRoute.CourierCustomerLocation("42").toRouteString()
        val cash = AppRoute.CourierCashConfirmation("42").toRouteString()
        val tracking = AppRoute.CourierTracking("42").toRouteString()

        listOf(detail, location, cash, tracking).forEach { route ->
            assertTrue(
                "Rute $route harus diawali prefiks assignment",
                route.startsWith("courier_assignments/42/")
            )
        }
    }

    @Test
    fun `different assignments produce different routes`() {
        assertNotEquals(
            AppRoute.CourierDeliveryDetail("7").toRouteString(),
            AppRoute.CourierDeliveryDetail("8").toRouteString()
        )
    }

    @Test
    fun `courier routes do not overlap with owner or customer routes`() {
        val courierRoutes = listOf(
            RoutePaths.COURIER_HOME,
            RoutePaths.COURIER_ASSIGNMENT_LIST,
            RoutePaths.COURIER_DELIVERY_DETAIL,
            RoutePaths.COURIER_CUSTOMER_LOCATION,
            RoutePaths.COURIER_CASH_CONFIRMATION,
            RoutePaths.COURIER_TRACKING,
            RoutePaths.COURIER_PROFILE
        )
        val otherRoleRoutes = listOf(
            RoutePaths.CUSTOMER_HOME,
            RoutePaths.CUSTOMER_ORDER_LIST,
            RoutePaths.CUSTOMER_ORDER_DETAIL,
            RoutePaths.CUSTOMER_PROFILE,
            RoutePaths.OWNER_HOME,
            RoutePaths.OWNER_ORDER_LIST,
            RoutePaths.OWNER_ORDER_DETAIL,
            RoutePaths.OWNER_PROFILE
        )

        // Path yang sama antar role akan membuat navigasi salah peran, jadi
        // keduanya harus terpisah.
        assertTrue(courierRoutes.none { otherRoleRoutes.contains(it) })
    }

    @Test
    fun `courier profile route exists even though the backend has no courier profile endpoint`() {
        // `GET /customer/profile` dan `PUT /customer/profile` hanya untuk role
        // CUSTOMER. Rute ini tetap ada supaya tidak ada navigasi ke mana pun yang
        // hilang, dan layarnya tetap Placeholder yang menjelaskan alasannya.
        assertEquals(RoutePaths.COURIER_PROFILE, AppRoute.CourierProfile.toRouteString())
        assertNotEquals(RoutePaths.COURIER_PROFILE, RoutePaths.CUSTOMER_PROFILE)
    }

    @Test
    fun `assignment filters use canonical assignment statuses`() {
        val values = CourierAssignmentFilter.entries.mapNotNull { it.status?.apiValue }

        // Dua filter dengan nilai yang sama membuat kurir melihat baris yang
        // sama untuk dua pilihan berbeda.
        assertEquals(values.size, values.distinct().size)
        values.forEach { value ->
            assertTrue(
                "Nilai filter harus berasal dari enum canonical: $value",
                AssignmentStatus.entries.any { it.apiValue == value }
            )
        }
        // Tepat satu filter yang tidak menyaring sama sekali.
        assertEquals(1, CourierAssignmentFilter.entries.count { it.status == null })
    }
}
