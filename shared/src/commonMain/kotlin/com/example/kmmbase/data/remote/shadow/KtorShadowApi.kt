package com.example.kmmbase.data.remote.shadow

import com.example.kmmbase.platform.ShadowTrace
import com.example.kmmbase.domain.model.ShadowConnectionException
import com.example.kmmbase.data.dto.shadow.*
import com.example.kmmbase.domain.repository.ShadowSessionSocket
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
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
internal class KtorShadowApi(endpoint: String, private val accessCode: String = "") : ShadowApi {
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
        jobToken: String? = null,
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
                if (jobToken != null) header("x-shadow-job-token", jobToken)
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

    override fun mediaHeadersFor(url: String): Map<String, String> {
        val target = runCatching { Url(url) }.getOrNull() ?: return emptyMap()
        val server = Url(origin)
        return if (target.protocol == server.protocol && target.host == server.host &&
            target.port == server.port && target.user == null && target.password == null) mediaHeaders() else emptyMap()
    }

    override fun mediaHeaders(): Map<String, String> =
        if (accessCode.isEmpty()) emptyMap() else mapOf(ACCESS_HEADER to accessCode)

    override suspend fun health(): ShadowHealthDto {
        val data = send("/api/health").jsonObject
        require(data["ok"]?.jsonPrimitive?.booleanOrNull == true) { "Máy chủ chưa sẵn sàng." }
        return ShadowHealthDto(
            data.text("mode"),
            data["access_required"]?.jsonPrimitive?.booleanOrNull == true,
            data.text("vision_model"),
        )
    }

    override suspend fun authenticate(code: String) {
        send("/api/access", buildJsonObject { put("code", code) })
    }

    override suspend fun skills(): List<ShadowSkillDto> = send("/api/skills").jsonArray.map {
        val row = it.jsonObject
        ShadowSkillDto(
            row.required("id"), row.required("display_name"), row.text("summary"),
            row.text("learned_from"), row.int("steps"), mediaUrl(row.text("cover_image_url")),
        )
    }

    override suspend fun lesson(id: String): ShadowLessonDto {
        val row = send("/api/skills/${id.encodeURLPathPart()}").jsonObject
        return ShadowLessonDto(
            row.required("id"), row.required("display_name"),
            row.steps(::mediaUrl), row.text("learned_from"), row.text("summary"),
        )
    }

    override suspend fun ask(skillId: String, stepId: String, question: String): ShadowAnswerDto {
        val row = send("/api/ask", buildJsonObject {
            put("skill_id", skillId); put("step_id", stepId); put("question", question)
        }).jsonObject
        return ShadowAnswerDto(row.required("answer"), row.text("source"))
    }

    override suspend fun deleteSkill(id: String) {
        send("/api/skills/${id.encodeURLPathPart()}", httpMethod = HttpMethod.Delete)
    }

    override suspend fun wardrobe(): List<WardrobeItemDto> = send("/api/wardrobe/items").jsonArray.map {
        it.jsonObject.toWardrobeItem(::mediaUrl)
    }

    override suspend fun suggest(event: String, notes: String, city: String): ShadowSuggestionsDto {
        val row = send("/api/stylist/suggest", buildJsonObject {
            put("event", event)
            put("notes", notes)
            if (city.isNotBlank()) put("city", city)
        }).jsonObject
        val weather = (row["weather"] as? JsonObject)?.let {
            ShadowWeatherDto(
                it["available"]?.jsonPrimitive?.booleanOrNull == true,
                it.text("city"), it.text("summary_vi"),
                it["t_min"]?.jsonPrimitive?.doubleOrNull,
                it["t_max"]?.jsonPrimitive?.doubleOrNull,
                it["rain_prob"]?.jsonPrimitive?.intOrNull,
            )
        }
        return ShadowSuggestionsDto(
            outfits = row.array("outfits").map { entry ->
                val outfit = entry.jsonObject
                ShadowOutfitDto(
                    outfit.text("title"), outfit.text("reason"),
                    outfit.array("items").map { item ->
                        val fields = item.jsonObject
                        ShadowOutfitItemDto(
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

    override suspend fun rules(): List<ShadowRuleDto> = send("/api/wardrobe/rules").jsonArray.map {
        val row = it.jsonObject
        ShadowRuleDto(row.required("id"), row.text("text"), row.text("taught_by"))
    }

    override suspend fun addRule(text: String, taughtBy: String) {
        send("/api/wardrobe/rules", buildJsonObject { put("text", text); put("taught_by", taughtBy) })
    }

    override suspend fun uploadVideo(source: com.example.kmmbase.domain.repository.TeachVideoSource, title: String, teacher: String, notes: String): TeachVideoTicketDto {
        val config = send("/api/teach/video/config").jsonObject
        require(config["available"]?.jsonPrimitive?.booleanOrNull == true) { "Máy chủ chưa bật học từ video." }
        val limit = config["max_bytes"]?.jsonPrimitive?.longOrNull ?: 32L * 1024 * 1024
        require(source.sizeBytes in 1..limit) { "Video trống hoặc vượt giới hạn 32 MB. Chọn clip ngắn hơn." }
        val response = client.post("$origin/api/teach/video/uploads") {
            timeout { requestTimeoutMillis = 180_000 }
            if (accessCode.isNotEmpty()) header(ACCESS_HEADER, accessCode)
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.Application.OctetStream
                override val contentLength = source.sizeBytes
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    var sent = 0L
                    while (true) {
                        val chunk = source.readChunk(64 * 1024) ?: break
                        require(chunk.isNotEmpty()) { "Không đọc được video." }
                        sent += chunk.size
                        require(sent <= source.sizeBytes && sent <= limit) { "Kích thước video thay đổi khi tải lên." }
                        channel.writeFully(chunk)
                    }
                    require(sent == source.sizeBytes) { "Video chưa được tải đủ." }
                }
            })
        }
        require(response.status.isSuccess()) { "Chưa tải được video (HTTP ${response.status.value}). Kiểm tra mã truy cập và thử lại." }
        val row = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return TeachVideoTicketDto(row.required("job_id"), row.required("token"), title, teacher, notes)
    }

    override suspend fun startVideo(ticket: TeachVideoTicketDto, retry: Boolean) {
        send("/api/teach/video/jobs/${ticket.jobId.encodeURLPathPart()}/${if (retry) "retry" else "start"}",
            buildJsonObject { put("title", ticket.title); put("teacher_name", ticket.teacher); put("notes", ticket.notes) }, jobToken = ticket.token)
    }
    override suspend fun videoStatus(ticket: TeachVideoTicketDto): TeachVideoStatusDto {
        val row = send("/api/teach/video/jobs/${ticket.jobId.encodeURLPathPart()}", jobToken = ticket.token).jsonObject
        return TeachVideoStatusDto(row.text("status"), row.text("error"),
            (row["result"] as? JsonObject)?.let { decodeDraft(it, ticket.title) })
    }
    override suspend fun cancelVideo(ticket: TeachVideoTicketDto) {
        send("/api/teach/video/jobs/${ticket.jobId.encodeURLPathPart()}/cancel", buildJsonObject {}, jobToken = ticket.token)
    }
    override suspend fun deleteVideo(ticket: TeachVideoTicketDto) {
        send("/api/teach/video/jobs/${ticket.jobId.encodeURLPathPart()}", httpMethod = HttpMethod.Delete, jobToken = ticket.token)
    }

    override suspend fun compileSkill(request: TeachRequestDto): TeachDraftDto {
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
        return decodeDraft(row, request.title)
    }

    private fun decodeDraft(row: JsonObject, fallbackTitle: String = ""): TeachDraftDto {
        val skill = (row["skill"] as? JsonObject) ?: row
        return TeachDraftDto(
            skillId = skill.text("id").ifBlank { row.text("draft_id") },
            title = skill.text("display_name").ifBlank { fallbackTitle },
            steps = skill.steps(::mediaUrl), raw = skill.toString(),
            draftId = row.text("draft_id"), summary = skill.text("summary"),
            compiledBy = row.text("compiled_by").ifBlank { skill.text("compiled_by") },
            fallbackReason = row.text("fallback_reason"),
            warnings = (row["video"] as? JsonObject)?.strings("warnings").orEmpty(),
            frames = row.array("frames").map { it.jsonObject }.map {
                TeachReferenceFrameDto(it.int("index"), it["t"]?.jsonPrimitive?.doubleOrNull ?: 0.0, mediaUrl(it.text("url")))
            },
            checks = skill.array("steps").map { it.jsonObject }.map {
                val check = it["verify"] as? JsonObject
                TeachStepCheckDto(it.text("id"), check?.text("status").orEmpty(), check?.text("reason").orEmpty())
            },
        )
    }

    override suspend fun verifyStep(draft: TeachDraftDto, stepIndex: Int, referenceFrame: Int?): TeachDraftDto {
        require(draft.draftId.isNotBlank()) { "Bản nháp không có mã kiểm chứng. Hãy học lại bài." }
        val skill = Json.parseToJsonElement(draft.raw).jsonObject
        val step = draft.steps[stepIndex]
        val result = send("/api/teach/verify-step", buildJsonObject {
            put("draft_id", draft.draftId); put("step", step.id); put("skill", skill)
            if (referenceFrame != null) put("reference_frame", referenceFrame)
            put("rewrite", true); put("save", false)
            // New BE uses this to rebuild criteria from edited instructions before judging.
            put("refresh_detector", true)
        }).jsonObject
        require(result["detector_refreshed"]?.jsonPrimitive?.booleanOrNull == true) {
            "Máy chủ chưa hỗ trợ cập nhật tiêu chí theo nội dung sửa. Chưa thể lưu thay đổi an toàn."
        }
        val stepPatch = result["step"] as? JsonObject ?: error("Thiếu kết quả kiểm chứng.")
        val rows = skill.array("steps").map { row ->
            val value = row.jsonObject
            if (value.text("id") == step.id) JsonObject(value + stepPatch) else value
        }
        val detectorKey = result.required("detector_key")
        val detector = result["detector"] as? JsonObject ?: error("Thiếu tiêu chí kiểm chứng.")
        val updated = JsonObject(skill + mapOf(
            "steps" to JsonArray(rows),
            "detectors" to JsonObject((skill["detectors"] as? JsonObject).orEmpty() + (detectorKey to detector)),
        ))
        val decoded = decodeDraft(buildJsonObject { put("skill", updated) })
        return decoded.copy(draftId = draft.draftId, compiledBy = draft.compiledBy,
            fallbackReason = draft.fallbackReason, warnings = draft.warnings, frames = draft.frames)
    }

    override suspend fun teachProgress(jobId: String): String =
        send("/api/teach/progress/${jobId.encodeURLPathPart()}").jsonObject.text("phase")

    override suspend fun saveSkill(draft: TeachDraftDto): String {
        val skill = Json.parseToJsonElement(draft.raw).jsonObject
        val id = skill.text("id").ifBlank { draft.skillId }
        require(id.isNotBlank()) { "Kỹ năng chưa có mã để lưu." }
        send("/api/skills/${id.encodeURLPathPart()}", skill, HttpMethod.Put)
        return id
    }

    override suspend fun tryonConfig(): TryonConfigDto {
        val row = send("/api/tryon/config").jsonObject
        val photo = row["photo"] as? JsonObject
        return TryonConfigDto(
            liveAvailable = (row["live"] as? JsonObject)?.get("available")?.jsonPrimitive?.booleanOrNull == true,
            photoProvider = photo?.text("provider").orEmpty(),
            expectedSeconds = photo?.int("expected_seconds") ?: 30,
        )
    }

    override suspend fun tryonPhoto(personImage: String, itemIds: List<String>): TryonResultDto {
        val row = send("/api/tryon/photo", buildJsonObject {
            put("person_image", personImage)
            putJsonArray("item_ids") { itemIds.forEach { add(it) } }
        }).jsonObject
        return TryonResultDto(
            mediaUrl(row.required("image_url")), row.text("provider"),
            row["seconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            row.strings("skipped_item_ids"), row.text("note"),
        )
    }

    override fun openSession(): ShadowSessionSocket =
        KtorSessionSocket(client, origin, accessCode, ::mediaUrl)

    override fun close() { client.close() }
}
