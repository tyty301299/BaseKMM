package com.example.kmmbase.presentation.studio

import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.StudioRepository
import com.example.kmmbase.domain.usecase.BuildDemoSkillUseCase
import com.example.kmmbase.domain.usecase.SuggestDemoOutfitsUseCase
import com.example.kmmbase.presentation.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class StudioStore internal constructor(
    private val repository: suspend () -> StudioRepository,
    private val newId: () -> String,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
    private val buildSkill: BuildDemoSkillUseCase = BuildDemoSkillUseCase(),
    private val suggest: SuggestDemoOutfitsUseCase = SuggestDemoOutfitsUseCase(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(StudioState())
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    private val effectChannel = Channel<StudioEffect>(Channel.BUFFERED)
    private var observation: Job? = null
    private var draftId = newId()
    private var pendingItem: StudioItem? = null
    val state = mutableState.asStateFlow()
    val currentState get() = mutableState.value
    val messages = messageChannel.receiveAsFlow()
    val effects = effectChannel.receiveAsFlow()
    init { observe() }

    fun observeState(onChange: (StudioState) -> Unit) = state.subscribeIn(scope, onChange)
    fun observeMessages(onMessage: (UiMessage) -> Unit) = messages.subscribeIn(scope, onMessage)
    fun observeEffects(onEffect: (StudioEffect) -> Unit) = effects.subscribeIn(scope, onEffect)

    fun dispatch(action: StudioAction) {
        if (currentState.busy.isNotEmpty()) return
        when (action) {
            StudioAction.Retry -> observe()
            is StudioAction.AddSample -> if (action.preset in setOf("coat", "blouse")) {
                val item = pendingItem?.takeIf { it.preset == action.preset } ?: StudioItem(newId(), action.preset,
                    if (action.preset == "coat") "Áo xanh rêu" else "Blouse cam đào", if (action.preset == "coat") "Áo khoác" else "Áo").also { pendingItem = it }
                run("Đang thêm trang phục…", "Đã thêm trang phục mẫu") {
                    repository().saveItem(item)
                    mutableState.update { it.copy(itemDraft = item, data = it.data.copy(items = it.data.items.filterNot { old -> old.id == item.id } + item)) }
                    pendingItem = null
                    effectChannel.send(StudioEffect.ItemAdded(item.id))
                }
            }
            is StudioAction.OpenItem -> mutableState.update { it.copy(itemDraft = it.data.items.find { item -> item.id == action.id }, error = null) }
            is StudioAction.WardrobeFilter -> if (action.value in setOf("all", "Áo", "Áo khoác")) {
                mutableState.update { it.copy(wardrobeFilter = action.value) }
            }
            is StudioAction.ItemCategory -> if (action.value in setOf("Áo", "Áo khoác")) {
                mutableState.update { it.copy(itemDraft = it.itemDraft?.copy(category = action.value)) }
            }
            is StudioAction.ItemName -> if (action.value.length <= 80) mutableState.update { it.copy(itemDraft = it.itemDraft?.copy(name = action.value)) }
            StudioAction.SaveItem -> currentState.itemDraft?.let { item ->
                if (item.name.isBlank()) fail("Nhập tên trang phục trước khi lưu.")
                else run("Đang lưu trang phục…", "Đã cập nhật trang phục") { repository().saveItem(item.copy(name = item.name.trim())) }
            }
            is StudioAction.DeleteItem -> run("Đang xóa trang phục…", "Đã xóa trang phục") {
                repository().deleteItem(action.id)
                mutableState.update { it.copy(itemDraft = null, suggestions = emptyList(), suggested = false) }
                effectChannel.send(StudioEffect.ItemDeleted)
            }
            is StudioAction.OutfitNotes -> if (action.value.length <= 300) mutableState.update { it.copy(outfitNotes = action.value, suggested = false) }
            is StudioAction.Suggest -> run("Đang chuẩn bị gợi ý mẫu…", "Đã chuẩn bị gợi ý mẫu") {
                yield()
                val result = suggest(currentState.data.items, action.occasion)
                mutableState.update { it.copy(suggestions = result, suggested = true) }
            }
            StudioAction.NewLesson -> {
                draftId = newId()
                mutableState.update { it.copy(title = "Lắp giá đỡ điện thoại", recording = false, recorded = false,
                    transcript = "Đặt chân đế nằm phẳng, gắn thanh đứng rồi siết khóa.", draftSteps = emptyList(),
                    savedSkillId = null, activeSkillId = null, stepIndex = 0, completed = false,
                    question = "", answer = "", error = null) }
            }
            StudioAction.StartRecording -> mutableState.update { it.copy(recording = true, recorded = false, draftSteps = emptyList(), savedSkillId = null, error = null) }
            StudioAction.StopRecording -> if (currentState.recording) mutableState.update { it.copy(recording = false, recorded = true) }
            is StudioAction.Transcript -> if (action.value.length <= 1000) mutableState.update { it.copy(transcript = action.value) }
            StudioAction.CompileSample -> if (currentState.recorded) run("Đang chuẩn bị các bước mẫu…", "Đã tạo các bước mẫu để duyệt") {
                yield()
                mutableState.update { it.copy(draftSteps = buildSkill(), savedSkillId = null) }
            } else fail("Hoàn tất đoạn thao tác mẫu trước khi xem các bước.")
            is StudioAction.LessonTitle -> if (action.value.length <= 80) mutableState.update { it.copy(title = action.value, savedSkillId = null) }
            is StudioAction.EditStep -> if (action.index in currentState.draftSteps.indices && action.value.length <= 250) {
                mutableState.update { it.copy(draftSteps = it.draftSteps.toMutableList().apply { set(action.index, action.value) }, savedSkillId = null) }
            }
            StudioAction.SaveSkill -> if (currentState.canSaveSkill && currentState.savedSkillId == null) {
                val snapshot = currentState.let { StudioSkill(draftId, it.title.trim(), it.draftSteps.map { step -> step.trim() }, now()) }
                run("Đang lưu kỹ năng…", "Đã lưu kỹ năng trên máy") {
                    repository().saveSkill(snapshot)
                    mutableState.update { it.copy(data = it.data.copy(skills = listOf(snapshot) + it.data.skills.filterNot { old -> old.id == snapshot.id }), savedSkillId = snapshot.id, activeSkillId = snapshot.id, stepIndex = 0, completed = false) }
                    effectChannel.send(StudioEffect.SkillSaved(snapshot.id))
                }
            } else if (!currentState.canSaveSkill) fail("Nhập tên và nội dung đầy đủ cho từng bước.")
            is StudioAction.OpenSkill -> mutableState.update { it.copy(activeSkillId = action.id, stepIndex = 0, completed = false, question = "", answer = "", error = null) }
            StudioAction.NextStep -> currentState.activeSkill?.let { skill ->
                if (currentState.stepIndex == skill.steps.lastIndex) {
                    mutableState.update { it.copy(completed = true) }
                    messageChannel.trySend(UiMessage("Đã hoàn thành hướng dẫn mẫu"))
                } else mutableState.update { it.copy(stepIndex = it.stepIndex + 1, question = "", answer = "") }
            }
            StudioAction.PreviousStep -> if (currentState.stepIndex > 0) mutableState.update { it.copy(stepIndex = it.stepIndex - 1, completed = false, question = "", answer = "") }
            is StudioAction.Question -> if (action.value.length <= 500) mutableState.update { it.copy(question = action.value, answer = "") }
            StudioAction.AskSample -> if (currentState.question.isNotBlank() && currentState.currentStep.isNotEmpty()) run("Đang mở gợi ý mẫu…", null) {
                yield()
                mutableState.update { it.copy(answer = "Gợi ý minh họa cho bước hiện tại: ${it.currentStep} Kiểm tra lại vị trí và độ chắc chắn trước khi tiếp tục. Đây là nội dung mẫu, chưa trả lời bằng AI.") }
            } else fail("Nhập câu hỏi và chọn một bước hướng dẫn trước.")
            is StudioAction.DeleteSkill -> run("Đang xóa kỹ năng…", "Đã xóa kỹ năng khỏi máy") {
                repository().deleteSkill(action.id)
                effectChannel.send(StudioEffect.SkillDeleted)
            }
            is StudioAction.Appearance -> run("Đang lưu giao diện…", "Đã cập nhật giao diện") {
                repository().saveSettings(StudioSettings(action.dark, action.glass))
            }
            StudioAction.ClearData -> run("Đang xóa dữ liệu mẫu…", "Đã xóa dữ liệu mẫu trên máy") {
                repository().clearDemoData()
                draftId = newId(); pendingItem = null
                mutableState.update { StudioState(data = StudioData(settings = it.data.settings), loading = false, busy = it.busy) }
                effectChannel.send(StudioEffect.DataCleared)
            }
        }
    }

    private fun fail(message: String) {
        mutableState.update { it.copy(error = message) }
        messageChannel.trySend(UiMessage(message, true))
    }

    private fun run(label: String, success: String?, block: suspend () -> Unit) {
        mutableState.update { it.copy(busy = label, error = null) }
        scope.launch {
            try {
                block()
                currentCoroutineContext().ensureActive()
                success?.let { messageChannel.send(UiMessage(it)) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fail("Không hoàn tất được thao tác. Nội dung vẫn được giữ; hãy thử lại.") }
            finally { mutableState.update { it.copy(busy = "") } }
        }
    }

    private fun observe() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        observation = scope.launch {
            try {
                repository().observe().collect { data ->
                    mutableState.update { it.copy(data = data, loading = false,
                        suggestions = it.suggestions.filter { choice -> data.items.any { item -> item.id == choice.itemId } }) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutableState.update { it.copy(loading = false) }
                fail("Không đọc được dữ liệu local. Bấm Tải lại để thử lại.")
            }
        }
    }

    override fun close() { scope.cancel(); messageChannel.close(); effectChannel.close() }
}
