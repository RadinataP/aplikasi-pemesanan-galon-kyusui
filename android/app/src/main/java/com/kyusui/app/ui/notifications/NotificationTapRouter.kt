package com.kyusui.app.ui.notifications

import com.kyusui.app.domain.model.NotificationEvent
import com.kyusui.app.domain.model.NotificationPayload
import com.kyusui.app.domain.model.UserRole
import com.kyusui.app.ui.navigation.RoutePaths

/**
 * Menentukan rute navigasi dari notification payload.
 *
 * Payload hanya membawa routing context. Android menentukan
 * tujuan berdasarkan role user dan tipe event, bukan dari
 * payload field saja.
 *
 * Specification 10 section 23: notification tap selalu menghasilkan
 * navigation context.
 */
object NotificationTapRouter {

    /**
     * Resolve rute Android dari notification payload.
     *
     * @param role Role user yang sedang login
     * @param payload Notification payload dari FCM
     * @return Route path untuk navigasi, atau null bila tidak ada
     *   rute yang cocok
     */
    fun resolveRoute(role: UserRole, payload: NotificationPayload): String? {
        val orderId = payload.orderId ?: return null
        val assignmentId = payload.assignmentId

        return when (payload.type) {
            NotificationEvent.ORDER_CREATED -> when (role) {
                UserRole.CUSTOMER -> RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", orderId)
                UserRole.OWNER -> RoutePaths.OWNER_ORDER_DETAIL.replace("{orderId}", orderId)
                else -> null
            }

            NotificationEvent.ORDER_PROCESSED ->
                if (role == UserRole.CUSTOMER) {
                    RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", orderId)
                } else null

            NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED ->
                if (role == UserRole.OWNER) {
                    RoutePaths.OWNER_ORDER_DETAIL.replace("{orderId}", orderId)
                } else null

            NotificationEvent.PAYMENT_QRIS_APPROVED ->
                if (role == UserRole.CUSTOMER) {
                    RoutePaths.CUSTOMER_PAYMENT_SELECTION.replace("{orderId}", orderId)
                } else null

            NotificationEvent.PAYMENT_QRIS_REJECTED ->
                if (role == UserRole.CUSTOMER) {
                    RoutePaths.CUSTOMER_PAYMENT_SELECTION.replace("{orderId}", orderId)
                } else null

            NotificationEvent.PAYMENT_CASH_CONFIRMED ->
                if (role == UserRole.CUSTOMER) {
                    RoutePaths.CUSTOMER_PAYMENT_SELECTION.replace("{orderId}", orderId)
                } else null

            NotificationEvent.COURIER_ASSIGNED -> when (role) {
                UserRole.CUSTOMER -> RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", orderId)
                UserRole.COURIER -> assignmentId?.let {
                    RoutePaths.COURIER_DELIVERY_DETAIL.replace("{assignmentId}", it)
                }
                else -> null
            }

            NotificationEvent.DELIVERY_STARTED ->
                if (role == UserRole.CUSTOMER) {
                    RoutePaths.CUSTOMER_ORDER_TRACKING.replace("{orderId}", orderId)
                } else null

            NotificationEvent.TRACKING_AVAILABLE ->
                if (role == UserRole.CUSTOMER) {
                    RoutePaths.CUSTOMER_ORDER_TRACKING.replace("{orderId}", orderId)
                } else null

            NotificationEvent.ORDER_COMPLETED -> when (role) {
                UserRole.CUSTOMER -> RoutePaths.CUSTOMER_ORDER_DETAIL.replace("{orderId}", orderId)
                UserRole.OWNER -> RoutePaths.OWNER_ORDER_DETAIL.replace("{orderId}", orderId)
                UserRole.COURIER -> assignmentId?.let {
                    RoutePaths.COURIER_DELIVERY_DETAIL.replace("{assignmentId}", it)
                }
                else -> null
            }
        }
    }

    /**
     * Cek apakah payload valid untuk ditampilkan sebagai notification.
     *
     * Payload yang tidak memiliki field wajib tidak boleh ditampilkan.
     */
    fun isValidPayload(payload: NotificationPayload): Boolean {
        if (payload.notificationId.isBlank()) return false
        if (payload.orderId.isNullOrBlank()) return false
        return true
    }
}