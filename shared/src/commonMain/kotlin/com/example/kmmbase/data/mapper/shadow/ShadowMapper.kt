package com.example.kmmbase.data.mapper.shadow

import com.example.kmmbase.data.dto.shadow.*
import com.example.kmmbase.domain.model.*

internal fun ShadowHealthDto.toDomain() = ShadowHealth(mode, accessRequired, visionModel)
internal fun ShadowSkillDto.toDomain() = ShadowSkill(id, title, summary, teacher, steps, coverImageUrl)
internal fun ShadowStepDto.toDomain() = ShadowStep(id, instruction, tip, warning, referenceImageUrl, onEnterSpeech)
internal fun ShadowLessonDto.toDomain() = ShadowLesson(id, title, steps.map { it.toDomain() }, teacher, summary)
internal fun ShadowAnswerDto.toDomain() = ShadowAnswer(text, source)
internal fun WardrobeItemDto.toDomain() = WardrobeItem(id, name, category, demo, imageUrl, colorHex, colors, gender)
internal fun ShadowWeatherDto.toDomain() = ShadowWeather(available, city, summary, tMin, tMax, rainProb)
internal fun ShadowOutfitItemDto.toDomain() = ShadowOutfitItem(id, name, category, imageUrl, colorHex)
internal fun ShadowOutfitDto.toDomain() = ShadowOutfit(title, reason, items.map { it.toDomain() }, tips, rulesUsed)
internal fun ShadowSuggestionsDto.toDomain() = ShadowSuggestions(outfits.map { it.toDomain() }, source, weather?.toDomain(), rulesUsed, wearing)
internal fun ShadowRuleDto.toDomain() = ShadowRule(id, text, taughtBy)
internal fun TranscriptLineDto.toDomain() = TranscriptLine(t0, t1, text)
internal fun TeachFrameDto.toDomain() = TeachFrame(t, image, reason)
internal fun TeachRequestDto.toDomain() = TeachRequest(title, teacherName, transcript.map { it.toDomain() }, frames.map { it.toDomain() }, notes, jobId)
internal fun TeachDraftDto.toDomain() = TeachDraft(skillId, title, steps.map { it.toDomain() }, raw,
    draftId = draftId, summary = summary, compiledBy = compiledBy, fallbackReason = fallbackReason, warnings = warnings,
    frames = frames.map { TeachReferenceFrame(it.index, it.seconds, it.imageUrl) },
    checks = checks.map { TeachStepCheck(it.stepId, it.status, it.reason) })
internal fun TryonConfigDto.toDomain() = TryonConfig(liveAvailable, photoProvider, expectedSeconds)
internal fun TryonResultDto.toDomain() = TryonResult(imageUrl, provider, seconds, skippedItemIds, note)

internal fun TeachRequest.toDto() = TeachRequestDto(title, teacherName,
    transcript.map { TranscriptLineDto(it.t0, it.t1, it.text) },
    frames.map { TeachFrameDto(it.t, it.image, it.reason) }, notes, jobId)

internal fun TeachDraft.toDto() = TeachDraftDto(skillId, title,
    steps.map { ShadowStepDto(it.id, it.instruction, it.tip, it.warning, it.referenceImageUrl, it.onEnterSpeech) },
    if (raw.isBlank()) encodeBuiltin() else withEditedInstructions().raw,
    draftId, summary, compiledBy, fallbackReason, warnings,
    frames.map { TeachReferenceFrameDto(it.index, it.seconds, it.imageUrl) },
    checks.map { TeachStepCheckDto(it.stepId, it.status, it.reason) })

/** Keep provider-specific detectors and transition fields intact while applying review edits. */
internal fun TeachDraft.withEditedInstructions(): TeachDraft {
    val root = kotlinx.serialization.json.Json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
        ?: error("Kỹ năng không hợp lệ.")
    val original = root["steps"] as? kotlinx.serialization.json.JsonArray ?: return this
    val byId = steps.associateBy { it.id }
    var changed = false
    val patched = kotlinx.serialization.json.JsonArray(original.map { element ->
        val step = element as? kotlinx.serialization.json.JsonObject ?: return@map element
        val id = (step["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val edited = byId[id] ?: return@map element
        val before = (step["instruction"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val fields = step.toMutableMap()
        fields["instruction"] = kotlinx.serialization.json.JsonPrimitive(edited.instruction)
        if (before != edited.instruction) {
            changed = true
            fields.remove("verify")
            fields["on_enter_speech"] = kotlinx.serialization.json.JsonPrimitive(edited.instruction)
        }
        val nextId = (step["next_step_id"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val next = byId[nextId]
        if (next != null) {
            val originalNext = original.filterIsInstance<kotlinx.serialization.json.JsonObject>().firstOrNull {
                (it["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content == nextId
            }
            if ((originalNext?.get("instruction") as? kotlinx.serialization.json.JsonPrimitive)?.content != next.instruction) {
                fields["complete_speech"] = kotlinx.serialization.json.JsonPrimitive("Tốt rồi. ${next.instruction}")
            }
        }
        kotlinx.serialization.json.JsonObject(fields)
    })
    val updated = root.toMutableMap()
    updated["steps"] = patched
    if (changed) updated.remove("quality")
    return copy(raw = kotlinx.serialization.json.JsonObject(updated).toString())
}

internal fun TeachVideoTicket.toDto() = TeachVideoTicketDto(jobId, token, title, teacher, notes)
internal fun TeachVideoTicketDto.toDomain() = TeachVideoTicket(jobId, token, title, teacher, notes)
internal fun TeachVideoStatusDto.toDomain() = TeachVideoStatus(phase, error, draft?.toDomain())
