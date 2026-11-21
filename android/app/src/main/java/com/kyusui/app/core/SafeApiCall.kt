package com.kyusui.app.core

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Satu-satunya titik pemetaan error untuk seluruh call jaringan.
 *
 * Penting: pemetaan ini harus terjadi **di dalam coroutine pemanggil**, bukan di
 * dalam OkHttp interceptor. Interceptor berjalan di thread OkHttp Dispatcher, dan
 * `RuntimeException` yang dilempar dari sana keluar dari
 * `RealCall.AsyncCall.execute()` tanpa tertangkap: OkHttp melakukan
 * `onFailure(IOException("canceled due to ..."))` lalu melempar ulang throwable
 * yang sama. Lemparan itu mencapai uncaught handler thread dan menjatuhkan proses
 * (`FATAL EXCEPTION: OkHttp Dispatcher`), sekaligus menutupi error aslinya
 * dengan pesan "canceled due to" yang menyesatkan.
 *
 * Karena itu `safeApiCall` menangkap apa pun yang Retrofit lempar — termasuk
 * `HttpException` untuk status non-2xx — lalu memetakannya ke exception domain
 * sehingga pemanggil hanya pernah menerima `Result.Success` atau `Result.Failure`.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: NetworkException) {
    Result.failure(e)
} catch (e: HttpException) {
    Result.failure(e.toNetworkException())
} catch (e: UnknownHostException) {
    Result.failure(NoNetworkException(cause = e))
} catch (e: SocketTimeoutException) {
    Result.failure(TimeoutException(cause = e))
} catch (e: IOException) {
    Result.failure(NoNetworkException(cause = e))
} catch (e: Exception) {
    Result.failure(UnknownException(e.message ?: "Terjadi kesalahan tidak diketahui", e))
}

private val defaultErrorParser: ApiErrorParser by lazy { ApiErrorParser() }

/**
 * Memetakan [HttpException] Retrofit ke exception domain.
 *
 * Body error dibaca apa adanya dan diparse dengan [ApiErrorParser] supaya pesan
 * per-field dari backend (Laravel `errors`) tetap utuh sampai UI. Versi lama
 * memakai interceptor yang melempar, sehingga jalur ini praktis tidak pernah
 * dipakai untuk error HTTP sungguhan dan `ValidationException.errors` selalu
 * kosong karena `HttpException.message()` tidak memuat detail field.
 */
fun HttpException.toNetworkException(
    parser: ApiErrorParser = defaultErrorParser
): NetworkException = parser.parse(
    statusCode = code(),
    rawBody = readErrorBody(),
    httpMessage = message()
)

private fun HttpException.readErrorBody(): String? = runCatching {
    response()?.errorBody()?.string()
}.getOrNull()

fun Throwable.userMessage(): String = when (this) {
    is NoNetworkException -> message ?: "Tidak ada koneksi internet"
    is TimeoutException -> message ?: "Permintaan timeout"
    is ServerException -> "Server sedang bermasalah (${statusCode}), coba lagi nanti"
    is UnauthorizedException -> "Sesi berakhir, silakan login kembali"
    is ForbiddenException -> "Akses ditolak"
    is ConflictException -> message ?: "Kondisi sudah berubah"
    is NotFoundException -> "Data tidak ditemukan"
    is ValidationException -> message ?: "Data yang dikirim tidak valid"
    is ParsingException -> message ?: "Gagal memproses respons"
    is UnknownException -> message ?: "Terjadi kesalahan tidak diketahui"
    else -> message ?: "Terjadi kesalahan tidak diketahui"
}
