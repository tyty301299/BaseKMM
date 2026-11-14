package com.example.kmmbase.data.dto.shadow

import kotlinx.serialization.json.*

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

internal fun JsonObject.steps(media: (String) -> String): List<ShadowStepDto> = array("steps").map {
    val step = it.jsonObject
    ShadowStepDto(
        step.required("id"), step.text("instruction"), step.text("tip"), step.text("warning"),
        media(step.text("reference_image_url")), step.text("on_enter_speech"),
    )
}

internal fun JsonObject.toWardrobeItem(media: (String) -> String) = WardrobeItemDto(
    required("id"), required("name"), text("category"),
    get("demo")?.jsonPrimitive?.booleanOrNull == true,
    media(text("image_url")), text("color_hex"), strings("colors"), text("gender").ifBlank { "unknown" },
)
