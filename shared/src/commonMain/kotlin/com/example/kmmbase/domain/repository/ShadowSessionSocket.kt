package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*

/** Live guide session over `/session/control`. */
interface ShadowSessionSocket : AutoCloseable {
    val events: kotlinx.coroutines.flow.Flow<SessionEvent>
    suspend fun start(skillId: String)
    suspend fun sendFrame(image: String, timestampMs: Long, trigger: String)
    suspend fun stop()
    suspend fun stepManually(forward: Boolean)
}
