package com.example.kmmbase.domain.model

data class WardrobeItem(
    val id: String,
    val name: String,
    val category: String,
    val demo: Boolean,
    val imageUrl: String = "",
    val colorHex: String = "",
    val colors: List<String> = emptyList(),
)
