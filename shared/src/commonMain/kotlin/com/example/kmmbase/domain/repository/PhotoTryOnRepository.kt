package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*

interface PhotoTryOnRepository {
    suspend fun tryonConfig(): TryonConfig
    suspend fun tryonPhoto(personImage: String, itemIds: List<String>): TryonResult
}
