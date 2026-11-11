package com.example.kmmbase.domain.model

class ShadowConnectionException(message: String) : Exception(message)

data class ShadowHealth(val mode: String, val accessRequired: Boolean, val visionModel: String = "")
data class ShadowSkill(
    val id: String,
    val title: String,
    val summary: String,
    val teacher: String,
    val steps: Int,
    val coverImageUrl: String = "",
)

data class ShadowStep(
    val id: String,
    val instruction: String,
    val tip: String,
    val warning: String,
    val referenceImageUrl: String = "",
    val onEnterSpeech: String = "",
)

data class ShadowLesson(
    val id: String,
    val title: String,
    val steps: List<ShadowStep>,
    val teacher: String = "",
    val summary: String = "",
)

data class ShadowAnswer(val text: String, val source: String)

data class WardrobeItem(
    val id: String,
    val name: String,
    val category: String,
    val demo: Boolean,
    val imageUrl: String = "",
    val colorHex: String = "",
    val colors: List<String> = emptyList(),
)

/** Weather for the styling request; `available` false means Open-Meteo could not be reached. */
data class ShadowWeather(
    val available: Boolean,
    val city: String,
    val summary: String,
    val tMin: Double?,
    val tMax: Double?,
    val rainProb: Int?,
)

data class ShadowOutfitItem(
    val id: String,
    val name: String,
    val category: String,
    val imageUrl: String,
    val colorHex: String,
)

data class ShadowOutfit(
    val title: String,
    val reason: String,
    val items: List<ShadowOutfitItem>,
    val tips: String,
    val rulesUsed: List<String> = emptyList(),
)

data class ShadowSuggestions(
    val outfits: List<ShadowOutfit>,
    val source: String,
    val weather: ShadowWeather? = null,
    val rulesUsed: List<String> = emptyList(),
    val wearing: String = "",
)

/** A rule a human taught the stylist, shown as "Học từ <taughtBy>". */
data class ShadowRule(val id: String, val text: String, val taughtBy: String)

// ---- Teach ----------------------------------------------------------------

data class TranscriptLine(val t0: Double, val t1: Double, val text: String)

/** `reason` tells the compiler why the frame was kept: interval | speech | scene | start | end. */
data class TeachFrame(val t: Double, val image: String, val reason: String)

data class TeachRequest(
    val title: String,
    val teacherName: String,
    val transcript: List<TranscriptLine>,
    val frames: List<TeachFrame>,
    val notes: String,
    val jobId: String,
)

/** Compiled skill kept as raw JSON: Review edits a few fields then PUTs the whole object back. */
data class TeachDraft(val skillId: String, val title: String, val steps: List<ShadowStep>, val raw: String)

// ---- Try-on ---------------------------------------------------------------

data class TryonConfig(val liveAvailable: Boolean, val photoProvider: String, val expectedSeconds: Int)
data class TryonResult(val imageUrl: String, val provider: String, val seconds: Double,
    val skippedItemIds: List<String> = emptyList(), val note: String = "")

fun shadowSourceLabel(source: String): String = when (source) {
    "ai" -> "AI"
    "mock" -> "Demo · kết quả giả lập"
    "fallback" -> "Dự phòng · không phải kết quả AI"
    "ai+fallback" -> "AI kết hợp gợi ý dự phòng"
    else -> "Chưa xác định nguồn kết quả"
}
