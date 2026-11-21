package com.kyusui.app.data.api

import android.util.Log
import com.kyusui.app.BuildConfig
import com.kyusui.app.core.ApiErrorParser
import com.kyusui.app.core.ValidationException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor diagnostik untuk response non-2xx.
 *
 * Aturan kontrak interceptor Berkah Water: **selalu mengembalikan [Response] dan
 * tidak pernah melempar exception**. Interceptor lama (`ResponseValidationInterceptor`)
 * melanggar aturan ini dengan melempar `ValidationException` dari dalam chain,
 * dan itu yang menyebabkan `FATAL EXCEPTION: OkHttp Dispatcher` pada setiap
 * HTTP 422 dari login dan register.
 *
 * Interceptor ini hanya mengamati. Response diteruskan apa adanya ke Retrofit,
 * yang melempar `retrofit2.HttpException`; pemetaan ke exception domain
 * dikerjakan di dalam coroutine oleh `com.kyusui.app.core.safeApiCall`.
 *
 * Yang dicatat hanya data aman: status HTTP, path endpoint tanpa query string,
 * nama field yang gagal divalidasi, dan pesan ringkas dari server. Body request,
 * nilai field, password, token, dan header `Authorization` tidak pernah masuk ke
 * sini. Pada build release `isEnabled` bernilai `false`, sehingga tidak ada
 * sensitive logging yang tertinggal di produksi.
 */
class ApiFailureLogInterceptor(
    private val errorParser: ApiErrorParser = ApiErrorParser(),
    private val isEnabled: Boolean = BuildConfig.DEBUG,
    private val log: (String) -> Unit = { message -> Log.w(TAG, message) }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (isEnabled && !response.isSuccessful) {
            log(describe(response))
        }

        return response
    }

    private fun describe(response: Response): String {
        val status = response.code
        val path = response.request.url.encodedPath
        val rawBody = runCatching {
            response.peekBody(MAX_ERROR_BODY_BYTES).string()
        }.getOrNull()

        val exception = errorParser.parse(
            statusCode = status,
            rawBody = rawBody,
            httpMessage = response.message
        )

        val fields = (exception as? ValidationException)?.errors?.keys?.sorted().orEmpty()
        return "HTTP $status ${exception::class.simpleName} path=$path " +
            "fields=$fields message=${exception.message}"
    }

    private companion object {
        const val TAG = "KyusuiApi"
        const val MAX_ERROR_BODY_BYTES = 4L * 1024L
    }
}
