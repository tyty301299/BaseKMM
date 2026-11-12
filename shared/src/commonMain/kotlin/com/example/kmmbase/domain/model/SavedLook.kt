package com.example.kmmbase.domain.model

/** Local history metadata; this record does not contain camera photos or videos. */
data class SavedLook(
    val id: String,
    val preset: String,
    val occasion: String,
    val kind: String,
    val createdAt: Long,
    val before: Boolean = false,
    val flipped: Boolean = false,
) {
    val isGuide: Boolean get() = kind.startsWith("guide-")
    val title: String get() = if (isGuide) occasion else if (preset == "blouse") "Cam đào dịu nhẹ" else "Xanh rêu, nhẹ tênh"
}
