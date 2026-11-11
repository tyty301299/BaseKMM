package com.example.kmmbase.di

import com.example.kmmbase.data.repository.DefaultGreetingRepository
import com.example.kmmbase.data.repository.HttpShadowRepository
import com.example.kmmbase.data.repository.normalizeShadowOrigin
import com.example.kmmbase.presentation.shadow.ShadowStore
import com.example.kmmbase.domain.usecase.GetGreetingUseCase
import com.example.kmmbase.domain.repository.GreetingRepository
import com.example.kmmbase.presentation.notes.NoteEditStore
import com.example.kmmbase.presentation.notes.NoteListStore
import com.example.kmmbase.presentation.studio.StudioStore
import com.example.kmmbase.presentation.looks.LookStore
import com.example.kmmbase.presentation.guide.GuideStore
import com.example.kmmbase.presentation.teach.TeachStore
import com.example.kmmbase.platform.FrameSource
import com.example.kmmbase.platform.Speaker
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Constructor injection: thay repository thật bằng fake khi test.
class AppContainer(repository: GreetingRepository) {
    constructor() : this(DefaultGreetingRepository())
    val getGreeting = GetGreetingUseCase(repository)

    fun shadowStore() = ShadowStore(
        { endpoint, code -> HttpShadowRepository(endpoint, code) },
        ::normalizeShadowOrigin,
        Dispatchers.Main.immediate,
    )

    /**
     * Guide and teach stores are built here so the platform only has to supply camera, voice and a
     * clock; coroutine plumbing stays in Kotlin.
     */
    fun guideStore(shadow: ShadowStore, camera: FrameSource, speaker: Speaker) = GuideStore(
        socketFactory = { requireNotNull(shadow.openSession()) { "Chưa kết nối máy chủ SHADOW." } },
        captureFrame = { camera.capture(it) },
        speak = speaker::speak,
        stopSpeaking = speaker::stop,
        nowMs = ::nowMillis,
        dispatcher = Dispatchers.Main.immediate,
    )

    @OptIn(ExperimentalUuidApi::class)
    fun teachStore(shadow: ShadowStore, camera: FrameSource) = TeachStore(
        repository = shadow::connectedRepository,
        captureFrame = { camera.capture(it) },
        nowMs = ::nowMillis,
        newId = { Uuid.random().toString().replace("-", "").take(20) },
        dispatcher = Dispatchers.Main.immediate,
    )

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    fun lookStore() = LookStore(
        { openLocalData().looks },
        { Uuid.random().toString() },
        { Clock.System.now().toEpochMilliseconds() },
        Dispatchers.Main.immediate,
    )

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    fun studioStore() = StudioStore(
        { openLocalData().studio },
        { Uuid.random().toString() },
        { Clock.System.now().toEpochMilliseconds() },
        Dispatchers.Main.immediate,
    )

    @OptIn(ExperimentalTime::class)
    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    // Open only when needed; greeting-only screens do not allocate a database.
    private val localDataDelegate = lazy { LocalDataContainer.openDefault() }

    // Realm.open blocks, so the first open must stay off the main thread.
    @Throws(Exception::class)
    suspend fun openLocalData(): LocalDataContainer =
        withContext(Dispatchers.Default) { localDataDelegate.value }

    // Each screen owns its store and closes it when the screen goes away.
    fun noteListStore(): NoteListStore =
        NoteListStore({ openLocalData().notes }, Dispatchers.Main.immediate)

    fun noteEditStore(noteId: String?): NoteEditStore =
        NoteEditStore(noteId, { openLocalData().notes }, Dispatchers.Main.immediate)

    // Cancel local-data work/collectors before closing. Discard this container afterwards.
    fun close() {
        if (localDataDelegate.isInitialized()) localDataDelegate.value.close()
    }
}
