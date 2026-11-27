package com.kyusui.app.ui.courier.tracking

import com.kyusui.app.core.ConflictException
import com.kyusui.app.core.ForbiddenException
import com.kyusui.app.core.Result
import com.kyusui.app.core.TrackingSchedule
import com.kyusui.app.core.UnauthorizedException
import com.kyusui.app.domain.model.CourierDeviceLocation
import com.kyusui.app.domain.model.CourierLocationFix
import com.kyusui.app.domain.repository.CourierRepository
import com.kyusui.app.ui.location.DeviceLocationSource
import com.kyusui.app.ui.location.LocationReadResult
import com.kyusui.app.ui.state.SubmitState
import com.kyusui.app.ui.state.toSubmitError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seam pengirim posisi kurir yang dipakai ViewModel, service, dan layar.
 *
 * Di produksi diimplementasikan oleh [CourierLocationShareController]; di unit
 * test diganti [FakeCourierLocationSharing] sehingga delegasi ViewModel bisa
 * diuji di JVM tanpa coroutine lokasi maupun Android framework.
 */
interface CourierLocationSharing {

    val state: StateFlow<CourierLocationShareState>

    /** Mulai pengiriman berkala untuk satu assignment aktif. */
    fun start(assignmentId: String)

    /**
     * Mulai pengiriman berkala untuk sesi bertugas tanpa assignment.
     *
     * Diabaikan bila sudah ada session berjalan (assignment atau bertugas),
     * karena controller memegang satu pengirim untuk seluruh proses.
     */
    fun startDuty()

    /** Hentikan pengiriman dan layanan latar belakangnya. */
    fun stop(reason: TrackingStopReason = TrackingStopReason.USER_REQUESTED)

    /** Hilangkan pesan/error sesaat tanpa menghentikan session. */
    fun clearMessage()

    /** True bila pengiriman sedang berjalan untuk assignment ini. */
    fun isSharingFor(assignmentId: String): Boolean
}

/**
 * State session pengiriman posisi kurir, dimiliki oleh controller singleton.
 *
 * [currentFix] adalah pembacaan perangkat terakhir dan belum tentu sudah
 * diterima server. [lastReportedLocation] adalah nilai yang balik dari
 * `POST .../location`, jadi hanya itu yang boleh ditampilkan sebagai posisi
 * resmi.
 */
data class CourierLocationShareState(
    val sessionAssignmentId: String? = null,
    /**
     * True bila session yang berjalan adalah sesi bertugas (tanpa assignment).
     * Saat true, [sessionAssignmentId] selalu null.
     */
    val isDuty: Boolean = false,
    val isSharing: Boolean = false,
    val stopReason: TrackingStopReason? = null,
    val locationReadStatus: LocationReadStatus = LocationReadStatus.Unknown,
    val submitState: SubmitState = SubmitState.Idle,
    val currentFix: CourierLocationFix? = null,
    val lastReportedLocation: CourierDeviceLocation? = null,
    val message: String? = null
) {
    /** True bila pengiriman berjalan untuk assignment ini. */
    fun isSharingFor(assignmentId: String): Boolean =
        isSharing && sessionAssignmentId == assignmentId

    /** True bila pengiriman sedang berjalan sebagai sesi bertugas. */
    val isOnDuty: Boolean
        get() = isSharing && isDuty
}

/**
 * Controller singleton yang memegang sesi pengiriman posisi kurir untuk
 * seluruh proses.
 *
 * ## Kenapa singleton, dan kenapa Foreground Service
 *
 * Pengiriman posisi adalah tanggung jawab proses, bukan satu layar. Begitu
 * kurir menekan "Mulai Kirim Posisi", posisi harus terus dikirim walaupun
 * kurir pindah layar atau meminimalkan aplikasi sampai delivery selesai.
 * Android mewajibkan kerja terlihat untuk kerja yang berlanjut berarti,
 * sehingga pengiriman dibungkus [CourierLocationService] (foreground service
 * tipe `location`) dengan notifikasi permanen. Batasan ini adalah jawaban
 * yang dipilih untuk `UD-TRACK-04` (tracking saat aplikasi di latar belakang):
 * FGS adalah mekanisme hasil keputusan Batch 2, spec-level `UD-TRACK-04`
 * sendiri tetap UNRESOLVED.
 *
 * ## Satu pengirim, bukan dua
 *
 * Controller memegang satu [loopJob] untuk seluruh proses. Hubungan 1:1 dengan
 * [CourierLocationService] dipastikan oleh guard di [start]: sesi kedua untuk
 * assignment lain memakai loop yang sama; daftar aktif dibaca dari server. Upload berjalan
 * satu per satu: pembacaan yang datang saat upload sebelumnya belum selesai
 * dibuang (`drop-while-submitting`), dan [kotlinx.coroutines.flow.conflate]
 * membatasi buffer sehingga pembacaan yang sudah basi tidak menumpuk.
 *
 * ## Hanya mengirim, tidak menulis status
 *
 * Controller tidak pernah menulis status delivery/business. Kelayakan mengirim
 * dibaca dari Assignment Resource backend lewat `canPostLocation`, dan
 * `POST .../location` mengembalikan posisi yang sah menurut server. Penolakan
 * `401` menghentikan sesi; penolakan assignment diperiksa ulang agar
 * pengantaran lain tetap terlacak.
 */
@Singleton
class CourierLocationShareController @Inject constructor(
    private val locationSource: DeviceLocationSource,
    private val courierRepository: CourierRepository,
    private val trackingSchedule: TrackingSchedule,
    private val serviceLauncher: CourierLocationServiceLauncher
) : CourierLocationSharing {

    /**
     * Scope proses yang sengaja tidak dibatalkan: controller hidup lebih lama
     * dari layar mana pun, dan berhentinya session dikendalikan [stop].
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(CourierLocationShareState())
    override val state: StateFlow<CourierLocationShareState> = _state.asStateFlow()

    /** Kolektor pembacaan lokasi; satu-satunya loop acquisition untuk proses. */
    private var loopJob: Job? = null

    /** Upload yang sedang berjalan; ikut dibatalkan saat session berhenti. */
    private var uploadJob: Job? = null

    override fun start(assignmentId: String) {
        if (assignmentId.isBlank()) return
        beginSession(SessionTarget.Assignment(assignmentId))
    }

    override fun startDuty() {
        beginSession(SessionTarget.Duty)
    }

    /**
     * Membuka satu session pengiriman untuk [target].
     *
     * Guard `isSharing` di sini yang menjaga invariant "satu pengirim": baik
     * assignment maupun sesi bertugas tidak bisa memulai pengiriman kedua
     * selama yang pertama masih berjalan.
     */
    private fun beginSession(target: SessionTarget) {
        val current = _state.value
        if (current.isSharing) return

        val started = when (target) {
            is SessionTarget.Assignment -> serviceLauncher.start(target.id)
            SessionTarget.Duty -> serviceLauncher.startDuty()
        }

        if (!started) {
            // Tanpa foreground service (mis. start dilarang karena aplikasi di
            // latar belakang) tidak boleh ada pengiriman tanpa notifikasi.
            _state.value = failedServiceStartState(target)
            return
        }

        _state.value = startedState(target)

        loopJob = scope.launch {
            // Stream lokasi dapat terputus karena error provider/GMS sementara.
            // Selama sesi masih aktif, pasang ulang stream secara berkala.
            // Hanya ada satu loopJob; guard submitState tetap mencegah upload paralel.
            while (isActive && _state.value.isSharing) {
                try {
                    locationSource
                        .stream(trackingSchedule.courierSubmissionIntervalMillis)
                        .conflate()
                        .collect { result -> handleLocationResult(target, result) }

                    // Flow yang selesai normal juga dicoba ulang selama controller
                    // belum menghentikan sesi berdasarkan izin atau status backend.
                    if (_state.value.isSharing) {
                        _state.update {
                            it.copy(
                                locationReadStatus = LocationReadStatus.Locating,
                                message = LOCATION_STREAM_RETRY_MESSAGE
                            )
                        }
                        delay(LOCATION_STREAM_RETRY_DELAY_MS)
                    }
                } catch (cancelled: CancellationException) {
                    // Berhenti karena pembatalan adalah alur normal; jangan retry.
                    throw cancelled
                } catch (error: Exception) {
                    // Request location updates dapat gagal pada perangkat/provider.
                    // Tampilkan pesan yang jelas, lalu coba lagi tanpa mengklaim
                    // bahwa koordinat sudah berhasil diterima backend.
                    if (_state.value.isSharing) {
                        _state.update {
                            it.copy(
                                locationReadStatus = LocationReadStatus.Unavailable,
                                message = LOCATION_STREAM_RETRY_MESSAGE
                            )
                        }
                        delay(LOCATION_STREAM_RETRY_DELAY_MS)
                    }
                }
            }
        }
    }

    private fun startedState(target: SessionTarget): CourierLocationShareState = when (target) {
        is SessionTarget.Assignment -> CourierLocationShareState(
            sessionAssignmentId = target.id,
            isSharing = true,
            locationReadStatus = LocationReadStatus.Locating
        )

        SessionTarget.Duty -> CourierLocationShareState(
            isDuty = true,
            isSharing = true,
            locationReadStatus = LocationReadStatus.Locating
        )
    }

    private fun failedServiceStartState(target: SessionTarget): CourierLocationShareState {
        // Tanpa foreground service tidak boleh ada pengiriman tanpa notifikasi.
        return when (target) {
            is SessionTarget.Assignment -> CourierLocationShareState(
                sessionAssignmentId = target.id,
                stopReason = TrackingStopReason.USER_REQUESTED,
                message = SERVICE_START_FAILED_MESSAGE
            )

            SessionTarget.Duty -> CourierLocationShareState(
                isDuty = true,
                stopReason = TrackingStopReason.USER_REQUESTED,
                message = SERVICE_START_FAILED_MESSAGE
            )
        }
    }

    override fun stop(reason: TrackingStopReason) {
        val current = _state.value
        if (!current.isSharing) return

        loopJob?.cancel()
        loopJob = null
        uploadJob?.cancel()
        uploadJob = null
        serviceLauncher.stop()

        _state.value = current.copy(
            isSharing = false,
            stopReason = reason,
            submitState = SubmitState.Idle
        )
    }

    override fun clearMessage() {
        _state.update { it.copy(message = null, submitState = if (uploadJob?.isActive == true) it.submitState else SubmitState.Idle) }
    }

    override fun isSharingFor(assignmentId: String): Boolean =
        state.value.isSharingFor(assignmentId)

    private suspend fun handleLocationResult(target: SessionTarget, result: LocationReadResult) {
        when (result) {
            is LocationReadResult.Fix -> acceptFix(target, result.fix)

            LocationReadResult.PermissionMissing -> {
                _state.update {
                    it.copy(
                        locationReadStatus = LocationReadStatus.PermissionDenied,
                        message = PERMISSION_MISSING_MESSAGE
                    )
                }
                stop(TrackingStopReason.PERMISSION_NOT_GRANTED)
            }

            LocationReadResult.ProviderDisabled -> {
                _state.update {
                    it.copy(
                        locationReadStatus = LocationReadStatus.Unavailable,
                        message = PROVIDER_DISABLED_MESSAGE
                    )
                }
                // Keep foreground session; retry after GPS becomes available.
            }

            LocationReadResult.Unavailable -> {
                _state.update {
                    it.copy(
                        locationReadStatus = LocationReadStatus.Unavailable,
                        message = LOCATION_UNAVAILABLE_MESSAGE
                    )
                }
                // Keep foreground session; retry temporary provider failures.
            }
        }
    }

    /**
     * Menerima satu pembacaan dan memulai upload-nya.
     *
     * Kolektor tidak pernah menunda di sini: upload dijalankan di coroutine
     * terpisah supaya pembacaan berikutnya langsung diuji guard
     * `submitState`. Pembacaan yang datang saat upload sebelumnya masih
     * berjalan dibuang, sehingga tidak pernah ada dua upload yang tumpang
     * tindih.
     */
    private fun acceptFix(target: SessionTarget, fix: CourierLocationFix) {
        val current = _state.value
        if (!current.isSharing) return
        if (current.submitState is SubmitState.Submitting || uploadJob?.isActive == true) return

        _state.update {
            it.copy(
                locationReadStatus = LocationReadStatus.Ready,
                currentFix = fix,
                message = null,
                submitState = SubmitState.Submitting
            )
        }

        uploadJob = scope.launch {
            val active = courierRepository.getActiveAssignmentIds()
            if (active is Result.Failure) {
                onUploadFailure(target, active)
                return@launch
            }
            val ids = (active as Result.Success).data
            val anchor = _state.value.sessionAssignmentId?.takeIf { it in ids } ?: ids.firstOrNull()
            val result = if (anchor != null) {
                _state.update { it.copy(sessionAssignmentId = anchor, isDuty = false) }
                courierRepository.updateLocation(anchor, fix)
            } else {
                // The server, not the initial session target, decides whether duty survives.
                when (val duty = courierRepository.getDutyState()) {
                    is Result.Failure -> {
                        onUploadFailure(target, duty)
                        return@launch
                    }
                    is Result.Success -> if (!duty.data.isActive) {
                        stop(if (_state.value.isDuty) TrackingStopReason.DUTY_SESSION_ENDED
                             else TrackingStopReason.DELIVERY_NOT_ACTIVE)
                        return@launch
                    }
                }
                _state.update { it.copy(sessionAssignmentId = null, isDuty = true) }
                courierRepository.sendDutyLocation(fix)
            }
            // An assignment may finish between the list and the upload. Recheck
            // before stopping; another active order must not lose its GPS stream.
            if (anchor != null && result is Result.Failure &&
                (result.exception is ConflictException || result.exception is ForbiddenException)) {
                when (val remaining = courierRepository.getActiveAssignmentIds()) {
                    is Result.Failure -> onUploadFailure(target, remaining)
                    is Result.Success -> {
                        val alternative = remaining.data.firstOrNull { it != anchor }
                        if (alternative != null) {
                            _state.update { it.copy(sessionAssignmentId = alternative, submitState = SubmitState.Idle) }
                        } else {
                            when (val duty = courierRepository.getDutyState()) {
                                is Result.Failure -> onUploadFailure(target, duty)
                                is Result.Success -> if (duty.data.isActive && remaining.data.isEmpty()) {
                                    _state.update { it.copy(sessionAssignmentId = null, isDuty = true, submitState = SubmitState.Idle) }
                                } else {
                                    stop(TrackingStopReason.DELIVERY_NOT_ACTIVE)
                                }
                            }
                        }
                    }
                }
                return@launch
            }

            when (result) {
                is Result.Success -> _state.update {
                    it.copy(
                        submitState = SubmitState.Success(anchor ?: DUTY_SESSION_KEY),
                        // Posisi yang baru diterima server ditampilkan. Nilai
                        // ini hasil server, bukan pembacaan lokal.
                        lastReportedLocation = result.data
                    )
                }

                is Result.Failure -> onUploadFailure(if (anchor == null) SessionTarget.Duty else SessionTarget.Assignment(anchor), result)
            }
        }
    }

    private suspend fun onUploadFailure(target: SessionTarget, failure: Result.Failure) {
        _state.update { it.copy(submitState = failure.toSubmitError()) }

        when (failure.exception) {
            is UnauthorizedException -> {
                stop(TrackingStopReason.SESSION_EXPIRED)
            }

            is ForbiddenException -> {
                // 403: bukan assignment milik kurir. Tidak akan berubah dengan
                // mengirim lagi.
                stop(TrackingStopReason.DELIVERY_NOT_ACTIVE)
            }

            is ConflictException -> {
                // 409 pada assignment: delivery sudah tidak aktif. 409 pada sesi
                // bertugas: tidak ada sesi aktif atau sesi sudah berakhir.
                stop(
                    when (target) {
                        is SessionTarget.Duty -> TrackingStopReason.DUTY_SESSION_ENDED
                        is SessionTarget.Assignment -> TrackingStopReason.DELIVERY_NOT_ACTIVE
                    }
                )
            }

            else -> {
                // Gangguan jaringan, timeout, 5xx, atau penolakan sementara
                // (mis. 422 jarak versus waktu) tidak menghentikan pengiriman:
                // pembacaan berikutnya bisa berhasil.
            }
        }
    }

    /**
     * Sasaran satu session pengiriman.
     *
     * Controller hanya memegang satu session pada satu waktu, jadi bentuk ini
     * yang menentukan endpoint lokasi mana yang dipakai tanpa menggandakan loop
     * acquisition maupun loop upload.
     */
    private sealed interface SessionTarget {
        val key: String

        data class Assignment(val id: String) : SessionTarget {
            override val key: String get() = id
        }

        data object Duty : SessionTarget {
            override val key: String get() = DUTY_SESSION_KEY
        }
    }

    companion object {
        const val PERMISSION_MISSING_MESSAGE =
            "Izin lokasi belum diberikan. Aktifkan izin lokasi untuk mengirim posisi."
        const val PROVIDER_DISABLED_MESSAGE =
            "Layanan lokasi dimatikan. Aktifkan GPS di pengaturan perangkat."
        const val LOCATION_UNAVAILABLE_MESSAGE =
            "Lokasi tidak dapat dibaca di perangkat ini."
        const val SERVICE_START_FAILED_MESSAGE =
            "Pengiriman posisi tidak dapat dimulai karena aplikasi berada di latar belakang."
        /** Pesan saat stream lokasi perlu dipasang ulang setelah gangguan sementara. */
        const val LOCATION_STREAM_RETRY_MESSAGE =
            "Pembacaan GPS terganggu. Aplikasi akan mencoba menyambungkan lokasi kembali."
        /** Jeda retry agar tidak membuat loop cepat ketika provider terus gagal. */
        const val LOCATION_STREAM_RETRY_DELAY_MS = 5_000L
        /** Kunci session untuk sesi bertugas, dipakai sebagai submit key. */
        const val DUTY_SESSION_KEY = "duty"
    }
}

/** Status pembacaan lokasi perangkat, ditampilkan di layar. */
enum class LocationReadStatus {
    Unknown,
    Locating,
    Ready,
    PermissionDenied,
    Unavailable
}

/**
 * Alasan tracking dihentikan.
 *
 * Dipisahkan supaya layar bisa menjelaskan kenapa posisi tidak lagi dikirim,
 * bukan hanya diam saja. Enumerasi ini dipakai bersama oleh controller,
 * ViewModel, dan layar.
 */
enum class TrackingStopReason {
    USER_REQUESTED,
    DELIVERY_NOT_ACTIVE,
    PERMISSION_NOT_GRANTED,
    PROVIDER_DISABLED,
    LOCATION_UNAVAILABLE,
    SESSION_EXPIRED,

    /** Sesi bertugas berakhir di server; pengiriman lokasi dihentikan. */
    DUTY_SESSION_ENDED
}
/** Reconcile completion against the server; never stop other active deliveries. */
internal suspend fun reconcileCourierTracking(
    repository: CourierRepository,
    sharing: CourierLocationSharing,
    reason: TrackingStopReason = TrackingStopReason.DELIVERY_NOT_ACTIVE
) {
    if (!sharing.state.value.isSharing) return
    when (val active = repository.getActiveAssignmentIds()) {
        is Result.Failure -> {
            if (active.exception is UnauthorizedException) sharing.stop(TrackingStopReason.SESSION_EXPIRED)
            return
        }
        is Result.Success -> if (active.data.isNotEmpty()) return
    }
    when (val duty = repository.getDutyState()) {
        is Result.Failure -> if (duty.exception is UnauthorizedException) sharing.stop(TrackingStopReason.SESSION_EXPIRED)
        is Result.Success -> if (!duty.data.isActive) sharing.stop(reason)
    }
}
