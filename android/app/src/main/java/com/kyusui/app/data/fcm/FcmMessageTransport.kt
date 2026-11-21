package com.kyusui.app.data.fcm

import com.kyusui.app.core.Result

/**
 * Transport untuk FCM token dan message.
 *
 * Interface ini memisahkan Firebase SDK dari repository dan service,
 * sehingga bisa diuji di JVM unit test tanpa Play services.
 */
interface FcmMessageTransport {

    /**
     * Ambil FCM token saat ini.
     *
     * Mengembalikan null bila Firebase tidak terinisialisasi atau
     * token belum tersedia. Bukan error — hanya berarti FCM belum
     * siap.
     */
    suspend fun getToken(): String?

    /**
     * Hapus FCM token dari backend.
     *
     * Dipanggil saat logout supaya backend tidak lagi mengirim
     * notification ke device ini.
     */
    suspend fun deleteToken(): Result<Unit>

    /**
     * Cek apakah Firebase sudah terinisialisasi dan FCM bisa dipakai.
     */
    fun isAvailable(): Boolean
}