package com.example.kmmbase.data.dto.shadow

internal data class WardrobeItemDto(
    val id: String,
    val name: String,
    val category: String,
    val demo: Boolean,
    val imageUrl: String = "",
    val colorHex: String = "",
    val colors: List<String> = emptyList(),
)
