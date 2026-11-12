package com.example.kmmbase.presentation.shadow

import com.example.kmmbase.domain.model.*

data class ShadowState(
    val endpoint: String = "",
    val connected: Boolean = false,
    val modeLabel: String = "Chưa kết nối",
    val busy: String = "",
    val error: String? = null,
    val skills: List<ShadowSkill> = emptyList(),
    val preparedGuideId: String? = null,
    val preparedGuideTitle: String = "",
    val wardrobe: List<WardrobeItem> = emptyList(),
    val lesson: ShadowLesson? = null,
    val answer: ShadowAnswer? = null,
    val answerLabel: String = "",
    val answerQuestion: String = "",
    val suggestions: ShadowSuggestions? = null,
    val suggestionsLabel: String = "",
    val suggestionsEvent: String = "",
    val tryon: TryonResult? = null,
    val tryingOn: Boolean = false,
)
