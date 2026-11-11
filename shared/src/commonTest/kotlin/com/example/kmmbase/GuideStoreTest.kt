package com.example.kmmbase

import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.ShadowSessionSocket
import com.example.kmmbase.presentation.guide.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GuideStoreTest {
    private class FakeSocket : ShadowSessionSocket {
        val sink = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)
        override val events = sink
        val frames = mutableListOf<Pair<String, Long>>()
        var started: String? = null
        var stopped = false
        var manualSteps = mutableListOf<Boolean>()
        var closed = false
        override suspend fun start(skillId: String) { started = skillId }
        override suspend fun sendFrame(image: String, timestampMs: Long, trigger: String) {
            frames += trigger to timestampMs
        }
        override suspend fun stop() { stopped = true }
        override suspend fun stepManually(forward: Boolean) { manualSteps += forward }
        override fun close() { closed = true }
    }

    private class Harness {
        val socket = FakeSocket()
        val spoken = mutableListOf<String>()
        var interruptions = 0
        var clock = 10_000L
        var captures = 0
        val store = GuideStore(
            socketFactory = { socket },
            captureFrame = { captures++; "data:image/jpeg;base64,AAAA" },
            speak = { spoken += it },
            stopSpeaking = { interruptions++ },
            nowMs = { clock },
            dispatcher = UnconfinedTestDispatcher(),
        )

        suspend fun guiding(stepId: String = "step_1") {
            store.dispatch(GuideAction.Start("lap-gia"))
            socket.sink.emit(hud(stepId))
        }

        fun hud(stepId: String, phase: SessionPhase = SessionPhase.GUIDING) = SessionEvent.Hud(
            phase = phase, skillName = "Lắp giá", tasks = emptyList(), activeTaskId = "",
            step = SessionStep(stepId, 0, 3, "Đặt đế", "", "", ""), speechEpoch = 0,
        )
    }

    @Test fun confirmingFrameIsSentImmediatelyDespiteTheMinimumGap() = runTest {
        val harness = Harness()
        harness.guiding()
        val afterStepChange = harness.socket.frames.size
        assertTrue(afterStepChange > 0, "entering a step should send a frame")

        // No time passes, so an ordinary trigger must be throttled...
        harness.socket.sink.emit(SessionEvent.VisionResult(confirming = false, latencyMs = 500, done = false, hint = ""))
        assertEquals(afterStepChange, harness.socket.frames.size)

        // ...but `confirming` bypasses the gap, because the server judges the next frame at once.
        harness.socket.sink.emit(SessionEvent.VisionResult(confirming = true, latencyMs = 500, done = true, hint = ""))
        assertEquals(afterStepChange + 1, harness.socket.frames.size)
        assertEquals("confirm", harness.socket.frames.last().first)
    }

    @Test fun staleSpeechEpochsAreDroppedAndNewerOnesInterrupt() = runTest {
        val harness = Harness()
        harness.guiding()
        val baseline = harness.interruptions

        harness.socket.sink.emit(SessionEvent.Speech("Bước một", epoch = 5, kind = "enter"))
        assertEquals(listOf("Bước một"), harness.spoken)
        assertEquals(baseline + 1, harness.interruptions)

        // Same epoch replayed after a reconnect, and an older one: both must stay silent.
        harness.socket.sink.emit(SessionEvent.Speech("Bước một", epoch = 5, kind = "enter"))
        harness.socket.sink.emit(SessionEvent.Speech("Câu cũ", epoch = 4, kind = "hint"))
        assertEquals(listOf("Bước một"), harness.spoken)
        assertEquals(baseline + 1, harness.interruptions, "stale lines must not interrupt playback")

        harness.socket.sink.emit(SessionEvent.Speech("Tốt lắm", epoch = 6, kind = "success"))
        assertEquals(listOf("Bước một", "Tốt lắm"), harness.spoken)
        assertEquals(baseline + 2, harness.interruptions)
    }

    @Test fun periodicFramesRespectTheMinimumGap() = runTest {
        val harness = Harness()
        harness.guiding()
        val sent = harness.socket.frames.size

        harness.socket.sink.emit(SessionEvent.VisionResult(false, 400, false, ""))
        assertEquals(sent, harness.socket.frames.size)

        harness.clock += 1_300 // past MIN_GAP_MS
        harness.socket.sink.emit(SessionEvent.VisionResult(true, 400, true, ""))
        assertEquals(sent + 1, harness.socket.frames.size)
    }

    @Test fun framesStopOnceTheSkillCompletes() = runTest {
        val harness = Harness()
        harness.guiding()
        val sent = harness.socket.frames.size
        harness.socket.sink.emit(harness.hud("step_1", SessionPhase.COMPLETED))
        assertTrue(harness.store.currentState.completed)

        harness.clock += 5_000
        harness.socket.sink.emit(SessionEvent.VisionResult(true, 400, true, ""))
        assertEquals(sent, harness.socket.frames.size, "a completed session must not keep sending frames")
    }

    @Test fun changingStepClearsThePreviousHint() = runTest {
        val harness = Harness()
        harness.guiding("step_1")
        harness.socket.sink.emit(SessionEvent.VisionResult(false, 400, false, "Lật mặt cao su xuống"))
        assertEquals("Lật mặt cao su xuống", harness.store.currentState.hint)

        harness.socket.sink.emit(harness.hud("step_2"))
        assertEquals("", harness.store.currentState.hint)
    }

    @Test fun errorsSurfaceAndCanBeDismissed() = runTest {
        val harness = Harness()
        harness.guiding()
        harness.socket.sink.emit(SessionEvent.Failure("Mất AI", "vision_unavailable"))
        assertEquals("Mất AI", harness.store.currentState.error)
        assertFalse(harness.store.currentState.judging)

        harness.store.dispatch(GuideAction.DismissError)
        assertNull(harness.store.currentState.error)
    }

    @Test fun debugStepsReachTheServerForTheOnStageRescueButtons() = runTest {
        val harness = Harness()
        harness.guiding()
        harness.store.dispatch(GuideAction.Step(forward = true))
        harness.store.dispatch(GuideAction.Step(forward = false))
        assertEquals(listOf(true, false), harness.socket.manualSteps)
    }
}
