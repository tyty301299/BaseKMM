package com.example.kmmbase.presentation.looks

import com.example.kmmbase.domain.model.SavedLook
import com.example.kmmbase.domain.repository.LookRepository
import com.example.kmmbase.presentation.common.StoreSubscription
import com.example.kmmbase.presentation.common.subscribeIn
import com.example.kmmbase.presentation.common.UiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LookStore internal constructor(
    private val repository: suspend () -> LookRepository,
    private val newId: () -> String,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(LookState())
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    fun observeMessages(onMessage: (UiMessage) -> Unit): StoreSubscription = messages.subscribeIn(scope, onMessage)
    private var observation: Job? = null
    private var outfitDraft: SavedLook? = null
    private val pendingGuideCompletions = ArrayDeque<LookAction.SaveGuideCompletion>()
    private val completionIds = mutableSetOf<String>()
    private var guideRetryRequired = false
    val state: StateFlow<LookState> = mutableState.asStateFlow()
    val currentState: LookState get() = mutableState.value

    init { observe() }

    fun observeState(onChange: (LookState) -> Unit): StoreSubscription = mutableState.subscribeIn(scope, onChange)

    fun dispatch(action: LookAction) {
        if (action is LookAction.SaveGuideCompletion) {
            if (action.sessionId.isBlank() || action.sessionId.length > 200 || action.skillId.isBlank() || action.skillId.length > 200 ||
                action.title.isBlank() || action.title.length > 100 || action.source !in setOf("manual", "ai", "demo")) return
            if (completionIds.add(action.sessionId) && currentState.records.none { it.id == action.sessionId }) {
                pendingGuideCompletions.addLast(action)
                drainGuideCompletions()
            }
            return
        }
        if (currentState.saving) return
        when (action) {
            is LookAction.SelectPreset -> if (action.preset in setOf("coat", "blouse")) {
                outfitDraft = null
                mutableState.update { it.copy(preset = action.preset, before = false, capture = null, savedId = null, outfitSaved = false, error = null) }
            }
            is LookAction.SelectOccasion -> if (action.occasion in setOf("Đi làm", "Đi chơi", "Hẹn hò")) {
                outfitDraft = null
                mutableState.update { it.copy(occasion = action.occasion, outfitSaved = false) }
            }
            is LookAction.Filter -> if (action.kind in setOf("all", "tryon", "outfit", "skill", "guide")) {
                mutableState.update { it.copy(filter = action.kind) }
            }
            LookAction.Compare -> mutableState.update { it.copy(before = !it.before) }
            LookAction.Flip -> mutableState.update { it.copy(flipped = !it.flipped) }
            LookAction.Capture -> mutableState.update { it.copy(capture = snapshot("tryon"), savedId = null, error = null) }
            LookAction.Retake -> mutableState.update { it.copy(capture = null, savedId = null, error = null) }
            LookAction.SaveCapture -> currentState.capture?.let { save(it) }
            LookAction.SaveOutfit -> {
                val draft = outfitDraft ?: snapshot("outfit").copy(before = false, flipped = false).also { outfitDraft = it }
                save(draft)
            }
            is LookAction.Delete -> write("Đã xóa khỏi Lịch sử") {
                repository().delete(action.id)
                mutableState.update { it.copy(records = it.records.filterNot { r -> r.id == action.id },
                    savedId = it.savedId?.takeUnless { id -> id == action.id },
                    outfitSaved = it.outfitSaved && outfitDraft?.id != action.id) }
            }
            is LookAction.Reuse -> currentState.records.find { it.id == action.id }?.let { record ->
                outfitDraft = null
                mutableState.update { it.copy(preset = record.preset, occasion = record.occasion,
                    before = record.before, flipped = record.flipped, capture = null, savedId = null, outfitSaved = false, error = null) }
            }
            LookAction.Retry -> { observe(); guideRetryRequired = false; drainGuideCompletions() }
            is LookAction.SaveGuideCompletion -> Unit
        }
    }

    private fun snapshot(kind: String) = currentState.let {
        SavedLook(newId(), it.preset, it.occasion, kind, now(), it.before, it.flipped)
    }

    private fun save(record: SavedLook) {
        if (currentState.savedId == record.id || (record.kind == "outfit" && currentState.outfitSaved)) return
        write("Đã lưu trên máy") {
            repository().save(record)
            mutableState.update { it.copy(savedId = if (record.kind == "tryon") record.id else it.savedId,
                outfitSaved = if (record.kind == "outfit") true else it.outfitSaved) }
        }
    }

    private fun drainGuideCompletions() {
        if (currentState.saving || guideRetryRequired || pendingGuideCompletions.isEmpty()) return
        val action = pendingGuideCompletions.removeFirst()
        val record = SavedLook(action.sessionId, action.skillId, action.title.trim(), "guide-${action.source}", now())
        write("Đã lưu vào Lịch sử", onFailure = {
            pendingGuideCompletions.addFirst(action)
            guideRetryRequired = true
        }) { repository().save(record) }
    }

    private fun write(success: String, onFailure: () -> Unit = {}, block: suspend () -> Unit) {
        mutableState.update { it.copy(saving = true, error = null) }
        scope.launch {
            try {
                block()
                currentCoroutineContext().ensureActive()
                messageChannel.send(UiMessage(success))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                onFailure()
                val message = "Không lưu được thay đổi. Dữ liệu vừa chọn vẫn được giữ; hãy thử lại."
                mutableState.update { it.copy(error = message) }
                messageChannel.send(UiMessage(message, true))
            } finally {
                mutableState.update { it.copy(saving = false) }
                drainGuideCompletions()
            }
        }
    }

    private fun observe() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        observation = scope.launch {
            try {
                repository().observeAll().collect { records ->
                    mutableState.update { it.copy(records = records, loading = false, savedId = it.savedId?.takeIf { id -> records.any { row -> row.id == id } },
                        outfitSaved = it.outfitSaved && records.any { row -> row.id == outfitDraft?.id }) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val message = "Không mở được Lịch sử trên máy. Hãy thử tải lại."
                mutableState.update { it.copy(loading = false, error = message) }
                messageChannel.send(UiMessage(message, true))
            }
        }
    }

    override fun close() { scope.cancel(); messageChannel.close() }
}
