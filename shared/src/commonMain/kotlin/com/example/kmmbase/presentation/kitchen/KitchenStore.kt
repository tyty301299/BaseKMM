package com.example.kmmbase.presentation.kitchen

import com.example.kmmbase.domain.device.KitchenDevice
import com.example.kmmbase.domain.model.AppError
import com.example.kmmbase.domain.model.DetectedObject
import com.example.kmmbase.domain.model.KitchenStep
import com.example.kmmbase.domain.model.KitchenSteps
import com.example.kmmbase.domain.model.StepVerification
import com.example.kmmbase.domain.model.VerificationStatus
import com.example.kmmbase.domain.model.ZoneBox
import com.example.kmmbase.domain.repository.KitchenRepository
import com.example.kmmbase.domain.repository.VoiceConnector
import com.example.kmmbase.domain.repository.VoiceEvent
import com.example.kmmbase.domain.repository.VoiceSession
import com.example.kmmbase.presentation.common.StoreSubscription
import com.example.kmmbase.presentation.common.subscribeIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class KitchenPhase { READY, WORKING, COMPLETED }
enum class VoiceStatus { OFF, CONNECTING, LISTENING, ERROR }
enum class OutputKind { INFO, SUCCESS, WARNING, ERROR }

/** What the latest action did, always shown to the user. */
data class ActionOutput(val kind: OutputKind, val message: String)

data class KitchenState(
    val phase: KitchenPhase = KitchenPhase.READY,
    val steps: List<KitchenStep> = KitchenSteps.all,
    val stepIndex: Int = 0,
    val voice: VoiceStatus = VoiceStatus.OFF,
    val isChecking: Boolean = false,
    val verification: StepVerification? = null,
    val caption: String = "",
    val error: AppError? = null,
    val beforeImage: String? = null,
    val afterImage: String? = null,
    val output: ActionOutput? = null,
    val targetZone: ZoneBox? = null,
    val misplaced: List<ZoneBox> = emptyList(),
    val objects: List<DetectedObject> = emptyList(),
) {
    val currentStep: KitchenStep get() = steps[stepIndex]

    /** For example "3 cốc, 2 bát". Empty until the first scan returns objects. */
    val objectSummary: String
        get() = objects.groupingBy { it.label }.eachCount().entries.joinToString { "${it.value} ${it.key}" }
    val canCheck: Boolean get() = phase == KitchenPhase.WORKING && !isChecking
}

sealed interface KitchenAction {
    data object Start : KitchenAction

    /** A step is only verified on this action or when the agent hears "Xong rồi". */
    data object Check : KitchenAction
    data object RetryVoice : KitchenAction
    data object Restart : KitchenAction
}

class KitchenStore internal constructor(
    private val repository: KitchenRepository,
    private val connector: VoiceConnector,
    private val device: KitchenDevice,
    dispatcher: CoroutineDispatcher,
    private val frameIntervalMs: Long = 1_000, // The Live API accepts at most one frame per second.
    private val scanIntervalMs: Long = 5_000,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(KitchenState())
    private var voiceJob: Job? = null
    private var checkJob: Job? = null
    private var scanJob: Job? = null
    private var session: VoiceSession? = null

    val state: StateFlow<KitchenState> = mutableState.asStateFlow()
    val currentState: KitchenState get() = mutableState.value

    fun observeState(onChange: (KitchenState) -> Unit): StoreSubscription =
        mutableState.subscribeIn(scope, onChange)

    fun dispatch(action: KitchenAction) {
        when (action) {
            KitchenAction.Start -> start()
            KitchenAction.Check -> check(callId = null)
            KitchenAction.RetryVoice -> retryVoice()
            KitchenAction.Restart -> restart()
        }
    }

    override fun close() {
        scanJob?.cancel()
        stopVoice()
        scope.cancel()
        device.shutdown()
    }

    private fun start() {
        if (mutableState.value.phase != KitchenPhase.READY) return
        val before = device.latestFrameJpegBase64()
        if (before == null) {
            mutableState.update { it.copy(error = AppError.CAMERA, output = noCameraOutput()) }
            return
        }
        mutableState.update {
            it.copy(
                phase = KitchenPhase.WORKING,
                beforeImage = before,
                error = null,
                targetZone = it.currentStep.defaultZone,
                output = ActionOutput(OutputKind.INFO, "Đã bắt đầu. ${it.currentStep.instruction}"),
            )
        }
        startScanning()
        startVoice()
    }

    private fun restart() {
        checkJob?.cancel()
        scanJob?.cancel()
        stopVoice()
        mutableState.value = KitchenState(output = ActionOutput(OutputKind.INFO, "Đã làm lại từ đầu."))
    }

    private fun retryVoice() {
        val current = mutableState.value
        if (current.phase != KitchenPhase.WORKING || current.voice != VoiceStatus.ERROR) return
        mutableState.update { it.copy(output = ActionOutput(OutputKind.INFO, "Đang kết nối lại giọng nói.")) }
        startVoice()
    }

    private fun stopVoice() {
        voiceJob?.cancel()
        voiceJob = null
    }

    private fun startVoice() {
        stopVoice()
        mutableState.update { it.copy(voice = VoiceStatus.CONNECTING, caption = "") }
        voiceJob = scope.launch {
            var active: VoiceSession? = null
            var pumps: Job? = null
            try {
                val connected = connector.connect(SYSTEM_PROMPT)
                active = connected
                session = connected
                connected.events.collect { event ->
                    if (event == VoiceEvent.Ready) pumps = startPumps(connected)
                    handleVoiceEvent(connected, event)
                }
                mutableState.update { it.copy(voice = VoiceStatus.ERROR) }
            } catch (e: CancellationException) {
                mutableState.update { it.copy(voice = VoiceStatus.OFF) }
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(voice = VoiceStatus.ERROR) }
            } finally {
                pumps?.cancel()
                device.stopMicrophone()
                device.stopPlayback()
                active?.close()
                session = null
            }
        }
    }

    /** Streams microphone audio and camera frames until cancelled or the socket fails. */
    private fun startPumps(target: VoiceSession): Job = scope.launch {
        val chunks = Channel<String>(capacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        device.startMicrophone { chunks.trySend(it) }
        launch {
            ignoringSendFailure { for (chunk in chunks) target.sendAudio(chunk) }
        }
        launch {
            ignoringSendFailure {
                while (isActive) {
                    device.latestFrameJpegBase64()?.let { target.sendFrame(it) }
                    delay(frameIntervalMs)
                }
            }
        }
    }

    private suspend fun handleVoiceEvent(target: VoiceSession, event: VoiceEvent) {
        when (event) {
            VoiceEvent.Ready -> {
                mutableState.update { it.copy(voice = VoiceStatus.LISTENING) }
                val step = mutableState.value.currentStep
                target.sendText("Bắt đầu buổi dọn bếp. Bước hiện tại: ${step.instruction}")
            }
            is VoiceEvent.Audio -> device.playAudio(event.pcmBase64)
            is VoiceEvent.Caption -> mutableState.update { it.copy(caption = it.caption + event.text) }
            VoiceEvent.TurnComplete -> Unit
            VoiceEvent.Interrupted -> device.stopPlayback()
            is VoiceEvent.CheckRequested -> {
                mutableState.update { it.copy(caption = "") }
                check(event.callId)
            }
        }
    }

    private fun check(callId: String?) {
        val current = mutableState.value
        if (!current.canCheck) {
            if (current.isChecking) {
                mutableState.update { it.copy(output = ActionOutput(OutputKind.INFO, "Đang kiểm tra, đợi một chút.")) }
            }
            report(callId, statusOnly(if (current.isChecking) "BUSY" else "ALREADY_FINISHED"))
            return
        }
        val frame = device.latestFrameJpegBase64()
        if (frame == null) {
            mutableState.update { it.copy(error = AppError.CAMERA, output = noCameraOutput()) }
            report(callId, statusOnly("NO_IMAGE"))
            return
        }
        mutableState.update {
            it.copy(
                isChecking = true,
                error = null,
                misplaced = emptyList(),
                output = ActionOutput(OutputKind.INFO, "Đang kiểm tra bước ${it.stepIndex + 1}."),
            )
        }
        checkJob = scope.launch {
            val result = try {
                repository.verify(current.currentStep.id, frame)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isChecking = false,
                        error = AppError.NETWORK,
                        output = ActionOutput(OutputKind.ERROR, "Không kết nối được máy chủ. Bước này giữ nguyên, hãy thử lại."),
                    )
                }
                report(callId, statusOnly("ERROR"))
                return@launch
            }
            applyResult(result, frame)
            report(callId, resultForAgent(result))
            when {
                mutableState.value.phase == KitchenPhase.COMPLETED -> scanJob?.cancel()
                result.status == VerificationStatus.DONE -> startScanning()
            }
        }
    }

    private fun applyResult(result: StepVerification, frame: String) {
        mutableState.update { state ->
            val checked = state.copy(isChecking = false, verification = result, misplaced = result.misplaced)
            when {
                result.status != VerificationStatus.DONE ->
                    checked.copy(output = ActionOutput(OutputKind.WARNING, result.message))
                state.stepIndex == state.steps.lastIndex -> checked.copy(
                    phase = KitchenPhase.COMPLETED,
                    afterImage = frame,
                    targetZone = null,
                    misplaced = emptyList(),
                    objects = emptyList(),
                    output = ActionOutput(OutputKind.SUCCESS, "${result.message} Đã hoàn thành cả ${state.steps.size} bước."),
                )
                else -> {
                    val next = state.steps[state.stepIndex + 1]
                    checked.copy(
                        stepIndex = state.stepIndex + 1,
                        targetZone = next.defaultZone,
                        misplaced = emptyList(),
                        output = ActionOutput(OutputKind.SUCCESS, "${result.message} Tiếp theo: ${next.instruction}"),
                    )
                }
            }
        }
    }

    /** Keeps the drawn boxes current while the user moves things. Failures keep the last result. */
    private fun startScanning() {
        scanJob?.cancel()
        val stepIndex = mutableState.value.stepIndex
        val stepId = mutableState.value.currentStep.id
        scanJob = scope.launch {
            while (isActive) {
                if (!mutableState.value.isChecking) scanOnce(stepIndex, stepId)
                delay(scanIntervalMs)
            }
        }
    }

    private suspend fun scanOnce(stepIndex: Int, stepId: String) {
        val frame = device.latestFrameJpegBase64() ?: return
        val scan = try {
            repository.scanScene(stepId, frame)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        mutableState.update {
            if (it.phase == KitchenPhase.WORKING && it.stepIndex == stepIndex) {
                it.copy(targetZone = scan.target ?: it.targetZone, objects = scan.objects)
            } else {
                it
            }
        }
    }

    private fun noCameraOutput() = ActionOutput(OutputKind.ERROR, "Chưa có hình từ camera. Hãy thử lại.")

    private fun resultForAgent(result: StepVerification): Map<String, String> {
        val state = mutableState.value
        return buildMap {
            put("status", result.status.name)
            put("message", result.message)
            if (state.phase == KitchenPhase.COMPLETED) {
                put("next", "Đã xong cả 3 bước. Hãy chúc mừng người dùng.")
            } else if (result.status == VerificationStatus.DONE) {
                put("next", state.currentStep.instruction)
            }
        }
    }

    private fun report(callId: String?, result: Map<String, String>) {
        val target = session ?: return
        scope.launch {
            ignoringSendFailure {
                if (callId != null) {
                    target.sendCheckResult(callId, result)
                } else {
                    // The button bypasses the agent, so tell it what to say.
                    target.sendText("Kết quả kiểm tra từ nút Check, không cần gọi công cụ: $result. Hãy nói kết quả với người dùng.")
                }
            }
        }
    }

    private fun statusOnly(status: String) = mapOf("status" to status)

    // A broken socket ends the event flow, which is where the failure gets reported.
    private suspend fun ignoringSendFailure(send: suspend () -> Unit) {
        try {
            send()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private companion object {
        val SYSTEM_PROMPT = """
            Bạn là KitchenMate, trợ lý giọng nói hướng dẫn người dùng dọn một mặt bàn bếp qua camera.
            Luôn nói tiếng Việt, ngắn gọn, xưng "em" và gọi người dùng là "anh".
            Chỉ hướng dẫn một việc mỗi lần, theo đúng bước được cung cấp, không tự đặt thêm bước.
            Khi người dùng nói "Xong rồi" hoặc ý tương tự, phải gọi công cụ check_current_step.
            Không bao giờ tự kết luận một bước đã xong nếu chưa có kết quả từ công cụ.
            Khi có kết quả: DONE thì khen ngắn và đọc việc trong trường next.
            NOT_DONE thì đọc lại nội dung message để người dùng sửa.
            UNCLEAR, NO_IMAGE thì nói "Em chưa nhìn rõ, anh cho em xem lại".
            ERROR thì xin người dùng thử lại, BUSY thì bảo người dùng đợi một chút.
        """.trimIndent()
    }
}
