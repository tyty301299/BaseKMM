package com.example.kmmbase.data.dto.shadow

/** Weather for the styling request; `available` false means Open-Meteo could not be reached. */
internal data class ShadowWeatherDto(
    val available: Boolean,
    val city: String,
    val summary: String,
    val tMin: Double?,
    val tMax: Double?,
    val rainProb: Int?,
)

internal data class ShadowOutfitItemDto(
    val id: String,
    val name: String,
    val category: String,
    val imageUrl: String,
    val colorHex: String,
)

internal data class ShadowOutfitDto(
    val title: String,
    val reason: String,
    val items: List<ShadowOutfitItemDto>,
    val tips: String,
    val rulesUsed: List<String> = emptyList(),
)

internal data class ShadowSuggestionsDto(
    val outfits: List<ShadowOutfitDto>,
    val source: String,
    val weather: ShadowWeatherDto? = null,
    val rulesUsed: List<String> = emptyList(),
    val wearing: String = "",
)

/** A rule a human taught the stylist, shown as "Học từ <taughtBy>". */
internal data class ShadowRuleDto(val id: String, val text: String, val taughtBy: String)
