package com.example.kmmbase.presentation.notes

sealed interface NoteEditAction {
    data class TitleChanged(val value: String) : NoteEditAction
    data class BodyChanged(val value: String) : NoteEditAction
    data object Save : NoteEditAction
    data object Delete : NoteEditAction
    data object Retry : NoteEditAction
}
