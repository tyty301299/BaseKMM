package com.example.kmmbase.domain.model

/** Position in a camera frame, as fractions (0..1) of its width and height. */
data class ZoneBox(val left: Double, val top: Double, val right: Double, val bottom: Double)

data class DetectedObject(val label: String, val box: ZoneBox)

/** What Gemini sees in one photo. [target] is null when the placement spot cannot be found. */
data class SceneScan(val target: ZoneBox?, val objects: List<DetectedObject>)

/** [defaultZone] is drawn until Gemini has located the real position in the photo. */
data class KitchenStep(
    val id: String,
    val title: String,
    val instruction: String,
    val defaultZone: ZoneBox,
)

// The ids must match CRITERIA in server/app/verify.py.
object KitchenSteps {
    val all: List<KitchenStep> = listOf(
        KitchenStep("cups", "Gom cốc", "Gom 3 cái cốc sang bên phải mặt bàn.", ZoneBox(0.55, 0.2, 0.95, 0.8)),
        KitchenStep("bowls", "Xếp bát", "Xếp 3 cái bát chồng lên nhau.", ZoneBox(0.3, 0.35, 0.7, 0.65)),
        KitchenStep("spoons", "Gom thìa", "Gom tất cả thìa vào khay.", ZoneBox(0.1, 0.55, 0.5, 0.85)),
    )
}

enum class VerificationStatus { DONE, NOT_DONE, UNCLEAR }

data class StepVerification(
    val status: VerificationStatus,
    val message: String,
    val misplaced: List<ZoneBox> = emptyList(),
)
