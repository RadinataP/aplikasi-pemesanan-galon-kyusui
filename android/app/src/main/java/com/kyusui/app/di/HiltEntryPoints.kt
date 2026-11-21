package com.kyusui.app.di

import com.kyusui.app.data.fcm.FcmMessageTransport
import com.kyusui.app.domain.repository.NotificationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider

/**
 * EntryPoint untuk class yang tidak bisa di-inject langsung oleh Hilt
 * (seperti `FirebaseMessagingService` yang dibuat Firebase).
 *
 * Hilt tidak bisa men-subclass `FirebaseMessagingService` karena class
 * itu `final`. Sebagai gantinya, service mengambil dependency manual
 * melalui entry point ini di [onCreate].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltEntryPoints {

    fun notificationRepository(): NotificationRepository

    fun fcmMessageTransport(): FcmMessageTransport
}