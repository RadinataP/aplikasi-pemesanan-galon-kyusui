package com.kyusui.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Notification API sesuai specification 10 section 36.
 *
 * Endpoint:
 * - `POST /notifications/device-token` — daftarkan FCM token
 * - `GET  /notifications` — daftar notifikasi milik user
 * - `PATCH /notifications/{notification}/read` — tandai dibaca
 * - `POST /notifications/read-all` — tandai semua dibaca
 *
 * Semua endpoint butuh autentikasi. Token FCM bukan credential —
 * hanya transport identifier.
 */
interface NotificationApi {

    @POST("notifications/device-token")
    suspend fun registerDeviceToken(
        @Body request: RegisterDeviceTokenRequestDto
    ): Response<EnvelopeDto<DeviceTokenDto>>

    @GET("notifications")
    suspend fun notifications(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = DEFAULT_PER_PAGE,
        @Query("is_read") isRead: Boolean? = null
    ): NotificationPageDto

    @PATCH("notifications/{notification}/read")
    suspend fun markAsRead(
        @Path("notification") notificationId: String
    ): Response<EnvelopeDto<NotificationDto>>

    @POST("notifications/read-all")
    suspend fun markAllAsRead(): Response<EnvelopeDto<Unit>>

    companion object {
        const val DEFAULT_PER_PAGE = 20
    }
}

// --- Request ---

@Serializable
data class RegisterDeviceTokenRequestDto(
    val token: String,
    val platform: String = "android"
)

// --- Response ---

@Serializable
data class DeviceTokenDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val platform: String,
    val is_active: Boolean,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class NotificationDto(
    @Serializable(with = TolerantIdSerializer::class) val id: Long,
    val type: String,
    val title: String,
    val body: String,
    val data: NotificationDataDto? = null,
    val read_at: String? = null,
    val created_at: String? = null
)

/**
 * `data` field dari notification record.
 *
 * Bentuknya sesuai payload contract specification 10 section 19.
 * Semua field nullable karena tidak semua event membawanya.
 */
@Serializable
data class NotificationDataDto(
    val schema_version: Int = 1,
    val notification_id: String? = null,
    val type: String? = null,
    val resource_type: String? = null,
    val resource_id: String? = null,
    val order_id: String? = null,
    val assignment_id: String? = null,
    val order_status: String? = null,
    val payment_method: String? = null,
    val payment_status: String? = null,
    val target_route: String? = null,
    val created_at: String? = null
)

@Serializable
data class NotificationPageDto(
    val data: List<NotificationDto> = emptyList(),
    val meta: NotificationPageMetaDto? = null,
    val message: String? = null
) {
    val currentPage: Int get() = meta?.current_page ?: 1
    val perPage: Int get() = meta?.per_page?.takeIf { it > 0 } ?: NotificationApi.DEFAULT_PER_PAGE
    val total: Int get() = meta?.total ?: data.size
    val hasMore: Boolean get() = data.isNotEmpty() && currentPage * perPage < total
    val unreadCount: Int get() = meta?.unread_count ?: 0
}

@Serializable
data class NotificationPageMetaDto(
    val current_page: Int = 1,
    val last_page: Int = 1,
    val per_page: Int = NotificationApi.DEFAULT_PER_PAGE,
    val total: Int = 0,
    val unread_count: Int = 0
)