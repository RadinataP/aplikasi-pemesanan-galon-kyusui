package com.kyusui.app.ui.courier.tracking

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kyusui.app.MainActivity
import com.kyusui.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Jembatan dari controller ke [CourierLocationService].
 *
 * Dipisahkan sebagai interface supaya unit test JVM bisa menyuntikkan fake
 * tanpa harus menjalankan Android framework.
 */
interface CourierLocationServiceLauncher {

    /**
     * Meminta [CourierLocationService] berdiri (startForegroundService) untuk
     * assignment ini.
     *
     * @return false bila service tidak bisa dijalankan, misalnya karena
     *   Android melarang start dari latar belakang. Panggil dari aksi layar
     *   yang sah (foreground action) agar biasanya selalu true.
     */
    fun start(assignmentId: String): Boolean

    /** Meminta service berdiri untuk sesi bertugas tanpa assignment. */
    fun startDuty(): Boolean

    /** Menghentikan service (stopService). Aman dipanggil saat sedang berhenti. */
    fun stop()
}

/**
 * Launcher produksi berbalut [startForegroundService]/[stopService].
 *
 * Kedua panggilan hanya mengatur lifecycle service. Controller yang memanggil
 * fungsi ini menetapkan ada/tidaknya session pengiriman; service di sini hanya
 * menjadi pembungkus notifikasi yang tampil selama pengiriman berjalan.
 */
class AndroidCourierLocationServiceLauncher(
    private val appContext: Context
) : CourierLocationServiceLauncher {

    override fun start(assignmentId: String): Boolean =
        startService(assignmentId = assignmentId, isDuty = false)

    override fun startDuty(): Boolean =
        startService(assignmentId = "", isDuty = true)

    private fun startService(assignmentId: String, isDuty: Boolean): Boolean {
        if (!com.kyusui.app.ui.location.hasLocationPermission(appContext)) return false
        val intent = Intent(appContext, CourierLocationService::class.java)
            .setAction(CourierLocationService.ACTION_SHARE)
            .putExtra(CourierLocationService.EXTRA_ASSIGNMENT_ID, assignmentId)
            .putExtra(CourierLocationService.EXTRA_IS_DUTY, isDuty)
        return try {
            ContextCompat.startForegroundService(appContext, intent)
            true
        } catch (_: ForegroundServiceStartNotAllowedException) {
            // API 31+: start dilarang karena aplikasi di latar belakang.
            false
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    override fun stop() {
        appContext.stopService(
            Intent(appContext, CourierLocationService::class.java)
                .setAction(CourierLocationService.ACTION_STOP)
        )
    }
}

/**
 * Foreground service tipe `location` yang membuat pengiriman posisi kurir
 * terlihat oleh pengguna selama berjalan.
 *
 * ## Hubungan dengan controller
 *
 * Service ini TIDAK mengirim lokasi dan TIDAK memulai controller. Controller
 * (singleton) yang mengirim; service hanya:
 *
 *  1. menerima `ACTION_SHARE` dan menampilkan notifikasi permanen;
 *  2. dihentikan via `stopService` oleh controller saat session berhenti;
 *  3. saat [onDestroy] karena alasan eksternal sementara session masih aktif,
 *     menghentikan controller juga supaya tidak ada pengiriman tanpa
 *     notifikasi (invariant "no FGS-less loop").
 *
 * `START_NOT_STICKY`: bila sistem membunuh service, app tidak menghidupkannya
 * kembali sendiri.
 */
@AndroidEntryPoint
class CourierLocationService : Service() {

    @Inject
    lateinit var sharing: CourierLocationSharing

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHARE -> try {
                startAsForeground(isDuty = intent.getBooleanExtra(EXTRA_IS_DUTY, false))
            } catch (_: SecurityException) {
                sharing.stop(TrackingStopReason.PERMISSION_NOT_GRANTED)
                stopSelf()
            }

            // StopService langsung menghasilkan onDestroy; branch ini hanya
            // membela diri bila sistem mengirim ulang intent lama.
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Halaman pemanggil akan menghubungi controller.stop() terlebih dahulu,
        // jadi state sudah tidak sharing saat onDestroy yang normal. Bila
        // onDestroy datang dari luar alur itu (misal sistem menghentikan FGS),
        // controller dihentikan supaya pengiriman tidak berlanjut tanpa FGS.
        if (sharing.state.value.isSharing) {
            sharing.stop(TrackingStopReason.USER_REQUESTED)
        }
        super.onDestroy()
    }

    private fun startAsForeground(isDuty: Boolean) {
        val channelId = getString(R.string.courier_location_channel_id)
        createNotificationChannel(channelId)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.courier_location_notification_title))
            .setContentText(
                getString(
                    if (isDuty) {
                        R.string.courier_location_notification_text_duty
                    } else {
                        R.string.courier_location_notification_text
                    }
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            channelId,
            getString(R.string.courier_location_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.courier_location_channel_description)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * Mengetuk notifikasi membuka app tanpa extra routing apa pun: tidak
     * memakai jalur NotificationIntentExtras milik Batch 1 (FCM). Hal ini
     * sengaja, karena notifikasi FGS hanya indikator proses, bukan pesan
     * bisnis.
     */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_SHARE = "com.kyusui.app.action.SHARE_LOCATION"
        const val ACTION_STOP = "com.kyusui.app.action.STOP_LOCATION"
        const val EXTRA_ASSIGNMENT_ID = "assignment_id"
        const val EXTRA_IS_DUTY = "is_duty"
        const val NOTIFICATION_ID = 4201
    }
}