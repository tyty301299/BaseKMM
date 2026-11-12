package com.example.kmmbase

import com.example.kmmbase.data.mapper.shadow.toDto
import com.example.kmmbase.data.remote.shadow.normalizeShadowOrigin
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
        var suggestionResult = ShadowSuggestions(emptyList(), "mock")
        var savedDraft: TeachDraft? = null
        var tryonCalls = 0
        var tryonIds = emptyList<String>()
        var tryonGate: CompletableDeferred<TryonResult>? = null
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
            suggestionResult
        override suspend fun rules() = emptyList<ShadowRule>()
        override suspend fun addRule(text: String, taughtBy: String) = Unit
        override suspend fun compileSkill(request: TeachRequest) =
            TeachDraft("draft", request.title, emptyList(), "{}")
        override suspend fun teachProgress(jobId: String) = "learning"
        override suspend fun saveSkill(draft: TeachDraft): String { savedDraft = draft; return draft.skillId }
        override suspend fun tryonConfig() = TryonConfig(false, "mock", 30)
        override suspend fun tryonPhoto(personImage: String, itemIds: List<String>): TryonResult {
            tryonCalls++
            tryonIds = itemIds
            return tryonGate?.await() ?: TryonResult("https://one.example/result.jpg", "mock", 0.0)
        }
        override fun mediaUrl(path: String) = path
        override fun mediaHeaders() = emptyMap<String, String>()
        override fun openSession(): com.example.kmmbase.domain.repository.ShadowSessionSocket =
            throw UnsupportedOperationException("not used in these tests")
        override fun close() { closed = true }
    }

    @Test fun preparesBuiltinGuideOnlyAfterConnectingAndExposesItForNavigation() = runTest {
        val repo = FakeShadow()
        val store = ShadowStore({ _, _ -> repo }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.PrepareBuiltinGuide("fold"))
        assertNull(repo.savedDraft)
        store.dispatch(ShadowAction.Connect("https://one.example", ""))
        store.dispatch(ShadowAction.PrepareBuiltinGuide("fold"))
        assertEquals("shadow-builtin-gap-ao-v1", repo.savedDraft?.skillId)
        assertEquals("shadow-builtin-gap-ao-v1", store.currentState.preparedGuideId)
        val payload = kotlinx.serialization.json.Json.parseToJsonElement(repo.savedDraft!!.toDto().raw) as kotlinx.serialization.json.JsonObject
        val steps = payload["steps"] as kotlinx.serialization.json.JsonArray
        val detectors = payload["detectors"] as kotlinx.serialization.json.JsonObject
        assertEquals(4, steps.size)
        steps.forEachIndexed { index, element ->
            val step = element as kotlinx.serialization.json.JsonObject
            val key = "step_${index + 1}"
            assertEquals("\"$key\"", step["detector_key"].toString())
            assertEquals("\"momentary_true_complete\"", step["evaluation_mode"].toString())
            assertTrue(detectors.containsKey(key))
            if (index < 3) assertEquals("\"step_${index + 2}\"", step["next_step_id"].toString())
            else assertNull(step["next_step_id"])
        }
        store.dispatch(ShadowAction.ClearPreparedGuide)
        assertNull(store.currentState.preparedGuideId)
        store.close()
    }

    @Test fun preparesSeparatePantsGuideWithFourSteps() = runTest {
        val repo = FakeShadow()
        val store = ShadowStore({ _, _ -> repo }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.Connect("https://one.example", ""))
        store.dispatch(ShadowAction.PrepareBuiltinGuide("pants"))
        assertEquals("shadow-builtin-gap-quan-v1", store.currentState.preparedGuideId)
        val payload = kotlinx.serialization.json.Json.parseToJsonElement(repo.savedDraft!!.toDto().raw) as kotlinx.serialization.json.JsonObject
        assertEquals(4, (payload["steps"] as kotlinx.serialization.json.JsonArray).size)
        assertTrue(repo.savedDraft!!.toDto().raw.contains("ống quần"))
        store.close()
    }

    @Test fun rejectsUnknownBuiltinGuide() = runTest {
        val repo = FakeShadow()
        val store = ShadowStore({ _, _ -> repo }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.Connect("https://one.example", ""))
        store.dispatch(ShadowAction.PrepareBuiltinGuide("unknown"))
        assertNull(repo.savedDraft)
        assertNull(store.currentState.preparedGuideId)
        store.close()
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
    @Test fun photoTryonUsesSuggestedItemsAndLabelsMockResult() = runTest {
        val repo = FakeShadow().apply {
            suggestionResult = ShadowSuggestions(listOf(ShadowOutfit("Outfit", "", listOf(
                ShadowOutfitItem("coat", "Coat", "top", "", ""),
                ShadowOutfitItem("coat", "Coat", "top", "", ""),
            ), "")), "ai")
        }
        val store = ShadowStore({ _, _ -> repo }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.Connect("https://one.example", ""))
        store.dispatch(ShadowAction.StylePerson("data:image/jpeg;base64,dGVzdA==", "Work", ""))
        assertEquals(listOf("coat"), repo.tryonIds)
        assertEquals("mock", store.currentState.tryon?.provider)
        assertFalse(store.currentState.tryingOn)
        assertEquals("", store.currentState.busy)
        store.close()
    }

    @Test fun photoTryonCancellationSuppressesLateResultAndDuplicates() = runTest {
        val repo = FakeShadow().apply {
            tryonGate = CompletableDeferred()
            suggestionResult = ShadowSuggestions(listOf(ShadowOutfit("Outfit", "", listOf(
                ShadowOutfitItem("coat", "Coat", "top", "", ""),
            ), "")), "ai")
        }
        val store = ShadowStore({ _, _ -> repo }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.Connect("https://one.example", ""))
        val action = ShadowAction.StylePerson("data:image/jpeg;base64,dGVzdA==", "Work", "")
        store.dispatch(action)
        store.dispatch(action)
        assertEquals(1, repo.tryonCalls)
        store.dispatch(ShadowAction.CancelTryon)
        repo.tryonGate!!.complete(TryonResult("stale", "mock", 0.0))
        assertNull(store.currentState.tryon)
        assertFalse(store.currentState.tryingOn)
        assertEquals("", store.currentState.busy)
        store.close()
    }

    @Test fun emptySuggestionsNeverSubmitPersonPhoto() = runTest {
        val repo = FakeShadow()
        val store = ShadowStore({ _, _ -> repo }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.Connect("https://one.example", ""))
        store.dispatch(ShadowAction.StylePerson("data:image/jpeg;base64,dGVzdA==", "Work", ""))
        assertEquals(0, repo.tryonCalls)
        assertNotNull(store.currentState.error)
        assertFalse(store.currentState.tryingOn)
        store.close()
    }

    @Test fun reconnectReusesSessionCredentialsWithoutPuttingThemInState() = runTest {
        val first = FakeShadow().apply { accessRequired = true }
        val second = FakeShadow().apply { accessRequired = true }
        var calls = 0
        val store = ShadowStore({ _, _ -> if (calls++ == 0) first else second }, { it }, UnconfinedTestDispatcher(testScheduler))
        store.dispatch(ShadowAction.Connect("https://one.example", "session-code"))
        store.dispatch(ShadowAction.Reconnect)
        assertTrue(first.closed)
        assertEquals("session-code", second.enteredCode)
        assertTrue(store.currentState.connected)
        assertFalse(store.currentState.toString().contains("session-code"))
        store.close()
    }

}
