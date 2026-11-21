package com.kyusui.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KyusuiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    /**
     * Inisialisasi Firebase untuk FCM.
     *
     * Firebase hanya terinisialisasi bila `google-services.json` ada di
     * folder `app/`. Tanpa file itu, Firebase tidak terinisialisasi dan
     * FCM otomatis nonaktif. Ini bukan error — hanya berarti FCM
     * belum dikonfigurasi di environment ini.
     *
     * Lihat [com.kyusui.app.data.fcm.FirebaseFcmMessageTransport].
     */
    private fun initializeFirebase() {
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            return
        }
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Firebase tidak terinisialisasi. FCM tidak akan berfungsi,
            // tapi aplikasi tetap berjalan normal.
            Log.w(TAG, "Firebase tidak terinisialisasi. FCM tidak aktif.", e)
        }
    }

    private companion object {
        const val TAG = "KyusuiApplication"
    }
}