package com.example.kmmbase.presentation.shadow

import com.example.kmmbase.domain.model.TeachDraft
import kotlinx.serialization.json.*

/** Versioned, app-owned sample skills. The server judges frames; uncertainty must not advance. */
internal fun builtinGuide(kind: String): TeachDraft {
    require(kind in setOf("fold", "pants", "gift"))
    val id = when (kind) { "fold" -> "shadow-builtin-gap-ao-v1"; "pants" -> "shadow-builtin-gap-quan-v1"; else -> "shadow-builtin-goi-qua-v1" }
    val title = when (kind) { "fold" -> "Gấp áo · SHADOW"; "pants" -> "Gấp quần · SHADOW"; else -> "Gói quà · SHADOW" }
    val steps = if (kind == "fold") listOf(
        "Trải áo phẳng, mặt trước úp xuống và vuốt thẳng." to "A single shirt is fully visible lying flat on a table, opened out with the body and both sleeves spread. The shirt is not folded. If uncertain return false.",
        "Gấp một bên áo và tay áo vào giữa." to "Exactly one side of the shirt including its sleeve is visibly folded inward across the torso; the opposite sleeve and side remain spread out. If hidden or both sides appear unfolded, return false.",
        "Gấp bên còn lại vào giữa để hai mép thẳng hàng." to "Both sleeves and both side edges of the shirt are folded inward over the body, producing a narrow long rectangle, but the lower half is not yet folded upward. If uncertain return false.",
        "Gấp phần thân áo từ dưới lên và vuốt phẳng." to "The bottom half of the shirt has been folded up over the upper half, forming a compact folded rectangle. A shirt merely laid flat or still long is not done. If uncertain return false."
    ) else if (kind == "pants") listOf(
        "Trải quần phẳng, kéo thẳng hai ống quần." to "A pair of trousers is laid flat and fully visible on a table with both legs extended and not folded. If the trousers or legs are obscured, return false.",
        "Gấp đôi quần theo chiều dọc, chồng hai ống lên nhau." to "The trousers are folded lengthwise so that one leg is directly over the other and the edges are aligned. If the legs are side by side or not visible, return false.",
        "Gấp phần đáy quần gọn vào trong." to "The protruding crotch area is visibly tucked inward, leaving a neat straight edge along the lengthwise folded trousers. If the crotch is obscured or protrudes, return false.",
        "Gấp ống quần lên cạp và vuốt phẳng." to "The lower legs have been folded upward toward the waistband, creating a compact, flat folded pair of trousers. If the legs are still fully extended or hidden, return false."
    ) else listOf(
        "Đặt hộp lên giữa mặt trái của giấy gói." to "A gift box sits on top of the center of a visibly larger, flat sheet of wrapping paper, with enough paper around it to wrap the box. If the box or paper is not visible return false.",
        "Kéo hai mép giấy qua hộp, chồng lên nhau và dán lại." to "The wrapping paper has been pulled over the box and the long edges overlap on top, enclosing the box around its middle. If paper remains flat or the overlapping seam is not visible, return false.",
        "Gấp hai cạnh bên thành hình tam giác, rồi gập và dán đầu hộp." to "One short end of the gift box has its wrapping paper folded inward and flattened against the end, with no loose paper protruding at that end. If the end is obscured or still open, return false.",
        "Lặp lại ở đầu còn lại, sau đó buộc ruy băng." to "The gift box is fully enclosed in wrapping paper at both ends and a ribbon or bow is visibly tied around it. If either end or the ribbon is not clearly visible, return false."
    )
    val raw = buildJsonObject {
        put("id", id); put("display_name", title); put("start_step_id", "step_1"); put("language", "vi")
        put("summary", "Hướng dẫn từng bước bằng camera")
        putJsonArray("tasks") { steps.forEachIndexed { index, (text, _) -> addJsonObject { put("id", "task_${index + 1}"); put("text", text) } } }
        putJsonObject("detectors") { steps.forEachIndexed { index, (_, prompt) -> putJsonObject("step_${index + 1}") {
            put("field", "flag"); put("prompt", "Return JSON with a single boolean flag. Return true only when: $prompt")
        } } }
        putJsonArray("steps") { steps.forEachIndexed { index, (text, _) -> addJsonObject {
            put("id", "step_${index + 1}"); put("task_id", "task_${index + 1}"); put("detector_key", "step_${index + 1}")
            put("evaluation_mode", "momentary_true_complete"); put("complete_on", true)
            put("instruction", text); put("on_enter_speech", text); put("allow_dynamic_hint", true)
            if (index < steps.lastIndex) put("next_step_id", "step_${index + 2}") else put("complete_speech", "Hoàn thành rồi!")
        } } }
    }.toString()
    return TeachDraft(id, title, emptyList(), raw)
}
