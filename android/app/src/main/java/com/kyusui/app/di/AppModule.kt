package com.kyusui.app.di

import android.content.ContentResolver
import com.kyusui.app.core.TrackingIntervalsSchedule
import com.kyusui.app.core.TrackingSchedule
import com.kyusui.app.data.fcm.FcmMessageTransport
import com.kyusui.app.data.fcm.FirebaseFcmMessageTransport
import com.kyusui.app.data.repository.AuthRepositoryImpl
import com.kyusui.app.data.repository.CourierRepositoryImpl
import com.kyusui.app.data.repository.CustomerRepositoryImpl
import com.kyusui.app.data.repository.NotificationRepositoryImpl
import com.kyusui.app.data.repository.OwnerRepositoryImpl
import com.kyusui.app.data.repository.FinanceDirectOrderRepositoryImpl
import com.kyusui.app.data.repository.ProfileRepositoryImpl
import com.kyusui.app.data.upload.ContentResolverProofFileSource
import com.kyusui.app.data.upload.ProofFileSource
import com.kyusui.app.domain.repository.AuthRepository
import com.kyusui.app.domain.repository.CourierRepository
import com.kyusui.app.domain.repository.CustomerRepository
import com.kyusui.app.domain.repository.NotificationRepository
import com.kyusui.app.domain.repository.OwnerRepository
import com.kyusui.app.domain.repository.FinanceDirectOrderRepository
import com.kyusui.app.domain.repository.ProfileRepository
import com.kyusui.app.ui.location.DeviceLocationSource
import com.kyusui.app.ui.location.FusedDeviceLocationSource
import com.kyusui.app.ui.courier.tracking.AndroidCourierLocationServiceLauncher
import com.kyusui.app.ui.courier.tracking.CourierLocationServiceLauncher
import com.kyusui.app.ui.courier.tracking.CourierLocationShareController
import com.kyusui.app.ui.courier.tracking.CourierLocationSharing
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCustomerRepository(impl: CustomerRepositoryImpl): CustomerRepository

    @Binds
    @Singleton
    abstract fun bindOwnerRepository(impl: OwnerRepositoryImpl): OwnerRepository

    @Binds
    @Singleton
    abstract fun bindFinanceDirectOrderRepository(impl: FinanceDirectOrderRepositoryImpl): FinanceDirectOrderRepository

    @Binds
    @Singleton
    abstract fun bindCourierRepository(impl: CourierRepositoryImpl): CourierRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindFcmMessageTransport(impl: FirebaseFcmMessageTransport): FcmMessageTransport

    @Binds
    @Singleton
    abstract fun bindProofFileSource(impl: ContentResolverProofFileSource): ProofFileSource

    /**
     * Interval tracking disuntikkan sebagai dependency, bukan dipanggil langsung
     * dari ViewModel, supaya test bisa mengoper nilai sendiri dan angka
     * produksi tetap hanya berada di satu batas konfigurasi.
     */
    @Binds
    @Singleton
    abstract fun bindTrackingSchedule(impl: TrackingIntervalsSchedule): TrackingSchedule

    /**
     * Pengiriman posisi kurir disuntikkan sebagai interface supaya unit test
     * JVM bisa mengganti controller yang memakai Android framework dengan fake.
     */
    @Binds
    @Singleton
    abstract fun bindCourierLocationSharing(
        impl: CourierLocationShareController
    ): CourierLocationSharing
}

/**
 * `ContentResolver` disuplai sebagai dependency agar `ProofFileSource` tetap
 * testable di JVM unit test tanpa instrumentation.
 */
@Module
@InstallIn(SingletonComponent::class)
object ContentModule {

    @Provides
    @Singleton
    fun provideContentResolver(
        @ApplicationContext context: android.content.Context
    ): ContentResolver = context.contentResolver
}

/**
 * Sumber lokasi perangkat disuplai sebagai dependency agar
 * [DeviceLocationSource] yang memakai Fused Location Provider bisa diganti
 * test double di JVM unit test, karena Play services tidak tersedia di sana.
 */
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideDeviceLocationSource(
        @ApplicationContext context: android.content.Context
    ): DeviceLocationSource = FusedDeviceLocationSource(context)

    /**
     * Launcher foreground service pengiriman posisi kurir. Disuplai sebagai
     * dependency agar controller bisa diuji tanpa Android framework.
     */
    @Provides
    @Singleton
    fun provideCourierLocationServiceLauncher(
        @ApplicationContext context: android.content.Context
    ): CourierLocationServiceLauncher = AndroidCourierLocationServiceLauncher(context)
}