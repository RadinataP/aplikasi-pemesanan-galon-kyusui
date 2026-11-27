package com.kyusui.app.data.mapper

import com.kyusui.app.data.api.CourierAssignmentDto
import com.kyusui.app.data.api.CourierAssignmentItemDto
import com.kyusui.app.data.api.CourierAssignmentOrderDto
import com.kyusui.app.data.api.CourierAssignmentPaymentDto
import com.kyusui.app.data.api.CourierCashReceiptDto
import com.kyusui.app.data.api.CourierDashboardDto
import com.kyusui.app.data.api.CourierDashboardItemDto
import com.kyusui.app.data.api.CourierDashboardOrderDto
import com.kyusui.app.data.api.CourierDutySessionDto
import com.kyusui.app.data.api.CourierDutyStateDto
import com.kyusui.app.data.api.CourierLocationDto
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierCashReceipt
import com.kyusui.app.domain.model.CourierDashboard
import com.kyusui.app.domain.model.CourierDashboardOrder
import com.kyusui.app.domain.model.CourierDashboardSummary
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeliveryItem
import com.kyusui.app.domain.model.CourierDeliveryOrder
import com.kyusui.app.domain.model.CourierDeliveryPayment
import com.kyusui.app.domain.model.DeliveryLocation
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierDutySession
import com.kyusui.app.domain.model.CourierDutyState
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus

/**
 * Mapping DTO -> domain untuk Courier API.
 *
 * Sama seperti mapping Customer dan Owner: nominal disalin apa adanya dari
 * backend, dan status yang tidak dikenal dipetakan ke null supaya UI
 * menampilkan "Status tidak dikenal" alih-alih mengarang nilai bisnis.
 *
 * Tidak ada mapper di file ini yang menulis `PaymentStatus.PAID` atau
 * `AssignmentStatus.ACTIVE` secara hard-coded. Semua status berasal dari
 * `fromApiValue(...)`, yaitu hanya dari response backend.
 */

fun CourierAssignmentPaymentDto.toDomain(): CourierDeliveryPayment = CourierDeliveryPayment(
    paymentMethod = PaymentMethod.fromApiValue(method),
    paymentStatus = PaymentStatus.fromApiValue(status)
)

fun CourierAssignmentItemDto.toDomain(): CourierDeliveryItem = CourierDeliveryItem(
    productName = product_name.orEmpty(),
    quantity = quantity,
    unitPrice = unit_price,
    lineTotal = line_total
)

/**
 * `order` di-mapping tanpa memaksa non-null.
 *
 * Backend selalu mengirim `order` untuk assignment milik kurir sendiri, tetapi
 * Android tidak mengarang order bila field itu hilang. Assignment tanpa order
 * tetap bisa ditampilkan, hanya tanpa data pengiriman.
 */
fun CourierAssignmentOrderDto?.toDomainOrNull(): CourierDeliveryOrder? {
    val source = this ?: return null
    return CourierDeliveryOrder(
        id = source.id.toString(),
        status = OrderStatus.fromApiValue(source.status),
        subtotalAmount = source.subtotal,
        totalAmount = source.total,
        deliveryAddress = source.delivery_address?.takeIf { it.isNotBlank() },
        deliveryLocation = source.delivery_location?.let { location ->
            val lat = location.latitude
            val lng = location.longitude
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                DeliveryLocation(
                    address = location.address?.takeIf { it.isNotBlank() } ?: source.delivery_address.orEmpty(),
                    point = GeoPoint(lat, lng)
                )
            } else null
        },
        // Nama customer kosong tidak ditampilkan sebagai "": nilainya null supaya
        // UI bisa membedakan "tidak dikirim backend" dari nama yang kosong.
        customerName = customer?.name?.takeIf { it.isNotBlank() },
        items = items.map { it.toDomain() },
        payment = payment?.toDomain()
    )
}

/**
 * Lokasi kurir terakhir.
 *
 * Mengembalikan null bila lat dan lng tidak lengkap, karena koordinat setengah
 * tidak berguna untuk ditampilkan maupun dibandingkan dengan titik tujuan.
 */
fun CourierLocationDto?.toDomainOrNull(): CourierDeviceLocation? {
    val source = this ?: return null
    val latitude = latitude ?: return null
    val longitude = longitude ?: return null
    return CourierDeviceLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy_meters,
        recordedAt = recorded_at
    )
}

fun CourierAssignmentDto.toDomain(): CourierDelivery = CourierDelivery(
    id = id.toString(),
    status = AssignmentStatus.fromApiValue(status),
    assignedAt = assigned_at,
    startedAt = started_at,
    completedAt = completed_at,
    order = order.toDomainOrNull(),
    latestLocation = latest_location.toDomainOrNull()
)

fun CourierCashReceiptDto.toDomain(): CourierCashReceipt = CourierCashReceipt(
    paymentId = id.toString(),
    // Status pembayaran pada hasil konfirmasi dibaca apa adanya dari backend.
    paymentMethod = PaymentMethod.fromApiValue(payment_method),
    paymentStatus = PaymentStatus.fromApiValue(payment_status),
    verifiedAt = verified_at
)

fun CourierDashboardItemDto.toDomain(): CourierDeliveryItem = CourierDeliveryItem(
    productName = name.orEmpty(),
    quantity = quantity,
    unitPrice = unit_price,
    lineTotal = line_total_amount
)

fun CourierDashboardOrderDto.toDomain(): CourierDashboardOrder = CourierDashboardOrder(
    orderId = id.toString(),
    status = OrderStatus.fromApiValue(status),
    subtotalAmount = subtotal_amount,
    totalAmount = total_amount,
    deliveryAddress = delivery_address?.takeIf { it.isNotBlank() },
    payment = payment?.let {
        CourierDeliveryPayment(
            paymentMethod = PaymentMethod.fromApiValue(it.payment_method),
            paymentStatus = PaymentStatus.fromApiValue(it.payment_status)
        )
    },
    items = items.map { it.toDomain() },
    // Dashboard hanya membawa referensi `{id, status}` untuk assignment, bukan
    // objek assignment penuh.
    assignmentId = assignment?.id?.toString(),
    assignmentStatus = AssignmentStatus.fromApiValue(assignment?.status),
    createdAt = created_at
)

/**
 * Dashboard kurir.
 *
 * `summary` nullable di DTO karena backend boleh tidak mengirimnya. Nilai
 * default di [CourierDashboardSummary] dipakai agar Android tidak mengarang
 * angka, dan memaporkannya ke nol bukan berarti "kurir tidak pernah mengantar".
 */
fun CourierDashboardDto.toDomain(): CourierDashboard = CourierDashboard(
    activeDelivery = active_assignment?.toDomain(),
    todayDeliveries = today_assignments.map { it.toDomain() },
    summary = summary?.let {
        CourierDashboardSummary(
            todayDeliveries = it.today_deliveries,
            monthDeliveries = it.month_deliveries,
            completedDeliveries = it.completed_deliveries,
            completionRate = it.completion_rate
        )
    } ?: CourierDashboardSummary(),
    unreadNotifications = unread_notifications,
    activeDeliveries = active_assignments.map { it.toDomain() }.ifEmpty { listOfNotNull(active_assignment?.toDomain()) }
)

/**
 * Sesi bertugas. `is_active` dibaca apa adanya dari backend, tanpa disimpulkan
 * ulang dari `ended_at`.
 */
fun CourierDutySessionDto.toDomain(): CourierDutySession = CourierDutySession(
    id = id.toString(),
    startedAt = started_at,
    endedAt = ended_at,
    isActive = is_active
)

fun CourierDutyStateDto.toDomain(): CourierDutyState = CourierDutyState(
    isActive = active,
    session = session?.toDomain(),
    latestLocation = latest_location.toDomainOrNull()
)