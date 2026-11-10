package com.example.kmmbase.presentation.common

/** One-shot feedback. Never use a success message before a durable write completes. */
data class UiMessage(val text: String, val isError: Boolean = false)
