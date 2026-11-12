package com.example.kmmbase.presentation.guide

import com.example.kmmbase.domain.model.*

data class GuideState(
    val connection: SocketStatus = SocketStatus.CONNECTING,
    val phase: SessionPhase = SessionPhase.WAITING_FOR_START,
    val skillName: String = "",
    val step: SessionStep? = null,
    val tasks: List<SessionTask> = emptyList(),
    val spokenLine: String = "",
    val hint: String = "",
    val judging: Boolean = false,
    val checkStatus: String = "waiting",
    val manualAdvanceUsed: Boolean = false,
    val latencyMs: Long = 0,
    val imageMaxSide: Int = 448,
    val error: String? = null,
    val debugControls: Boolean = false,
    val speechAvailable: Boolean = true,
) {
    val completed: Boolean get() = phase == SessionPhase.COMPLETED
    val running: Boolean get() = phase == SessionPhase.GUIDING
}
