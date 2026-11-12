package com.example.kmmbase.presentation.notes

sealed interface NoteEditEffect {
    data object Finished : NoteEditEffect
}
