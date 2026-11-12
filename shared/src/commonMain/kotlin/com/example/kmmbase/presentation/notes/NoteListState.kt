package com.example.kmmbase.presentation.notes

import com.example.kmmbase.domain.model.*

data class NoteListState(
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
)
