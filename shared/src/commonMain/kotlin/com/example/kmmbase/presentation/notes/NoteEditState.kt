package com.example.kmmbase.presentation.notes

import com.example.kmmbase.domain.model.*

data class NoteEditState(
    val title: String = "",
    val body: String = "",
    val isExisting: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: AppError? = null,
) {
    val canSave: Boolean
        get() = !isLoading && !isSaving && error != AppError.NOT_FOUND && title.isNotBlank()
}
