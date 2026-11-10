package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*
import kotlinx.coroutines.flow.Flow

interface StudioRepository {
    fun observe(): Flow<StudioData>
    suspend fun saveItem(item: StudioItem)
    suspend fun deleteItem(id: String)
    suspend fun saveSkill(skill: StudioSkill)
    suspend fun deleteSkill(id: String)
    suspend fun saveSettings(settings: StudioSettings)
    suspend fun clearDemoData()
}
