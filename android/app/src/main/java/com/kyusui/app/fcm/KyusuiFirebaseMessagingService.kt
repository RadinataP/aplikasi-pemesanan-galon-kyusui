package com.kyusui.app.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kyusui.app.MainActivity
import com.kyusui.app.R
import com.kyusui.app.core.Result
import com.kyusui.app.data.fcm.FcmMessageTransport
import com.kyusui.app.di.HiltEntryPoints
import com.kyusui.app.domain.model.NotificationPayload
import com.kyusui.app.domain.repository.NotificationRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FCM service untuk menerima message dan menampilkan system notification.
 *
 * FCM hanya transport. Payload tidak menentukan business state — payload
 * hanya membawa konteks routing. Saat notification dibuka, Android
 * mengambil state terbaru dari API dan baru itu yang dirender.
 *
 * Logika yang benar-benar perlu diuji (judul, isi, parsing payload,
 * penentuan rute) sengaja tidak ada di kelas ini, melainkan di
 * [NotificationContent] dan `NotificationTapRouter`, sehingga bisa diuji
 * di JVM tanpa Firebase maupun Android framework.
 *
 * Karena Firebase membuat instance service ini sendiri (di luar siklus
 * hidup Hilt), injeksi field `@Inject` tidak berfungsi. Sebagai gantinya,
 * dependency diambil manual melalui `EntryPointAccessors` di [onCreate].
 */
class KyusuiFirebaseMessagingService : FirebaseMessagingService() {

    private lateinit var notificationRepository: NotificationRepository

    @Suppress("unused")
    private lateinit var fcmTransport: FcmMessageTransport

    /**
     * Scope untuk pekerjaan FIRE-and-forget di callback FCM.
     *
     * Callback FCM tidak suspend, jadi repository dipanggil dari scope
     * sendiri. Kegagalan sengaja dibiarkan: FCM akan menganggap pesan
     * sudah ditangani dan tidak mengirim ulang.
     */
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Ambil dependency via Hilt EntryPoint. Hilt tidak bisa inject
        // field di FirebaseMessagingService karena Firebase yang
        // membuat instance service ini, bukan Hilt.
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, HiltEntryPoints::class.java
        )
        notificationRepository = entryPoint.notificationRepository()
        fcmTransport = entryPoint.fcmMessageTransport()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token baru didaftarkan ke backend lewat repository.
        // FCM token bukan credential — hanya transport identifier.
        //
        // Route ini berada di dalam grup `auth:sanctum`, jadi pendaftaran
        // hanya berhasil bila user sudah login. Bila gagal (misalnya
        // session sudah berakhir), token akan didaftarkan ulang oleh
        // layar notifikasi setelah login berikutnya.
        serviceScope.launch {
            notificationRepository.registerDeviceToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val parseResult = notificationRepository.parsePayload(message.data)
        if (parseResult !is Result.Success) {
            // Payload tidak valid atau tipenya tidak dikenal. Android tidak
            // boleh menebak-nebak maupun crash, jadi payload diabaikan
            // tanpa menampilkan apa pun.
            return
        }

        // Payload hanya dipakai untuk menampilkan teks. Status bisnis
        // (dibayar belum, dikirim belum) tetap dibaca dari API saat
        // notification dibuka.
        showNotification(data = message.data, payload = parseResult.data)
    }

    private fun showNotification(data: Map<String, String>, payload: NotificationPayload) {
        val channelId = getString(R.string.notification_channel_id)
        createNotificationChannel(channelId)

        val notificationManager = NotificationManagerCompat.from(this)
        if (!notificationManager.areNotificationsEnabled()) {
            // Izin POST_NOTIFICATIONS belum diberikan. Menampilkan
            // notification diam-diam tidak akan terlihat-user, jadi
            // lebih baik tidak dipaksa.
            return
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(NotificationContent.titleFor(payload))
            .setContentText(NotificationContent.bodyFor(payload))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(NotificationContent.bodyFor(payload))
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(notificationIntent(data, payload))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationIdFor(payload), notification)
    }

    /**
     * `MainActivity` menerima payload lewat Intent extra. Rute akhir
     * ditentukan setelah session dipulihkan, karena role user dibutuhkan
     * untuk memilih tujuan. Flag `CLEAR_TASK` dipakai supaya notification
     * yang ditekan dari status bar membuka navigasi dari awal, bukan
     * menumpuk di atas activity lama yang mungkin sudah membeku.
     *
     * Seluruh `RemoteMessage.data` dipindahkan utuh sebagai satu Bundle,
     * sehingga `MainActivity` dapat memakai parser payload yang sama
     * dengan `onMessageReceived` tanpa menduplikasi kontrak field.
     */
    private fun notificationIntent(
        data: Map<String, String>,
        payload: NotificationPayload
    ): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(NotificationIntentExtras.DATA, dataAsBundle(data))
        }

        return PendingIntent.getActivity(
            this,
            notificationIdFor(payload),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dataAsBundle(data: Map<String, String>): Bundle = Bundle().apply {
        data.forEach { (key, value) -> putString(key, value) }
    }

    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private companion object {
        /**
         * Id notifikasi sistem diturunkan dari `notification_id` milik
         * backend, sehingga pesan untuk notifikasi yang sama tidak
         * menumpuk menjadi beberapa baris.
         */
        fun notificationIdFor(payload: NotificationPayload): Int =
            payload.notificationId.hashCode()
    }
}