package com.example.kmmbase.domain.model

data class ShadowSkill(
    val id: String,
    val title: String,
    val summary: String,
    val teacher: String,
    val steps: Int,
    val coverImageUrl: String = "",
)

data class ShadowStep(
    val id: String,
    val instruction: String,
    val tip: String,
    val warning: String,
    val referenceImageUrl: String = "",
    val onEnterSpeech: String = "",
)

data class ShadowLesson(
    val id: String,
    val title: String,
    val steps: List<ShadowStep>,
    val teacher: String = "",
    val summary: String = "",
)

data class ShadowAnswer(val text: String, val source: String)
