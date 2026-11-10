package com.example.kmmbase.domain.model

/** Demo metadata only: no camera photo, cloud upload or generated AI image. */
data class SavedLook(
    val id: String,
    val preset: String,
    val occasion: String,
    val kind: String,
    val createdAt: Long,
    val before: Boolean = false,
    val flipped: Boolean = false,
) {
    val title: String get() = if (preset == "blouse") "Cam đào dịu nhẹ" else "Xanh rêu, nhẹ tênh"
}
