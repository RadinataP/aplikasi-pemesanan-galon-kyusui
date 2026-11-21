package com.kyusui.app.fcm

/**
 * Kontrak Intent antara system notification dan [com.kyusui.app.MainActivity].
 *
 * Payload FCM asli (`RemoteMessage.data`) dipindahkan **apa adanya** sebagai
 * satu Bundle, bukan diterjemahkan menjadi extras per field. Dengan begitu
 * parsing payload tetap hanya ada di satu tempat
 * ([com.kyusui.app.domain.repository.NotificationRepository.parsePayload]) dan
 * notification tap tidak pernah menebak-nebak bentuk payload.
 *
 * Isi Bundle hanya dipakai sebagai konteks navigasi. Status bisnis (order,
 * payment, delivery) tetap diambil dari API oleh layar tujuan, karena payload
 * bukan source of truth.
 */
object NotificationIntentExtras {

    /**
     * Bundle berisi seluruh pasangan string dari `RemoteMessage.data`.
     *
     * Dinamai dengan prefiks paket supaya tidak bertabrakan dengan extra
     * framework (`android.intent.extra.*`) pada Intent yang sama.
     */
    const val DATA = "com.kyusui.app.extra.NOTIFICATION_DATA"
}
