package com.kyusui.app.ui.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Aturan izin `POST_NOTIFICATIONS` yang dipisah dari pemeriksa `Context`
 * supaya bisa diuji di JVM unit test, tempat `ContextCompat` tidak tersedia.
 */
object NotificationPermission {

    const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

    /**
     * Android 13 (API 33) adalah versi pertama yang mewajibkan izin runtime
     * untuk menampilkan notifikasi. Di bawah itu izin diberikan saat instalasi.
     */
    const val MIN_SDK_WITH_RUNTIME_PERMISSION = 33

    fun isRequired(sdkInt: Int): Boolean = sdkInt >= MIN_SDK_WITH_RUNTIME_PERMISSION
}

/**
 * Meminta izin `POST_NOTIFICATIONS` sekali setelah sesi aktif dan Android 13+.
 *
 * Kalau izin ditolak, tidak ada yang dipaksa: `KyusuiFirebaseMessagingService`
 * sudah memeriksa [androidx.core.app.NotificationManagerCompat.areNotificationsEnabled]
 * sebelum menampilkan notifikasi, jadi penolakan hanya berarti notifikasi
 * tidak muncul, bukan crash.
 *
 * @param enabled true setelah role sesi diketahui, supaya dialog izin muncul
 *   di konteks yang jelas (setelah login) dan tidak menghadang layar splash.
 */
@Composable
fun NotificationPermissionEffect(enabled: Boolean) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Hasil izin tidak menahan alur. Penolakan cukup membuat
        // notification tidak tampil, dan itu sudah ditangani service.
    }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        if (!NotificationPermission.isRequired(Build.VERSION.SDK_INT)) return@LaunchedEffect

        val granted = ContextCompat.checkSelfPermission(
            context,
            NotificationPermission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(NotificationPermission.POST_NOTIFICATIONS)
        }
    }
}
