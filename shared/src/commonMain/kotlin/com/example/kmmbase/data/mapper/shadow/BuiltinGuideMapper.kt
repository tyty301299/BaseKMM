package com.example.kmmbase.data.mapper.shadow

import com.example.kmmbase.domain.model.TeachDraft
import kotlinx.serialization.json.*

internal fun TeachDraft.encodeBuiltin(): String {
    require(steps.isNotEmpty() && steps.size == detectorPrompts.size)
    return buildJsonObject {
        put("id", skillId); put("display_name", title); put("start_step_id", "step_1"); put("language", "vi")
        put("summary", "Hướng dẫn từng bước bằng camera")
        putJsonArray("tasks") { steps.forEachIndexed { index, step -> addJsonObject { put("id", "task_${index + 1}"); put("text", step.instruction) } } }
        putJsonObject("detectors") { detectorPrompts.forEachIndexed { index, prompt -> putJsonObject("step_${index + 1}") {
            put("field", "flag"); put("prompt", "Return JSON with a single boolean flag. Return true only when: $prompt")
        } } }
        putJsonArray("steps") { steps.forEachIndexed { index, step -> addJsonObject {
            put("id", "step_${index + 1}"); put("task_id", "task_${index + 1}"); put("detector_key", "step_${index + 1}")
            put("evaluation_mode", "momentary_true_complete"); put("complete_on", true)
            put("instruction", step.instruction); put("on_enter_speech", step.onEnterSpeech); put("allow_dynamic_hint", true)
            if (index < steps.lastIndex) put("next_step_id", "step_${index + 2}") else put("complete_speech", "Hoàn thành rồi!")
        } } }
    }.toString()
}
