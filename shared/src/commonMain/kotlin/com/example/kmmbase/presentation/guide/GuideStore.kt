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
    private var sessionScope: CoroutineScope? = null
    private var frameJob: Job? = null
    private var closed = false

    fun dispatch(action: GuideAction) {
        if (closed) return
        trace.event("action", "type=${action::class.simpleName}")
        when (action) {
            is GuideAction.Start -> start(action.skillId)
            GuideAction.Stop -> stop()
            is GuideAction.SpeechAvailable -> mutableState.update { it.copy(speechAvailable = action.value) }
            GuideAction.RepeatInstruction -> {
                val instruction = currentState.step?.instruction?.takeIf { currentState.running && it.isNotBlank() }
                if (instruction != null) {
                    stopSpeaking()
                    speak(instruction)
                    mutableState.update { it.copy(spokenLine = instruction) }
                }
            }
            is GuideAction.Step -> {
                if (socket != null && currentState.running && currentState.connection == SocketStatus.OPEN) {
                    mutableState.update { it.copy(manualAdvanceUsed = true) }
                    val live = socket
                    sessionScope?.launch { live?.stepManually(action.forward) }
                }
            }
            is GuideAction.ShowDebugControls ->
                mutableState.update { it.copy(debugControls = action.visible) }
            GuideAction.DismissError -> mutableState.update { it.copy(error = null) }
        }
    }

    private fun start(skillId: String) {
        if (socket != null) return
        lastSpokenEpoch = -1L
        lastSentAt = 0L
        mutableState.value = GuideState(speechAvailable = currentState.speechAvailable)
        if (skillId == "shadow-builtin-goi-qua-v1") {
            val preparation = "Chuẩn bị hộp quà, giấy gói, băng dính và ruy băng. Đặt điện thoại cố định để camera nhìn rõ hộp và hai tay."
            mutableState.update { it.copy(spokenLine = preparation) }
            stopSpeaking()
            speak(preparation)
        }
        val live = try { socketFactory() } catch (_: Exception) {
            mutableState.update { it.copy(connection = SocketStatus.CLOSED, error = "Chưa kết nối được hướng dẫn. Hãy thử lại.", checkStatus = "error") }
            return
        }
        socket = live
        val work = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        sessionScope = work
        pump = work.launch {
            live.events.collect { event -> handle(event) }
        }
        work.launch {
            // The socket may still be connecting; start() is replayed by the socket once it opens.
            live.start(skillId)
        }
        ticker = work.launch {
            while (isActive) {
                delay(250)
                if (currentState.running && nowMs() - lastSentAt >= PERIODIC_MS) send("interval")
            }
        }
    }

    private fun stop() {
        stopSpeaking()
        sessionScope?.cancel(); sessionScope = null
        frameJob = null
        sending = false
        ticker?.cancel(); ticker = null
        val live = socket
        socket = null
        pump?.cancel(); pump = null
        // Closing the control socket destroys the backend session, even if this screen's scope is cancelled next.
        live?.close()
        mutableState.update {
            it.copy(connection = SocketStatus.CLOSED, phase = SessionPhase.WAITING_FOR_START, step = null, spokenLine = "", hint = "", judging = false, checkStatus = "waiting")
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
                        checkStatus = if (event.step?.id != previousStep) "waiting" else it.checkStatus,
                        judging = if (event.step?.id != previousStep) false else it.judging,
                    )
                }
                if (event.phase == SessionPhase.COMPLETED) {
                    ticker?.cancel(); ticker = null
                    frameJob?.cancel(); frameJob = null; sending = false
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
                if (event.stale || (event.stepId.isNotBlank() && event.stepId != currentState.step?.id)) return
                trace.event("vision_result", "confirming=${event.confirming} done=${event.done} latency_ms=${event.latencyMs}")
                mutableState.update {
                    it.copy(judging = false, latencyMs = event.latencyMs,
                        checkStatus = if (event.confirming) "confirming" else if (event.done) "done" else "not_done",
                        error = null, hint = event.hint.ifBlank { it.hint })
                }
                if (event.confirming) send("confirm", force = true)
            }

            is SessionEvent.Failure -> mutableState.update { it.copy(judging = false, checkStatus = "error", error = event.message) }
        }
    }

    private fun send(trigger: String, force: Boolean = false) {
        if (sending || !currentState.running || currentState.connection != SocketStatus.OPEN) return
        val live = socket ?: return
        val work = sessionScope ?: return
        val stepId = currentState.step?.id
        if (!force && nowMs() - lastSentAt < MIN_GAP_MS) return
        sending = true
        frameJob = work.launch {
            try {
                val image = captureFrame(currentState.imageMaxSide)
                currentCoroutineContext().ensureActive()
                if (socket !== live || !currentState.running || currentState.step?.id != stepId) return@launch
                if (image.isNullOrBlank()) {
                    trace.event("frame_unavailable")
                    mutableState.update { it.copy(judging = false, checkStatus = "no_frame") }
                    return@launch
                }
                trace.event("frame_dispatch", "trigger=$trigger forced=$force")
                lastSentAt = nowMs()
                mutableState.update { it.copy(judging = true) }
                live.sendFrame(image, lastSentAt, trigger)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                trace.failure("frame_failed", error)
                mutableState.update { it.copy(judging = false, checkStatus = "error", error = "Chưa gửi được ảnh. Hãy kiểm tra kết nối.") }
            } finally {
                if (socket === live) sending = false
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        trace.event("close")
        stop()
        scope.cancel()
    }
}
