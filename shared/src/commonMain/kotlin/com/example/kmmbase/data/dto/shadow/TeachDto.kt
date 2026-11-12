package com.example.kmmbase.data.dto.shadow

// ---- Teach ----------------------------------------------------------------

internal data class TranscriptLineDto(val t0: Double, val t1: Double, val text: String)

/** `reason` tells the compiler why the frame was kept: interval | speech | scene | start | end. */
internal data class TeachFrameDto(val t: Double, val image: String, val reason: String)

internal data class TeachRequestDto(
    val title: String,
    val teacherName: String,
    val transcript: List<TranscriptLineDto>,
    val frames: List<TeachFrameDto>,
    val notes: String,
    val jobId: String,
)

/** Compiled skill kept as raw JSON: Review edits a few fields then PUTs the whole object back. */
internal data class TeachDraftDto(
    val skillId: String, val title: String, val steps: List<ShadowStepDto>, val raw: String,
    val draftId: String = "",
    val summary: String = "",
    val compiledBy: String = "",
    val fallbackReason: String = "",
    val warnings: List<String> = emptyList(),
    val frames: List<TeachReferenceFrameDto> = emptyList(),
    val checks: List<TeachStepCheckDto> = emptyList(),
)

internal data class TeachReferenceFrameDto(val index: Int, val seconds: Double, val imageUrl: String)
internal data class TeachStepCheckDto(val stepId: String, val status: String, val reason: String)
