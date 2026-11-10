package com.example.kmmbase

import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.StudioRepository
import com.example.kmmbase.presentation.studio.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StudioStoreTest {
    private class FakeStudio : StudioRepository {
        val data = MutableStateFlow(StudioData())
        var failWrites = false
        var gate: CompletableDeferred<Unit>? = null
        var writes = 0
        private suspend fun beforeWrite() { gate?.await(); if (failWrites) error("disk full"); writes++ }
        override fun observe() = data
        override suspend fun saveItem(item: StudioItem) {
            beforeWrite(); data.value = data.value.copy(items = data.value.items.filterNot { it.id == item.id } + item)
        }
        override suspend fun deleteItem(id: String) {
            beforeWrite(); data.value = data.value.copy(items = data.value.items.filterNot { it.id == id })
        }
        override suspend fun saveSkill(skill: StudioSkill) {
            beforeWrite(); data.value = data.value.copy(skills = data.value.skills.filterNot { it.id == skill.id } + skill)
        }
        override suspend fun deleteSkill(id: String) {
            beforeWrite(); data.value = data.value.copy(skills = data.value.skills.filterNot { it.id == id })
        }
        override suspend fun saveSettings(settings: StudioSettings) { beforeWrite(); data.value = data.value.copy(settings = settings) }
        override suspend fun clearDemoData() { beforeWrite(); data.value = StudioData(settings = data.value.settings) }
    }
    private val dispatcher = UnconfinedTestDispatcher()
    private var next = 0
    private fun store(repo: FakeStudio) = StudioStore({ repo }, { "id-${++next}" }, { 1000L }, dispatcher)
    private fun prepareLesson(store: StudioStore) {
        store.dispatch(StudioAction.NewLesson)
        store.dispatch(StudioAction.StartRecording)
        store.dispatch(StudioAction.StopRecording)
        store.dispatch(StudioAction.CompileSample)
    }

    @Test fun wardrobeFlowPersistsEditsAndOnlySuggestsOwnedItems() = runTest(dispatcher) {
        val repo = FakeStudio(); val store = store(repo)
        assertTrue(store.currentState.data.items.isEmpty())
        store.dispatch(StudioAction.AddSample("blouse"))
        val event = store.effects.first() as StudioEffect.ItemAdded
        assertFalse(store.messages.first().isError)
        store.dispatch(StudioAction.OpenItem(event.id))
        store.dispatch(StudioAction.ItemName("Áo của tôi"))
        store.dispatch(StudioAction.ItemCategory("Áo khoác"))
        store.dispatch(StudioAction.SaveItem)
        assertEquals("Áo của tôi", repo.data.value.items.single().name)
        store.dispatch(StudioAction.WardrobeFilter("Áo"))
        assertTrue(store.currentState.filteredItems.isEmpty())
        store.dispatch(StudioAction.WardrobeFilter("Áo khoác"))
        assertEquals(1, store.currentState.filteredItems.size)
        store.dispatch(StudioAction.Suggest("Đi làm"))
        testScheduler.runCurrent()
        assertEquals(event.id, store.currentState.suggestions.single().itemId)
        store.dispatch(StudioAction.DeleteItem(event.id))
        assertTrue(store.currentState.suggestions.isEmpty())
        store.close()
    }

    @Test fun failedAddRetainsStableIdAndErrorToastUntilRetrySucceeds() = runTest(dispatcher) {
        val repo = FakeStudio().apply { failWrites = true }; val store = store(repo)
        store.dispatch(StudioAction.AddSample("coat"))
        assertTrue(store.messages.first().isError)
        assertTrue(repo.data.value.items.isEmpty())
        val issuedIds = next
        repo.failWrites = false
        store.dispatch(StudioAction.AddSample("coat"))
        assertFalse(store.messages.first().isError)
        assertEquals(issuedIds, next)
        assertEquals(1, repo.data.value.items.size)
        store.close()
    }

    @Test fun saveSkillSnapshotsEditedStepsAndReloadsForGuidance() = runTest(dispatcher) {
        val repo = FakeStudio(); val store = store(repo)
        prepareLesson(store)
        testScheduler.runCurrent()
        store.dispatch(StudioAction.EditStep(0, "Bước đầu đã chỉnh"))
        store.dispatch(StudioAction.LessonTitle("Kỹ năng của tôi"))
        store.dispatch(StudioAction.SaveSkill)
        store.dispatch(StudioAction.SaveSkill)
        assertEquals(1, repo.data.value.skills.size)
        assertEquals(1, repo.writes)
        val skill = repo.data.value.skills.single()
        assertEquals("Bước đầu đã chỉnh", skill.steps.first())
        store.close()
        val reopened = store(repo)
        reopened.dispatch(StudioAction.OpenSkill(skill.id))
        reopened.dispatch(StudioAction.Question("Làm thế nào?"))
        reopened.dispatch(StudioAction.AskSample)
        testScheduler.runCurrent()
        assertTrue(reopened.currentState.answer.contains("Bước đầu đã chỉnh"))
        reopened.dispatch(StudioAction.NextStep)
        assertEquals("", reopened.currentState.answer)
        repeat(skill.steps.size - 1) { reopened.dispatch(StudioAction.NextStep) }
        assertTrue(reopened.currentState.completed)
        reopened.close()
    }

    @Test fun writeFailureKeepsReviewAndDoesNotEmitSuccess() = runTest(dispatcher) {
        val repo = FakeStudio(); val store = store(repo)
        prepareLesson(store)
        testScheduler.runCurrent()
        store.messages.first() // Compile feedback.
        val steps = store.currentState.draftSteps
        repo.failWrites = true
        store.dispatch(StudioAction.SaveSkill)
        assertTrue(store.messages.first().isError)
        assertNull(store.currentState.savedSkillId)
        assertEquals(steps, store.currentState.draftSteps)
        assertTrue(store.currentState.busy.isEmpty())
        repo.failWrites = false
        store.dispatch(StudioAction.SaveSkill)
        assertFalse(store.messages.first().isError)
        assertNotNull(store.currentState.savedSkillId)
        store.close()
    }

    @Test fun loadingAndWriteGuardPreventDuplicateOperations() = runTest(dispatcher) {
        val repo = FakeStudio().apply { gate = CompletableDeferred() }; val store = store(repo)
        assertFalse(store.currentState.loading)
        store.dispatch(StudioAction.AddSample("coat"))
        assertTrue(store.currentState.busy.isNotEmpty())
        store.dispatch(StudioAction.AddSample("coat"))
        store.dispatch(StudioAction.ClearData)
        repo.gate!!.complete(Unit)
        assertEquals(1, repo.writes)
        assertEquals(1, repo.data.value.items.size)
        assertTrue(store.currentState.busy.isEmpty())
        store.close()
    }

    @Test fun blankReviewCannotSaveAndSettingsSurviveClear() = runTest(dispatcher) {
        val repo = FakeStudio(); val store = store(repo)
        prepareLesson(store)
        testScheduler.runCurrent()
        store.dispatch(StudioAction.EditStep(0, "  "))
        assertFalse(store.currentState.canSaveSkill)
        store.dispatch(StudioAction.SaveSkill)
        assertTrue(repo.data.value.skills.isEmpty())
        store.dispatch(StudioAction.Appearance(dark = false, glass = false))
        store.dispatch(StudioAction.AddSample("coat"))
        store.dispatch(StudioAction.ClearData)
        assertTrue(store.currentState.data.items.isEmpty())
        assertEquals(StudioSettings(false, false), store.currentState.data.settings)
        store.close()
    }

    @Test fun storageOpenFailureCanRecoverWithoutDiscardingDraft() = runTest(dispatcher) {
        val repo = FakeStudio(); var fail = true
        val store = StudioStore({ if (fail) error("unavailable") else repo }, { "draft" }, { 0L }, dispatcher)
        assertTrue(store.messages.first().isError)
        assertFalse(store.currentState.loading)
        store.dispatch(StudioAction.OutfitNotes("Giữ nội dung này"))
        fail = false
        store.dispatch(StudioAction.Retry)
        assertNull(store.currentState.error)
        assertEquals("Giữ nội dung này", store.currentState.outfitNotes)
        store.close()
    }
}
