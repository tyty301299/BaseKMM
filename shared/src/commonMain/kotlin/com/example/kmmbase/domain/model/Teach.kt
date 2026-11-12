package com.example.kmmbase.domain.model

// ---- Teach ----------------------------------------------------------------

data class TranscriptLine(val t0: Double, val t1: Double, val text: String)

/** `reason` tells the compiler why the frame was kept: interval | speech | scene | start | end. */
data class TeachFrame(val t: Double, val image: String, val reason: String)

data class TeachRequest(
    val title: String,
    val teacherName: String,
    val transcript: List<TranscriptLine>,
    val frames: List<TeachFrame>,
    val notes: String,
    val jobId: String,
)

/** Compiled skill kept as raw JSON: Review edits a few fields then PUTs the whole object back. */
data class TeachDraft(
    val skillId: String, val title: String, val steps: List<ShadowStep>, val raw: String,
    val detectorPrompts: List<String> = emptyList(),
    val draftId: String = "",
    val summary: String = "",
    val compiledBy: String = "",
    val fallbackReason: String = "",
    val warnings: List<String> = emptyList(),
    val frames: List<TeachReferenceFrame> = emptyList(),
    val checks: List<TeachStepCheck> = emptyList(),
)

data class TeachReferenceFrame(val index: Int, val seconds: Double, val imageUrl: String)
data class TeachStepCheck(val stepId: String, val status: String, val reason: String)
