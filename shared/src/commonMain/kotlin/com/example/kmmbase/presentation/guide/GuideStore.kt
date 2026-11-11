package com.example.kmmbase.presentation.guide

import com.example.kmmbase.platform.ShadowTrace
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.ShadowSessionSocket
import com.example.kmmbase.presentation.common.StoreSubscription
import com.example.kmmbase.presentation.common.subscribeIn
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Cadence mirrors the web client: a frame every 1.5 s, never closer together than 1.2 s. */
private const val PERIODIC_MS = 1_500L
private const val MIN_GAP_MS = 1_200L

data class GuideState(
    val connection: SocketStatus = SocketStatus.CONNECTING,
    val phase: SessionPhase = SessionPhase.WAITING_FOR_START,
    val skillName: String = "",
    val step: SessionStep? = null,
    val tasks: List<SessionTask> = emptyList(),
    val spokenLine: String = "",
    val hint: String = "",
    val judging: Boolean = false,
    val latencyMs: Long = 0,
    val imageMaxSide: Int = 448,
    val error: String? = null,
    val debugControls: Boolean = false,
) {
    val completed: Boolean get() = phase == SessionPhase.COMPLETED
    val running: Boolean get() = phase == SessionPhase.GUIDING
}

sealed interface GuideAction {
    data class Start(val skillId: String) : GuideAction
    data object Stop : GuideAction
    data class Step(val forward: Boolean) : GuideAction
    data class ShowDebugControls(val visible: Boolean) : GuideAction
    data object DismissError : GuideAction
}

/**
 * Drives one guide session: paces camera frames, and speaks lines through [speak].
 *
 * Two behaviours are load-bearing and easy to get wrong:
 * - `vision.result.confirming` means the server saw a "done" frame and will judge the next one
 *   immediately, so we must send one right away instead of waiting out the usual gap. Honouring
 *   this is what makes a step confirm in ~0.9 s instead of ~3.9 s.
 * - `speech.epoch` orders spoken lines. A line whose epoch is not newer than the last one spoken is
 *   stale and must be dropped; a newer one interrupts whatever is still being read aloud.
 */
class GuideStore(
    private val socketFactory: () -> ShadowSessionSocket,
    private val captureFrame: suspend (maxSide: Int) -> String?,
    private val speak: (text: String) -> Unit,
    private val stopSpeaking: () -> Unit,
    private val nowMs: () -> Long,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val trace = ShadowTrace("Guide")
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(GuideState())
    val state: StateFlow<GuideState> = mutableState.asStateFlow()
    val currentState: GuideState get() = mutableState.value

    fun observeState(onChange: (GuideState) -> Unit): StoreSubscription = mutableState.subscribeIn(scope, onChange)

    private var socket: ShadowSessionSocket? = null
    private var pump: Job? = null
    private var ticker: Job? = null
    private var lastSentAt = 0L
    private var lastSpokenEpoch = -1L
    private var sending = false

    fun dispatch(action: GuideAction) {
        trace.event("action", "type=${action::class.simpleName}")
        when (action) {
            is GuideAction.Start -> start(action.skillId)
            GuideAction.Stop -> stop()
            is GuideAction.Step -> scope.launch { socket?.stepManually(action.forward) }
            is GuideAction.ShowDebugControls ->
                mutableState.update { it.copy(debugControls = action.visible) }
            GuideAction.DismissError -> mutableState.update { it.copy(error = null) }
        }
    }

    private fun start(skillId: String) {
        if (socket != null) return
        lastSpokenEpoch = -1L
        val live = socketFactory()
        socket = live
        pump = scope.launch {
            live.events.collect { event -> handle(event) }
        }
        scope.launch {
            // The socket may still be connecting; start() is replayed by the socket once it opens.
            live.start(skillId)
        }
        ticker = scope.launch {
            while (isActive) {
                delay(250)
                if (currentState.running && nowMs() - lastSentAt >= PERIODIC_MS) send("interval")
            }
        }
    }

    private fun stop() {
        stopSpeaking()
        ticker?.cancel(); ticker = null
        val live = socket
        socket = null
        pump?.cancel(); pump = null
        scope.launch {
            try { live?.stop() } finally { live?.close() }
        }
        mutableState.update {
            it.copy(phase = SessionPhase.WAITING_FOR_START, step = null, spokenLine = "", hint = "", judging = false)
        }
    }

    private suspend fun handle(event: SessionEvent) {
        trace.event("received", "type=${event::class.simpleName}")
        when (event) {
            is SessionEvent.Connection -> {
                trace.event("connection", "status=${event.status}")
                mutableState.update { it.copy(connection = event.status) }
            }

            is SessionEvent.Ready -> mutableState.update { it.copy(imageMaxSide = event.imageMaxSide) }

            is SessionEvent.Hud -> {
                trace.event("hud", "phase=${event.phase} step=${event.step?.index} total=${event.step?.total}")
                val previousStep = currentState.step?.id
                mutableState.update {
                    it.copy(
                        phase = event.phase, skillName = event.skillName,
                        step = event.step, tasks = event.tasks,
                        // A new step invalidates the previous step's hint.
                        hint = if (event.step?.id != previousStep) "" else it.hint,
                    )
                }
                if (event.phase == SessionPhase.COMPLETED) {
                    ticker?.cancel(); ticker = null
                } else if (event.phase == SessionPhase.GUIDING && event.step?.id != previousStep) {
                    send("interval", force = true)
                }
            }

            is SessionEvent.Speech -> {
                if (event.epoch <= lastSpokenEpoch) {
                    trace.event("speech_stale", "epoch=${event.epoch} last=$lastSpokenEpoch")
                    return
                }
                trace.event("speech_play", "epoch=${event.epoch}")
                lastSpokenEpoch = event.epoch
                stopSpeaking()
                speak(event.text)
                mutableState.update { it.copy(spokenLine = event.text) }
            }

            is SessionEvent.VisionResult -> {
                trace.event("vision_result", "confirming=${event.confirming} done=${event.done} latency_ms=${event.latencyMs}")
                mutableState.update {
                    it.copy(judging = false, latencyMs = event.latencyMs,
                        hint = event.hint.ifBlank { it.hint })
                }
                if (event.confirming) send("confirm", force = true)
            }

            is SessionEvent.Failure -> mutableState.update { it.copy(judging = false, error = event.message) }
        }
    }

    private fun send(trigger: String, force: Boolean = false) {
        if (sending || !currentState.running) return
        if (!force && nowMs() - lastSentAt < MIN_GAP_MS) return
        sending = true
        scope.launch {
            try {
                val image = captureFrame(currentState.imageMaxSide)
                if (image.isNullOrBlank()) {
                    trace.event("frame_unavailable")
                    return@launch
                }
                trace.event("frame_dispatch", "trigger=$trigger forced=$force")
                lastSentAt = nowMs()
                mutableState.update { it.copy(judging = true) }
                socket?.sendFrame(image, lastSentAt, trigger)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                trace.failure("frame_failed", error)
                mutableState.update { it.copy(judging = false) }
            } finally {
                sending = false
            }
        }
    }

    override fun close() {
        trace.event("close")
        stopSpeaking()
        scope.cancel()
        socket?.close()
    }
}
