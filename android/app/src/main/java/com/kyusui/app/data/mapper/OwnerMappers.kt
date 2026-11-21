package com.kyusui.app.data.mapper

import com.kyusui.app.data.api.OwnerPaymentProofHistoryDto
import com.kyusui.app.domain.model.OwnerPaymentProofHistory

import com.kyusui.app.data.api.OrderAssignmentDto
import com.kyusui.app.data.api.OwnerActiveQrisDto
import com.kyusui.app.data.api.OwnerDashboardDto
import com.kyusui.app.data.api.DashboardOrderDto
import com.kyusui.app.data.api.DashboardPaymentDto
import com.kyusui.app.data.api.DashboardAssignmentDto
import com.kyusui.app.data.api.DashboardCourierDto
import com.kyusui.app.data.api.OwnerCourierDto
import com.kyusui.app.data.api.OwnerCustomerDto
import com.kyusui.app.data.api.OwnerDeliveryLocationDto
import com.kyusui.app.data.api.OwnerOrderDto
import com.kyusui.app.data.api.OwnerOrderPaymentDto
import com.kyusui.app.data.api.OwnerPaymentProofDto
import com.kyusui.app.data.api.OwnerPendingPaymentDto
import com.kyusui.app.data.api.OwnerProductDto
import com.kyusui.app.data.api.PaymentVerificationEnvelopeDto
import com.kyusui.app.domain.model.AssignmentStatus
import com.kyusui.app.domain.model.CourierSummary
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.domain.model.OrderAssignment
import com.kyusui.app.domain.model.OrderStatus
import com.kyusui.app.domain.model.OwnerCustomer
import com.kyusui.app.domain.model.OwnerOrder
import com.kyusui.app.domain.model.OwnerDashboardData
import com.kyusui.app.domain.model.OwnerDashboardOrder
import com.kyusui.app.domain.model.OwnerDashboardPayment
import com.kyusui.app.domain.model.OwnerDashboardAssignment
import com.kyusui.app.domain.model.OwnerRevenuePoint
import com.kyusui.app.domain.model.OwnerOrderPayment
import com.kyusui.app.domain.model.OwnerPaymentProof
import com.kyusui.app.domain.model.OwnerPendingPayment
import com.kyusui.app.domain.model.OwnerQrisConfig
import com.kyusui.app.domain.model.OwnerVerificationAction
import com.kyusui.app.domain.model.OwnerVerificationResult
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.PaymentStatus
import com.kyusui.app.domain.model.Product

/**
 * Mapping DTO -> domain untuk Owner API.
 *
 * Sama seperti mapping customer: nominal disalin apa adanya dari backend dan
 * status yang tidak dikenal dipetakan ke null supaya UI menampilkan "Status
 * tidak dikenal" daripada mengarang nilai bisnis.
 *
 * Tidak ada mapper yang menulis `PaymentStatus.PAID` atau
 * `OrderStatus.DIPROSES` secara hard-coded. Nilai status selalu datang dari
 * `fromApiValue(...)`, yaitu hanya dari response backend.
 */

fun OwnerCustomerDto.toDomain(): OwnerCustomer = OwnerCustomer(
    id = id.toString(),
    name = name
)

fun OwnerOrderPaymentDto.toDomain(): OwnerOrderPayment = OwnerOrderPayment(
    id = id?.toString(),
    paymentMethod = PaymentMethod.fromApiValue(payment_method),
    paymentStatus = PaymentStatus.fromApiValue(payment_status),
    amount = amount
)

fun OwnerDeliveryLocationDto.toDomainPoint(): GeoPoint? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return GeoPoint(latitude = lat, longitude = lng)
}

/**
 * Mengembalikan null bila assignment tidak punya `courier_id`.
 *
 * Android tidak menebak `courier_id` dan tidak menampilkan assignment setengah
 * jadi, karena Owner tidak boleh melihat "sudah ditugaskan" tanpa tahu ke siapa.
 */
fun OrderAssignmentDto.toDomainOrNull(): OrderAssignment? {
    val courier = courier_id ?: return null
    return OrderAssignment(
        id = id.toString(),
        courierId = courier.toString(),
        status = AssignmentStatus.fromApiValue(status),
        assignedAt = assigned_at
    )
}

fun OwnerOrderDto.toDomain(): OwnerOrder = OwnerOrder(
    id = id.toString(),
    orderNumber = order_number.orEmpty(),
    customerId = customer_id?.toString(),
    customer = customer?.toDomain(),
    items = items.map { it.toDomain() },
    subtotalAmount = subtotal_amount,
    deliveryFee = delivery_fee,
    totalAmount = total_amount,
    orderStatus = OrderStatus.fromApiValue(order_status),
    payment = payment?.toDomain(),
    deliveryAddress = delivery_location?.address?.takeIf { it.isNotBlank() },
    deliveryLocation = delivery_location?.toDomainPoint(),
    assignment = assignment?.toDomainOrNull()
)

fun OwnerPendingPaymentDto.toDomain(): OwnerPendingPayment = OwnerPendingPayment(
    paymentId = id.toString(),
    orderId = order_id.toString(),
    orderNumber = order_number.orEmpty(),
    customer = customer?.toDomain(),
    paymentMethod = PaymentMethod.fromApiValue(payment_method),
    paymentStatus = PaymentStatus.fromApiValue(payment_status),
    amount = amount,
    hasProof = proof?.available == true,
    createdAt = created_at,
    updatedAt = updated_at
)

fun OwnerPaymentProofDto.toDomain(): OwnerPaymentProof = OwnerPaymentProof(
    orderId = order_id.toString(),
    paymentId = payment_id?.toString(),
    paymentMethod = PaymentMethod.fromApiValue(payment_method),
    paymentStatus = PaymentStatus.fromApiValue(payment_status),
    hasProof = proof?.available == true,
    proofUrl = proof?.url?.takeIf { it.isNotBlank() }
)

fun OwnerActiveQrisDto.toDomain(): OwnerQrisConfig = OwnerQrisConfig(
    qrisImage = qris_image?.takeIf { it.isNotBlank() },
    updatedAt = updated_at,
    updatedBy = updated_by?.toString()
)

/**
 * Response 12.3 dibalut di dalam `payment`.
 *
 * `action` dicatat supaya ViewModel bisa membedakan hasil approve dari hasil
 * reject saat menampilkan umpan balik ke Owner.
 */
fun PaymentVerificationEnvelopeDto.toVerificationResult(
    action: OwnerVerificationAction
): OwnerVerificationResult = OwnerVerificationResult(
    payment = payment?.toDomain()
        ?: throw IllegalStateException(VERIFICATION_MISSING_PAYMENT_MESSAGE),
    action = action
)

private const val VERIFICATION_MISSING_PAYMENT_MESSAGE =
    "Respons backend tidak memuat payment."

/**
 * Mengubah model `Courier` mentah menjadi ringkasan yang dipakai layar
 * penugasan. Nama diambil dari relasi `user`; tidak ada nama kurir yang
 * dikarang bila backend hanya mengirim id.
 */
fun OwnerCourierDto.toDomain(): CourierSummary = CourierSummary(
    id = id.toString(),
    name = user?.name.orEmpty(),
    phone = phone?.takeIf { it.isNotBlank() },
    vehicle = vehicle?.takeIf { it.isNotBlank() }
)

/**
 * Mengubah `ProductResource` Owner menjadi [Product].
 *
 * `is_available` dipakai apa adanya; Android tidak menentukan ketersediaan
 * produk sendiri.
 */
fun OwnerProductDto.toDomain(): Product = Product(
    id = id.toString(),
    name = name,
    description = description?.takeIf { it.isNotBlank() },
    price = price,
    isAvailable = is_available
)

/** Mapping summary/dashboard response tanpa menghitung ulang transaksi di Android. */
fun OwnerDashboardDto.toDomain(): OwnerDashboardData = OwnerDashboardData(
    totalOrders = summary.total_orders,
    waitingProcess = summary.waiting_process,
    completedOrders = summary.completed_orders,
    waitingQris = summary.waiting_qris_verification,
    activeDeliveries = summary.active_deliveries,
    availableProducts = summary.available_products,
    todayRevenue = summary.today_revenue.toBigDecimal().toPlainString(),
    monthRevenue = summary.month_revenue.toBigDecimal().toPlainString(),
    monthExpense = summary.month_expense.toBigDecimal().toPlainString(),
    monthNet = summary.month_net.toBigDecimal().toPlainString(),
    averageOrderValue = summary.avg_order_value.toBigDecimal().toPlainString(),
    completionRate = summary.completion_rate,
    revenueChart = revenue_chart.map {
        OwnerRevenuePoint(month = it.month, label = it.label, income = it.income, expense = it.expense)
    },
    recentOrders = recent_orders.map { it.toDashboardDomain() },
    waitingOrders = waiting_orders.map { it.toDashboardDomain() },
    pendingQrisPayments = pending_qris_payments.map { it.toDashboardDomain() },
    activeDeliveriesList = active_deliveries_list.map { it.toDashboardDomain() },
    activeCouriers = active_couriers.map { it.toDashboardDomain() }
)

private fun DashboardOrderDto.toDashboardDomain() = OwnerDashboardOrder(
    id = id.toString(), status = status, totalAmount = total_amount,
    deliveryAddress = delivery_address, paymentMethod = payment?.payment_method,
    paymentStatus = payment?.payment_status, createdAt = created_at
)

private fun DashboardPaymentDto.toDashboardDomain() = OwnerDashboardPayment(
    id = id.toString(), paymentMethod = payment_method, paymentStatus = payment_status,
    amount = amount, orderId = order?.id?.toString(), customerName = order?.customer_name,
    createdAt = created_at
)

private fun DashboardAssignmentDto.toDashboardDomain() = OwnerDashboardAssignment(
    id = id.toString(), status = status, courierName = courier_name,
    orderId = order?.id?.toString(), customerName = order?.customer_name,
    deliveryAddress = order?.delivery_address, assignedAt = assigned_at
)

private fun DashboardCourierDto.toDashboardDomain() = CourierSummary(
    id = id.toString(), name = name.orEmpty(), phone = phone, vehicle = vehicle
)


/** Mapping riwayat bukti tanpa mengarang nama aktor, alasan, atau waktu. */
fun OwnerPaymentProofHistoryDto.toDomain(): OwnerPaymentProofHistory =
    OwnerPaymentProofHistory(
        id = id.toString(),
        decision = decision,
        note = note,
        actor = actor,
        decidedAt = decided_at,
        proofAvailable = proof_available
    )
