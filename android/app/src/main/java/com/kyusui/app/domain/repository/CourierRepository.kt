package com.kyusui.app.domain.repository

import com.kyusui.app.core.Result
import com.kyusui.app.domain.model.CourierCashReceipt
import com.kyusui.app.domain.model.CourierDashboard
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierDutySession
import com.kyusui.app.domain.model.CourierDutyState
import com.kyusui.app.domain.model.CourierLocationFix

/**
 * Courier data source.
 *
 * Seluruh operasi memakai endpoint yang benar-benar ada pada
 * `backend/routes/api.php` di dalam grup `prefix('courier')` serta
 * `GET dashboard/courier`. Endpoint tersebut assignment-centric, jadi seluruh
 * operasi di sini bekerja pada `assignmentId`, bukan `orderId`.
 *
 * Repository ini tidak punya:
 *
 * - operasi menulis status apa pun. [startDelivery], [confirmCashReceived], dan
 *   [completeDelivery] hanya mengirim aksi dan mengembalikan Assignment Resource
 *   apa adanya dari backend.
 * - operasi yang menerima `courier_id`, `order_id`, nominal uang, atau bukti
 *   pembayaran dari client. `POST .../cash/confirm` sengaja tanpa body karena
 *   backend yang menentukan apakah kurir berhak mengonfirmasi.
 * - pagination pada daftar assignment. `GET courier/assignments` mengembalikan
 *   array penuh tanpa objek `meta`.
 * - endpoint peta. Navigasi ke aplikasi peta dan SDK peta in-app bukan bagian dari
 *   kontrak ini.
 */
interface CourierRepository {
    suspend fun getOpenOrders(): Result<List<com.kyusui.app.domain.model.OpenCourierOrder>> = Result.success(emptyList())
    suspend fun claimOrder(orderId: String): Result<CourierDelivery> = Result.failure("Pengambilan pesanan belum tersedia.")
    suspend fun getActiveAssignmentIds(): Result<List<String>> = getAssignments().map { list ->
        list.filter { it.canPostLocation }.map { it.id }
    }


    suspend fun getAssignments(): Result<List<CourierDelivery>>

    suspend fun getAssignment(assignmentId: String): Result<CourierDelivery>

    /**
     * Meminta backend memulai pengantaran.
     *
     * Backend menolak dengan `409` bila assignment bukan `ASSIGNED`, order belum
     * `DITUGASKAN`, atau kurir masih punya assignment `ACTIVE` lain.
     */
    suspend fun startDelivery(assignmentId: String): Result<CourierDelivery>

    /**
     * Mengirim posisi kurir saat ini.
     *
     * Repository yang menambah `idempotency_key`, sehingga retry akibat koneksi
     * terputus tidak membuat backend menyimpan titik lokasi yang sama dua kali.
     * `recorded_at` sengaja tidak dikirim dan backend memakai jam server.
     */
    suspend fun updateLocation(
        assignmentId: String,
        fix: CourierLocationFix
    ): Result<CourierDeviceLocation>

    /**
     * Mengirim aksi "Uang Diterima".
     *
     * Tanpa argumen lain. Backend memeriksa urutan berikut dan menolak dengan
     * `403` atau `409` bila salah satunya tidak terpenuhi:
     *
     * ```
     * authenticated courier
     * owns assignment
     * order belongs to assignment
     * delivery active
     * cash confirmation valid
     * ```
     */
    suspend fun confirmCashReceived(assignmentId: String): Result<CourierCashReceipt>

    /**
     * Meminta backend menyelesaikan pengantaran.
     *
     * Backend menolak dengan `409` bila delivery belum `ACTIVE` atau payment
     * belum `PAID`.
     */
    suspend fun completeDelivery(assignmentId: String): Result<CourierDelivery>

    suspend fun getDashboard(): Result<CourierDashboard>

    // --- Sesi bertugas (on-duty) tanpa assignment ---

    /**
     * Status sesi bertugas saat ini dari `GET courier/duty`.
     *
     * `isActive` dibaca apa adanya; repository tidak menyimpulkannya sendiri
     * dari `latestLocation` maupun dari waktu.
     */
    suspend fun getDutyState(): Result<CourierDutyState>

    /**
     * Memulai sesi bertugas. Backend menolak `409` bila sudah ada sesi aktif.
     */
    suspend fun startDuty(): Result<CourierDutySession>

    /**
     * Mengakhiri sesi bertugas. Backend menolak `409` bila tidak ada sesi aktif.
     */
    suspend fun stopDuty(): Result<CourierDutySession>

    /**
     * Mengirim posisi selama sesi bertugas.
     *
     * Berbeda dari [updateLocation] yang assignment-centric: tidak ada
     * `assignmentId`. Backend menolak `409` bila tidak ada sesi aktif atau bila
     * sesi yang dipegang klien sudah berakhir, sehingga repository memetakan
     * `409` menjadi [com.kyusui.app.core.ConflictException] agar pengirim lokasi
     * bisa berhenti.
     */
    suspend fun sendDutyLocation(fix: CourierLocationFix): Result<CourierDeviceLocation>
}