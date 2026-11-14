package com.example.kmmbase

import com.example.kmmbase.domain.model.StylingLocation
import com.example.kmmbase.domain.repository.*
import com.example.kmmbase.presentation.conversation.ConversationStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConversationStoreTest {
    private class FakeConversation : ConversationRepository {
        var closed = false
        var text = ""
        var image: String? = null
        var location: StylingLocation? = null
        var history = emptyList<ConversationTurn>()
        var fail = false
        var hold = false
        val started = CompletableDeferred<Unit>()
        override suspend fun converse(text: String, history: List<ConversationTurn>, image: String?, location: StylingLocation?, onEvent: (ConversationEvent) -> Unit) {
            this.text = text; this.history = history; this.image = image; this.location = location; started.complete(Unit)
            if (hold) awaitCancellation()
            if (fail) error("Phản hồi bị gián đoạn. Bạn thử lại nhé.")
            onEvent(ConversationEvent.Text("Tủ đồ "))
            onEvent(ConversationEvent.Text("trong SHADOW."))
            onEvent(ConversationEvent.Action(ConversationLink("wardrobe", "Xem tủ đồ")))
            onEvent(ConversationEvent.Done)
        }
        override fun close() { closed = true }
    }

    @Test fun eachTurnUsesLatestGpsAndPermissionRemovalClearsIt() = runTest {
        val repo = FakeConversation()
        var gps: StylingLocation? = null
        val store = ConversationStore({ repo }, StandardTestDispatcher(testScheduler), locationProvider = { gps })
        try {
            store.send("Phối đồ mùa hè", null); runCurrent()
            assertNull(repo.location)
            gps = StylingLocation(10.8, 106.6)
            store.send("Ngày mai mặc gì?", null); runCurrent()
            assertEquals(gps, repo.location)
            gps = null
            store.send("Nếu trời lạnh?", null); runCurrent()
            assertNull(repo.location)
        } finally { store.close() }
    }

    @Test fun streamsTextAndActionsAndSendsBoundedHistory() = runTest {
        val repo = FakeConversation()
        val store = ConversationStore({ repo }, StandardTestDispatcher(testScheduler))
        try {
            store.send("  tủ đồ  ", "data:image/jpeg;base64,YQ==")
            assertTrue(store.currentState.busy)
            runCurrent()
            assertEquals("tủ đồ", repo.text)
            assertEquals("data:image/jpeg;base64,YQ==", repo.image)
            assertEquals("Tủ đồ trong SHADOW.", store.currentState.reply)
            assertEquals("wardrobe", store.currentState.links.single().route)
            assertTrue(repo.closed)
            repeat(5) { store.send("tiếp $it", null); runCurrent() }
            assertEquals(6, repo.history.size)
            assertEquals(listOf("user", "assistant", "user", "assistant", "user", "assistant"), repo.history.map { it.role })
        } finally { store.close() }
    }

    @Test fun disconnectedServerShowsErrorInsteadOfInventingAReply() = runTest {
        val store = ConversationStore({ null }, StandardTestDispatcher(testScheduler))
        try {
            store.send("xin chào", null); runCurrent()
            assertFalse(store.currentState.busy)
            assertTrue(store.currentState.error.orEmpty().contains("Base URL"))
            assertEquals("", store.currentState.reply)
            assertTrue(store.currentState.links.isEmpty())
        } finally { store.close() }
    }

    @Test fun changingConnectionCancelsRequestClosesClientAndClearsConversation() = runTest {
        val repo = FakeConversation().apply { hold = true }
        val store = ConversationStore({ repo }, StandardTestDispatcher(testScheduler))
        try {
            store.send("hướng dẫn", null); runCurrent()
            assertTrue(repo.started.isCompleted)
            store.clear(); runCurrent()
            assertTrue(repo.closed)
            assertFalse(store.currentState.busy)
            assertTrue(store.currentState.turns.isEmpty())
            assertTrue(store.currentState.links.isEmpty())
        } finally { store.close() }
    }

    @Test fun rejectsOversizeTextAndNeverUploadsOversizeCameraFrame() = runTest {
        val repo = FakeConversation()
        val store = ConversationStore({ repo }, StandardTestDispatcher(testScheduler))
        try {
            store.send("a".repeat(501), null); runCurrent()
            assertFalse(repo.started.isCompleted)
            assertNotNull(store.currentState.error)
            store.send("xem ảnh", "data:image/jpeg;base64," + "a".repeat(500_000)); runCurrent()
            assertNull(repo.image)
            assertNull(store.currentState.error)
        } finally { store.close() }
    }

    @Test fun failedResponseDoesNotLeaveLoadingOrActionButtons() = runTest {
        val repo = FakeConversation().apply { fail = true }
        val store = ConversationStore({ repo }, StandardTestDispatcher(testScheduler))
        try {
            store.send("xin chào", null); runCurrent()
            assertFalse(store.currentState.busy)
            assertNotNull(store.currentState.error)
            assertTrue(store.currentState.links.isEmpty())
            assertTrue(repo.closed)
        } finally { store.close() }
    }
}
