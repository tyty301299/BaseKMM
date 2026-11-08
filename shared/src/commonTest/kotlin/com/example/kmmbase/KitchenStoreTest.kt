package com.example.kmmbase

import com.example.kmmbase.data.remote.parseLiveMessage
import com.example.kmmbase.data.remote.parseScan
import com.example.kmmbase.data.remote.parseVerification
import com.example.kmmbase.data.remote.setupMessage
import com.example.kmmbase.data.remote.toolResponseMessage
import com.example.kmmbase.domain.device.KitchenDevice
import com.example.kmmbase.domain.model.AppError
import com.example.kmmbase.domain.model.DetectedObject
import com.example.kmmbase.domain.model.SceneScan
import com.example.kmmbase.domain.model.StepVerification
import com.example.kmmbase.domain.model.VerificationStatus
import com.example.kmmbase.domain.model.ZoneBox
import com.example.kmmbase.domain.model.VerificationStatus.DONE
import com.example.kmmbase.domain.model.VerificationStatus.NOT_DONE
import com.example.kmmbase.domain.model.VerificationStatus.UNCLEAR
import com.example.kmmbase.domain.repository.KitchenRepository
import com.example.kmmbase.domain.repository.VoiceConnector
import com.example.kmmbase.domain.repository.VoiceEvent
import com.example.kmmbase.domain.repository.VoiceSession
import com.example.kmmbase.presentation.kitchen.KitchenAction
import com.example.kmmbase.presentation.kitchen.KitchenPhase
import com.example.kmmbase.presentation.kitchen.KitchenStore
import com.example.kmmbase.presentation.kitchen.OutputKind
import com.example.kmmbase.presentation.kitchen.VoiceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KitchenStoreTest {
    private class FakeDevice(var frame: String? = "frame-1") : KitchenDevice {
        var micOn = false
        var released = false
        val played = mutableListOf<String>()
        var playbackStops = 0
        override fun latestFrameJpegBase64() = frame
        override fun startMicrophone(onChunk: (String) -> Unit) { micOn = true }
        override fun stopMicrophone() { micOn = false }
        override fun playAudio(pcmBase64: String) { played += pcmBase64 }
        override fun stopPlayback() { playbackStops++ }
        override fun shutdown() { released = true }
    }

    private class FakeRepository(vararg results: VerificationStatus) : KitchenRepository {
        val queue = results.toMutableList()
        val checkedSteps = mutableListOf<String>()
        var fail = false
        var located: ZoneBox? = null
        var misplaced: List<ZoneBox> = emptyList()
        val locatedSteps = mutableListOf<String>()
        override suspend fun verify(stepId: String, imageBase64: String): StepVerification {
            if (fail) error("offline")
            checkedSteps += stepId
            return StepVerification(queue.removeAt(0), "msg", misplaced)
        }
        var objects: List<DetectedObject> = emptyList()
        override suspend fun scanScene(stepId: String, imageBase64: String): SceneScan {
            locatedSteps += stepId
            if (fail) error("offline")
            return SceneScan(located, objects)
        }
    }

    private class FakeSession : VoiceSession {
        val incoming = Channel<VoiceEvent>(Channel.UNLIMITED)
        val texts = mutableListOf<String>()
        val toolResults = mutableListOf<Pair<String, Map<String, String>>>()
        var closed = false
        override val events: Flow<VoiceEvent> = incoming.receiveAsFlow()
        override suspend fun sendAudio(pcmBase64: String) = Unit
        override suspend fun sendFrame(jpegBase64: String) = Unit
        override suspend fun sendText(text: String) { texts += text }
        override suspend fun sendCheckResult(callId: String, result: Map<String, String>) {
            toolResults += callId to result
        }
        override fun close() { closed = true }
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val noVoice = VoiceConnector { error("voice unavailable") }

    private fun store(
        repository: KitchenRepository,
        device: KitchenDevice = FakeDevice(),
        connector: VoiceConnector = noVoice,
    ) = KitchenStore(repository, connector, device, dispatcher)

    @Test fun completesAllStepsAndKeepsBeforeAndAfterPhotos() = runTest(dispatcher) {
        val repository = FakeRepository(DONE, DONE, DONE)
        val device = FakeDevice()
        val store = store(repository, device)
        store.dispatch(KitchenAction.Start)
        assertEquals("frame-1", store.currentState.beforeImage)

        device.frame = "frame-2"
        repeat(3) { store.dispatch(KitchenAction.Check) }

        assertEquals(listOf("cups", "bowls", "spoons"), repository.checkedSteps)
        assertEquals(KitchenPhase.COMPLETED, store.currentState.phase)
        assertEquals("frame-2", store.currentState.afterImage)
        store.close()
        assertTrue(device.released)
    }

    @Test fun wrongOrUnclearResultStaysOnTheStep() = runTest(dispatcher) {
        val store = store(FakeRepository(NOT_DONE, UNCLEAR, DONE))
        store.dispatch(KitchenAction.Start)
        store.dispatch(KitchenAction.Check)
        assertEquals(0, store.currentState.stepIndex)
        assertEquals(NOT_DONE, store.currentState.verification?.status)
        store.dispatch(KitchenAction.Check)
        assertEquals(0, store.currentState.stepIndex)
        store.dispatch(KitchenAction.Check)
        assertEquals(1, store.currentState.stepIndex)
        store.close()
    }

    @Test fun networkFailureKeepsStepAndAllowsRetry() = runTest(dispatcher) {
        val repository = FakeRepository(DONE)
        repository.fail = true
        val store = store(repository)
        store.dispatch(KitchenAction.Start)
        store.dispatch(KitchenAction.Check)
        assertEquals(AppError.NETWORK, store.currentState.error)
        assertEquals(0, store.currentState.stepIndex)
        assertFalse(store.currentState.isChecking)

        repository.fail = false
        store.dispatch(KitchenAction.Check)
        assertNull(store.currentState.error)
        assertEquals(1, store.currentState.stepIndex)
        store.close()
    }

    @Test fun cannotStartWithoutACameraFrame() = runTest(dispatcher) {
        val store = store(FakeRepository(), FakeDevice(frame = null))
        store.dispatch(KitchenAction.Start)
        assertEquals(KitchenPhase.READY, store.currentState.phase)
        assertEquals(AppError.CAMERA, store.currentState.error)
        store.close()
    }

    @Test fun voiceFailureLeavesButtonCheckWorking() = runTest(dispatcher) {
        val store = store(FakeRepository(DONE))
        store.dispatch(KitchenAction.Start)
        assertEquals(VoiceStatus.ERROR, store.currentState.voice)
        store.dispatch(KitchenAction.Check)
        assertEquals(1, store.currentState.stepIndex)
        store.close()
    }

    @Test fun voiceCheckAnswersTheToolCallWithTheNextStep() = runTest(dispatcher) {
        val session = FakeSession()
        val device = FakeDevice()
        val store = store(FakeRepository(DONE), device, VoiceConnector { session })
        store.dispatch(KitchenAction.Start)
        session.incoming.send(VoiceEvent.Ready)
        assertEquals(VoiceStatus.LISTENING, store.currentState.voice)
        assertTrue(device.micOn)
        assertTrue(session.texts.single().contains("cốc"))

        session.incoming.send(VoiceEvent.CheckRequested("call-1"))
        val (id, result) = session.toolResults.single()
        assertEquals("call-1", id)
        assertEquals("DONE", result["status"])
        assertTrue(result.getValue("next").contains("bát"))
        store.close()
        assertFalse(device.micOn)
        assertTrue(session.closed)
    }

    @Test fun agentAudioIsPlayedAndDroppedOnInterrupt() = runTest(dispatcher) {
        val session = FakeSession()
        val device = FakeDevice()
        val store = store(FakeRepository(), device, VoiceConnector { session })
        store.dispatch(KitchenAction.Start)
        session.incoming.send(VoiceEvent.Audio("pcm"))
        session.incoming.send(VoiceEvent.Interrupted)
        assertEquals(listOf("pcm"), device.played)
        assertEquals(1, device.playbackStops)
        store.close()
    }

    @Test fun droppedConnectionCanBeRetried() = runTest(dispatcher) {
        val sessions = mutableListOf<FakeSession>()
        val store = store(FakeRepository(), FakeDevice(), VoiceConnector { FakeSession().also { sessions += it } })
        store.dispatch(KitchenAction.Start)
        sessions[0].incoming.close()
        assertEquals(VoiceStatus.ERROR, store.currentState.voice)
        assertTrue(sessions[0].closed)
        store.dispatch(KitchenAction.RetryVoice)
        assertEquals(2, sessions.size)
        store.close()
    }

    @Test fun restartReturnsToTheBeginning() = runTest(dispatcher) {
        val store = store(FakeRepository(DONE))
        store.dispatch(KitchenAction.Start)
        store.dispatch(KitchenAction.Check)
        store.dispatch(KitchenAction.Restart)
        assertEquals(KitchenPhase.READY, store.currentState.phase)
        assertEquals(0, store.currentState.stepIndex)
        assertNull(store.currentState.beforeImage)
        store.close()
    }

    @Test fun everyActionProducesAnOutput() = runTest(dispatcher) {
        val repository = FakeRepository(NOT_DONE, DONE, DONE, DONE)
        val store = store(repository)
        store.dispatch(KitchenAction.Start)
        assertEquals(OutputKind.INFO, store.currentState.output?.kind)
        assertTrue(store.currentState.output!!.message.contains("cốc"))

        store.dispatch(KitchenAction.Check)
        assertEquals(OutputKind.WARNING, store.currentState.output?.kind)

        store.dispatch(KitchenAction.Check)
        assertEquals(OutputKind.SUCCESS, store.currentState.output?.kind)
        assertTrue(store.currentState.output!!.message.contains("bát"))

        store.dispatch(KitchenAction.RetryVoice)
        assertEquals("Đang kết nối lại giọng nói.", store.currentState.output?.message)

        repository.fail = true
        store.dispatch(KitchenAction.Check)
        assertEquals(OutputKind.ERROR, store.currentState.output?.kind)

        repository.fail = false
        store.dispatch(KitchenAction.Restart)
        assertEquals("Đã làm lại từ đầu.", store.currentState.output?.message)
        store.close()
    }

    @Test fun missingCameraFrameProducesAnErrorOutput() = runTest(dispatcher) {
        val store = store(FakeRepository(), FakeDevice(frame = null))
        store.dispatch(KitchenAction.Start)
        assertEquals(OutputKind.ERROR, store.currentState.output?.kind)
        store.close()
    }

    @Test fun drawsDefaultZoneThenLocatedZoneForEachStep() = runTest(dispatcher) {
        val located = ZoneBox(0.1, 0.1, 0.2, 0.2)
        val repository = FakeRepository(DONE)
        val store = store(repository)
        store.dispatch(KitchenAction.Start)
        assertEquals(store.currentState.steps[0].defaultZone, store.currentState.targetZone)

        repository.located = located
        store.dispatch(KitchenAction.Check)
        assertEquals(listOf("cups", "bowls"), repository.locatedSteps)
        assertEquals(located, store.currentState.targetZone)
        store.close()
    }

    @Test fun failedLocateKeepsDefaultZone() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.fail = true
        val store = store(repository)
        store.dispatch(KitchenAction.Start)
        assertEquals(store.currentState.steps[0].defaultZone, store.currentState.targetZone)
        store.close()
    }

    @Test fun misplacedItemsAreShownUntilTheStepPasses() = runTest(dispatcher) {
        val box = ZoneBox(0.3, 0.3, 0.4, 0.4)
        val repository = FakeRepository(NOT_DONE, DONE)
        repository.misplaced = listOf(box)
        val store = store(repository)
        store.dispatch(KitchenAction.Start)
        store.dispatch(KitchenAction.Check)
        assertEquals(listOf(box), store.currentState.misplaced)

        repository.misplaced = emptyList()
        store.dispatch(KitchenAction.Check)
        assertTrue(store.currentState.misplaced.isEmpty())
        store.close()
    }

    @Test fun showsDetectedObjectsAndRescansPeriodically() = runTest(dispatcher) {
        val box = ZoneBox(0.1, 0.1, 0.2, 0.2)
        val repository = FakeRepository()
        repository.objects = listOf(DetectedObject("cốc", box), DetectedObject("cốc", box), DetectedObject("bát", box))
        val store = store(repository)
        store.dispatch(KitchenAction.Start)
        assertEquals("2 cốc, 1 bát", store.currentState.objectSummary)
        assertEquals(1, repository.locatedSteps.size)

        repository.objects = listOf(DetectedObject("thìa", box))
        testScheduler.advanceTimeBy(5_001)
        assertEquals(2, repository.locatedSteps.size)
        assertEquals("1 thìa", store.currentState.objectSummary)
        store.close()
    }

    @Test fun failedScanKeepsLastObjects() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.objects = listOf(DetectedObject("cốc", ZoneBox(0.1, 0.1, 0.2, 0.2)))
        val store = store(repository)
        store.dispatch(KitchenAction.Start)
        repository.fail = true
        testScheduler.advanceTimeBy(5_001)
        assertEquals("1 cốc", store.currentState.objectSummary)
        store.close()
    }

    @Test fun completionStopsScanningAndClearsObjects() = runTest(dispatcher) {
        val repository = FakeRepository(DONE, DONE, DONE)
        repository.objects = listOf(DetectedObject("cốc", ZoneBox(0.1, 0.1, 0.2, 0.2)))
        val store = store(repository)
        store.dispatch(KitchenAction.Start)
        repeat(3) { store.dispatch(KitchenAction.Check) }
        val scans = repository.locatedSteps.size
        assertTrue(store.currentState.objects.isEmpty())
        testScheduler.advanceTimeBy(20_000)
        assertEquals(scans, repository.locatedSteps.size)
        store.close()
    }

    @Test fun parsesScanAndSkipsInvalidObjects() {
        val body = Json.parseToJsonElement(
            """{"target":null,"objects":[{"label":"cốc","box":[0.1,0.2,0.3,0.4]},{"label":"bát"},"x"]}""",
        ).jsonObject
        val scan = parseScan(body)
        assertNull(scan.target)
        assertEquals(listOf(DetectedObject("cốc", ZoneBox(0.1, 0.2, 0.3, 0.4))), scan.objects)
    }

    @Test fun parsesVerificationBoxesAndSkipsInvalidOnes() {
        val body = Json.parseToJsonElement(
            """{"status":"NOT_DONE","message":"m","misplaced":[[0.1,0.2,0.3,0.4],[1,2],"x"]}""",
        ).jsonObject
        val result = parseVerification(body)
        assertEquals(NOT_DONE, result.status)
        assertEquals(listOf(ZoneBox(0.1, 0.2, 0.3, 0.4)), result.misplaced)
    }

    @Test fun parsesGeminiServerMessages() {
        assertEquals(listOf<VoiceEvent>(VoiceEvent.Ready), parseLiveMessage("""{"setupComplete":{}}"""))
        assertEquals(
            listOf(VoiceEvent.Caption("Chào"), VoiceEvent.Audio("QUJD"), VoiceEvent.TurnComplete),
            parseLiveMessage(
                """{"serverContent":{"outputTranscription":{"text":"Chào"},
                "modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"QUJD"}}]},
                "turnComplete":true}}""",
            ),
        )
        assertEquals(
            listOf<VoiceEvent>(VoiceEvent.Interrupted),
            parseLiveMessage("""{"serverContent":{"interrupted":true}}"""),
        )
        assertEquals(
            listOf<VoiceEvent>(VoiceEvent.CheckRequested("a1")),
            parseLiveMessage("""{"toolCall":{"functionCalls":[{"id":"a1","name":"check_current_step"},{"id":"b","name":"other"}]}}"""),
        )
        assertEquals(emptyList(), parseLiveMessage("""{"usageMetadata":{"totalTokenCount":3}}"""))
    }

    @Test fun buildsGeminiClientMessages() {
        val setup = setupMessage("gemini-3.8-live", "prompt").toString()
        assertTrue(""""model":"models/gemini-3.8-live"""" in setup)
        assertTrue("slidingWindow" in setup)
        assertEquals(
            """{"toolResponse":{"functionResponses":[{"id":"a1","name":"check_current_step","response":{"status":"DONE"}}]}}""",
            toolResponseMessage("a1", mapOf("status" to "DONE")).toString(),
        )
    }
}
