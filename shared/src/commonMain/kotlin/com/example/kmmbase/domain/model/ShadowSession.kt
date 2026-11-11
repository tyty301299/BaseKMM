package com.example.kmmbase.domain.model

/** Phases the backend reports in `hud.state`. */
enum class SessionPhase { WAITING_FOR_START, GUIDING, COMPLETED, ERROR }

data class SessionTask(val id: String, val text: String, val completed: Boolean)

data class SessionStep(
    val id: String,
    val index: Int,
    val total: Int,
    val instruction: String,
    val tip: String,
    val warning: String,
    val referenceImageUrl: String,
)

/** Messages the server pushes over `/session/control`. */
sealed interface SessionEvent {
    /** `imageMaxSide` is the size the server downscales to; sending larger only wastes bandwidth. */
    data class Ready(val sessionId: String, val imageMaxSide: Int) : SessionEvent

    data class Hud(
        val phase: SessionPhase,
        val skillName: String,
        val tasks: List<SessionTask>,
        val activeTaskId: String,
        val step: SessionStep?,
        val speechEpoch: Long,
    ) : SessionEvent

    /** Spoken line; `epoch` drives barge-in so a new line interrupts a stale one. */
    data class Speech(val text: String, val epoch: Long, val kind: String) : SessionEvent

    /**
     * Result of judging one frame. When [confirming] is true the server already saw a "done"
     * frame and will judge the next one without its usual gap, so the client must send one now.
     */
    data class VisionResult(val confirming: Boolean, val latencyMs: Long, val done: Boolean, val hint: String) : SessionEvent

    data class Failure(val message: String, val code: String) : SessionEvent

    data class Connection(val status: SocketStatus) : SessionEvent
}

enum class SocketStatus { CONNECTING, OPEN, CLOSED }
