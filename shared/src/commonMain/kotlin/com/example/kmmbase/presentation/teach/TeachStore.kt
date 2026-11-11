package com.example.kmmbase.presentation.teach

import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.ShadowRepository
import com.example.kmmbase.presentation.common.StoreSubscription
import com.example.kmmbase.presentation.common.subscribeIn
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** The compiler accepts at most 64 frames and 600 transcript lines. */
private const val MAX_FRAMES = 64
private const val MAX_TRANSCRIPT_LINES = 600
private const val KEYFRAME_INTERVAL_MS = 2_000L

enum class TeachPhase { IDLE, RECORDING, COMPILING, REVIEW, SAVED }

data class TeachState(
    val phase: TeachPhase = TeachPhase.IDLE,
    val title: String = "",
    val teacher: String = "",
    val notes: String = "",
    val frameCount: Int = 0,
    val lines: List<TranscriptLine> = emptyList(),
    val liveTranscript: String = "",
    val elapsedMs: Long = 0,
    /** "learning" then "verifying", polled from the server for the two-phase wait screen. */
    val compilePhase: String = "",
    val draft: TeachDraft? = null,
    val savedSkillId: String? = null,
    val error: String? = null,
    val speechAvailable: Boolean = true,
) {
    val canCompile: Boolean get() = frameCount > 0 && title.isNotBlank() &&
        (lines.isNotEmpty() || notes.isNotBlank())
}

sealed interface TeachAction {
    data object StartRecording : TeachAction
    data object StopRecording : TeachAction
    data class Title(val value: String) : TeachAction
    data class Teacher(val value: String) : TeachAction
    data class Notes(val value: String) : TeachAction
    data class SpeechAvailable(val value: Boolean) : TeachAction
    /** A finished sentence from speech recognition, timestamped on the recording clock. */
    data class Sentence(val t0: Double, val t1: Double, val text: String) : TeachAction
    data class PartialSentence(val text: String) : TeachAction
    data object Compile : TeachAction
    data object CancelCompile : TeachAction
    data class EditStep(val index: Int, val instruction: String) : TeachAction
    data object Save : TeachAction
    data object Reset : TeachAction
}

/**
 * Records a demonstration and turns it into a skill.
 *
 * The backend never sees video: it takes keyframes plus a timestamped transcript. Frames are kept
 * every ~2 s and right after each spoken sentence, because a sentence usually ends on the result it
 * describes. When the transcript overflows we drop the oldest frames rather than the newest, so the
 * end state of the task always survives.
 */
class TeachStore(
    private val repository: () -> ShadowRepository?,
    private val captureFrame: suspend (maxSide: Int) -> String?,
    private val nowMs: () -> Long,
    private val newId: () -> String,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(TeachState())
    val state: StateFlow<TeachState> = mutableState.asStateFlow()
    val currentState: TeachState get() = mutableState.value

    fun observeState(onChange: (TeachState) -> Unit): StoreSubscription = mutableState.subscribeIn(scope, onChange)

    private val frames = mutableListOf<TeachFrame>()
    private var startedAt = 0L
    private var sampler: Job? = null
    private var compileJob: Job? = null
    private var progressJob: Job? = null
    private var jobId = ""

    fun dispatch(action: TeachAction) {
        when (action) {
            TeachAction.StartRecording -> startRecording()
            TeachAction.StopRecording -> stopRecording()
            is TeachAction.Title -> if (action.value.length <= 200) {
                mutableState.update { it.copy(title = action.value) }
            }
            is TeachAction.Teacher -> if (action.value.length <= 100) {
                mutableState.update { it.copy(teacher = action.value) }
            }
            is TeachAction.Notes -> if (action.value.length <= 4_000) {
                mutableState.update { it.copy(notes = action.value) }
            }
            is TeachAction.SpeechAvailable -> mutableState.update { it.copy(speechAvailable = action.value) }
            is TeachAction.Sentence -> addSentence(action)
            is TeachAction.PartialSentence -> mutableState.update { it.copy(liveTranscript = action.text) }
            TeachAction.Compile -> compile()
            TeachAction.CancelCompile -> {
                compileJob?.cancel(); progressJob?.cancel()
                mutableState.update { it.copy(phase = TeachPhase.IDLE, compilePhase = "") }
            }
            is TeachAction.EditStep -> editStep(action)
            TeachAction.Save -> save()
            TeachAction.Reset -> reset()
        }
    }

    private fun startRecording() {
        if (currentState.phase == TeachPhase.RECORDING) return
        frames.clear()
        startedAt = nowMs()
        jobId = newId().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40)
        mutableState.update {
            it.copy(phase = TeachPhase.RECORDING, frameCount = 0, lines = emptyList(),
                liveTranscript = "", elapsedMs = 0, draft = null, savedSkillId = null, error = null)
        }
        sampler = scope.launch {
            keep("start")
            while (isActive) {
                delay(KEYFRAME_INTERVAL_MS)
                mutableState.update { it.copy(elapsedMs = nowMs() - startedAt) }
                keep("interval")
            }
        }
    }

    private fun stopRecording() {
        if (currentState.phase != TeachPhase.RECORDING) return
        sampler?.cancel(); sampler = null
        scope.launch {
            keep("end")
            mutableState.update { it.copy(phase = TeachPhase.IDLE, elapsedMs = nowMs() - startedAt) }
        }
    }

    private fun addSentence(action: TeachAction.Sentence) {
        val text = action.text.trim()
        if (text.isEmpty()) return
        mutableState.update {
            val lines = (it.lines + TranscriptLine(action.t0, action.t1, text)).takeLast(MAX_TRANSCRIPT_LINES)
            it.copy(lines = lines, liveTranscript = "")
        }
        // A sentence usually ends on the result it describes, so this is a good reference frame.
        if (currentState.phase == TeachPhase.RECORDING) scope.launch { keep("speech") }
    }

    private suspend fun keep(reason: String) {
        val image = captureFrame(512) ?: return
        val at = (nowMs() - startedAt) / 1000.0
        frames += TeachFrame(at, image, reason)
        if (frames.size > MAX_FRAMES) {
            // Thin out the middle: the opening and the end state matter most to the compiler.
            val keep = buildList {
                add(frames.first())
                addAll(frames.drop(1).dropLast(1).filterIndexed { index, _ -> index % 2 == 0 })
                add(frames.last())
            }.takeLast(MAX_FRAMES)
            frames.clear()
            frames += keep
        }
        mutableState.update { it.copy(frameCount = frames.size) }
    }

    private fun compile() {
        val repo = repository() ?: return fail("Chưa kết nối máy chủ SHADOW.")
        if (!currentState.canCompile) {
            return fail("Cần tên kỹ năng và ít nhất một câu mô tả hoặc ghi chú.")
        }
        mutableState.update { it.copy(phase = TeachPhase.COMPILING, compilePhase = "learning", error = null) }
        val request = TeachRequest(
            title = currentState.title.trim(),
            teacherName = currentState.teacher.trim(),
            transcript = currentState.lines,
            frames = frames.toList(),
            notes = currentState.notes.trim(),
            jobId = jobId,
        )
        progressJob = scope.launch {
            while (isActive) {
                delay(1_500)
                val phase = runCatching { repo.teachProgress(jobId) }.getOrNull() ?: continue
                if (phase.isNotBlank() && phase != "unknown") {
                    mutableState.update { it.copy(compilePhase = phase) }
                }
            }
        }
        compileJob = scope.launch {
            try {
                val draft = repo.compileSkill(request)
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(phase = TeachPhase.REVIEW, draft = draft, compilePhase = "") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(phase = TeachPhase.IDLE, compilePhase = "") }
                fail(error.message ?: "AI chưa học được kỹ năng. Thử lại nhé.")
            } finally {
                progressJob?.cancel()
            }
        }
    }

    private fun editStep(action: TeachAction.EditStep) {
        val draft = currentState.draft ?: return
        if (action.index !in draft.steps.indices || action.instruction.length > 250) return
        val steps = draft.steps.toMutableList()
        steps[action.index] = steps[action.index].copy(instruction = action.instruction)
        mutableState.update { it.copy(draft = draft.copy(steps = steps)) }
    }

    private fun save() {
        val repo = repository() ?: return fail("Chưa kết nối máy chủ SHADOW.")
        val draft = currentState.draft ?: return
        scope.launch {
            try {
                val id = repo.saveSkill(draft.withEditedInstructions())
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(phase = TeachPhase.SAVED, savedSkillId = id) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                fail(error.message ?: "Chưa lưu được kỹ năng. Thử lại nhé.")
            }
        }
    }

    private fun reset() {
        sampler?.cancel(); compileJob?.cancel(); progressJob?.cancel()
        frames.clear()
        mutableState.value = TeachState(speechAvailable = currentState.speechAvailable)
    }

    private fun fail(message: String) {
        mutableState.update { it.copy(error = message) }
    }

    override fun close() { scope.cancel() }
}

/**
 * Folds edited instructions back into the raw skill JSON, which is what the server expects on PUT.
 * Only `instruction` is editable here, so the rest of the compiled skill passes through untouched.
 */
internal fun TeachDraft.withEditedInstructions(): TeachDraft {
    val root = kotlinx.serialization.json.Json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
        ?: return this
    val original = root["steps"] as? kotlinx.serialization.json.JsonArray ?: return this
    val patched = kotlinx.serialization.json.buildJsonArray {
        original.forEachIndexed { index, element ->
            val step = element as? kotlinx.serialization.json.JsonObject
            val edited = steps.getOrNull(index)
            if (step == null || edited == null) {
                add(element)
            } else {
                add(kotlinx.serialization.json.JsonObject(
                    step + ("instruction" to kotlinx.serialization.json.JsonPrimitive(edited.instruction))))
            }
        }
    }
    val updated = kotlinx.serialization.json.JsonObject(root + ("steps" to patched))
    return copy(raw = updated.toString())
}
