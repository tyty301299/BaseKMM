package com.example.kmmbase.presentation.teach

sealed interface TeachAction {
    data class UploadVideo(val source: com.example.kmmbase.domain.repository.TeachVideoSource) : TeachAction
    data object ResumeVideo : TeachAction
    data object RetryVideo : TeachAction
    data object CancelVideo : TeachAction
    data object DeleteVideo : TeachAction
    data object StartRecording : TeachAction
    data object StopRecording : TeachAction
    data class Title(val value: String) : TeachAction
    data class Teacher(val value: String) : TeachAction
    data class Notes(val value: String) : TeachAction
    data class SpeechAvailable(val value: Boolean) : TeachAction
    /** A finished sentence from speech recognition, timestamped on the recording clock. */
    data class Sentence(val t0: Double, val t1: Double, val text: String) : TeachAction
    data class PartialSentence(val text: String) : TeachAction
    data object Compile : TeachAction
    data object CancelCompile : TeachAction
    data class EditStep(val index: Int, val instruction: String) : TeachAction
    data class VerifyStep(val index: Int, val referenceFrame: Int? = null) : TeachAction
    data object Save : TeachAction
    data object Reset : TeachAction
}
