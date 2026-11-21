package com.kyusui.app.domain.repository

import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.NotificationEvent
import com.kyusui.app.domain.model.NotificationPayload
import com.kyusui.app.domain.model.PaginatedResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository untuk notification.
 *
 * Notification bukan source of truth. Repository ini hanya:
 * - mendaftarkan FCM token ke backend;
 * - mengambil daftar notifikasi dari backend;
 * - menandai notifikasi sebagai dibaca.
 *
 * Business state (order, payment, delivery) tetap diambil dari
 * endpoint masing-masing.
 */
interface NotificationRepository {

    /**
     * Daftarkan FCM token ke backend.
     *
     * Token hanya didaftarkan setelah user authenticated. Token
     * bukan credential — hanya transport identifier.
     */
    suspend fun registerDeviceToken(token: String): Result<Unit>

    /**
     * Daftar notifikasi milik user yang sedang login.
     */
    fun notifications(
        page: Int,
        perPage: Int,
        isRead: Boolean? = null
    ): Flow<Result<PaginatedResult<NotificationSummary>>>

    /**
     * Tandai satu notifikasi sebagai dibaca.
     */
    suspend fun markAsRead(notificationId: String): Result<Unit>

    /**
     * Tandai semua notifikasi sebagai dibaca.
     */
    suspend fun markAllAsRead(): Result<Unit>

    /**
     * Parse payload FCM menjadi [NotificationPayload].
     *
     * Dipisah dari repository supaya bisa diuji tanpa network.
     */
    fun parsePayload(data: Map<String, String>): Result<NotificationPayload>
}

/**
 * Ringkasan notifikasi untuk ditampilkan di list.
 */
data class NotificationSummary(
    val id: String,
    val type: NotificationEvent,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: String?,
    val payload: NotificationPayload
)