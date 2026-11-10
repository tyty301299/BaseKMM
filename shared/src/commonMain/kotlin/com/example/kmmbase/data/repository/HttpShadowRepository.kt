package com.example.kmmbase.data.repository

import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.ShadowRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*

/** Each connection owns its cookie jar; changing server never carries credentials across hosts. */
class HttpShadowRepository(endpoint: String) : ShadowRepository {
    private val origin = normalizeShadowOrigin(endpoint)
    private val client = HttpClient {
        followRedirects = false
        install(HttpCookies)
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 90_000
            socketTimeoutMillis = 90_000
        }
    }

    private suspend fun request(path: String, body: JsonObject? = null): JsonElement {
        val response = client.request("$origin$path") {
            method = if (body == null) HttpMethod.Get else HttpMethod.Post
            accept(ContentType.Application.Json)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        if (!response.status.isSuccess()) {
            val message = when (response.status.value) {
                401, 403 -> "Cần mã truy cập hợp lệ. Mở Kết nối và nhập lại mã."
                404 -> "Không tìm thấy dữ liệu. Hãy tải lại."
                409 -> "Tủ đồ đang trống. Hãy thêm ảnh trang phục của bạn."
                429 -> "Bạn thao tác quá nhanh. Đợi một chút rồi thử lại."
                in 300..399 -> "Máy chủ chuyển hướng. Hãy nhập địa chỉ HTTPS cuối cùng."
                else -> "Máy chủ chưa xử lý được yêu cầu (${response.status.value}). Thử lại nhé."
            }
            throw ShadowConnectionException(message)
        }
        return Json.parseToJsonElement(response.bodyAsText())
    }

    override suspend fun health(): ShadowHealth {
        val data = request("/api/health").jsonObject
        require(data["ok"]?.jsonPrimitive?.booleanOrNull == true) { "Máy chủ chưa sẵn sàng." }
        return ShadowHealth(data.text("mode"), data["access_required"]?.jsonPrimitive?.booleanOrNull == true)
    }

    override suspend fun authenticate(code: String) {
        request("/api/access", buildJsonObject { put("code", code) })
    }

    override suspend fun skills(): List<ShadowSkill> = request("/api/skills").jsonArray.map {
        val row = it.jsonObject
        ShadowSkill(row.required("id"), row.required("display_name"), row.text("summary"),
            row.text("learned_from"), row["steps"]?.jsonPrimitive?.intOrNull ?: 0)
    }

    override suspend fun lesson(id: String): ShadowLesson {
        val row = request("/api/skills/${id.encodeURLPathPart()}").jsonObject
        return ShadowLesson(row.required("id"), row.required("display_name"), row.getValue("steps").jsonArray.map {
            val step = it.jsonObject
            ShadowStep(step.required("id"), step.required("instruction"), step.text("tip"), step.text("warning"))
        })
    }

    override suspend fun ask(skillId: String, stepId: String, question: String): ShadowAnswer {
        val row = request("/api/ask", buildJsonObject {
            put("skill_id", skillId); put("step_id", stepId); put("question", question)
        }).jsonObject
        return ShadowAnswer(row.required("answer"), row.text("source"))
    }

    override suspend fun wardrobe(): List<WardrobeItem> = request("/api/wardrobe/items").jsonArray.map {
        val row = it.jsonObject
        WardrobeItem(row.required("id"), row.required("name"), row.text("category"),
            row["demo"]?.jsonPrimitive?.booleanOrNull == true)
    }

    override suspend fun suggest(event: String, notes: String): ShadowSuggestions {
        val row = request("/api/stylist/suggest", buildJsonObject {
            put("event", event); put("notes", notes)
        }).jsonObject
        return ShadowSuggestions(row.getValue("outfits").jsonArray.map {
            val outfit = it.jsonObject
            ShadowOutfit(outfit.required("title"), outfit.text("reason"),
                outfit.getValue("items").jsonArray.map { item -> item.jsonObject.required("name") }, outfit.text("tips"))
        }, row.text("source"))
    }

    override fun close() { client.close() }
}

/** Accept an origin only: no credentials, query, access code or silently discarded path. */
fun normalizeShadowOrigin(value: String): String {
    val url = try { Url(value.trim()) } catch (_: Exception) {
        throw IllegalArgumentException("Địa chỉ máy chủ không hợp lệ.")
    }
    require(url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() &&
        url.user == null && url.password == null && url.encodedPath in listOf("", "/") &&
        url.parameters.isEmpty() && url.fragment.isEmpty()) {
        "Nhập địa chỉ gốc HTTPS, ví dụ https://shadow.example.com, không kèm đường dẫn hoặc mã truy cập."
    }
    return url.toString().trimEnd('/')
}

private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.required(key: String): String = text(key).also {
    require(it.isNotBlank()) { "Phản hồi máy chủ thiếu dữ liệu." }
}
