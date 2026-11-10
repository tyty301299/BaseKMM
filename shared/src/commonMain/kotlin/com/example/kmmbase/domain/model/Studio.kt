package com.example.kmmbase.domain.model

/** Local UI-demo data, separate from server-backed wardrobe and skills. */
data class StudioItem(val id: String, val preset: String, val name: String, val category: String)
data class StudioSkill(val id: String, val title: String, val steps: List<String>, val createdAt: Long)
data class StudioSettings(val dark: Boolean = true, val glass: Boolean = true)
data class StudioData(
    val items: List<StudioItem> = emptyList(),
    val skills: List<StudioSkill> = emptyList(),
    val settings: StudioSettings = StudioSettings(),
)
data class StudioOutfit(val itemId: String, val preset: String, val title: String, val reason: String)
