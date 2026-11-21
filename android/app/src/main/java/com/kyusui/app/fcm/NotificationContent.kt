package com.kyusui.app.fcm

import com.kyusui.app.domain.model.NotificationEvent
import com.kyusui.app.domain.model.NotificationPayload

/**
 * Judul dan badan notifikasi sistem, diturunkan dari payload.
 *
 * Ini murni: tidak menyentuh Android framework, sehingga dapat diuji di
 * JVM unit test untuk setiap event type.
 *
 * Penting: teks di sini hanya untuk tampilan. Android tidak pernah
 * menyimpulkan business state dari teks ini — status sebenarnya diambil
 * dari API ketika notification dibuka.
 */
object NotificationContent {

    fun titleFor(payload: NotificationPayload): String = when (payload.type) {
        NotificationEvent.ORDER_CREATED -> "Pesanan Baru"
        NotificationEvent.ORDER_PROCESSED -> "Pesanan Diproses"
        NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED -> "Bukti QRIS Menunggu Verifikasi"
        NotificationEvent.PAYMENT_QRIS_APPROVED -> "Pembayaran Disetujui"
        NotificationEvent.PAYMENT_QRIS_REJECTED -> "Pembayaran Ditolak"
        NotificationEvent.PAYMENT_CASH_CONFIRMED -> "Pembayaran Dikonfirmasi"
        NotificationEvent.COURIER_ASSIGNED -> "Kurir Ditugaskan"
        NotificationEvent.DELIVERY_STARTED -> "Pesanan Sedang Diantar"
        NotificationEvent.TRACKING_AVAILABLE -> "Lokasi Pengantar Tersedia"
        NotificationEvent.ORDER_COMPLETED -> "Pesanan Selesai"
    }

    fun bodyFor(payload: NotificationPayload): String {
        // Nomor pesanan sengaja ditulis apa adanya kalau backend tidak
        // mengirimnya. Tidak ada nomor tebakan atau nomor placeholder,
        // karena itu akan menampilkan data yang tidak ada di server.
        val orderRef = payload.orderId?.let { " #$it" } ?: ""
        return when (payload.type) {
            NotificationEvent.ORDER_CREATED -> "Pesanan$orderRef sudah masuk dan menunggu diproses."
            NotificationEvent.ORDER_PROCESSED -> "Pesanan$orderRef sedang diproses."
            NotificationEvent.PAYMENT_QRIS_PROOF_UPLOADED ->
                "Bukti pembayaran QRIS untuk pesanan$orderRef menunggu verifikasi."
            NotificationEvent.PAYMENT_QRIS_APPROVED ->
                "Pembayaran QRIS untuk pesanan$orderRef telah disetujui."
            NotificationEvent.PAYMENT_QRIS_REJECTED ->
                "Pembayaran QRIS untuk pesanan$orderRef ditolak. Silakan lakukan pembayaran ulang."
            NotificationEvent.PAYMENT_CASH_CONFIRMED ->
                "Pembayaran tunai untuk pesanan$orderRef telah dikonfirmasi kurir."
            NotificationEvent.COURIER_ASSIGNED ->
                "Kurir sudah ditugaskan untuk pesanan$orderRef."
            NotificationEvent.DELIVERY_STARTED ->
                "Pengantar sedang menuju alamat Anda untuk pesanan$orderRef."
            NotificationEvent.TRACKING_AVAILABLE ->
                "Lokasi pengantar untuk pesanan$orderRef sudah dapat dilacak."
            NotificationEvent.ORDER_COMPLETED ->
                "Pesanan$orderRef sudah selesai diantar."
        }
    }
}