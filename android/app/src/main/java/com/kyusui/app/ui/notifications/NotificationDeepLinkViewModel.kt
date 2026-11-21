package com.kyusui.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyusui.app.core.Result
import com.kyusui.app.data.fcm.FcmMessageTransport
import com.kyusui.app.domain.model.NotificationPayload
import com.kyusui.app.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Menjembatani system notification (Intent dari
 * [com.kyusui.app.fcm.KyusuiFirebaseMessagingService]) dengan navigasi
 * Compose.
 *
 * Kelas ini sengaja tidak menentukan rute sendiri. Ia hanya:
 * - mem-parse extras Intent menjadi [NotificationPayload] memakai parser yang
 *   sama dengan `onMessageReceived`;
 * - menyimpan payload itu sebagai pending sampai role sesi diketahui;
 * - mendaftarkan FCM token saat sesi aktif.
 *
 * Penentuan tujuan tetap dilakukan [NotificationTapRouter] dengan role nyata.
 * Payload tidak pernah dipakai sebagai sumber kebenaran status bisnis.
 *
 * Di-scope ke activity supaya Intent yang tiba lewat `onNewIntent` terlihat
 * oleh [com.kyusui.app.ui.navigation.AppNavHost].
 */
@HiltViewModel
class NotificationDeepLinkViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val fcmTransport: FcmMessageTransport
) : ViewModel() {

    private val _pendingNotification = MutableStateFlow<NotificationPayload?>(null)

    /**
     * Payload notification yang menunggu dibuka. Null berarti tidak ada
     * notification tap yang belum ditangani.
     */
    val pendingNotification: StateFlow<NotificationPayload?> =
        _pendingNotification.asStateFlow()

    private var tokenSyncJob: Job? = null

    /**
     * Terima isi Intent dari system notification.
     *
     * @return true bila payload valid dan disimpan sebagai pending. Payload
     *   yang tidak valid (field wajib hilang, tipe tidak dikenal) diabaikan
     *   tanpa menebak tujuan.
     */
    fun onNotificationIntent(extras: Map<String, String>): Boolean {
        if (extras.isEmpty()) return false
        val payload = (notificationRepository.parsePayload(extras) as? Result.Success)?.data
            ?: return false
        _pendingNotification.value = payload
        return true
    }

    /**
     * Tandai pending notification sudah dibuka.
     *
     * Dipanggil setelah rute dieksekusi supaya satu Intent tidak memicu
     * navigasi dua kali ketika komposisi berulang.
     */
    fun consumePendingNotification() {
        _pendingNotification.value = null
    }

    /**
     * Daftarkan FCM token perangkat ke backend untuk sesi yang aktif.
     *
     * Dipanggil saat sesi tervalidasi (login maupun pemulihan sesi), karena
     * endpoint `notifications/device-token` berada di dalam `auth:sanctum`.
     * Bila Firebase belum dikonfigurasi, [FcmMessageTransport.getToken]
     * mengembalikan null dan pemanggilan ini menjadi no-op.
     */
    fun syncDeviceToken() {
        if (tokenSyncJob?.isActive == true) return
        tokenSyncJob = viewModelScope.launch {
            val token = fcmTransport.getToken() ?: return@launch
            notificationRepository.registerDeviceToken(token)
        }
    }
}
