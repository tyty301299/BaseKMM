package com.example.kmmbase.data.repository

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
            sink.emit(SessionEvent.Connection(SocketStatus.CONNECTING))
            try {
                client.wss(url, request = { header(ACCESS_HEADER, accessCode) }) {
                    session = this
                    attempt = 0
                    sink.emit(SessionEvent.Connection(SocketStatus.OPEN))
                    activeSkillId?.let { send(Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                        put("type", "session.start"); put("skill_id", it)
                    })) }
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        decode(frame.readText())?.let { sink.emit(it) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Fall through to the backoff below; the status event tells the UI to show a warning.
            }
            session = null
            sink.emit(SessionEvent.Connection(SocketStatus.CLOSED))
            if (!currentCoroutineContext().isActive) return
            attempt = (attempt + 1).coerceAtMost(5)
            delay(500L * (1L shl (attempt - 1)))
        }
    }

    private fun decode(raw: String): SessionEvent? {
        val message = try { Json.parseToJsonElement(raw).jsonObject } catch (_: Exception) { return null }
        return when (message.text("type")) {
            "session.ready" -> SessionEvent.Ready(
                message.text("session_id"),
                (message["vision"] as? JsonObject)?.int("image_max_side")?.takeIf { it > 0 } ?: 448,
            )
            "hud.state" -> {
                val step = (message["step"] as? JsonObject)?.let {
                    SessionStep(
                        it.text("id"), it.int("index"), it.int("total"), it.text("instruction"),
                        it.text("tip"), it.text("warning"), media(it.text("reference_image_url")),
                    )
                }
                SessionEvent.Hud(
                    phase = runCatching { SessionPhase.valueOf(message.text("phase")) }
                        .getOrDefault(SessionPhase.WAITING_FOR_START),
                    skillName = message.text("recipe_name"),
                    tasks = message.array("tasks").map {
                        val task = it.jsonObject
                        SessionTask(task.text("id"), task.text("text"),
                            task["completed"]?.jsonPrimitive?.booleanOrNull == true)
                    },
                    activeTaskId = message.text("active_task_id"),
                    step = step,
                    speechEpoch = message["speech_epoch"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
            "speech" -> SessionEvent.Speech(
                message.text("text"),
                message["epoch"]?.jsonPrimitive?.longOrNull ?: 0L,
                message.text("kind"),
            )
            "vision.result" -> {
                val result = message["result"] as? JsonObject
                SessionEvent.VisionResult(
                    confirming = message["confirming"]?.jsonPrimitive?.booleanOrNull == true,
                    latencyMs = message["latency_ms"]?.jsonPrimitive?.longOrNull ?: 0L,
                    done = result?.get("flag")?.jsonPrimitive?.booleanOrNull == true,
                    hint = result?.text("hint").orEmpty(),
                )
            }
            "hud.error" -> SessionEvent.Failure(
                message.text("message").ifBlank { "Máy chủ báo lỗi." }, message.text("code"))
            else -> null
        }
    }

    private suspend fun emit(payload: JsonObject) {
        val open = session ?: return
        sendLock.withLock {
            try { open.send(Json.encodeToString(JsonObject.serializer(), payload)) } catch (_: Exception) { }
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
        pump.cancel()
        scope.cancel()
    }
}
