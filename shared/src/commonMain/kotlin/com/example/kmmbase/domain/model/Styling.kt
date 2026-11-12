package com.example.kmmbase.domain.model

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
