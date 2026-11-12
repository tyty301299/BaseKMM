package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*
import kotlinx.coroutines.flow.Flow

interface LiveTryOnRepository : AutoCloseable {
    suspend fun config(): LiveTryOnConfig
    suspend fun reference(item: WardrobeItem): String
    suspend fun createSession(reference: String): LiveTryOnSession
    fun control(session: LiveTryOnSession): LiveTryOnControl
}
