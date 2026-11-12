package com.example.kmmbase

import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.*
import com.example.kmmbase.platform.TeachJobPersistence
import com.example.kmmbase.presentation.teach.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TeachVideoStoreTest {
    private val ticket = TeachVideoTicket("video-job", "private-token", "Gấp quần", "", "Gấp gọn")
    private val draft = TeachDraft("pants", "Gấp quần", listOf(ShadowStep("s1", "Trải quần", "", "")), "{}", compiledBy = "mock")
    private class Persistence : TeachJobPersistence {
        var value: TeachVideoTicket? = null
        override fun load() = value
        override fun save(ticket: TeachVideoTicket?) { value = ticket }
    }
    private class Source : TeachVideoSource {
        var closed = false
        override val sizeBytes = 5L
        override suspend fun readChunk(maxBytes: Int): ByteArray? = null
        override fun close() { closed = true }
    }
    private inner class Repo : TeachRepository {
        var uploads = 0
        var starts = 0
        var status = "queued"
        val gate = CompletableDeferred<Unit>()
        override suspend fun uploadVideo(source: TeachVideoSource, title: String, teacher: String, notes: String): TeachVideoTicket {
            uploads++
            gate.await()
            return ticket
        }
        override suspend fun startVideo(ticket: TeachVideoTicket, retry: Boolean) { starts++ }
        override suspend fun videoStatus(ticket: TeachVideoTicket) = TeachVideoStatus(status, draft = if (status == "ready") draft else null)
        override suspend fun cancelVideo(ticket: TeachVideoTicket) { status = "cancelled" }
        override suspend fun deleteVideo(ticket: TeachVideoTicket) { status = "deleted" }
        override suspend fun compileSkill(request: TeachRequest): TeachDraft = error("not used")
        override suspend fun teachProgress(jobId: String) = ""
        override suspend fun saveSkill(draft: TeachDraft) = draft.skillId
    }
    private fun TestScope.store(repo: Repo, persistence: Persistence) = TeachStore(
        { repo }, { null }, { 0L }, { "id" }, UnconfinedTestDispatcher(testScheduler), persistence)

    @Test fun uploadIsSingleAndTicketSurvivesClosingTheScreen() = runTest {
        val repo = Repo()
        val persisted = Persistence()
        val store = store(repo, persisted)
        store.dispatch(TeachAction.Title("Gấp quần"))
        val source = Source()
        val duplicate = Source()
        store.dispatch(TeachAction.UploadVideo(source))
        store.dispatch(TeachAction.UploadVideo(duplicate))
        assertEquals(1, repo.uploads)
        assertTrue(duplicate.closed)
        repo.gate.complete(Unit)
        assertEquals(ticket, persisted.value)
        assertEquals(1, repo.starts)
        store.close()
        assertTrue(source.closed)
        assertEquals(ticket, persisted.value)
        assertEquals("queued", repo.status, "Closing UI does not cancel the server job")
    }

    @Test fun reopenReadsCompletedDraftWithoutCreatingAnotherJob() = runTest {
        val repo = Repo().apply { status = "ready" }
        val persisted = Persistence().apply { value = ticket }
        val store = store(repo, persisted)
        assertTrue(store.currentState.hasVideoJob)
        store.dispatch(TeachAction.ResumeVideo)
        assertEquals(TeachPhase.REVIEW, store.currentState.phase)
        assertEquals(draft, store.currentState.draft)
        assertEquals(0, repo.starts)
        assertEquals(0, repo.uploads)
        store.close()
    }

    @Test fun cancelledJobRetainsTicketUntilExplicitDelete() = runTest {
        val repo = Repo()
        val persisted = Persistence().apply { value = ticket }
        val store = store(repo, persisted)
        store.dispatch(TeachAction.CancelVideo)
        assertEquals("cancelled", repo.status)
        assertEquals(ticket, persisted.value)
        store.dispatch(TeachAction.DeleteVideo)
        assertNull(persisted.value)
        assertFalse(store.currentState.hasVideoJob)
        store.close()
    }

    @Test fun closingDuringUploadReleasesNativeFileAndDoesNotStartJob() = runTest {
        val repo = Repo()
        val persisted = Persistence()
        val store = store(repo, persisted)
        store.dispatch(TeachAction.Title("Test"))
        val source = Source()
        store.dispatch(TeachAction.UploadVideo(source))
        store.close()
        repo.gate.complete(Unit)
        assertTrue(source.closed)
        assertNull(persisted.value)
        assertEquals(0, repo.starts)
    }
}
