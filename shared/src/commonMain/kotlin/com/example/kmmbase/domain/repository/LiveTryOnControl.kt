package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*
import kotlinx.coroutines.flow.Flow

interface LiveTryOnControl {
    val failures: Flow<String>
    suspend fun connect()
    /** Completes when sent. Vidu sends type 14 only on failure, no success ACK. */
    suspend fun switchReference(reference: String)
    fun close()
}
