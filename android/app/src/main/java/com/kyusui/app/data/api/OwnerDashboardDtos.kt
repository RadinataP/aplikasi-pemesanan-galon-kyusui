package com.kyusui.app.data.api

import kotlinx.serialization.Serializable

/** Response agregasi dashboard Owner dari GET /dashboard/owner. */
@Serializable
data class OwnerDashboardDto(
    val summary: OwnerDashboardSummaryDto = OwnerDashboardSummaryDto(),
    val revenue_chart: List<OwnerRevenuePointDto> = emptyList(),
    val recent_orders: List<DashboardOrderDto> = emptyList(),
    val waiting_orders: List<DashboardOrderDto> = emptyList(),
    val pending_qris_payments: List<DashboardPaymentDto> = emptyList(),
    val active_deliveries_list: List<DashboardAssignmentDto> = emptyList(),
    val active_couriers: List<DashboardCourierDto> = emptyList()
)

/** Nilai numerik dihitung backend; Android hanya memformat untuk presentasi. */
@Serializable
data class OwnerDashboardSummaryDto(
    val total_orders: Int = 0,
    val waiting_process: Int = 0,
    val completed_orders: Int = 0,
    val waiting_qris_verification: Int = 0,
    val active_deliveries: Int = 0,
    val available_products: Int = 0,
    val today_revenue: Double = 0.0,
    val month_revenue: Double = 0.0,
    val month_expense: Double = 0.0,
    val month_net: Double = 0.0,
    val avg_order_value: Double = 0.0,
    val completion_rate: Double = 0.0
)

/** Satu titik grafik untuk satu bulan kalender. */
@Serializable
data class OwnerRevenuePointDto(
    val month: String,
    val label: String,
    val income: Double = 0.0,
    val expense: Double = 0.0
)


/** Baris order pada dashboard; field mengikuti DashboardOrderResource backend. */
@Serializable
data class DashboardOrderDto(
    val id: Long,
    val status: String? = null,
    val subtotal_amount: String? = null,
    val total_amount: String? = null,
    val delivery_address: String? = null,
    val payment: DashboardOrderPaymentDto? = null,
    val items: List<DashboardOrderItemDto> = emptyList(),
    val assignment: DashboardOrderAssignmentDto? = null,
    val created_at: String? = null
)

@Serializable
data class DashboardOrderPaymentDto(
    val payment_method: String? = null,
    val payment_status: String? = null
)

@Serializable
data class DashboardOrderItemDto(
    val product_id: Long? = null,
    val name: String? = null,
    val quantity: Int = 0,
    val unit_price: String? = null,
    val line_total: String? = null
)

@Serializable
data class DashboardOrderAssignmentDto(
    val id: Long? = null,
    val status: String? = null
)

/** Antrean pembayaran QRIS yang menunggu verifikasi. */
@Serializable
data class DashboardPaymentDto(
    val id: Long,
    val payment_method: String? = null,
    val payment_status: String? = null,
    val amount: String? = null,
    val order: DashboardPaymentOrderDto? = null,
    val created_at: String? = null
)

@Serializable
data class DashboardPaymentOrderDto(
    val id: Long? = null,
    val total_amount: String? = null,
    val customer_name: String? = null
)

/** Pengiriman aktif beserta ringkasan order dan kurir dari backend. */
@Serializable
data class DashboardAssignmentDto(
    val id: Long,
    val status: String? = null,
    val assigned_at: String? = null,
    val courier_name: String? = null,
    val order: DashboardAssignmentOrderDto? = null
)

@Serializable
data class DashboardAssignmentOrderDto(
    val id: Long? = null,
    val total_amount: String? = null,
    val customer_name: String? = null,
    val delivery_address: String? = null
)

@Serializable
data class DashboardCourierDto(
    val id: Long,
    val name: String? = null,
    val phone: String? = null,
    val vehicle: String? = null
)
