package com.kyusui.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kyusui.app.fcm.NotificationIntentExtras
import com.kyusui.app.ui.navigation.AppNavHost
import com.kyusui.app.ui.notifications.NotificationDeepLinkViewModel
import com.kyusui.app.ui.theme.KyusuiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * State holder untuk notification tap. Di-scope ke activity supaya Intent
     * yang tiba lewat [onNewIntent] terlihat oleh [AppNavHost].
     */
    private val notificationDeepLink: NotificationDeepLinkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Intent awal hanya dibaca pada peluncuran baru. Saat activity dibuat
        // ulang karena perubahan konfigurasi, `savedInstanceState` tidak null
        // dan Intent yang sama tidak boleh diproses lagi, kalau tidak rotasi
        // layar akan memicu navigasi kedua.
        if (savedInstanceState == null) {
            deliverNotificationIntent(intent)
        }
        setContent {
            KyusuiTheme {
                AppNavHost()
            }
        }
    }

    /**
     * Dipanggil saat activity (singleTop) menerima Intent baru tanpa dibuat
     * ulang, misalnya ketika app sedang di foreground lalu sebuah notifikasi
     * ditekan.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverNotificationIntent(intent)
    }

    private fun deliverNotificationIntent(intent: Intent?) {
        val extras = intent?.let(::notificationPayloadExtras) ?: return
        if (extras.isEmpty()) return
        notificationDeepLink.onNotificationIntent(extras)
    }

    /**
     * Rekonstruksi `RemoteMessage.data` dari Bundle yang dikirim
     * [com.kyusui.app.fcm.KyusuiFirebaseMessagingService]. Hanya entri string
     * yang diambil; extra framework pada Intent yang sama tidak ikut.
     */
    private fun notificationPayloadExtras(intent: Intent): Map<String, String> {
        val bundle = intent.getBundleExtra(NotificationIntentExtras.DATA) ?: return emptyMap()
        return buildMap {
            bundle.keySet().forEach { key ->
                bundle.getString(key)?.let { value -> put(key, value) }
            }
        }
    }
}
