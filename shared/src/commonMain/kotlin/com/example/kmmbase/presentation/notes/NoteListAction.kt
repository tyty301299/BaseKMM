package com.example.kmmbase.presentation.notes

sealed interface NoteListAction {
    data object Retry : NoteListAction
    data class Delete(val id: String) : NoteListAction
}
