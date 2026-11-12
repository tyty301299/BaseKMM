package com.example.kmmbase

import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.*
import com.example.kmmbase.presentation.livetryon.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTryOnStoreTest {
    private val shirt = WardrobeItem("shirt", "Áo", "top", false, "https://example.com/one.jpg")
    private val second = shirt.copy(id = "second", imageUrl = "https://example.com/two.jpg")

    @Test fun onlyTopsAreEligibleAndUnavailableConfigCannotStart() = runTest {
        val repo = FakeRepository().apply { available = false }
        val store = LiveTryOnStore(repo, FakeEngine(), listOf(shirt, shirt.copy(id = "shoe", category = "shoes")), StandardTestDispatcher(testScheduler), nowMs = { testScheduler.currentTime })
        runCurrent()
        assertEquals(listOf(shirt), store.currentState.garments)
        store.start(); runCurrent()
        assertEquals(0, repo.creations)
        store.close()
    }

    @Test fun duplicateStartDoesNotCreateAnotherBillableSession() = runTest {
        val repo = FakeRepository()
        val engine = FakeEngine()
        val store = LiveTryOnStore(repo, engine, listOf(shirt), StandardTestDispatcher(testScheduler), nowMs = { testScheduler.currentTime })
        runCurrent()
        store.start(); store.start(); runCurrent()
        assertEquals(1, repo.creations)
        assertEquals(LiveTryOnPhase.CONNECTING, store.currentState.phase)
        engine.listener!!.onRemoteVideo(); runCurrent()
        assertEquals(LiveTryOnPhase.LIVE, store.currentState.phase)
        store.close()
    }

    @Test fun callbacksFromClosedGenerationCannotReviveVideo() = runTest {
        val engine = FakeEngine()
        val repo = FakeRepository()
        val store = LiveTryOnStore(repo, engine, listOf(shirt), StandardTestDispatcher(testScheduler), nowMs = { testScheduler.currentTime })
        runCurrent(); store.start(); runCurrent()
        val old = engine.listener!!
        store.stop(); old.onRemoteVideo(); old.onStats(30); old.onFailure("late"); runCurrent()
        assertEquals(LiveTryOnPhase.ENDED, store.currentState.phase)
        assertEquals(0, store.currentState.fps)
        assertTrue(repo.socket.closed)
        store.start(); runCurrent()
        old.onFailure("old session"); runCurrent()
        assertEquals(LiveTryOnPhase.CONNECTING, store.currentState.phase)
        store.close()
    }

    @Test fun cancellationDuringReferenceLoadDoesNotCreateSession() = runTest {
        val repo = FakeRepository().apply { holdReference = true }
        val store = LiveTryOnStore(repo, FakeEngine(), listOf(shirt), StandardTestDispatcher(testScheduler), nowMs = { testScheduler.currentTime })
        runCurrent(); store.start(); runCurrent(); store.stop()
        advanceTimeBy(5000); runCurrent()
        assertEquals(0, repo.creations)
        assertEquals(LiveTryOnPhase.ENDED, store.currentState.phase)
        store.close()
    }

    @Test fun sessionCapReleasesCameraAndControlEvenWithoutFirstFrame() = runTest {
        val repo = FakeRepository().apply { maxSeconds = 3 }
        val engine = FakeEngine()
        val store = LiveTryOnStore(repo, engine, listOf(shirt), StandardTestDispatcher(testScheduler), nowMs = { testScheduler.currentTime })
        runCurrent(); store.start(); runCurrent()
        advanceTimeBy(3000); runCurrent()
        assertEquals(LiveTryOnPhase.ENDED, store.currentState.phase)
        assertTrue(repo.socket.closed)
        assertNull(engine.listener)
        store.close()
    }

    @Test fun missingFirstFrameTimesOutInsteadOfShowingCameraAsAI() = runTest {
        val repo = FakeRepository()
        val store = LiveTryOnStore(repo, FakeEngine(), listOf(shirt), StandardTestDispatcher(testScheduler), nowMs = { testScheduler.currentTime })
        runCurrent(); store.start(); runCurrent()
        advanceTimeBy(25_000); runCurrent()
        assertEquals(LiveTryOnPhase.ERROR, store.currentState.phase)
        assertEquals("", store.currentState.sentGarmentId)
        assertTrue(repo.socket.closed)
        store.close()
    }

    @Test fun selectionIsNotMarkedSentUntilTransmissionCompletes() = runTest {
        val repo = FakeRepository()
        val engine = FakeEngine()
        val store = LiveTryOnStore(repo, engine, listOf(shirt, second), StandardTestDispatcher(testScheduler), nowMs = { testScheduler.currentTime })
        runCurrent(); store.start(); runCurrent(); engine.listener!!.onRemoteVideo(); runCurrent()
        store.select(second.id); store.applySelection(); store.applySelection(); runCurrent()
        assertTrue(store.currentState.changing)
        assertEquals(shirt.id, store.currentState.sentGarmentId)
        assertEquals(1, repo.socket.switches)
        repo.socket.transmitted.complete(Unit); runCurrent()
        assertEquals(second.id, store.currentState.sentGarmentId)
        assertFalse(store.currentState.changing)
        store.close()
    }

    @Test fun timedOutControlHandshakeReleasesSession() = runTest {
        val repo = FakeRepository().apply { socket.timeout = true }
        val store = LiveTryOnStore(repo, FakeEngine(), listOf(shirt), StandardTestDispatcher(testScheduler), nowMs = { testScheduler.currentTime })
        runCurrent(); store.start(); runCurrent(); advanceTimeBy(10); runCurrent()
        assertEquals(LiveTryOnPhase.ERROR, store.currentState.phase)
        assertTrue(repo.socket.closed)
        store.close()
    }

    private class FakeEngine : LiveVideoEngine {
        var listener: LiveVideoListener? = null
        override fun start(session: LiveTryOnSession, listener: LiveVideoListener) { this.listener = listener }
        override fun stop() { listener = null }
    }

    private class FakeControl : LiveTryOnControl {
        override val failures = MutableSharedFlow<String>()
        val transmitted = CompletableDeferred<Unit>()
        var switches = 0
        var closed = false
        var timeout = false
        override suspend fun connect() { if (timeout) withTimeout(1) { delay(100) } }
        override suspend fun switchReference(reference: String) { switches++; transmitted.await() }
        override fun close() { closed = true }
    }

    private class FakeRepository : LiveTryOnRepository {
        val socket = FakeControl()
        var available = true
        var holdReference = false
        var maxSeconds = 120
        var creations = 0
        override suspend fun config() = LiveTryOnConfig(available, maxSeconds, 60, "Unavailable")
        override suspend fun reference(item: WardrobeItem): String { if (holdReference) delay(5000); return "reference" }
        override suspend fun createSession(reference: String): LiveTryOnSession {
            creations++
            return LiveTryOnSession("live", "wss://api.vidu.com/live/ws/live/connect", "secret", "app", "channel", "token", "100", 100, "101", maxSeconds)
        }
        override fun control(session: LiveTryOnSession) = socket
        override fun close() {}
    }
}
