package com.example.kmmbase

import com.example.kmmbase.domain.model.AppError
import com.example.kmmbase.domain.model.Note
import com.example.kmmbase.domain.repository.NoteRepository
import com.example.kmmbase.presentation.notes.NoteEditAction
import com.example.kmmbase.presentation.notes.NoteEditEffect
import com.example.kmmbase.presentation.notes.NoteEditStore
import com.example.kmmbase.presentation.notes.NoteListAction
import com.example.kmmbase.presentation.notes.NoteListStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NoteStoresTest {
    private class FakeNotes(vararg initial: Note) : NoteRepository {
        val items = MutableStateFlow(initial.toList())
        var failWrites = false

        override fun observeAll(): Flow<List<Note>> = items
        override suspend fun findById(id: String) = items.value.find { it.id == id }
        override suspend fun save(note: Note) {
            if (failWrites) error("write failed")
            items.value = items.value.filterNot { it.id == note.id } + note
        }
        override suspend fun delete(id: String) {
            items.value = items.value.filterNot { it.id == id }
        }
    }

    private val dispatcher = UnconfinedTestDispatcher()

    @Test fun listSortsNotesAndReactsToDelete() = runTest(dispatcher) {
        val notes = FakeNotes(Note("2", "Bơ", ""), Note("1", "an", ""))
        val store = NoteListStore({ notes }, dispatcher)
        assertEquals(listOf("an", "Bơ"), store.currentState.notes.map { it.title })
        assertFalse(store.currentState.isLoading)

        store.dispatch(NoteListAction.Delete("1"))
        assertEquals(listOf("Bơ"), store.currentState.notes.map { it.title })
        store.close()
    }

    @Test fun listReportsOpenFailureAndRecoversOnRetry() = runTest(dispatcher) {
        var fail = true
        val notes = FakeNotes()
        val store = NoteListStore({ if (fail) error("open failed") else notes }, dispatcher)
        assertEquals(AppError.STORAGE, store.currentState.error)

        fail = false
        store.dispatch(NoteListAction.Retry)
        assertEquals(null, store.currentState.error)
        store.close()
    }

    @Test fun newNoteRequiresTitleThenSavesAndFinishes() = runTest(dispatcher) {
        val notes = FakeNotes()
        val store = NoteEditStore(null, { notes }, dispatcher)
        store.dispatch(NoteEditAction.TitleChanged("   "))
        assertFalse(store.currentState.canSave)
        store.dispatch(NoteEditAction.Save)
        assertTrue(notes.items.value.isEmpty())

        store.dispatch(NoteEditAction.TitleChanged("  Tiêu đề  "))
        store.dispatch(NoteEditAction.BodyChanged("Nội dung"))
        store.dispatch(NoteEditAction.Save)
        assertEquals(NoteEditEffect.Finished, store.effects.first())
        val saved = notes.items.value.single()
        assertEquals("Tiêu đề", saved.title)
        assertEquals("Nội dung", saved.body)
        assertTrue(saved.id.isNotBlank())
        store.close()
    }

    @Test fun existingNoteLoadsAndMissingNoteCannotBeSaved() = runTest(dispatcher) {
        val notes = FakeNotes(Note("1", "Cũ", "Thân"))
        val existing = NoteEditStore("1", { notes }, dispatcher)
        assertEquals("Cũ", existing.currentState.title)
        assertTrue(existing.currentState.isExisting)

        val missing = NoteEditStore("nope", { notes }, dispatcher)
        assertEquals(AppError.NOT_FOUND, missing.currentState.error)
        assertFalse(missing.currentState.canSave)
        existing.close()
        missing.close()
    }

    @Test fun failedSaveKeepsInputAndAllowsAnotherTry() = runTest(dispatcher) {
        val notes = FakeNotes().apply { failWrites = true }
        val store = NoteEditStore(null, { notes }, dispatcher)
        store.dispatch(NoteEditAction.TitleChanged("Giữ lại"))
        store.dispatch(NoteEditAction.Save)
        assertEquals(AppError.STORAGE, store.currentState.error)
        assertEquals("Giữ lại", store.currentState.title)
        assertTrue(store.currentState.canSave)
        store.close()
    }
}
