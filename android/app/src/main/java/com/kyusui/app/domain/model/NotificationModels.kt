package com.kyusui.app.domain.model

/**
 * Tipe event notification sesuai specification 10.
 *
 * Setiap event memiliki payload contract yang berbeda. Payload hanya membawa
 * context minimum untuk routing — bukan source of truth untuk business state.
 */
enum class NotificationEvent {
    ORDER_CREATED,
    ORDER_PROCESSED,
    PAYMENT_QRIS_PROOF_UPLOADED,
    PAYMENT_QRIS_APPROVED,
    PAYMENT_QRIS_REJECTED,
    PAYMENT_CASH_CONFIRMED,
    COURIER_ASSIGNED,
    DELIVERY_STARTED,
    TRACKING_AVAILABLE,
    ORDER_COMPLETED;

    companion object {
        /**
         * Parse dari string payload FCM.
         *
         * Nilai yang tidak dikenal menjadi null, bukan ditebak. Backend
         * mungkin menambah event baru; Android tidak boleh crash karena
         * event yang tidak dikenal.
         */
        fun fromApiValue(value: String?): NotificationEvent? =
            entries.firstOrNull { it.name == value }
    }
}

/**
 * Resource type yang terkait notification.
 */
enum class NotificationResourceType {
    ORDER,
    PAYMENT,
    DELIVERY_ASSIGNMENT;

    companion object {
        fun fromApiValue(value: String?): NotificationResourceType? =
            entries.firstOrNull { it.name == value }
    }
}

/**
 * Payload notification dari FCM.
 *
 * Sesuai specification 10 section 19:
 * schema_version, notification_id, type, resource_type, resource_id,
 * order_id, target_route, created_at.
 *
 * Event-specific fields (order_status, payment_method, payment_status,
 * assignment_id) dibaca sebagai nullable karena tidak semua event
 * membawanya.
 */
data class NotificationPayload(
    val schemaVersion: Int = 1,
    val notificationId: String,
    val type: NotificationEvent,
    val resourceType: NotificationResourceType,
    val resourceId: String? = null,
    val orderId: String? = null,
    val assignmentId: String? = null,
    val orderStatus: String? = null,
    val paymentMethod: String? = null,
    val paymentStatus: String? = null,
    val targetRoute: String? = null,
    val createdAt: String? = null
)