package com.kyusui.app.ui.courier.tracking

import com.kyusui.app.ui.state.SubmitState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pengganti [CourierLocationShareController] untuk unit test JVM.
 *
 * Controller produksi memakai Fused Location Provider dan Android framework,
 * jadi ViewModel-ViewModel yang menerima [CourierLocationSharing] diuji dengan
 * fake ini: start/stop tercatat, state bisa dipublikasikan secara manual lewat
 * [publish], dan hal-hal yang hanya bisa diuji tanpa framework tetap terbuka.
 */
class FakeCourierLocationSharing : CourierLocationSharing {

    private val _state = MutableStateFlow(CourierLocationShareState())
    override val state: StateFlow<CourierLocationShareState> = _state.asStateFlow()

    /** Assignment yang pernah diminta start, urut. */
    val started = mutableListOf<String>()

    /** Berapa kali sesi bertugas diminta start. */
    var dutyStartCount: Int = 0
        private set

    /** Alasan berhenti yang pernah diminta, urut. */
    val stoppedReasons = mutableListOf<TrackingStopReason>()

    var clearMessageCount: Int = 0
        private set

    override fun start(assignmentId: String) {
        if (assignmentId.isBlank()) return
        if (_state.value.isSharing) return
        started += assignmentId
        _state.value = CourierLocationShareState(
            sessionAssignmentId = assignmentId,
            isSharing = true,
            locationReadStatus = LocationReadStatus.Locating
        )
    }

    override fun startDuty() {
        if (_state.value.isSharing) return
        dutyStartCount++
        _state.value = CourierLocationShareState(
            isDuty = true,
            isSharing = true,
            locationReadStatus = LocationReadStatus.Locating
        )
    }

    override fun stop(reason: TrackingStopReason) {
        val current = _state.value
        if (!current.isSharing) return
        stoppedReasons += reason
        _state.value = current.copy(
            isSharing = false,
            stopReason = reason,
            submitState = SubmitState.Idle
        )
    }

    override fun clearMessage() {
        clearMessageCount++
        _state.update { it.copy(message = null, submitState = SubmitState.Idle) }
    }

    override fun isSharingFor(assignmentId: String): Boolean =
        state.value.isSharingFor(assignmentId)

    /** Menyuntikkan state seolah-olah controller produksi yang memublikasikannya. */
    fun publish(share: CourierLocationShareState) {
        _state.value = share
    }
}