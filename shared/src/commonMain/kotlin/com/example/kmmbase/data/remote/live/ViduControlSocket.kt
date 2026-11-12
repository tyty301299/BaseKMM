package com.example.kmmbase.data.remote.live

import com.example.kmmbase.data.dto.live.*
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.LiveTryOnControl
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

internal class ViduControlSocket(private val credentials: LiveTryOnSession) : LiveTryOnControl {
    private val client = HttpClient { install(WebSockets); followRedirects = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val failureChannel = Channel<String>(Channel.BUFFERED)
    override val failures = failureChannel.receiveAsFlow()
    private val lock = Mutex()
    @OptIn(ExperimentalUuidApi::class)
    private val connectionId = Uuid.random().toString()
    private var socket: DefaultClientWebSocketSession? = null
    private var sequence = 0
    private var stopped = false

    override suspend fun connect() {
        try {
            withTimeout(15_000) {
                val url = URLBuilder(credentials.socketUrl).apply {
                    parameters.append("live_id", credentials.liveId)
                    parameters.append("conn_id", connectionId)
                    parameters.append("client_secret", credentials.clientSecret)
                }.buildString()
                val opened = client.webSocketSession(url)
                socket = opened
                var ready = false
                for (attempt in 1..8) {
                    send(1, buildJsonObject { put("conn_init", buildJsonObject { put("version", 1) }) })
                    val ack = receiveInit(opened)
                    if (ack["success"]?.jsonPrimitive?.booleanOrNull == true) { ready = true; break }
                    if (ack.string("error_code") != "NOT_READY") throw LiveTryOnException("Vidu từ chối kết nối live.")
                    delay(1000)
                }
                if (!ready) throw LiveTryOnException("Vidu chưa sẵn sàng. Hãy mở phiên mới.")
            }
            scope.launch { receiveLoop() }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    private suspend fun receiveInit(opened: DefaultClientWebSocketSession): JsonObject {
        for (frame in opened.incoming) {
            if (frame !is Frame.Text) continue
            val message = Json.parseToJsonElement(frame.readText()).jsonObject
            if (message.integer("type") == 5) throw LiveTryOnException("Phiên Vidu đã kết thúc.")
            if (message.integer("type") == 2) return message.getValue("payload").jsonObject.getValue("conn_init_ack").jsonObject
        }
        throw LiveTryOnException("Mất kết nối điều khiển Vidu.")
    }

    private suspend fun receiveLoop() {
        try {
            val opened = socket ?: return
            for (frame in opened.incoming) {
                if (frame !is Frame.Text) continue
                val message = Json.parseToJsonElement(frame.readText()).jsonObject
                when (message.integer("type")) {
                    5 -> throw LiveTryOnException("Phiên Vidu đã kết thúc.")
                    14 -> {
                        // Vidu documents type 14 only on failure; success has NO acknowledgment.
                        failureChannel.send("Vidu từ chối ảnh áo mới. Hãy dừng và chọn ảnh khác.")
                    }
                }
            }
            if (!stopped) failureChannel.send("Kết nối Vidu đã đóng.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!stopped) failureChannel.send("Mất kết nối điều khiển Vidu. Hãy mở phiên mới.")
        }
    }

    override suspend fun switchReference(reference: String) = lock.withLock {
        withTimeout(10_000) {
            send(13, buildJsonObject {
                put("switch_prompt", buildJsonObject {
                    put("editing_type", "virtual_tryon")
                    put("prompts", buildJsonArray { add(buildJsonObject { put("type", "image"); put("content", reference) }) })
                })
            })
        }
        // This means sent, not applied: only the resulting video can establish visual success.
    }

    private suspend fun send(type: Int, payload: JsonObject) {
        check(!stopped)
        requireNotNull(socket).send(Frame.Text(LiveControlMessageDto.encode(type, credentials.liveId, connectionId, ++sequence, payload)))
    }

    override fun close() {
        if (stopped) return
        stopped = true
        val opened = socket
        socket = null
        // Own cleanup scope survives cancellation of the screen's coroutine scope.
        scope.launch {
            try {
                withTimeoutOrNull(1500) {
                    opened?.send(Frame.Text(LiveControlMessageDto.encode(5, credentials.liveId, connectionId, ++sequence,
                        buildJsonObject { put("hangup", buildJsonObject { put("hangup_reason", "user_hangup") }) })))
                    opened?.close()
                }
            } catch (_: Exception) {
                // A dropped connection may already be closed. Local cleanup must still complete.
            } finally {
                client.close()
                failureChannel.close()
                scope.cancel()
            }
        }
    }
}
