package com.example.kmmbase.domain.repository

/** Database lifetime belongs to the composition root, never a screen. */
interface LocalRepositories : AutoCloseable {
    val notes: NoteRepository
    val looks: LookRepository
    val studio: StudioRepository
}
