package com.example.kmmbase.domain.model

class ShadowConnectionException(message: String) : Exception(message)

data class ShadowHealth(val mode: String, val accessRequired: Boolean)
data class ShadowSkill(val id: String, val title: String, val summary: String, val teacher: String, val steps: Int)
data class ShadowStep(val id: String, val instruction: String, val tip: String, val warning: String)
data class ShadowLesson(val id: String, val title: String, val steps: List<ShadowStep>)
data class ShadowAnswer(val text: String, val source: String)
data class WardrobeItem(val id: String, val name: String, val category: String, val demo: Boolean)
data class ShadowOutfit(val title: String, val reason: String, val items: List<String>, val tips: String)
data class ShadowSuggestions(val outfits: List<ShadowOutfit>, val source: String)

fun shadowSourceLabel(source: String): String = when (source) {
    "ai" -> "AI"
    "mock" -> "Demo · kết quả giả lập"
    "fallback" -> "Dự phòng · không phải kết quả AI"
    "ai+fallback" -> "AI kết hợp gợi ý dự phòng"
    else -> "Chưa xác định nguồn kết quả"
}
