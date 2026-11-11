package com.example.kmmbase

import com.example.kmmbase.data.repository.normalizeShadowOrigin
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.ShadowRepository
import com.example.kmmbase.presentation.shadow.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ShadowStoreTest {
    private class FakeShadow : ShadowRepository {
        var closed = false
        var accessRequired = false
        var enteredCode: String? = null
        var askCount = 0
        var answerGate: CompletableDeferred<ShadowAnswer>? = null
        var skillGate: CompletableDeferred<List<ShadowSkill>>? = null
        override suspend fun health() = ShadowHealth("mock", accessRequired)
        override suspend fun authenticate(code: String) { enteredCode = code }
        override suspend fun skills() = skillGate?.await() ?: listOf(ShadowSkill("stand", "Lắp giá", "", "Tuấn", 1))
        override suspend fun lesson(id: String) = ShadowLesson(id, "Lắp giá", listOf(ShadowStep("one", "Đặt đế", "", "")))
        override suspend fun ask(skillId: String, stepId: String, question: String): ShadowAnswer {
            askCount++
            return answerGate?.await() ?: ShadowAnswer("Đặt đế lên bàn.", "fallback")
        }
        override suspend fun deleteSkill(id: String) = Unit
        override suspend fun wardrobe() = emptyList<WardrobeItem>()
        override suspend fun suggest(event: String, notes: String, city: String) =
            ShadowSuggestions(emptyList(), "mock")
        override suspend fun rules() = emptyList<ShadowRule>()
        override suspend fun addRule(text: String, taughtBy: String) = Unit
        override suspend fun compileSkill(request: TeachRequest) =
            TeachDraft("draft", request.title, emptyList(), "{}")
        override suspend fun teachProgress(jobId: String) = "learning"
        override suspend fun saveSkill(draft: TeachDraft) = draft.skillId
        override suspend fun tryonConfig() = TryonConfig(false, "mock", 30)
        override suspend fun tryonPhoto(personImage: String, itemIds: List<String>) =
            TryonResult("", "mock", 0.0)
        override fun mediaUrl(path: String) = path
        override fun mediaHeaders() = emptyMap<String, String>()
        override fun openSession(): com.example.kmmbase.domain.repository.ShadowSessionSocket =
            throw UnsupportedOperationException("not used in these tests")
        override fun close() { closed = true }
    }

    @Test fun originsRejectCredentialsPathsAndNonHttps() {
        assertEquals("https://shadow.example.com", normalizeShadowOrigin(" https://shadow.example.com/ "))
        listOf("http://shadow.example.com", "https://secret@shadow.example.com", "https://shadow.example.com/api",
            "https://shadow.example.com/?code=secret", "https://shadow.example.com/#/teach", "not a URL").forEach {
            assertFailsWith<IllegalArgumentException> { normalizeShadowOrigin(it) }
        }
    }

    @Test fun authenticatesBeforeLoadingAndLabelsFallbackHonestly() = runTest {
        val repo = FakeShadow().apply { accessRequired = true }
        val store = ShadowStore({ _, _ -> repo }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.Connect("https://one.example", " code "))
        assertEquals("code", repo.enteredCode)
        assertTrue(store.currentState.connected)
        assertEquals("Demo · kết quả giả lập", store.currentState.modeLabel)
        store.dispatch(ShadowAction.OpenLesson("stand"))
        store.dispatch(ShadowAction.Ask("one", "Làm sao?"))
        assertEquals("Dự phòng · không phải kết quả AI", store.currentState.answerLabel)
        store.dispatch(ShadowAction.ClearAnswer)
        assertNull(store.currentState.answer)
        store.close()
        assertTrue(repo.closed)
    }

    @Test fun rejectsDuplicateRequestsAndDoesNotPublishAfterClosingLesson() = runTest {
        val repo = FakeShadow().apply { answerGate = CompletableDeferred() }
        val store = ShadowStore({ _, _ -> repo }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.Connect("https://one.example", ""))
        store.dispatch(ShadowAction.OpenLesson("stand"))
        store.dispatch(ShadowAction.Ask("one", "Làm sao?"))
        store.dispatch(ShadowAction.Ask("one", "Làm sao?"))
        assertEquals(1, repo.askCount)
        store.dispatch(ShadowAction.CloseLesson)
        repo.answerGate!!.complete(ShadowAnswer("Late", "ai"))
        assertNull(store.currentState.lesson)
        assertNull(store.currentState.answer)
        assertEquals("", store.currentState.busy)
        store.close()
    }

    @Test fun changingServerCancelsOldConnectionAndClearsOldData() = runTest {
        val old = FakeShadow().apply { skillGate = CompletableDeferred() }
        val fresh = FakeShadow()
        val store = ShadowStore({ endpoint, _ -> if (endpoint.contains("old")) old else fresh }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.Connect("https://old.example", ""))
        store.dispatch(ShadowAction.Connect("https://new.example", ""))
        old.skillGate!!.complete(listOf(ShadowSkill("stale", "Old", "", "", 1)))
        assertTrue(old.closed)
        assertEquals("https://new.example", store.currentState.endpoint)
        assertEquals("stand", store.currentState.skills.single().id)
        assertNull(store.currentState.error)
        store.close()
    }

    @Test fun invalidQuestionsNeverCallBackend() = runTest {
        val repo = FakeShadow()
        val store = ShadowStore({ _, _ -> repo }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.Connect("https://one.example", ""))
        store.dispatch(ShadowAction.OpenLesson("stand"))
        store.dispatch(ShadowAction.Ask("one", " "))
        store.dispatch(ShadowAction.Ask("one", "x".repeat(501)))
        store.dispatch(ShadowAction.Ask("missing", "Làm sao?"))
        assertEquals(0, repo.askCount)
        store.close()
    }
}
