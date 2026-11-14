package com.example.kmmbase.di

import com.example.kmmbase.data.repository.greeting.DefaultGreetingRepository
import com.example.kmmbase.domain.repository.LiveVideoEngine
import com.example.kmmbase.presentation.livetryon.LiveTryOnStore
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
class AppContainer(repository: GreetingRepository, private val services: AppServices) {
    constructor(repository: GreetingRepository) : this(repository, DefaultAppServices())
    constructor() : this(DefaultGreetingRepository(), DefaultAppServices())
    val getGreeting = GetGreetingUseCase(repository)
    private var location: com.example.kmmbase.domain.model.StylingLocation? = null
    private var locationUpdatedAt = 0L

    fun updateLocation(lat: Double, lon: Double) {
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        location = com.example.kmmbase.domain.model.StylingLocation(lat, lon)
        locationUpdatedAt = nowMillis()
    }

    fun clearLocation() { location = null }
    private fun currentLocation() = location?.takeIf { nowMillis() - locationUpdatedAt < 10 * 60_000 }

    @Throws(IllegalArgumentException::class)
    fun normalizeEndpoint(endpoint: String): String = services.normalizeEndpoint(endpoint)

    fun conversationStore(shadow: ShadowStore) = com.example.kmmbase.presentation.conversation.ConversationStore(
        repositoryFactory = {
            if (shadow.currentState.connected)
                com.example.kmmbase.data.repository.conversation.HttpConversationRepository(shadow.currentState.endpoint, shadow.mediaHeaders())
            else null
        },
        dispatcher = Dispatchers.Main.immediate,
        locationProvider = ::currentLocation,
    )

    fun shadowStore() = ShadowStore(
        services::shadow,
        services::normalizeEndpoint,
        Dispatchers.Main.immediate,
        locationProvider = ::currentLocation,
    )

    fun liveTryOnStore(shadow: ShadowStore, engine: LiveVideoEngine): LiveTryOnStore {
        require(shadow.currentState.connected) { "Chưa kết nối SHADOW." }
        return LiveTryOnStore(
            services.liveTryOn(shadow.currentState.endpoint, shadow.mediaHeaders()),
            engine, shadow.currentState.wardrobe, Dispatchers.Main.immediate,
        )
    }

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

    @OptIn(ExperimentalUuidApi::class)
    fun teachStoreWithVideo(shadow: ShadowStore, camera: FrameSource, persistence: com.example.kmmbase.platform.TeachJobPersistence) = TeachStore(
        repository = shadow::connectedRepository, captureFrame = { camera.capture(it) }, nowMs = ::nowMillis,
        newId = { Uuid.random().toString().replace("-", "").take(20) }, dispatcher = Dispatchers.Main.immediate,
        videoPersistence = persistence,
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
    private val localDataDelegate = lazy { services.openLocalData() }

    // Realm.open blocks, so the first open must stay off the main thread.
    @Throws(Exception::class)
    suspend fun openLocalData(): com.example.kmmbase.domain.repository.LocalRepositories =
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
