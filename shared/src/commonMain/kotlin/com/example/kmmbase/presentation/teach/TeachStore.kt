package com.example.kmmbase.presentation.teach

import com.example.kmmbase.platform.ShadowTrace
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.TeachRepository
import com.example.kmmbase.presentation.common.StoreSubscription
import com.example.kmmbase.presentation.common.subscribeIn
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** The compiler accepts at most 64 frames and 600 transcript lines. */
private const val MAX_FRAMES = 64
private const val MAX_TRANSCRIPT_LINES = 600
private const val KEYFRAME_INTERVAL_MS = 2_000L


/**
 * Records a demonstration and turns it into a skill.
 *
 * Live recording sends keyframes plus a timestamped transcript; imported video uses persisted jobs.
 * Live frames are kept
 * every ~2 s and right after each spoken sentence, because a sentence usually ends on the result it
 * describes. When the transcript overflows we drop the oldest frames rather than the newest, so the
 * end state of the task always survives.
 */
class TeachStore(
    private val repository: () -> TeachRepository?,
    private val captureFrame: suspend (maxSide: Int) -> String?,
    private val nowMs: () -> Long,
    private val newId: () -> String,
    dispatcher: CoroutineDispatcher,
    private val videoPersistence: com.example.kmmbase.platform.TeachJobPersistence? = null,
) : AutoCloseable {
    private val trace = ShadowTrace("Teach")
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
    private var generation = 0L
    private var recordingScope: CoroutineScope? = null
    private var verifyJob: Job? = null
    private var saveJob: Job? = null
    private var closed = false
    private var videoJob: Job? = null
    private var videoTicket: TeachVideoTicket? = videoPersistence?.load()

    init {
        videoTicket?.let { ticket ->
            mutableState.update { it.copy(title = ticket.title, teacher = ticket.teacher, notes = ticket.notes, videoLocalUri = ticket.localUri, hasVideoJob = true, videoStatus = "paused") }
        }
    }

    private fun current(epoch: Long) = !closed && epoch == generation


    fun dispatch(action: TeachAction) {
        if (closed) {
            if (action is TeachAction.UploadVideo) action.source.close()
            return
        }
        if (action !is TeachAction.PartialSentence) {
            trace.event("action", "type=${action::class.simpleName} phase=${currentState.phase}")
        }
        when (action) {
            is TeachAction.UploadVideo -> uploadVideo(action.source)
            TeachAction.ResumeVideo -> resumeVideo(false)
            TeachAction.RetryVideo -> resumeVideo(true)
            TeachAction.CancelVideo -> changeVideo(false)
            TeachAction.DeleteVideo -> changeVideo(true)
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
            is TeachAction.PartialSentence -> if (currentState.phase == TeachPhase.RECORDING && !currentState.stopping) {
                mutableState.update { it.copy(liveTranscript = action.text) }
            }
            TeachAction.Compile -> compile()
            TeachAction.CancelCompile -> {
                if (currentState.phase == TeachPhase.COMPILING) {
                    generation++
                    compileJob?.cancel(); progressJob?.cancel()
                    mutableState.update { it.copy(phase = TeachPhase.IDLE, compilePhase = "") }
                }
            }
            is TeachAction.EditStep -> editStep(action)
            is TeachAction.VerifyStep -> verify(action)
            TeachAction.Save -> save()
            TeachAction.Reset -> reset()
        }
    }

    private fun uploadVideo(source: com.example.kmmbase.domain.repository.TeachVideoSource) {
        if (currentState.videoBusy) { source.close(); return }
        if (currentState.phase != TeachPhase.IDLE || currentState.stopping || currentState.title.isBlank() || videoTicket != null) {
            source.close()
            fail("Nhập tên kỹ năng; hoàn tất hoặc xóa tác vụ video trước khi chọn video khác.")
            return
        }
        val repo = repository() ?: run { source.close(); return fail("Chưa kết nối máy chủ.") }
        val input = currentState
        val epoch = ++generation
        mutableState.update { it.copy(videoBusy = true, videoLocalUri = source.localUri, videoStatus = "uploading", error = null) }
        videoJob = scope.launch {
            try {
                val ticket = repo.uploadVideo(source, input.title.trim(), input.teacher.trim(), input.notes.trim()).copy(localUri = source.localUri)
                currentCoroutineContext().ensureActive()
                if (!current(epoch)) return@launch
                videoTicket = ticket
                videoPersistence?.save(ticket)
                mutableState.update { it.copy(hasVideoJob = true, videoStatus = "queued") }
                repo.startVideo(ticket)
                pollVideo(repo, ticket, epoch)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (current(epoch)) {
                mutableState.update { it.copy(videoStatus = if (videoTicket == null) "failed" else "paused") }
                fail(error.message ?: "Chưa xử lý được video.")
            } }
            finally {
                source.close()
                if (current(epoch)) mutableState.update { it.copy(videoBusy = false) }
            }
        }
    }

    private fun resumeVideo(retry: Boolean) {
        if (currentState.dirtyStepIds.isNotEmpty()) return fail("Hãy lưu hoặc đặt lại bản sửa trước khi tải lại kết quả video.")
        val ticket = videoTicket ?: return
        if (currentState.videoBusy || currentState.saving || currentState.phase == TeachPhase.RECORDING || currentState.phase == TeachPhase.COMPILING) return
        val repo = repository() ?: return fail("Chưa kết nối máy chủ.")
        val epoch = ++generation
        val updated = if (retry) ticket.copy(title = currentState.title, teacher = currentState.teacher, notes = currentState.notes) else ticket
        videoTicket = updated
        videoPersistence?.save(updated)
        mutableState.update { it.copy(videoBusy = true, error = null) }
        videoJob = scope.launch {
            try {
                if (retry) repo.startVideo(updated, retry = true)
                else if (repo.videoStatus(updated).phase == "uploaded") repo.startVideo(updated)
                pollVideo(repo, updated, epoch)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (current(epoch)) {
                mutableState.update { it.copy(videoStatus = "paused") }
                fail(error.message ?: "Chưa mở lại được tác vụ.")
            } }
            finally { if (current(epoch)) mutableState.update { it.copy(videoBusy = false) } }
        }
    }

    private suspend fun pollVideo(repo: TeachRepository, ticket: TeachVideoTicket, epoch: Long) {
        while (current(epoch)) {
            val status = repo.videoStatus(ticket)
            currentCoroutineContext().ensureActive()
            if (!current(epoch)) return
            mutableState.update { it.copy(videoStatus = status.phase) }
            when (status.phase) {
                "ready" -> {
                    val draft = requireNotNull(status.draft) { "Máy chủ chưa trả bản nháp." }
                    mutableState.update { it.copy(phase = TeachPhase.REVIEW, draft = draft, dirtyStepIds = emptyList()) }
                    return
                }
                "failed", "interrupted", "cancelled", "deleted" -> {
                    if (status.error.isNotBlank()) fail(status.error)
                    return
                }
            }
            delay(1_500)
        }
    }

    private fun changeVideo(delete: Boolean) {
        if (currentState.phase == TeachPhase.RECORDING || currentState.phase == TeachPhase.COMPILING || currentState.verifyingStep != null) return
        val ticket = videoTicket ?: return
        if (currentState.saving || (!delete && currentState.videoStatus == "ready")) return
        val repo = repository() ?: return fail("Chưa kết nối máy chủ.")
        val epoch = ++generation
        videoJob?.cancel()
        mutableState.update { it.copy(videoBusy = true, error = null) }
        videoJob = scope.launch {
            try {
                if (delete) repo.deleteVideo(ticket) else repo.cancelVideo(ticket)
                currentCoroutineContext().ensureActive()
                if (!current(epoch)) return@launch
                if (delete) { videoTicket = null; videoPersistence?.save(null) }
                mutableState.update { it.copy(videoStatus = if (delete) "" else "cancelled", hasVideoJob = !delete) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (current(epoch)) fail(error.message ?: "Chưa cập nhật được tác vụ.") }
            finally { if (current(epoch)) mutableState.update { it.copy(videoBusy = false) } }
        }
    }

    private fun startRecording() {
        if (videoTicket != null) return fail("Hoàn tất hoặc xóa tác vụ video trước khi ghi hình một bài mới.")
        if (currentState.phase !in setOf(TeachPhase.IDLE, TeachPhase.REVIEW, TeachPhase.SAVED) || currentState.saving || currentState.stopping || currentState.verifyingStep != null || currentState.videoBusy) return
        generation++
        recordingScope?.cancel()
        val epoch = generation
        val captureScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        recordingScope = captureScope
        frames.clear()
        startedAt = nowMs()
        jobId = newId().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40)
        mutableState.update {
            it.copy(phase = TeachPhase.RECORDING, frameCount = 0, lines = emptyList(),
                liveTranscript = "", elapsedMs = 0, draft = null, dirtyStepIds = emptyList(), savedSkillId = null, error = null)
        }
        sampler = captureScope.launch {
            keep("start", epoch)
            while (isActive) {
                delay(KEYFRAME_INTERVAL_MS)
                mutableState.update { it.copy(elapsedMs = nowMs() - startedAt) }
                keep("interval", epoch)
            }
        }
    }

    private fun stopRecording() {
        if (currentState.phase != TeachPhase.RECORDING || currentState.stopping) return
        sampler?.cancel(); sampler = null
        // Cancel in-flight speech/interval frames before taking the final frame.
        recordingScope?.cancel()
        generation++
        val epoch = generation
        val captureScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        recordingScope = captureScope
        mutableState.update { it.copy(stopping = true, liveTranscript = "") }
        captureScope.launch {
            keep("end", epoch)
            if (current(epoch)) mutableState.update {
                it.copy(phase = TeachPhase.IDLE, stopping = false, elapsedMs = nowMs() - startedAt)
            }
        }
    }

    private fun addSentence(action: TeachAction.Sentence) {
        if (currentState.phase != TeachPhase.RECORDING || currentState.stopping) return
        val text = action.text.trim()
        if (text.isEmpty()) return
        mutableState.update {
            val lines = (it.lines + TranscriptLine(action.t0, action.t1, text)).takeLast(MAX_TRANSCRIPT_LINES)
            it.copy(lines = lines, liveTranscript = "")
        }
        // A sentence usually ends on the result it describes, so this is a good reference frame.
        val epoch = generation
        recordingScope?.launch { keep("speech", epoch) }
    }

    private suspend fun keep(reason: String, epoch: Long) {
        val image = try { withTimeout(5_000) { captureFrame(512) } }
        catch (_: TimeoutCancellationException) { null }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        currentCoroutineContext().ensureActive()
        if (!current(epoch)) return
        val captured = image ?: run {
            trace.event("frame_unavailable", "reason=$reason")
            return
        }
        val at = (nowMs() - startedAt) / 1000.0
        frames += TeachFrame(at, captured, reason)
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
        trace.event("frame_kept", "reason=$reason count=${frames.size}")
        mutableState.update { it.copy(frameCount = frames.size) }
    }

    private fun compile() {
        if (currentState.phase != TeachPhase.IDLE || currentState.stopping || currentState.saving || currentState.videoBusy) return
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
        trace.event("compile_start", "frames=${request.frames.size} lines=${request.transcript.size}")
        val epoch = ++generation
        val progress = scope.launch {
            while (isActive) {
                delay(1_500)
                val phase = try { repo.teachProgress(request.jobId) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { continue }
                currentCoroutineContext().ensureActive()
                if (!current(epoch)) return@launch
                if (phase.isNotBlank() && phase != "unknown") {
                    mutableState.update { it.copy(compilePhase = phase) }
                }
            }
        }
        progressJob = progress
        compileJob = scope.launch {
            try {
                val draft = repo.compileSkill(request)
                trace.event("compile_success", "steps=${draft.steps.size}")
                currentCoroutineContext().ensureActive()
                if (!current(epoch)) return@launch
                mutableState.update { it.copy(phase = TeachPhase.REVIEW, draft = draft, compilePhase = "") }
            } catch (error: CancellationException) {
                trace.event("cancelled")
                throw error
            } catch (error: Exception) {
                if (!current(epoch)) return@launch
                trace.failure("operation_failed", error)
                mutableState.update { it.copy(phase = TeachPhase.IDLE, compilePhase = "") }
                fail(error.message ?: "AI chưa học được kỹ năng. Thử lại nhé.")
            } finally {
                progress.cancel()
            }
        }
    }

    private fun editStep(action: TeachAction.EditStep) {
        if (currentState.phase != TeachPhase.REVIEW || currentState.saving || currentState.verifyingStep != null || currentState.videoBusy) return
        val draft = currentState.draft ?: return
        if (action.index !in draft.steps.indices || action.instruction.length > 250) return
        if (draft.steps[action.index].instruction == action.instruction) return
        val steps = draft.steps.toMutableList()
        steps[action.index] = steps[action.index].copy(instruction = action.instruction, onEnterSpeech = action.instruction)
        mutableState.update { it.copy(draft = draft.copy(steps = steps), dirtyStepIds = (it.dirtyStepIds + steps[action.index].id).distinct()) }
    }

    private fun verify(action: TeachAction.VerifyStep) {
        if (currentState.phase != TeachPhase.REVIEW || currentState.saving || currentState.verifyingStep != null || currentState.videoBusy) return
        val draft = currentState.draft ?: return
        if (action.index !in draft.steps.indices || draft.steps[action.index].instruction.isBlank()) return
        val repo = repository() ?: return fail("Chưa kết nối máy chủ SHADOW.")
        val stepId = draft.steps[action.index].id
        val epoch = generation
        mutableState.update { it.copy(verifyingStep = action.index, error = null,
            dirtyStepIds = (it.dirtyStepIds + stepId).distinct()) }
        verifyJob = scope.launch {
            try {
                val verified = repo.verifyStep(draft, action.index, action.referenceFrame)
                currentCoroutineContext().ensureActive()
                if (!current(epoch)) return@launch
                val passed = verified.checks.any { it.stepId == stepId && it.status in setOf("ok", "fixed") }
                mutableState.update { it.copy(draft = verified,
                    dirtyStepIds = if (passed) it.dirtyStepIds - stepId else it.dirtyStepIds,
                    error = if (passed) null else "Bước này chưa đạt kiểm chứng. Chọn ảnh rõ hơn hoặc sửa hướng dẫn rồi thử lại.") }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (current(epoch)) fail(error.message ?: "Chưa kiểm chứng được bước.") }
            finally { if (current(epoch)) mutableState.update { it.copy(verifyingStep = null) } }
        }
    }

    private fun save() {
        if (!currentState.canSave) return
        val repo = repository() ?: return fail("Chưa kết nối máy chủ SHADOW.")
        val draft = currentState.draft ?: return
        val epoch = generation
        mutableState.update { it.copy(saving = true, error = null) }
        saveJob = scope.launch {
            try {
                trace.event("save_start")
                val id = repo.saveSkill(draft)
                trace.event("save_success")
                currentCoroutineContext().ensureActive()
                if (!current(epoch)) return@launch
                mutableState.update { it.copy(phase = TeachPhase.SAVED, savedSkillId = id) }
            } catch (error: CancellationException) {
                trace.event("cancelled")
                throw error
            } catch (error: Exception) {
                if (current(epoch)) {
                    trace.failure("operation_failed", error)
                    fail(error.message ?: "Chưa lưu được kỹ năng. Thử lại nhé.")
                }
            } finally {
                if (current(epoch)) mutableState.update { it.copy(saving = false) }
            }
        }
    }

    private fun reset() {
        generation++
        recordingScope?.cancel(); recordingScope = null
        sampler?.cancel(); compileJob?.cancel(); progressJob?.cancel(); saveJob?.cancel(); verifyJob?.cancel(); videoJob?.cancel()
        frames.clear()
        mutableState.value = TeachState(speechAvailable = currentState.speechAvailable, hasVideoJob = videoTicket != null, videoStatus = if (videoTicket != null) "paused" else "")
    }

    private fun fail(message: String) {
        trace.event("error_shown", "phase=${currentState.phase}")
        mutableState.update { it.copy(error = message) }
    }

    override fun close() {
        if (closed) return
        closed = true
        generation++
        trace.event("close")
        scope.cancel()
    }
}
