package com.example.kmmbase

import com.example.kmmbase.domain.model.SavedLook
import com.example.kmmbase.domain.repository.LookRepository
import com.example.kmmbase.presentation.looks.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LookStoreTest {
    private class FakeLooks : LookRepository {
        val records = MutableStateFlow<List<SavedLook>>(emptyList())
        var failWrite = false
        var gate: CompletableDeferred<Unit>? = null
        var saves = 0
        override fun observeAll() = records
        override suspend fun save(look: SavedLook) {
            gate?.await()
            if (failWrite) error("disk full")
            saves++
            records.value = (records.value.filterNot { it.id == look.id } + look).sortedByDescending { it.createdAt }
        }
        override suspend fun delete(id: String) {
            if (failWrite) error("disk unavailable")
            records.value = records.value.filterNot { it.id == id }
        }
    }
    private val dispatcher = UnconfinedTestDispatcher()
    private var id = 0
    private fun store(repo: FakeLooks) = LookStore({ repo }, { "id-${++id}" }, { 1234L }, dispatcher)

    @Test fun captureIsAnImmutableSnapshotAndSaveIsIdempotent() = runTest(dispatcher) {
        val repo = FakeLooks()
        val store = store(repo)
        store.dispatch(LookAction.Compare)
        store.dispatch(LookAction.Flip)
        store.dispatch(LookAction.Capture)
        val capture = store.currentState.capture!!
        store.dispatch(LookAction.Compare)
        store.dispatch(LookAction.SaveCapture)
        store.dispatch(LookAction.SaveCapture)
        assertEquals(capture, repo.records.value.single())
        assertTrue(capture.before && capture.flipped)
        assertFalse(store.currentState.outfitSaved)
        assertEquals(1, repo.saves)
        store.close()
    }

    @Test fun writeFailureKeepsCaptureAndRetryUsesSameId() = runTest(dispatcher) {
        val repo = FakeLooks().apply { failWrite = true }
        val store = store(repo)
        store.dispatch(LookAction.Capture)
        val capture = store.currentState.capture
        store.dispatch(LookAction.SaveCapture)
        assertNotNull(store.currentState.error)
        assertTrue(store.messages.first().isError)
        assertNull(store.currentState.savedId)
        assertEquals(capture, store.currentState.capture)
        repo.failWrite = false
        store.dispatch(LookAction.SaveCapture)
        assertFalse(store.messages.first().isError)
        assertEquals(capture!!.id, store.currentState.savedId)
        assertNull(store.currentState.error)
        store.close()
    }

    @Test fun pendingWriteBlocksRepeatedCaptureAndSave() = runTest(dispatcher) {
        val repo = FakeLooks().apply { gate = CompletableDeferred() }
        val store = store(repo)
        store.dispatch(LookAction.Capture)
        val capture = store.currentState.capture
        store.dispatch(LookAction.SaveCapture)
        assertTrue(store.currentState.saving)
        store.dispatch(LookAction.Capture)
        store.dispatch(LookAction.SaveCapture)
        store.dispatch(LookAction.SelectPreset("blouse"))
        assertEquals(capture, store.currentState.capture)
        repo.gate!!.complete(Unit)
        assertFalse(store.currentState.saving)
        assertEquals(1, repo.saves)
        store.close()
    }

    @Test fun historyReopensFiltersReusesAndDeletesOnlyChosenRecord() = runTest(dispatcher) {
        val repo = FakeLooks()
        val first = store(repo)
        first.dispatch(LookAction.SaveOutfit)
        first.dispatch(LookAction.SaveOutfit)
        assertEquals(1, repo.saves)
        first.dispatch(LookAction.SelectPreset("blouse"))
        first.dispatch(LookAction.Capture)
        first.dispatch(LookAction.SaveCapture)
        first.close()
        val reopened = store(repo)
        assertEquals(2, reopened.currentState.records.size)
        reopened.dispatch(LookAction.Filter("tryon"))
        val saved = reopened.currentState.filteredRecords.single()
        reopened.dispatch(LookAction.Reuse(saved.id))
        assertEquals("blouse", reopened.currentState.preset)
        assertNull(reopened.currentState.capture)
        repo.failWrite = true
        reopened.dispatch(LookAction.Delete(saved.id))
        assertEquals(2, reopened.currentState.records.size)
        repo.failWrite = false
        reopened.dispatch(LookAction.Delete(saved.id))
        assertEquals("outfit", reopened.currentState.records.single().kind)
        reopened.close()
    }

    @Test fun failedOpenCanRecoverAndUnknownPresetsAreRejected() = runTest(dispatcher) {
        val repo = FakeLooks()
        var fail = true
        val store = LookStore({ if (fail) error("open failed") else repo }, { "id" }, { 1234L }, dispatcher)
        assertNotNull(store.currentState.error)
        assertFalse(store.currentState.loading)
        fail = false
        store.dispatch(LookAction.Retry)
        assertNull(store.currentState.error)
        store.dispatch(LookAction.SelectPreset("unknown"))
        assertEquals("coat", store.currentState.preset)
        store.close()
    }
}
