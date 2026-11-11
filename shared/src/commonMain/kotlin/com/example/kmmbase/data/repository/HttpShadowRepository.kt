package com.example.kmmbase.data.repository

import com.example.kmmbase.platform.ShadowTrace
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.ShadowRepository
import com.example.kmmbase.domain.repository.ShadowSessionSocket
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * The access code travels in the `x-shadow-access` header rather than a cookie: native clients have
 * no browser jar, and the header is accepted on both the REST API and the session WebSocket.
 */
class HttpShadowRepository(endpoint: String, private val accessCode: String = "") : ShadowRepository {
    private val origin = normalizeShadowOrigin(endpoint)
    private val client = HttpClient {
        followRedirects = false
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 90_000
            socketTimeoutMillis = 90_000
        }
    }

    private suspend fun send(
        path: String,
        body: JsonObject? = null,
        httpMethod: HttpMethod? = null,
    ): JsonElement {
        val trace = ShadowTrace("HTTP")
        val requestMethod = httpMethod ?: if (body == null) HttpMethod.Get else HttpMethod.Post
        val route = when {
            path.startsWith("/api/skills/") -> "/api/skills/:id"
            path.startsWith("/api/teach/progress/") -> "/api/teach/progress/:id"
            else -> path
        }
        trace.event("start", "method=${requestMethod.value} route=$route")
        try {
            val response = client.request("$origin$path") {
                method = requestMethod
                accept(ContentType.Application.Json)
                if (accessCode.isNotEmpty()) header(ACCESS_HEADER, accessCode)
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }
            trace.event("response", "status=${response.status.value}")
            if (!response.status.isSuccess()) {
                val message = when (response.status.value) {
                    401, 403 -> "Mã truy cập không đúng hoặc đã hết hạn. Mở Cài đặt để nhập lại."
                    404 -> "Không tìm thấy dữ liệu. Hãy tải lại."
                    409 -> "Tủ đồ đang trống. Hãy thêm ảnh trang phục hoặc nạp tủ đồ mẫu."
                    413 -> "Ảnh quá lớn. Thử lại với ảnh nhỏ hơn."
                    429 -> "Bạn thao tác quá nhanh. Đợi một chút rồi thử lại."
                    in 300..399 -> "Máy chủ chuyển hướng. Hãy nhập địa chỉ HTTPS cuối cùng."
                    in 500..599 -> "Máy chủ đang lỗi (${response.status.value}). Thử lại sau nhé."
                    else -> "Máy chủ chưa xử lý được yêu cầu (${response.status.value}). Thử lại nhé."
                }
                throw ShadowConnectionException(message)
            }
            val text = response.bodyAsText()
            val result = if (text.isBlank()) JsonNull else Json.parseToJsonElement(text)
            trace.event("success")
            return result
        } catch (error: kotlinx.coroutines.CancellationException) {
            trace.event("cancelled")
            throw error
        } catch (error: Exception) {
            trace.failure("failed", error)
            throw error
        }
    }

    override fun mediaUrl(path: String): String = when {
        path.isBlank() -> ""
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> "$origin$path"
        else -> "$origin/$path"
    }

    override fun mediaHeaders(): Map<String, String> =
        if (accessCode.isEmpty()) emptyMap() else mapOf(ACCESS_HEADER to accessCode)

    override suspend fun health(): ShadowHealth {
        val data = send("/api/health").jsonObject
        require(data["ok"]?.jsonPrimitive?.booleanOrNull == true) { "Máy chủ chưa sẵn sàng." }
        return ShadowHealth(
            data.text("mode"),
            data["access_required"]?.jsonPrimitive?.booleanOrNull == true,
            data.text("vision_model"),
        )
    }

    override suspend fun authenticate(code: String) {
        send("/api/access", buildJsonObject { put("code", code) })
    }

    override suspend fun skills(): List<ShadowSkill> = send("/api/skills").jsonArray.map {
        val row = it.jsonObject
        ShadowSkill(
            row.required("id"), row.required("display_name"), row.text("summary"),
            row.text("learned_from"), row.int("steps"), mediaUrl(row.text("cover_image_url")),
        )
    }

    override suspend fun lesson(id: String): ShadowLesson {
        val row = send("/api/skills/${id.encodeURLPathPart()}").jsonObject
        return ShadowLesson(
            row.required("id"), row.required("display_name"),
            row.steps(::mediaUrl), row.text("learned_from"), row.text("summary"),
        )
    }

    override suspend fun ask(skillId: String, stepId: String, question: String): ShadowAnswer {
        val row = send("/api/ask", buildJsonObject {
            put("skill_id", skillId); put("step_id", stepId); put("question", question)
        }).jsonObject
        return ShadowAnswer(row.required("answer"), row.text("source"))
    }

    override suspend fun deleteSkill(id: String) {
        send("/api/skills/${id.encodeURLPathPart()}", httpMethod = HttpMethod.Delete)
    }

    override suspend fun wardrobe(): List<WardrobeItem> = send("/api/wardrobe/items").jsonArray.map {
        it.jsonObject.toWardrobeItem(::mediaUrl)
    }

    override suspend fun suggest(event: String, notes: String, city: String): ShadowSuggestions {
        val row = send("/api/stylist/suggest", buildJsonObject {
            put("event", event)
            put("notes", notes)
            if (city.isNotBlank()) put("city", city)
        }).jsonObject
        val weather = (row["weather"] as? JsonObject)?.let {
            ShadowWeather(
                it["available"]?.jsonPrimitive?.booleanOrNull == true,
                it.text("city"), it.text("summary_vi"),
                it["t_min"]?.jsonPrimitive?.doubleOrNull,
                it["t_max"]?.jsonPrimitive?.doubleOrNull,
                it["rain_prob"]?.jsonPrimitive?.intOrNull,
            )
        }
        return ShadowSuggestions(
            outfits = row.array("outfits").map { entry ->
                val outfit = entry.jsonObject
                ShadowOutfit(
                    outfit.text("title"), outfit.text("reason"),
                    outfit.array("items").map { item ->
                        val fields = item.jsonObject
                        ShadowOutfitItem(
                            fields.text("id"), fields.text("name"), fields.text("category"),
                            mediaUrl(fields.text("image_url")), fields.text("color_hex"),
                        )
                    },
                    outfit.text("tips"), outfit.strings("rules_used"),
                )
            },
            source = row.text("source"),
            weather = weather,
            rulesUsed = row.strings("rules_used"),
            wearing = row.text("wearing"),
        )
    }

    override suspend fun rules(): List<ShadowRule> = send("/api/wardrobe/rules").jsonArray.map {
        val row = it.jsonObject
        ShadowRule(row.required("id"), row.text("text"), row.text("taught_by"))
    }

    override suspend fun addRule(text: String, taughtBy: String) {
        send("/api/wardrobe/rules", buildJsonObject { put("text", text); put("taught_by", taughtBy) })
    }

    override suspend fun compileSkill(request: TeachRequest): TeachDraft {
        val row = send("/api/teach/compile", buildJsonObject {
            put("title", request.title)
            put("teacher_name", request.teacherName)
            put("notes", request.notes)
            put("job_id", request.jobId)
            putJsonArray("transcript") {
                request.transcript.forEach { line ->
                    addJsonObject { put("t0", line.t0); put("t1", line.t1); put("text", line.text) }
                }
            }
            putJsonArray("frames") {
                request.frames.forEach { frame ->
                    addJsonObject { put("t", frame.t); put("image", frame.image); put("reason", frame.reason) }
                }
            }
        }).jsonObject
        // The response wraps the skill; Review edits a few fields and PUTs the whole object back.
        val skill = (row["skill"] as? JsonObject) ?: row
        return TeachDraft(
            skillId = skill.text("id").ifBlank { row.text("draft_id") },
            title = skill.text("display_name").ifBlank { request.title },
            steps = skill.steps(::mediaUrl),
            raw = skill.toString(),
        )
    }

    override suspend fun teachProgress(jobId: String): String =
        send("/api/teach/progress/${jobId.encodeURLPathPart()}").jsonObject.text("phase")

    override suspend fun saveSkill(draft: TeachDraft): String {
        val skill = Json.parseToJsonElement(draft.raw).jsonObject
        val id = skill.text("id").ifBlank { draft.skillId }
        require(id.isNotBlank()) { "Kỹ năng chưa có mã để lưu." }
        send("/api/skills/${id.encodeURLPathPart()}", skill, HttpMethod.Put)
        return id
    }

    override suspend fun tryonConfig(): TryonConfig {
        val row = send("/api/tryon/config").jsonObject
        val photo = row["photo"] as? JsonObject
        return TryonConfig(
            liveAvailable = (row["live"] as? JsonObject)?.get("available")?.jsonPrimitive?.booleanOrNull == true,
            photoProvider = photo?.text("provider").orEmpty(),
            expectedSeconds = photo?.int("expected_seconds") ?: 30,
        )
    }

    override suspend fun tryonPhoto(personImage: String, itemIds: List<String>): TryonResult {
        val row = send("/api/tryon/photo", buildJsonObject {
            put("person_image", personImage)
            putJsonArray("item_ids") { itemIds.forEach { add(it) } }
        }).jsonObject
        return TryonResult(
            mediaUrl(row.required("image_url")), row.text("provider"),
            row["seconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            row.strings("skipped_item_ids"), row.text("note"),
        )
    }

    override fun openSession(): ShadowSessionSocket =
        KtorSessionSocket(client, origin, accessCode, ::mediaUrl)

    override fun close() { client.close() }
}

internal const val ACCESS_HEADER = "x-shadow-access"

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

// ---- JSON helpers shared with the session socket --------------------------

internal fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.int(key: String): Int = (get(key) as? JsonPrimitive)?.intOrNull ?: 0
internal fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()
internal fun JsonObject.strings(key: String): List<String> =
    array(key).mapNotNull { entry ->
        when (entry) {
            is JsonPrimitive -> entry.contentOrNull
            is JsonObject -> entry.text("text").ifBlank { null }
            else -> null
        }
    }

internal fun JsonObject.required(key: String): String = text(key).also {
    require(it.isNotBlank()) { "Phản hồi máy chủ thiếu dữ liệu." }
}

internal fun JsonObject.steps(media: (String) -> String): List<ShadowStep> = array("steps").map {
    val step = it.jsonObject
    ShadowStep(
        step.required("id"), step.text("instruction"), step.text("tip"), step.text("warning"),
        media(step.text("reference_image_url")), step.text("on_enter_speech"),
    )
}

internal fun JsonObject.toWardrobeItem(media: (String) -> String) = WardrobeItem(
    required("id"), required("name"), text("category"),
    get("demo")?.jsonPrimitive?.booleanOrNull == true,
    media(text("image_url")), text("color_hex"), strings("colors"),
)
