package com.example.kmmbase.presentation.shadow

sealed interface ShadowAction {
    data class Connect(val endpoint: String, val code: String) : ShadowAction
    data object Reconnect : ShadowAction
    data object Refresh : ShadowAction
    data class PrepareBuiltinGuide(val kind: String) : ShadowAction
    data object ClearPreparedGuide : ShadowAction
    data class OpenLesson(val id: String) : ShadowAction
    data object CloseLesson : ShadowAction
    data object ClearAnswer : ShadowAction
    data class Ask(val stepId: String, val question: String) : ShadowAction
    data class Suggest(val event: String, val notes: String) : ShadowAction
    data class StylePerson(val image: String, val event: String, val notes: String) : ShadowAction
    data object CancelTryon : ShadowAction
    data object ClearTryon : ShadowAction
}
