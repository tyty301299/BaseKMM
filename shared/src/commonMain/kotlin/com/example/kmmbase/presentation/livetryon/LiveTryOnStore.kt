package com.example.kmmbase.presentation.livetryon

import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.*
import com.example.kmmbase.domain.usecase.PrepareLiveTryOnUseCase
import com.example.kmmbase.presentation.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.TimeSource

private val liveClockOrigin = TimeSource.Monotonic.markNow()

/** Single session owner shared by SwiftUI and Compose. Platform callbacks carry a generation. */
class LiveTryOnStore internal constructor(
    private val repository: LiveTryOnRepository,
    private val engine: LiveVideoEngine,
    garments: List<WardrobeItem>,
    dispatcher: CoroutineDispatcher,
    private val nowMs: () -> Long = { liveClockOrigin.elapsedNow().inWholeMilliseconds },
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(LiveTryOnState(garments = garments.filter { it.category == "top" && it.imageUrl.isNotBlank() }))
    val state = mutableState.asStateFlow()
    val currentState get() = mutableState.value
    fun observeState(onChange: (LiveTryOnState) -> Unit): StoreSubscription = state.subscribeIn(scope, onChange)
    private val prepare = PrepareLiveTryOnUseCase(repository)
    private var operation: Job? = null
    private var timer: Job? = null
    private var failureObserver: Job? = null
    private var firstFrameTimeout: Job? = null
    private var control: LiveTryOnControl? = null
    private var config: LiveTryOnConfig? = null
    private var generation = 0
    private var lastActivityMs = 0L
    private var closed = false

    init { reload() }

    fun reload() {
        if (closed || currentState.phase in setOf(LiveTryOnPhase.CONNECTING, LiveTryOnPhase.LIVE)) return
        operation?.cancel()
        mutableState.update { it.copy(phase = LiveTryOnPhase.LOADING, message = "Đang kiểm tra thử đồ trực tiếp…") }
        operation = scope.launch {
            try {
                val loaded = repository.config()
                ensureActive()
                config = loaded
                mutableState.update { it.copy(available = loaded.available, phase = LiveTryOnPhase.READY,
                    selectedId = it.selectedId.ifEmpty { it.garments.firstOrNull()?.id.orEmpty() },
                    message = if (loaded.available) "Chọn áo rồi bắt đầu. Camera sẽ được gửi tới Vidu khi mở phiên." else loaded.detail) }
            } catch (error: TimeoutCancellationException) { fail(LiveTryOnException("Máy chủ phản hồi quá lâu. Hãy thử lại.")) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { fail(error) }
        }
    }

    fun select(id: String) {
        if (closed || currentState.changing || currentState.phase == LiveTryOnPhase.CONNECTING || currentState.garments.none { it.id == id }) return
        mutableState.update { it.copy(selectedId = id) }
        activity()
    }

    fun start() {
        if (closed || !currentState.available || currentState.phase !in setOf(LiveTryOnPhase.READY, LiveTryOnPhase.ENDED, LiveTryOnPhase.ERROR)) return
        val item = currentState.garments.firstOrNull { it.id == currentState.selectedId } ?: return
        release()
        val epoch = generation
        mutableState.update { it.copy(phase = LiveTryOnPhase.CONNECTING, message = "Đang kết nối Vidu…", fps = 0, sentGarmentId = "") }
        operation = scope.launch {
            try {
                val session = prepare(item)
                ensureActive()
                val connection = repository.control(session)
                control = connection
                connection.connect()
                ensureActive()
                failureObserver = scope.launch { connection.failures.collect { if (epoch == generation) fail(LiveTryOnException(it)) } }
                val duration = minOf(session.maxSeconds, config?.maxSeconds ?: 120)
                mutableState.update { it.copy(remainingSeconds = duration) }
                lastActivityMs = nowMs()
                val expiresAtMs = nowMs() + duration * 1000L
                timer = scope.launch {
                    while (isActive && epoch == generation) {
                        delay(1000)
                        val remaining = ((expiresAtMs - nowMs() + 999) / 1000).toInt().coerceAtLeast(0)
                        mutableState.update { it.copy(remainingSeconds = remaining) }
                        if (currentState.remainingSeconds == 0) { stop("Đã hết thời lượng phiên."); break }
                        if (nowMs() - lastActivityMs >= (config?.idleSeconds ?: 60) * 1000L) { stop("Đã dừng do không thao tác. Bạn có thể mở phiên mới."); break }
                    }
                }
                firstFrameTimeout = scope.launch {
                    delay(25_000)
                    if (epoch == generation && currentState.phase == LiveTryOnPhase.CONNECTING) fail(LiveTryOnException("Chưa nhận được video AI. Hãy thử lại."))
                }
                engine.start(session, object : LiveVideoListener {
                    override fun onRemoteVideo() { scope.launch {
                        if (epoch != generation || closed || currentState.phase != LiveTryOnPhase.CONNECTING) return@launch
                        firstFrameTimeout?.cancel()
                        mutableState.update { it.copy(phase = LiveTryOnPhase.LIVE, sentGarmentId = item.id, message = "Video thử đồ AI") }
                    } }
                    override fun onFailure(message: String) { scope.launch {
                        if (epoch == generation && !closed) fail(LiveTryOnException(message))
                    } }
                    override fun onStats(framesPerSecond: Int) { scope.launch {
                        if (epoch == generation && !closed) mutableState.update { it.copy(fps = framesPerSecond.coerceAtLeast(0)) }
                    } }
                })
            } catch (error: TimeoutCancellationException) { if (epoch == generation) fail(LiveTryOnException("Vidu kết nối quá lâu. Hãy thử lại.")) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (epoch == generation) fail(error) }
        }
    }

    fun applySelection() {
        if (closed || currentState.phase != LiveTryOnPhase.LIVE || currentState.changing || currentState.selectedId == currentState.sentGarmentId) return
        val item = currentState.garments.firstOrNull { it.id == currentState.selectedId } ?: return
        val connection = control ?: return
        val epoch = generation
        activity()
        mutableState.update { it.copy(changing = true, message = "Đang gửi áo mới…") }
        operation = scope.launch {
            try {
                connection.switchReference(repository.reference(item))
                ensureActive()
                if (epoch == generation) mutableState.update { it.copy(changing = false, sentGarmentId = item.id, message = "Đã gửi áo mới. Chờ video cập nhật.") }
            } catch (error: TimeoutCancellationException) { if (epoch == generation) fail(LiveTryOnException("Không gửi được áo mới kịp thời. Hãy mở lại phiên.")) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (epoch == generation) fail(error) }
        }
    }

    fun activity() { lastActivityMs = nowMs() }
    fun stop(message: String = "Đã dừng phiên.") {
        if (closed) return
        release()
        mutableState.update { it.copy(phase = LiveTryOnPhase.ENDED, changing = false, fps = 0, remainingSeconds = 0, message = message) }
    }

    private fun fail(error: Exception) {
        release()
        mutableState.update { it.copy(phase = LiveTryOnPhase.ERROR, changing = false, fps = 0, remainingSeconds = 0,
            message = (error as? LiveTryOnException)?.message ?: "Không kết nối được thử đồ live. Kiểm tra mạng rồi thử lại.") }
    }

    private fun release() {
        generation++
        operation?.cancel(); operation = null
        timer?.cancel(); timer = null
        firstFrameTimeout?.cancel(); firstFrameTimeout = null
        failureObserver?.cancel(); failureObserver = null
        control?.close(); control = null
        engine.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
        release()
        scope.cancel()
        repository.close()
    }
}
