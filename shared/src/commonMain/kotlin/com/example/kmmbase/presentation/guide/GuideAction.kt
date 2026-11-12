package com.example.kmmbase.presentation.guide

sealed interface GuideAction {
    data class Start(val skillId: String) : GuideAction
    data object Stop : GuideAction
    data object RepeatInstruction : GuideAction
    data class SpeechAvailable(val value: Boolean) : GuideAction
    data class Step(val forward: Boolean) : GuideAction
    data class ShowDebugControls(val visible: Boolean) : GuideAction
    data object DismissError : GuideAction
}
