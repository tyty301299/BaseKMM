package com.example.kmmbase.presentation.notes

import com.example.kmmbase.domain.model.AppError
import com.example.kmmbase.domain.model.Note
import com.example.kmmbase.domain.repository.NoteRepository
import com.example.kmmbase.presentation.common.StoreSubscription
import com.example.kmmbase.presentation.common.subscribeIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NoteListStore internal constructor(
    private val notes: suspend () -> NoteRepository,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(NoteListState())
    private var observation: Job? = null

    val state: StateFlow<NoteListState> = mutableState.asStateFlow()
    val currentState: NoteListState get() = mutableState.value

    init {
        observeNotes()
    }

    fun dispatch(action: NoteListAction) {
        when (action) {
            NoteListAction.Retry -> observeNotes()
            is NoteListAction.Delete -> delete(action.id)
        }
    }

    fun observeState(onChange: (NoteListState) -> Unit): StoreSubscription =
        mutableState.subscribeIn(scope, onChange)

    override fun close() {
        scope.cancel()
    }

    private fun observeNotes() {
        observation?.cancel()
        mutableState.update { it.copy(isLoading = true, error = null) }
        observation = scope.launch {
            try {
                notes().observeAll().collect { items ->
                    val sorted = items.sortedBy { it.title.lowercase() }
                    mutableState.update { it.copy(notes = sorted, isLoading = false, error = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(isLoading = false, error = AppError.STORAGE) }
            }
        }
    }

    private fun delete(id: String) {
        scope.launch {
            try {
                notes().delete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(error = AppError.STORAGE) }
            }
        }
    }
}
