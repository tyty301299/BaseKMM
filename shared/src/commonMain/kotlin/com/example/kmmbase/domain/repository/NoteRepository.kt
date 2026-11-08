package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeAll(): Flow<List<Note>>

    // @Throws turns Kotlin failures into Swift errors instead of terminating the app.
    @Throws(Exception::class)
    suspend fun findById(id: String): Note?

    @Throws(Exception::class)
    suspend fun save(note: Note)

    @Throws(Exception::class)
    suspend fun delete(id: String)
}
