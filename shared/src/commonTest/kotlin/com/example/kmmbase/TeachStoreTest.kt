package com.example.kmmbase

import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.ShadowRepository
import com.example.kmmbase.domain.repository.ShadowSessionSocket
import com.example.kmmbase.presentation.teach.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TeachStoreTest {
    private class FakeRepo : ShadowRepository {
        var compiled: TeachRequest? = null
        var savedRaw: String? = null
        var draftRaw = """{"id":"pha-tra","display_name":"Pha trà","steps":[
            {"id":"step_1","instruction":"Rót nước","tip":"Nước nóng","detector_key":"step_1"},
            {"id":"step_2","instruction":"Cho trà","tip":"","detector_key":"step_2"}]}"""
        override suspend fun health() = ShadowHealth("ai", true)
        override suspend fun authenticate(code: String) = Unit
        override suspend fun skills() = emptyList<ShadowSkill>()
        override suspend fun lesson(id: String) = ShadowLesson(id, "", emptyList())
        override suspend fun ask(skillId: String, stepId: String, question: String) = ShadowAnswer("", "ai")
        override suspend fun deleteSkill(id: String) = Unit
        override suspend fun wardrobe() = emptyList<WardrobeItem>()
        override suspend fun suggest(event: String, notes: String, city: String) =
            ShadowSuggestions(emptyList(), "ai")
        override suspend fun rules() = emptyList<ShadowRule>()
        override suspend fun addRule(text: String, taughtBy: String) = Unit
        override suspend fun compileSkill(request: TeachRequest): TeachDraft {
            compiled = request
            val root = Json.parseToJsonElement(draftRaw).jsonObject
            return TeachDraft(
                root["id"]!!.jsonPrimitive.content,
                root["display_name"]!!.jsonPrimitive.content,
                root["steps"]!!.jsonArray.map {
                    val step = it.jsonObject
                    ShadowStep(
                        step["id"]!!.jsonPrimitive.content,
                        step["instruction"]!!.jsonPrimitive.content,
                        step["tip"]?.jsonPrimitive?.content.orEmpty(), "",
                    )
                },
                draftRaw,
            )
        }
        override suspend fun teachProgress(jobId: String) = "verifying"
        override suspend fun saveSkill(draft: TeachDraft): String {
            savedRaw = draft.raw
            return draft.skillId
        }
        override suspend fun tryonConfig() = TryonConfig(false, "mock", 30)
        override suspend fun tryonPhoto(personImage: String, itemIds: List<String>) = TryonResult("", "mock", 0.0)
        override fun mediaUrl(path: String) = path
        override fun mediaHeaders() = emptyMap<String, String>()
        override fun openSession(): ShadowSessionSocket = throw UnsupportedOperationException()
        override fun close() = Unit
    }

    /** Shares the test scheduler so `advanceTimeBy` also drives the store's keyframe sampler. */
    private fun TestScope.store(
        repo: FakeRepo?,
        clock: () -> Long,
        captures: () -> String? = { "data:image/jpeg;base64,AA" },
    ) = TeachStore(
        repository = { repo },
        captureFrame = { captures() },
        nowMs = clock,
        newId = { "job-abc123" },
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test fun recordingKeepsKeyframesOnIntervalAndAfterEachSentence() = runTest {
        var now = 0L
        val teach = store(FakeRepo(), { now })
        teach.dispatch(TeachAction.StartRecording)
        assertEquals(1, teach.currentState.frameCount, "a frame is kept when recording starts")

        teach.dispatch(TeachAction.Sentence(0.0, 1.5, "Đầu tiên rót nước nóng"))
        assertEquals(2, teach.currentState.frameCount, "a finished sentence keeps a frame")
        assertEquals(1, teach.currentState.lines.size)

        now = 2_100
        advanceTimeBy(2_100)
        assertTrue(teach.currentState.frameCount >= 3, "the 2 s sampler keeps frames too")
        teach.close()
    }

    @Test fun compileSendsTranscriptFramesAndTheJobId() = runTest {
        val repo = FakeRepo()
        var now = 0L
        val teach = store(repo, { now })
        teach.dispatch(TeachAction.StartRecording)
        teach.dispatch(TeachAction.Sentence(0.0, 2.0, "Rót nước nóng vào cốc"))
        teach.dispatch(TeachAction.StopRecording)
        teach.dispatch(TeachAction.Title("Pha trà"))
        teach.dispatch(TeachAction.Teacher("Tuấn"))
        teach.dispatch(TeachAction.Compile)

        val request = assertNotNull(repo.compiled)
        assertEquals("Pha trà", request.title)
        assertEquals("Tuấn", request.teacherName)
        assertEquals("job-abc123", request.jobId)
        assertEquals(listOf("Rót nước nóng vào cốc"), request.transcript.map { it.text })
        assertTrue(request.frames.isNotEmpty())
        assertTrue(request.frames.all { it.image.startsWith("data:image/jpeg;base64,") })
        assertEquals(TeachPhase.REVIEW, teach.currentState.phase)
        teach.close()
    }

    @Test fun compileNeedsATitleAndSomeDescription() = runTest {
        val repo = FakeRepo()
        val teach = store(repo, { 0 })
        teach.dispatch(TeachAction.StartRecording)
        teach.dispatch(TeachAction.StopRecording)

        teach.dispatch(TeachAction.Compile) // no title yet
        assertNull(repo.compiled)
        assertNotNull(teach.currentState.error)

        // Typed notes stand in for speech recognition, which may be unavailable on a device.
        teach.dispatch(TeachAction.Title("Pha trà"))
        teach.dispatch(TeachAction.Notes("Rót nước rồi cho trà"))
        assertTrue(teach.currentState.canCompile)
        teach.dispatch(TeachAction.Compile)
        assertNotNull(repo.compiled)
        teach.close()
    }

    @Test fun editedInstructionsAreFoldedIntoTheSavedSkillJson() = runTest {
        val repo = FakeRepo()
        val teach = store(repo, { 0 })
        teach.dispatch(TeachAction.StartRecording)
        teach.dispatch(TeachAction.StopRecording)
        teach.dispatch(TeachAction.Title("Pha trà"))
        teach.dispatch(TeachAction.Notes("Rót nước"))
        teach.dispatch(TeachAction.Compile)

        teach.dispatch(TeachAction.EditStep(0, "Rót nước sôi vào cốc"))
        teach.dispatch(TeachAction.Save)

        val saved = Json.parseToJsonElement(assertNotNull(repo.savedRaw)).jsonObject
        val steps = saved["steps"]!!.jsonArray
        assertEquals("Rót nước sôi vào cốc", steps[0].jsonObject["instruction"]!!.jsonPrimitive.content)
        assertEquals("Cho trà", steps[1].jsonObject["instruction"]!!.jsonPrimitive.content)
        // Fields the app never edits must survive the round trip untouched.
        assertEquals("step_1", steps[0].jsonObject["detector_key"]!!.jsonPrimitive.content)
        assertEquals("Nước nóng", steps[0].jsonObject["tip"]!!.jsonPrimitive.content)
        assertEquals(TeachPhase.SAVED, teach.currentState.phase)
        teach.close()
    }

    @Test fun frameCountStaysWithinTheCompilerLimit() = runTest {
        var now = 0L
        val teach = store(FakeRepo(), { now })
        teach.dispatch(TeachAction.StartRecording)
        repeat(200) { index ->
            teach.dispatch(TeachAction.Sentence(index.toDouble(), index + 1.0, "Câu $index"))
        }
        assertTrue(teach.currentState.frameCount <= 64,
            "the compiler rejects more than 64 frames, got ${teach.currentState.frameCount}")
        teach.close()
    }

    @Test fun compilingWithoutAServerReportsItInsteadOfHanging() = runTest {
        val teach = store(null, { 0 })
        teach.dispatch(TeachAction.StartRecording)
        teach.dispatch(TeachAction.StopRecording)
        teach.dispatch(TeachAction.Title("Pha trà"))
        teach.dispatch(TeachAction.Notes("Rót nước"))
        teach.dispatch(TeachAction.Compile)
        assertNotNull(teach.currentState.error)
        assertEquals(TeachPhase.IDLE, teach.currentState.phase)
        teach.close()
    }
}
