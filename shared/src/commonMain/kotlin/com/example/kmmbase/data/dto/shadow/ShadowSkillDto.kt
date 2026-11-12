package com.example.kmmbase.data.dto.shadow

internal data class ShadowSkillDto(
    val id: String,
    val title: String,
    val summary: String,
    val teacher: String,
    val steps: Int,
    val coverImageUrl: String = "",
)

internal data class ShadowStepDto(
    val id: String,
    val instruction: String,
    val tip: String,
    val warning: String,
    val referenceImageUrl: String = "",
    val onEnterSpeech: String = "",
)

internal data class ShadowLessonDto(
    val id: String,
    val title: String,
    val steps: List<ShadowStepDto>,
    val teacher: String = "",
    val summary: String = "",
)

internal data class ShadowAnswerDto(val text: String, val source: String)
