package com.example.kmmbase.data.remote

import com.example.kmmbase.domain.model.DetectedObject
import com.example.kmmbase.domain.model.SceneScan
import com.example.kmmbase.domain.model.StepVerification
import com.example.kmmbase.domain.model.VerificationStatus
import com.example.kmmbase.domain.model.ZoneBox
import com.example.kmmbase.domain.repository.KitchenRepository
import com.example.kmmbase.domain.repository.VoiceConnector
import com.example.kmmbase.domain.repository.VoiceEvent
import com.example.kmmbase.domain.repository.VoiceSession
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal const val CHECK_TOOL = "check_current_step"
private const val REQUEST_TIMEOUT_MS = 30_000L
private const val LIVE_ENDPOINT = "wss://generativelanguage.googleapis.com/ws/" +
    "google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContentConstrained"

internal fun createHttpClient() = HttpClient {
    install(HttpTimeout)
    install(WebSockets)
}

private suspend fun HttpClient.postJson(url: String, body: JsonObject): JsonObject {
    val response: HttpResponse = post(url) {
        timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }
    check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
    return Json.parseToJsonElement(response.bodyAsText()).jsonObject
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal class RemoteKitchenRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : KitchenRepository {
    override suspend fun verify(stepId: String, imageBase64: String): StepVerification =
        parseVerification(client.postJson("$baseUrl/api/verify", stepRequest(stepId, imageBase64)))

    override suspend fun scanScene(stepId: String, imageBase64: String): SceneScan =
        parseScan(client.postJson("$baseUrl/api/scan", stepRequest(stepId, imageBase64)))

    private fun stepRequest(stepId: String, imageBase64: String) = buildJsonObject {
        put("stepId", stepId)
        put("imageBase64", imageBase64)
    }
}

internal fun parseVerification(body: JsonObject) = StepVerification(
    status = VerificationStatus.valueOf(requireNotNull(body.string("status"))),
    message = requireNotNull(body.string("message")),
    misplaced = (body["misplaced"] as? JsonArray).orEmpty().mapNotNull { it.toZoneBox() },
)

internal fun parseScan(body: JsonObject) = SceneScan(
    target = body["target"]?.toZoneBox(),
    objects = (body["objects"] as? JsonArray).orEmpty().mapNotNull { item ->
        val entry = item as? JsonObject ?: return@mapNotNull null
        val label = entry.string("label") ?: return@mapNotNull null
        val box = entry["box"]?.toZoneBox() ?: return@mapNotNull null
        DetectedObject(label, box)
    },
)

/** Reads the server's [left, top, right, bottom] array. */
internal fun JsonElement.toZoneBox(): ZoneBox? {
    val values = (this as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull ?: return null }
    if (values?.size != 4) return null
    return ZoneBox(values[0], values[1], values[2], values[3])
}

internal class GeminiVoiceConnector(
    private val client: HttpClient,
    private val baseUrl: String,
) : VoiceConnector {
    override suspend fun connect(systemPrompt: String): VoiceSession {
        val credentials = client.postJson("$baseUrl/api/live-token", buildJsonObject {})
        val token = requireNotNull(credentials.string("token"))
        val model = requireNotNull(credentials.string("model"))
        val socket = client.webSocketSession("$LIVE_ENDPOINT?access_token=$token")
        return GeminiVoiceSession(socket).also { it.send(setupMessage(model, systemPrompt)) }
    }
}

private class GeminiVoiceSession(private val socket: DefaultClientWebSocketSession) : VoiceSession {
    // Gemini sends JSON in binary frames, so both frame types are accepted.
    override val events: Flow<VoiceEvent> = flow {
        for (frame in socket.incoming) {
            val text = when (frame) {
                is Frame.Text -> frame.readText()
                is Frame.Binary -> frame.readBytes().decodeToString()
                else -> continue
            }
            parseLiveMessage(text).forEach { emit(it) }
        }
    }.flowOn(Dispatchers.Default)

    suspend fun send(message: JsonObject) = socket.send(Frame.Text(message.toString()))

    override suspend fun sendAudio(pcmBase64: String) =
        send(realtimeBlob("audio", "audio/pcm;rate=16000", pcmBase64))

    override suspend fun sendFrame(jpegBase64: String) =
        send(realtimeBlob("video", "image/jpeg", jpegBase64))

    override suspend fun sendText(text: String) = send(
        buildJsonObject { putJsonObject("realtimeInput") { put("text", text) } },
    )

    override suspend fun sendCheckResult(callId: String, result: Map<String, String>) =
        send(toolResponseMessage(callId, result))

    override fun close() = socket.cancel()
}

private fun realtimeBlob(field: String, mimeType: String, base64: String) = buildJsonObject {
    putJsonObject("realtimeInput") {
        putJsonObject(field) {
            put("mimeType", mimeType)
            put("data", base64)
        }
    }
}

internal fun setupMessage(model: String, systemPrompt: String) = buildJsonObject {
    putJsonObject("setup") {
        put("model", "models/$model")
        putJsonObject("generationConfig") {
            putJsonArray("responseModalities") { add("AUDIO") }
        }
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", systemPrompt) } }
        }
        putJsonArray("tools") {
            addJsonObject {
                putJsonArray("functionDeclarations") {
                    addJsonObject {
                        put("name", CHECK_TOOL)
                        put("description", "Chụp ảnh mặt bàn và kiểm tra bước hiện tại đã hoàn thành chưa.")
                    }
                }
            }
        }
        putJsonObject("outputAudioTranscription") {}
        // Without compression, sessions with video end after about two minutes.
        putJsonObject("contextWindowCompression") { putJsonObject("slidingWindow") {} }
    }
}

internal fun toolResponseMessage(callId: String, result: Map<String, String>) = buildJsonObject {
    putJsonObject("toolResponse") {
        putJsonArray("functionResponses") {
            addJsonObject {
                put("id", callId)
                put("name", CHECK_TOOL)
                putJsonObject("response") { result.forEach { (key, value) -> put(key, value) } }
            }
        }
    }
}

internal fun parseLiveMessage(text: String): List<VoiceEvent> {
    val message = Json.parseToJsonElement(text).jsonObject
    val events = mutableListOf<VoiceEvent>()
    if ("setupComplete" in message) events += VoiceEvent.Ready

    message.objectAt("serverContent")?.let { content ->
        if (content.isTrue("interrupted")) events += VoiceEvent.Interrupted
        content.objectAt("outputTranscription")?.string("text")?.let { events += VoiceEvent.Caption(it) }
        content.objectAt("modelTurn")?.get("parts")?.jsonArray?.forEach { part ->
            part.jsonObject.objectAt("inlineData")?.string("data")?.let { events += VoiceEvent.Audio(it) }
        }
        if (content.isTrue("turnComplete")) events += VoiceEvent.TurnComplete
    }

    message.objectAt("toolCall")?.get("functionCalls")?.jsonArray?.forEach { call ->
        val function = call.jsonObject
        val id = function.string("id")
        if (function.string("name") == CHECK_TOOL && id != null) events += VoiceEvent.CheckRequested(id)
    }
    return events
}

private fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.isTrue(key: String): Boolean =
    (this[key] as? JsonElement)?.jsonPrimitive?.booleanOrNull == true
