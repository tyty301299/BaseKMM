package com.example.kmmbase.domain

import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeAll(): Flow<List<Note>>
    suspend fun findById(id: String): Note?
    suspend fun save(note: Note)
    suspend fun delete(id: String)
}
