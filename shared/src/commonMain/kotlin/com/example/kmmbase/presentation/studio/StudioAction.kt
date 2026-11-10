package com.example.kmmbase.presentation.studio

sealed interface StudioAction {
    data object Retry : StudioAction
    data class AddSample(val preset: String) : StudioAction
    data class OpenItem(val id: String) : StudioAction
    data class WardrobeFilter(val value: String) : StudioAction
    data class ItemCategory(val value: String) : StudioAction
    data class ItemName(val value: String) : StudioAction
    data object SaveItem : StudioAction
    data class DeleteItem(val id: String) : StudioAction
    data class OutfitNotes(val value: String) : StudioAction
    data class Suggest(val occasion: String) : StudioAction
    data object NewLesson : StudioAction
    data object StartRecording : StudioAction
    data object StopRecording : StudioAction
    data class Transcript(val value: String) : StudioAction
    data object CompileSample : StudioAction
    data class LessonTitle(val value: String) : StudioAction
    data class EditStep(val index: Int, val value: String) : StudioAction
    data object SaveSkill : StudioAction
    data class OpenSkill(val id: String) : StudioAction
    data object NextStep : StudioAction
    data object PreviousStep : StudioAction
    data class Question(val value: String) : StudioAction
    data object AskSample : StudioAction
    data class DeleteSkill(val id: String) : StudioAction
    data class Appearance(val dark: Boolean, val glass: Boolean) : StudioAction
    data object ClearData : StudioAction
}
