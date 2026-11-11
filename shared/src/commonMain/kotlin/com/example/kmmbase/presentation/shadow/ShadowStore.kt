package com.example.kmmbase.presentation.shadow

import com.example.kmmbase.platform.ShadowTrace
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.ShadowRepository
import com.example.kmmbase.presentation.common.StoreSubscription
import com.example.kmmbase.presentation.common.subscribeIn
import com.example.kmmbase.presentation.common.UiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ShadowState(
    val endpoint: String = "",
    val connected: Boolean = false,
    val modeLabel: String = "Chưa kết nối",
    val busy: String = "",
    val error: String? = null,
    val skills: List<ShadowSkill> = emptyList(),
    val wardrobe: List<WardrobeItem> = emptyList(),
    val lesson: ShadowLesson? = null,
    val answer: ShadowAnswer? = null,
    val answerLabel: String = "",
    val answerQuestion: String = "",
    val suggestions: ShadowSuggestions? = null,
    val suggestionsLabel: String = "",
    val suggestionsEvent: String = "",
)

sealed interface ShadowAction {
    data class Connect(val endpoint: String, val code: String) : ShadowAction
    data object Refresh : ShadowAction
    data class OpenLesson(val id: String) : ShadowAction
    data object CloseLesson : ShadowAction
    data object ClearAnswer : ShadowAction
    data class Ask(val stepId: String, val question: String) : ShadowAction
    data class Suggest(val event: String, val notes: String) : ShadowAction
}

/** Weather context for styling; the demo is in Hanoi. */
internal const val DEFAULT_CITY = "Hà Nội"

class ShadowStore internal constructor(
    private val repositoryFactory: (String, String) -> ShadowRepository,
    private val normalizeEndpoint: (String) -> String,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val trace = ShadowTrace("App")
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(ShadowState())
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    fun observeMessages(onMessage: (UiMessage) -> Unit): StoreSubscription = messages.subscribeIn(scope, onMessage)
    private var repository: ShadowRepository? = null
    private var operation: Job? = null
    val state: StateFlow<ShadowState> = mutableState.asStateFlow()
    val currentState: ShadowState get() = mutableState.value

    fun observeState(onChange: (ShadowState) -> Unit): StoreSubscription = mutableState.subscribeIn(scope, onChange)

    /** Opens a guide session on the connected server, or null when not connected yet. */
    fun openSession(): com.example.kmmbase.domain.repository.ShadowSessionSocket? =
        repository?.takeIf { currentState.connected }?.openSession()

    /**
     * The connected server, for flows that own their own store (teach, guide). Null until a
     * connection succeeds, and swapped when the user changes server.
     */
    fun connectedRepository(): ShadowRepository? = repository?.takeIf { currentState.connected }

    /**
     * Headers an image loader needs for server media. Kept off [ShadowState] so the access code is
     * never captured in UI snapshots or saved instance state.
     */
    fun mediaHeaders(): Map<String, String> = repository?.mediaHeaders() ?: emptyMap()

    fun dispatch(action: ShadowAction) {
        trace.event("action", "type=${action::class.simpleName}")
        when (action) {
            is ShadowAction.Connect -> connect(action)
            ShadowAction.CloseLesson -> {
                operation?.cancel()
                mutableState.update { it.copy(lesson = null, answer = null, answerLabel = "", busy = "", error = null) }
            }
            else -> {
                val repo = repository ?: return
                if (!currentState.connected || currentState.busy.isNotEmpty()) return
                when (action) {
                    ShadowAction.ClearAnswer -> mutableState.update { it.copy(answer = null, answerLabel = "", error = null) }
                    ShadowAction.Refresh -> run("Đang tải dữ liệu…") { refresh(repo) }
                    is ShadowAction.OpenLesson -> run("Đang mở kỹ năng…") {
                        val lesson = repo.lesson(action.id)
                        currentCoroutineContext().ensureActive()
                        mutableState.update { it.copy(lesson = lesson, answer = null, answerLabel = "") }
                    }
                    is ShadowAction.Ask -> {
                        val lesson = currentState.lesson ?: return
                        val question = action.question.trim()
                        if (question.isEmpty() || question.length > 500 || lesson.steps.none { it.id == action.stepId }) return
                        mutableState.update { it.copy(answer = null, answerLabel = "") }
                        run("SHADOW đang trả lời…") {
                            val answer = repo.ask(lesson.id, action.stepId, question)
                            currentCoroutineContext().ensureActive()
                            mutableState.update { it.copy(answer = answer, answerLabel = shadowSourceLabel(answer.source), answerQuestion = question) }
                        }
                    }
                    is ShadowAction.Suggest -> {
                        if (action.event.isBlank() || action.event.length > 200 || action.notes.length > 300) return
                        mutableState.update { it.copy(suggestions = null, suggestionsLabel = "") }
                        run("Đang phối từ tủ đồ của bạn…") {
                            val result = repo.suggest(action.event.trim(), action.notes.trim(), DEFAULT_CITY)
                            currentCoroutineContext().ensureActive()
                            mutableState.update { it.copy(suggestions = result, suggestionsLabel = shadowSourceLabel(result.source), suggestionsEvent = action.event.trim()) }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun connect(action: ShadowAction.Connect) {
        operation?.cancel()
        repository?.close()
        repository = null
        mutableState.value = ShadowState()
        run("Đang kết nối…") {
            val endpoint = normalizeEndpoint(action.endpoint)
            val repo = repositoryFactory(endpoint, action.code.trim())
            repository = repo
            try {
                val health = repo.health()
                // /api/health is ungated, so validate the code explicitly before loading anything.
                if (health.accessRequired) repo.authenticate(action.code.trim())
                val skills = repo.skills()
                val wardrobe = repo.wardrobe()
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(endpoint = endpoint, connected = true,
                    modeLabel = shadowSourceLabel(health.mode), skills = skills, wardrobe = wardrobe) }
            } catch (error: Exception) {
                repo.close()
                if (repository === repo) repository = null
                throw error
            }
        }
    }

    private suspend fun refresh(repo: ShadowRepository) {
        val health = repo.health()
        val skills = repo.skills()
        val wardrobe = repo.wardrobe()
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(skills = skills, wardrobe = wardrobe,
            modeLabel = shadowSourceLabel(health.mode), suggestions = null, suggestionsLabel = "") }
    }

    private fun run(label: String, block: suspend () -> Unit) {
        val operationTrace = ShadowTrace("AppOperation")
        operationTrace.event("start")
        mutableState.update { it.copy(busy = label, error = null) }
        operation = scope.launch {
            try {
                block()
                currentCoroutineContext().ensureActive()
                operationTrace.event("success")
                mutableState.update { it.copy(busy = "") }
                messageChannel.send(UiMessage("Đã hoàn tất thao tác với SHADOW"))
            } catch (error: CancellationException) {
                operationTrace.event("cancelled")
                throw error
            } catch (error: Exception) {
                operationTrace.failure("failed", error)
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(busy = "", error = when (error) {
                    is IllegalArgumentException -> error.message ?: "Dữ liệu không hợp lệ."
                    is ShadowConnectionException -> error.message
                    else -> "Không kết nối được máy chủ hoặc dữ liệu chưa hợp lệ. Kiểm tra mạng rồi thử lại."
                }) }
                messageChannel.send(UiMessage(currentState.error ?: "Không hoàn tất được thao tác.", true))
            }
        }
    }

    override fun close() {
        scope.cancel()
        repository?.close()
        messageChannel.close()
    }
}
