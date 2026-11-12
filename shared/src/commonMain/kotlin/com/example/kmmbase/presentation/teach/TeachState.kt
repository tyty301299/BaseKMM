package com.example.kmmbase.presentation.teach

import com.example.kmmbase.domain.model.*

data class TeachState(
    val phase: TeachPhase = TeachPhase.IDLE,
    val title: String = "",
    val teacher: String = "",
    val notes: String = "",
    val frameCount: Int = 0,
    val lines: List<TranscriptLine> = emptyList(),
    val liveTranscript: String = "",
    val elapsedMs: Long = 0,
    /** "learning" then "verifying", polled from the server for the two-phase wait screen. */
    val compilePhase: String = "",
    val draft: TeachDraft? = null,
    val savedSkillId: String? = null,
    val error: String? = null,
    val videoLocalUri: String = "",
    val videoStatus: String = "",
    val hasVideoJob: Boolean = false,
    val videoBusy: Boolean = false,
    val stopping: Boolean = false,
    val dirtyStepIds: List<String> = emptyList(),
    val verifyingStep: Int? = null,
    val saving: Boolean = false,
    val speechAvailable: Boolean = true,
) {
    val videoStatusLabel: String get() = when (videoStatus) {
        "uploading" -> "Đang tải video"
        "uploaded", "queued" -> "Đang chờ xử lý"
        "extracting" -> "Đang chọn hình ảnh quan trọng"
        "transcribing" -> "Đang nhận dạng lời nói"
        "learning" -> "Đang tạo bài hướng dẫn"
        "verifying" -> "Đang kiểm chứng các bước"
        "ready" -> "Bản nháp đã sẵn sàng"
        "failed" -> "Xử lý chưa thành công"
        "cancelled" -> "Đã dừng xử lý"
        "interrupted", "paused" -> "Có tác vụ đã lưu; có thể mở lại"
        else -> videoStatus
    }
    val canCompile: Boolean get() = phase == TeachPhase.IDLE && !videoBusy && !stopping && !saving && frameCount > 0 && title.isNotBlank() &&
        (lines.isNotEmpty() || notes.isNotBlank())
    val canSave: Boolean get() = phase == TeachPhase.REVIEW && !videoBusy && !saving && verifyingStep == null && dirtyStepIds.isEmpty() &&
        draft?.steps?.let { steps -> steps.isNotEmpty() && steps.all { it.instruction.isNotBlank() } } == true
}

enum class TeachPhase { IDLE, RECORDING, COMPILING, REVIEW, SAVED }
