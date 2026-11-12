package com.example.kmmbase.data.dto.shadow

import kotlinx.serialization.json.*

/** Phases the backend reports in `hud.state`. */
internal enum class SessionPhaseDto { WAITING_FOR_START, GUIDING, COMPLETED, ERROR }

internal data class SessionTaskDto(val id: String, val text: String, val completed: Boolean)

internal data class SessionStepDto(
    val id: String,
    val index: Int,
    val total: Int,
    val instruction: String,
    val tip: String,
    val warning: String,
    val referenceImageUrl: String,
)

/** Messages the server pushes over `/session/control`. */
internal sealed interface SessionEventDto {
    /** `imageMaxSide` is the size the server downscales to; sending larger only wastes bandwidth. */
    data class Ready(val sessionId: String, val imageMaxSide: Int) : SessionEventDto

    data class Hud(
        val phase: SessionPhaseDto,
        val skillName: String,
        val tasks: List<SessionTaskDto>,
        val activeTaskId: String,
        val step: SessionStepDto?,
        val speechEpoch: Long,
    ) : SessionEventDto

    /** Spoken line; `epoch` drives barge-in so a new line interrupts a stale one. */
    data class Speech(val text: String, val epoch: Long, val kind: String) : SessionEventDto

    /**
     * Result of judging one frame. When [confirming] is true the server already saw a "done"
     * frame and will judge the next one without its usual gap, so the client must send one now.
     */
    data class VisionResult(val confirming: Boolean, val latencyMs: Long, val done: Boolean, val hint: String,
        val stepId: String = "", val stale: Boolean = false) : SessionEventDto

    data class Failure(val message: String, val code: String) : SessionEventDto

    data class Connection(val status: SocketStatusDto) : SessionEventDto
}

internal enum class SocketStatusDto { CONNECTING, OPEN, CLOSED }

internal fun decodeSessionEvent(raw: String): SessionEventDto? {
        val message = Json.parseToJsonElement(raw).jsonObject
        return when (message.text("type")) {
            "session.ready" -> SessionEventDto.Ready(
                message.text("session_id"),
                (message["vision"] as? JsonObject)?.int("image_max_side")?.takeIf { it > 0 } ?: 448,
            )
            "hud.state" -> {
                val step = (message["step"] as? JsonObject)?.let {
                    SessionStepDto(
                        it.text("id"), it.int("index"), it.int("total"), it.text("instruction"),
                        it.text("tip"), it.text("warning"), it.text("reference_image_url"),
                    )
                }
                SessionEventDto.Hud(
                    phase = runCatching { SessionPhaseDto.valueOf(message.text("phase")) }
                        .getOrDefault(SessionPhaseDto.WAITING_FOR_START),
                    skillName = message.text("recipe_name"),
                    tasks = message.array("tasks").map {
                        val task = it.jsonObject
                        SessionTaskDto(task.text("id"), task.text("text"),
                            task["completed"]?.jsonPrimitive?.booleanOrNull == true)
                    },
                    activeTaskId = message.text("active_task_id"),
                    step = step,
                    speechEpoch = message["speech_epoch"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
            "speech" -> SessionEventDto.Speech(
                message.text("text"),
                message["epoch"]?.jsonPrimitive?.longOrNull ?: 0L,
                message.text("kind"),
            )
            "vision.result" -> {
                val result = message["result"] as? JsonObject
                if (message.text("error").isNotBlank()) {
                    SessionEventDto.Failure("AI chưa kiểm tra được ảnh. Kiểm tra kết nối hoặc xác nhận bước thủ công.", "vision_unavailable")
                } else SessionEventDto.VisionResult(
                    confirming = message["confirming"]?.jsonPrimitive?.booleanOrNull == true,
                    latencyMs = message["latency_ms"]?.jsonPrimitive?.longOrNull ?: 0L,
                    done = result?.get("flag")?.jsonPrimitive?.booleanOrNull == true,
                    hint = result?.text("hint").orEmpty(),
                    stepId = message.text("step_id"),
                    stale = message["stale"]?.jsonPrimitive?.booleanOrNull == true,
                )
            }
            "hud.error" -> SessionEventDto.Failure(
                message.text("message").ifBlank { "Máy chủ báo lỗi." }, message.text("code"))
            else -> null
        }
    }
