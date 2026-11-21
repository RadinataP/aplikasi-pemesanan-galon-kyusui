package com.kyusui.app.data.repository

import com.kyusui.app.core.ConflictException
import com.kyusui.app.core.ParsingException
import com.kyusui.app.core.Result
import com.kyusui.app.core.safeApiCall
import com.kyusui.app.data.api.CourierApi
import com.kyusui.app.data.api.CourierAssignmentDto
import com.kyusui.app.data.api.CourierDutySessionDto
import com.kyusui.app.data.api.CourierLocationRequestDto
import com.kyusui.app.data.api.EnvelopeDto
import com.kyusui.app.data.mapper.toDomain
import com.kyusui.app.data.mapper.toDomainOrNull
import com.kyusui.app.domain.model.CourierCashReceipt
import com.kyusui.app.domain.model.CourierDashboard
import com.kyusui.app.domain.model.CourierDelivery
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierDutySession
import com.kyusui.app.domain.model.CourierDutyState
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.repository.CourierRepository
import retrofit2.HttpException
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi [CourierRepository] di atas endpoint yang benar-benar ada pada
 * `backend/routes/api.php`.
 *
 * Prinsip yang dijaga di seluruh file ini:
 *
 * - Tidak ada operasi yang menulis status. Semua aksi mengembalikan Assignment
 *   Resource dari backend dan hasilnya dipakai apa adanya.
 * - `confirmCashReceived` mengirim request tanpa body. Otorisasi, validasi
 *   delivery aktif, dan pengecekan metode CASH semuanya milik backend.
 * - `409` dan `422` dilempar sebagai [HttpException] supaya
 *   [com.kyusui.app.core.safeApiCall] memetakannya menjadi exception yang
 *   sama seperti endpoint lain. Tanpa itu, penolakan backend bisa terlihat
 *   sebagai aksi yang berhasil.
 * - Respons `2xx` yang tidak memuat data yang bisa dipakai dilempar sebagai
 *   [ParsingException], bukan `IllegalStateException`. Alasannya `
 *   safeApiCall` menelan exception biasa menjadi `UnknownException`, sehingga
 *   layar tidak bisa membedakan "server menjawab hal yang tidak bisa kubaca"
 *   dari "kesalahan tidak diketahui". `ParsingException` lolos apa adanya dan
 *   pesannya sampai ke kurir.
 * - Id assignment selalu dibaca apa adanya. Repository tidak memvalidasi
 *   format angka id, karena keputusan "apakah ini assignment saya" milik backend.
 */
@Singleton
class CourierRepositoryImpl @Inject constructor(
    private val courierApi: CourierApi
) : CourierRepository {

    /**
     * Daftar assignment milik kurir.
     *
     * Response backend berupa array penuh tanpa `meta`, jadi hasilnya langsung
     * dipetakan ke list. Android tidak membuat halaman berikutnya yang tidak
     * ada di endpoint ini.
     */
    override suspend fun getAssignments(): Result<List<CourierDelivery>> = safeApiCall {
        courierApi.assignments().requireData().map { it.toDomain() }
    }

    override suspend fun getAssignment(assignmentId: String): Result<CourierDelivery> =
        safeApiCall {
            courierApi.assignment(assignmentId).requireData().toDomain()
        }

    override suspend fun startDelivery(assignmentId: String): Result<CourierDelivery> =
        safeApiCall {
            courierApi.startDelivery(assignmentId).requireDeliveryBody()
        }

    /**
     * Mengirim posisi kurir.
     *
     * `idempotency_key` dibuat di sini, bukan di ViewModel, karena itu detail
     * transport: pengguna menekan sekali, retry atau koneksi terputus boleh
     * mengirim key yang sama, dan backend akan menyimpan titik lokasi itu satu
     * kali saja.
     *
     * `recorded_at` dibiarkan null. Backend menghitung kecepatan perpindahan
     * lokasi dari nilai itu; memakai jam perangkat berisiko ditolak `422` bila
     * jam perangkat tertinggal dari jam server. Backend sudah memakai waktu
     * server saat field ini tidak dikirim.
     */
    override suspend fun updateLocation(
        assignmentId: String,
        fix: CourierLocationFix
    ): Result<CourierDeviceLocation> = safeApiCall {
        val response = courierApi.updateLocation(
            assignmentId = assignmentId,
            request = CourierLocationRequestDto(
                latitude = fix.point.latitude,
                longitude = fix.point.longitude,
                accuracy_meters = fix.accuracyMeters,
                recorded_at = null,
                idempotency_key = UUID.randomUUID().toString()
            )
        )

        if (!response.isSuccessful) {
            // `409` pada endpoint lokasi selalu berarti delivery sudah tidak
            // aktif (backend: abort_unless ACTIVE + DALAM_PENGANTARAN). Pengecualian
            // terpisah dipakai supaya pengirim lokasi bisa menghentikan service
            // ketika backend menolak karena alasan yang tidak akan berubah dengan
            // pengiriman berikutnya. Endpoint lain tetap melempar [HttpException]
            // dan memakai pemetaan global yang sama seperti sebelumnya.
            if (response.code() == CONFLICT_STATUS_CODE) {
                throw ConflictException(CONFLICT_LOCATION_MESSAGE)
            }
            throw HttpException(response)
        }

        // Backend membungkus lokasi di dalam `data.location`, bukan di `data`
        // itu sendiri. Nilai yang dipakai adalah yang sah di mata server.
        response.body()?.requireData()?.location?.toDomainOrNull()
            ?: throw ParsingException(MISSING_LOCATION_MESSAGE)
    }

    /**
     * Aksi "Uang Diterima".
     *
     * Tidak ada body. Android tidak mengirim id kurir, id order, nominal, atau
     * bukti apa pun, dan tidak punya jalur yang menandai pembayaran sebagai lunas
     * sebelum backend mengatakannya.
     */
    override suspend fun confirmCashReceived(assignmentId: String): Result<CourierCashReceipt> =
        safeApiCall {
            val response = courierApi.confirmCashReceived(assignmentId)

            if (!response.isSuccessful) {
                throw HttpException(response)
            }

            response.body()?.requireData()?.toDomain()
                ?: throw ParsingException(MISSING_CASH_RECEIPT_MESSAGE)
        }

    override suspend fun completeDelivery(assignmentId: String): Result<CourierDelivery> =
        safeApiCall {
            courierApi.completeDelivery(assignmentId).requireDeliveryBody()
        }

    override suspend fun getDashboard(): Result<CourierDashboard> = safeApiCall {
        courierApi.dashboard().requireData().toDomain()
    }

    override suspend fun getDutyState(): Result<CourierDutyState> = safeApiCall {
        courierApi.dutyState().requireData().toDomain()
    }

    override suspend fun startDuty(): Result<CourierDutySession> = safeApiCall {
        courierApi.startDuty().requireDutyBody()
    }

    override suspend fun stopDuty(): Result<CourierDutySession> = safeApiCall {
        courierApi.stopDuty().requireDutyBody()
    }

    /**
     * Mengirim posisi selama bertugas.
     *
     * `courier_duty_locations` tidak punya kolom idempotency, jadi
     * `idempotency_key` tidak diisi (berbeda dari jalur assignment yang kolomnya
     * memang ada). Backend mengabaikan field itu.
     */
    override suspend fun sendDutyLocation(fix: CourierLocationFix): Result<CourierDeviceLocation> =
        safeApiCall {
            val response = courierApi.sendDutyLocation(
                CourierLocationRequestDto(
                    latitude = fix.point.latitude,
                    longitude = fix.point.longitude,
                    accuracy_meters = fix.accuracyMeters,
                    recorded_at = null,
                    idempotency_key = null
                )
            )

            if (!response.isSuccessful) {
                // `409` pada endpoint lokasi bertugas berarti tidak ada sesi
                // aktif atau sesi yang dipegang klien sudah berakhir. Keduanya
                // tidak akan berubah dengan mengirim lokasi lagi, jadi dipetakan
                // ke ConflictException supaya pengirim berhenti.
                if (response.code() == CONFLICT_STATUS_CODE) {
                    throw ConflictException(CONFLICT_DUTY_MESSAGE)
                }
                throw HttpException(response)
            }

            response.body()?.requireData()?.location?.toDomainOrNull()
                ?: throw ParsingException(MISSING_LOCATION_MESSAGE)
        }

    /**
     * Endpoint aksi membalut assignment di dalam `data`.
     *
     * Status HTTP non-2xx dilempar sebagai [HttpException] agar dipetakan ke
     * exception yang seragam dengan endpoint lain. Ini yang membuat `409` dari
     * `start`, `cash/confirm`, dan `complete` tampil sebagai penolakan backend,
     * bukan sebagai keberhasilan.
     */
    private fun Response<EnvelopeDto<CourierAssignmentDto>>.requireDeliveryBody():
        CourierDelivery {
        if (!isSuccessful) {
            throw HttpException(this)
        }
        return body()?.requireData()?.toDomain()
            ?: throw ParsingException(MISSING_ASSIGNMENT_MESSAGE)
    }

    private fun Response<EnvelopeDto<CourierDutySessionDto>>.requireDutyBody(): CourierDutySession {
        if (!isSuccessful) {
            throw HttpException(this)
        }
        return body()?.requireData()?.toDomain()
            ?: throw ParsingException(MISSING_DUTY_MESSAGE)
    }

    private fun <T> EnvelopeDto<T>.requireData(): T =
        data ?: throw ParsingException(MISSING_DATA_MESSAGE)

    private companion object {
        const val CONFLICT_STATUS_CODE = 409
        const val CONFLICT_LOCATION_MESSAGE =
            "Lokasi tidak dapat dikirim karena pengantaran sudah tidak aktif."
        const val CONFLICT_DUTY_MESSAGE =
            "Lokasi tidak dapat dikirim karena sesi bertugas sudah berakhir."
        const val MISSING_DATA_MESSAGE = "Respons backend tidak memuat data yang diminta."
        const val MISSING_ASSIGNMENT_MESSAGE = "Respons backend tidak memuat assignment."
        const val MISSING_DUTY_MESSAGE = "Respons backend tidak memuat sesi bertugas."
        const val MISSING_LOCATION_MESSAGE = "Respons backend tidak memuat lokasi kurir."
        const val MISSING_CASH_RECEIPT_MESSAGE = "Respons backend tidak memuat konfirmasi pembayaran."
    }
}