package com.example.kmmbase.data.repository.conversation

import com.example.kmmbase.domain.model.StylingLocation
import com.example.kmmbase.data.remote.shadow.normalizeShadowOrigin
import com.example.kmmbase.domain.repository.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.*

/** Calls the existing NDJSON endpoint directly, without a browser or embedded web page. */
class HttpConversationRepository(endpoint: String, private val headers: Map<String, String>) : ConversationRepository {
    private val origin = normalizeShadowOrigin(endpoint)
    private val client = HttpClient {
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 90_000
            socketTimeoutMillis = 30_000
        }
    }

    override suspend fun converse(text: String, history: List<ConversationTurn>, image: String?, location: StylingLocation?, onEvent: (ConversationEvent) -> Unit) {
        val payload = buildJsonObject {
            put("text", text)
            put("history", buildJsonArray { history.takeLast(6).forEach { turn ->
                add(buildJsonObject { put("role", turn.role); put("text", turn.text.take(500)) })
            } })
            if (image != null) put("image", image)
            if (location != null) put("location", buildJsonObject { put("lat", location.lat); put("lon", location.lon) })
        }
        client.preparePost("$origin/api/conversation") {
            this@HttpConversationRepository.headers.forEach { (name, value) -> header(name, value) }
            contentType(ContentType.Application.Json)
            accept(ContentType.parse("application/x-ndjson"))
            setBody(payload.toString())
        }.execute { response ->
            check(response.status.isSuccess()) {
                if (response.status.value in listOf(401, 403)) "Kiểm tra mã truy cập trong Cài đặt."
                else "Chưa nhận được phản hồi. Bạn thử lại nhé."
            }
            val channel = response.bodyAsChannel()
            var completed = false
            while (true) {
                val line = channel.readUTF8Line(256_000) ?: break
                if (line.isBlank()) continue
                val event = Json.parseToJsonElement(line).jsonObject
                fun value(name: String) = event[name]?.jsonPrimitive?.contentOrNull.orEmpty()
                when (value("type")) {
                    "text" -> onEvent(ConversationEvent.Text(value("text")))
                    "action" -> onEvent(ConversationEvent.Action(ConversationLink(value("route"), value("label"), value("id"))))
                    "error" -> error("Kết nối hội thoại bị gián đoạn. Bạn thử lại nhé.")
                    "done" -> { completed = true; onEvent(ConversationEvent.Done); break }
                }
            }
            check(completed) { "Phản hồi bị gián đoạn. Bạn thử lại nhé." }
        }
    }

    override fun close() = client.close()
}
