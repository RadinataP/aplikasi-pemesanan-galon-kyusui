package com.kyusui.app.ui.state

import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.ConflictException
import com.kyusui.app.core.NotFoundException
import com.kyusui.app.core.Result
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.core.userMessage
import com.kyusui.app.domain.model.PaginatedResult

/**
 * Pemetaan hasil repository ke state UI.
 *
 * Dipakai bersama oleh Customer dan Owner karena bentuk error yang arriving dari
 * backend sama untuk semua role. `isUnauthorized` dan `isRetryable` ditandai
 * terpisah supaya layer UI bisa mengarahkan pengguna kembali ke login saat sesi
 * sudah tidak valid, bukan sekadar menampilkan pesan error lalu mencoba reload
 * terus-menerus.
 *
 * Pemetaan ini hanya mengubah Exception menjadi state UI. Pemetaan ini tidak
 * pernah mengubah nilai bisnis: status order dan payment tetap dibaca apa adanya
 * dari backend.
 */

internal fun Result.Failure.toLoadError(): LoadState.Error = LoadState.Error(
    message = exception.userMessage(),
    isUnauthorized = exception is UnauthorizedException,
    isRetryable = exception !is UnauthorizedException && exception !is ForbiddenException,
    isNotFound = exception is NotFoundException
)

internal fun Result.Failure.toSubmitError(): SubmitState.Error = SubmitState.Error(
    message = exception.userMessage(),
    isUnauthorized = exception is UnauthorizedException,
    isRetryable = exception !is UnauthorizedException &&
        exception !is ForbiddenException &&
        exception !is ConflictException
)

/**
 * Memetakan list datar, dengan memisahkan kasus "sukses tapi kosong" dari
 * "sukses dengan data".
 */
internal fun <T> List<T>.toListLoadState(emptyMessage: String): LoadState<List<T>> =
    if (isEmpty()) LoadState.Empty(emptyMessage) else LoadState.Success(this)

/**
 * Memetakan halaman ber-paginasi menjadi list datar. Penanda `hasMore`
 * tetap dibaca terpisah oleh ViewModel untuk infinite scroll.
 */
internal fun <T> Result<PaginatedResult<T>>.toPagedListLoadState(
    emptyMessage: String
): LoadState<List<T>> = when (this) {
    is Result.Success -> data.data.toListLoadState(emptyMessage)
    is Result.Failure -> toLoadError()
}