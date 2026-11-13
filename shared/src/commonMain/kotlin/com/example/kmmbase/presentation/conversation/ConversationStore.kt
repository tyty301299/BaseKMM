package com.example.kmmbase.presentation.conversation

import com.example.kmmbase.domain.repository.*
import com.example.kmmbase.presentation.common.StoreSubscription
import com.example.kmmbase.presentation.common.subscribeIn
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ConversationState(
    val turns: List<ConversationTurn> = emptyList(),
    val links: List<ConversationLink> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val reply: String = "",
)

class ConversationStore internal constructor(
    private val repositoryFactory: () -> ConversationRepository?,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = mutableState.asStateFlow()
    val currentState: ConversationState get() = mutableState.value
    private var operation: Job? = null
    fun observeState(onChange: (ConversationState) -> Unit): StoreSubscription = mutableState.subscribeIn(scope, onChange)

    fun send(text: String, image: String?) {
        val prompt = text.trim()
        if (currentState.busy || prompt.isEmpty()) return
        if (prompt.length > 500) {
            mutableState.update { it.copy(error = "Nhập tối đa 500 ký tự mỗi lần.") }; return
        }
        val history = currentState.turns.takeLast(6)
        mutableState.update { it.copy(turns = it.turns + ConversationTurn("user", prompt), links = emptyList(), busy = true, error = null, reply = "") }
        operation = scope.launch {
            var repository: ConversationRepository? = null
            try {
                repository = repositoryFactory() ?: error("Chưa kết nối máy chủ. Kiểm tra Base URL trong Cài đặt.")
                repository.converse(prompt, history, image?.takeIf { it.startsWith("data:image/jpeg;base64,") && it.length <= 500_000 }) { event ->
                    when (event) {
                        is ConversationEvent.Text -> mutableState.update { it.copy(reply = it.reply + event.text) }
                        is ConversationEvent.Action -> mutableState.update { it.copy(links = (it.links + event.link).distinct()) }
                        ConversationEvent.Done -> Unit
                    }
                }
                mutableState.update { it.copy(turns = (it.turns + ConversationTurn("assistant", it.reply)).takeLast(20), busy = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(busy = false, links = emptyList(), error =
                    if (error is IllegalStateException) error.message else "Không kết nối được hội thoại. Kiểm tra mạng rồi thử lại.") }
            } finally { repository?.close() }
        }
    }

    fun cancel() { operation?.cancel(); mutableState.update { it.copy(busy = false, links = emptyList()) } }
    fun clear() { cancel(); mutableState.value = ConversationState() }
    override fun close() { scope.cancel() }
}
