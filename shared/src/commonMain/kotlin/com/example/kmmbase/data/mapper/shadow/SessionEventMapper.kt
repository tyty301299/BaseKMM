package com.example.kmmbase.data.mapper.shadow

import com.example.kmmbase.data.dto.shadow.*
import com.example.kmmbase.domain.model.*

internal fun SessionEventDto.toDomain(media: (String) -> String): SessionEvent = when (this) {
    is SessionEventDto.Ready -> SessionEvent.Ready(sessionId, imageMaxSide)
    is SessionEventDto.Hud -> SessionEvent.Hud(SessionPhase.valueOf(phase.name), skillName,
        tasks.map { SessionTask(it.id, it.text, it.completed) }, activeTaskId,
        step?.let { SessionStep(it.id, it.index, it.total, it.instruction, it.tip, it.warning, media(it.referenceImageUrl)) }, speechEpoch)
    is SessionEventDto.Speech -> SessionEvent.Speech(text, epoch, kind)
    is SessionEventDto.VisionResult -> SessionEvent.VisionResult(confirming, latencyMs, done, hint, stepId, stale)
    is SessionEventDto.Failure -> SessionEvent.Failure(message, code)
    is SessionEventDto.Connection -> SessionEvent.Connection(SocketStatus.valueOf(status.name))
}
