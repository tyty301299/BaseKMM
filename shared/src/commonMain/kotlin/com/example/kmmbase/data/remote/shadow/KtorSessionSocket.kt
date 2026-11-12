package com.example.kmmbase.data.remote.shadow

import com.example.kmmbase.data.dto.shadow.*
import com.example.kmmbase.data.mapper.shadow.toDomain
import com.example.kmmbase.platform.ShadowTrace
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.ShadowSessionSocket
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.header
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/**
 * Guide session over `/session/control`, reconnecting with backoff so a dropped phone network
 * resumes on the step the server still holds.
 */
internal class KtorSessionSocket(
    private val client: HttpClient,
    origin: String,
    private val accessCode: String,
    private val media: (String) -> String,
) : ShadowSessionSocket {
    private val trace = ShadowTrace("WS")
    private val url = origin.replaceFirst("https://", "wss://") + "/session/control"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sink = MutableSharedFlow<SessionEvent>(replay = 0, extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: Flow<SessionEvent> = sink.asSharedFlow()

    private val sendLock = Mutex()
    private var session: DefaultClientWebSocketSession? = null
    /** Replayed after a reconnect so the learner keeps guiding instead of landing on the start screen. */
    private var activeSkillId: String? = null
    private val pump = scope.launch { connectLoop() }

    private suspend fun connectLoop() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            trace.event("connecting", "attempt=${attempt + 1}")
            sink.emit(SessionEvent.Connection(SocketStatus.CONNECTING))
            try {
                client.wss(url, request = { header(ACCESS_HEADER, accessCode) }) {
                    trace.event("open", "resume=${activeSkillId != null}")
                    session = this
                    attempt = 0
                    sink.emit(SessionEvent.Connection(SocketStatus.OPEN))
                    activeSkillId?.let { send(Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                        put("type", "session.start"); put("skill_id", it)
                    })) }
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        decode(frame.readText())?.let { sink.emit(it.toDomain(media)) }
                    }
                }
            } catch (error: CancellationException) {
                trace.event("cancelled")
                throw error
            } catch (error: Exception) {
                trace.failure("connection_failed", error)
                // Fall through to the backoff below; the status event tells the UI to show a warning.
            }
            session = null
            sink.emit(SessionEvent.Connection(SocketStatus.CLOSED))
            if (!currentCoroutineContext().isActive) return
            attempt = (attempt + 1).coerceAtMost(5)
            val backoff = 500L * (1L shl (attempt - 1))
            trace.event("closed_retry", "delay_ms=$backoff")
            delay(backoff)
        }
    }

    private fun decode(raw: String): SessionEventDto? = try {
        decodeSessionEvent(raw)
    } catch (error: Exception) {
        trace.failure("decode_failed", error)
        null
    }

    private suspend fun emit(payload: JsonObject) {
        val type = payload.text("type")
        val open = session ?: run {
            trace.event("send_skipped_closed", "type=$type")
            return
        }
        sendLock.withLock {
            try {
                open.send(Json.encodeToString(JsonObject.serializer(), payload))
                trace.event("sent", "type=$type")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                trace.failure("send_failed", error)
            }
        }
    }

    override suspend fun start(skillId: String) {
        activeSkillId = skillId
        emit(buildJsonObject { put("type", "session.start"); put("skill_id", skillId) })
    }

    override suspend fun sendFrame(image: String, timestampMs: Long, trigger: String) {
        emit(buildJsonObject {
            put("type", "vision.frame"); put("image", image)
            put("ts", timestampMs); put("trigger", trigger)
        })
    }

    override suspend fun stop() {
        activeSkillId = null
        emit(buildJsonObject { put("type", "session.stop") })
    }

    override suspend fun stepManually(forward: Boolean) {
        emit(buildJsonObject {
            put("type", "debug.step"); put("direction", if (forward) "forward" else "backward")
        })
    }

    override fun close() {
        trace.event("close")
        pump.cancel()
        scope.cancel()
    }
}
