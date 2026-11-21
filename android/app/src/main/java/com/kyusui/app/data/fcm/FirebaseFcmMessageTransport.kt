package com.kyusui.app.data.fcm

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.kyusui.app.core.Result
import com.kyusui.app.core.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi [FcmMessageTransport] memakai Firebase SDK.
 *
 * Firebase hanya terinisialisasi bila `google-services.json` ada di
 * folder `app/` dan `KyusuiApplication` berhasil memulainya. Tanpa
 * file itu, [isAvailable] mengembalikan false dan seluruh operasi FCM
 * menjadi no-op.
 *
 * Ini bukan error — FCM yang belum dikonfigurasi adalah kondisi yang
 * didukung, sama seperti peta yang belum dikonfigurasi di Phase 7.
 */
@Singleton
class FirebaseFcmMessageTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : FcmMessageTransport {

    override suspend fun getToken(): String? {
        if (!isAvailable()) return null
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            // Token belum tersedia. Ini kondisi normal saat app baru
            // dibuka pertama kali, bukan error.
            null
        }
    }

    override suspend fun deleteToken(): Result<Unit> {
        if (!isAvailable()) return Result.success(Unit)
        return safeApiCall {
            FirebaseMessaging.getInstance().deleteToken().await()
            Unit
        }
    }

    override fun isAvailable(): Boolean =
        runCatching { FirebaseApp.getApps(context) }
            .getOrDefault(emptyList())
            .isNotEmpty()
}