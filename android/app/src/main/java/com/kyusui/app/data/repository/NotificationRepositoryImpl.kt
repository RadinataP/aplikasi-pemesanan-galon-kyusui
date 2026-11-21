package com.kyusui.app.data.repository

import com.kyusui.app.core.Result
import com.kyusui.app.core.safeApiCall
import com.kyusui.app.data.api.NotificationApi
import com.kyusui.app.data.api.NotificationDataDto
import com.kyusui.app.data.api.NotificationDto
import com.kyusui.app.data.api.NotificationPageDto
import com.kyusui.app.data.api.RegisterDeviceTokenRequestDto
import com.kyusui.app.domain.model.NotificationEvent
import com.kyusui.app.domain.model.NotificationPayload
import com.kyusui.app.domain.model.NotificationResourceType
import com.kyusui.app.domain.model.PaginatedResult
import com.kyusui.app.domain.repository.NotificationRepository
import com.kyusui.app.domain.repository.NotificationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationApi: NotificationApi
) : NotificationRepository {

    override suspend fun registerDeviceToken(token: String): Result<Unit> = safeApiCall {
        val response = notificationApi.registerDeviceToken(
            RegisterDeviceTokenRequestDto(token = token)
        )
        if (!response.isSuccessful) {
            throw IllegalStateException("Gagal mendaftarkan token: HTTP ${response.code()}")
        }
        Unit
    }

    override fun notifications(
        page: Int,
        perPage: Int,
        isRead: Boolean?
    ): Flow<Result<PaginatedResult<NotificationSummary>>> = flow {
        val result = safeApiCall {
            notificationApi.notifications(page = page, perPage = perPage, isRead = isRead)
        }
        emit(
            when (result) {
                is Result.Success -> Result.success(result.data.toDomain())
                is Result.Failure -> result
            }
        )
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> = safeApiCall {
        val response = notificationApi.markAsRead(notificationId)
        if (!response.isSuccessful) {
            throw IllegalStateException("Gagal menandai dibaca: HTTP ${response.code()}")
        }
        Unit
    }

    override suspend fun markAllAsRead(): Result<Unit> = safeApiCall {
        val response = notificationApi.markAllAsRead()
        if (!response.isSuccessful) {
            throw IllegalStateException("Gagal menandai semua dibaca: HTTP ${response.code()}")
        }
        Unit
    }

    override fun parsePayload(data: Map<String, String>): Result<NotificationPayload> {
        val typeString = data["type"]
 ?: return Result.failure("Payload tidak memiliki field 'type'.")
        val type = NotificationEvent.fromApiValue(typeString)
            ?: return Result.failure("Tipe notification tidak dikenal: $typeString")

        val resourceTypeString = data["resource_type"]
            ?: return Result.failure("Payload tidak memiliki field 'resource_type'.")
        val resourceType = NotificationResourceType.fromApiValue(resourceTypeString)
            ?: return Result.failure("Resource type tidak dikenal: $resourceTypeString")

        val notificationId = data["notification_id"]
            ?: return Result.failure("Payload tidak memiliki field 'notification_id'.")

        return Result.success(
            NotificationPayload(
                schemaVersion = data["schema_version"]?.toIntOrNull() ?: 1,
                notificationId = notificationId,
                type = type,
                resourceType = resourceType,
                resourceId = data["resource_id"],
                orderId = data["order_id"],
                assignmentId = data["assignment_id"],
                orderStatus = data["order_status"],
                paymentMethod = data["payment_method"],
                paymentStatus = data["payment_status"],
                targetRoute = data["target_route"],
                createdAt = data["created_at"]
            )
        )
    }

    private fun NotificationPageDto.toDomain(): PaginatedResult<NotificationSummary> =
        PaginatedResult(
            data = data.map { it.toDomain() },
            currentPage = currentPage,
            perPage = perPage,
            total = total
        )

    private fun NotificationDto.toDomain(): NotificationSummary {
        // `data` di bawah adalah `NotificationDto.data` (payload contract),
        // bukan `kotlin.Result.data`. Namavariabel lokal sengaja dinamai
        // `payloadData` supaya tidak menutupi receiver extension.
        val payloadData = data
        val payload = payloadData?.let { pd ->
            NotificationPayload(
                schemaVersion = pd.schema_version,
                notificationId = pd.notification_id ?: id.toString(),
                type = NotificationEvent.fromApiValue(pd.type) ?: NotificationEvent.ORDER_CREATED,
                resourceType = NotificationResourceType.fromApiValue(pd.resource_type)
                    ?: NotificationResourceType.ORDER,
                resourceId = pd.resource_id,
                orderId = pd.order_id,
                assignmentId = pd.assignment_id,
                orderStatus = pd.order_status,
                paymentMethod = pd.payment_method,
                paymentStatus = pd.payment_status,
                targetRoute = pd.target_route,
                createdAt = pd.created_at
            )
        } ?: NotificationPayload(
            notificationId = id.toString(),
            type = NotificationEvent.ORDER_CREATED,
            resourceType = NotificationResourceType.ORDER
        )

        return NotificationSummary(
            id = id.toString(),
            type = payload.type,
            title = title,
            body = body,
            isRead = read_at != null,
            createdAt = created_at,
            payload = payload
        )
    }
}