package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*

interface ShadowRepository : AutoCloseable, SkillRepository, WardrobeRepository, TeachRepository, PhotoTryOnRepository {
    suspend fun health(): ShadowHealth
    suspend fun authenticate(code: String)

    /** Absolute URL for a server-relative media path, so the UI can load images. */
    fun mediaUrl(path: String): String

    /** Headers an image loader must send, since server media sits behind the access gate. */
    fun mediaHeadersFor(url: String): Map<String, String> = emptyMap()
    fun mediaHeaders(): Map<String, String>

    /** Opens the guide session socket. Each call owns its connection. */
    fun openSession(): ShadowSessionSocket
}
