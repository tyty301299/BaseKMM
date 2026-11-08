package com.example.kmmbase.presentation.notes

import com.example.kmmbase.domain.model.AppError
import com.example.kmmbase.domain.model.Note
import com.example.kmmbase.domain.repository.NoteRepository
import com.example.kmmbase.presentation.common.StoreSubscription
import com.example.kmmbase.presentation.common.subscribeIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditState(
    val title: String = "",
    val body: String = "",
    val isExisting: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: AppError? = null,
) {
    val canSave: Boolean
        get() = !isLoading && !isSaving && error != AppError.NOT_FOUND && title.isNotBlank()
}

sealed interface NoteEditAction {
    data class TitleChanged(val value: String) : NoteEditAction
    data class BodyChanged(val value: String) : NoteEditAction
    data object Save : NoteEditAction
    data object Delete : NoteEditAction
    data object Retry : NoteEditAction
}

sealed interface NoteEditEffect {
    data object Finished : NoteEditEffect
}

class NoteEditStore internal constructor(
    private val existingId: String?,
    private val notes: suspend () -> NoteRepository,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val noteId = existingId ?: newNoteId()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(
        NoteEditState(isExisting = existingId != null, isLoading = existingId != null),
    )
    private val effectChannel = Channel<NoteEditEffect>(Channel.BUFFERED)

    val state: StateFlow<NoteEditState> = mutableState.asStateFlow()
    val currentState: NoteEditState get() = mutableState.value
    val effects: Flow<NoteEditEffect> = effectChannel.receiveAsFlow()

    init {
        load()
    }

    fun dispatch(action: NoteEditAction) {
        when (action) {
            is NoteEditAction.TitleChanged -> mutableState.update { it.copy(title = action.value) }
            is NoteEditAction.BodyChanged -> mutableState.update { it.copy(body = action.value) }
            NoteEditAction.Save -> save()
            NoteEditAction.Delete -> delete()
            NoteEditAction.Retry -> load()
        }
    }

    fun observeState(onChange: (NoteEditState) -> Unit): StoreSubscription =
        mutableState.subscribeIn(scope, onChange)

    fun observeEffects(onEffect: (NoteEditEffect) -> Unit): StoreSubscription =
        effects.subscribeIn(scope, onEffect)

    override fun close() {
        scope.cancel()
    }

    private fun load() {
        val id = existingId ?: return
        mutableState.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            try {
                val note = notes().findById(id)
                mutableState.update {
                    if (note == null) {
                        it.copy(isLoading = false, error = AppError.NOT_FOUND)
                    } else {
                        it.copy(title = note.title, body = note.body, isLoading = false)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(isLoading = false, error = AppError.STORAGE) }
            }
        }
    }

    private fun save() {
        val current = mutableState.value
        if (!current.canSave) return
        writeThenFinish {
            notes().save(Note(id = noteId, title = current.title.trim(), body = current.body))
        }
    }

    private fun delete() {
        val current = mutableState.value
        if (!current.isExisting || current.isSaving) return
        writeThenFinish { notes().delete(noteId) }
    }

    private fun writeThenFinish(write: suspend () -> Unit) {
        mutableState.update { it.copy(isSaving = true, error = null) }
        scope.launch {
            try {
                write()
                effectChannel.send(NoteEditEffect.Finished)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(isSaving = false, error = AppError.STORAGE) }
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun newNoteId(): String = Uuid.random().toString()
