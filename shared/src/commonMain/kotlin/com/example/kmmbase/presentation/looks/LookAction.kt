package com.example.kmmbase.presentation.looks

sealed interface LookAction {
    data class SelectPreset(val preset: String) : LookAction
    data class SelectOccasion(val occasion: String) : LookAction
    data class Filter(val kind: String) : LookAction
    data class SaveGuideCompletion(val sessionId: String, val skillId: String, val title: String, val source: String) : LookAction
    data class Delete(val id: String) : LookAction
    data class Reuse(val id: String) : LookAction
    data object Compare : LookAction
    data object Flip : LookAction
    data object Capture : LookAction
    data object Retake : LookAction
    data object SaveCapture : LookAction
    data object SaveOutfit : LookAction
    data object Retry : LookAction
}
