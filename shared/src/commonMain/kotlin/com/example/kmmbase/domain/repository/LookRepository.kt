package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.SavedLook
import kotlinx.coroutines.flow.Flow

interface LookRepository {
    fun observeAll(): Flow<List<SavedLook>>
    suspend fun save(look: SavedLook)
    suspend fun delete(id: String)
}
