package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*

interface ShadowRepository : AutoCloseable {
    suspend fun health(): ShadowHealth
    suspend fun authenticate(code: String)
    suspend fun skills(): List<ShadowSkill>
    suspend fun lesson(id: String): ShadowLesson
    suspend fun ask(skillId: String, stepId: String, question: String): ShadowAnswer
    suspend fun deleteSkill(id: String)

    suspend fun wardrobe(): List<WardrobeItem>
    suspend fun suggest(event: String, notes: String, city: String): ShadowSuggestions
    suspend fun rules(): List<ShadowRule>
    suspend fun addRule(text: String, taughtBy: String)

    /** Compiling calls a large model and takes 10-30 s; poll [teachProgress] to show the phase. */
    suspend fun compileSkill(request: TeachRequest): TeachDraft
    suspend fun teachProgress(jobId: String): String
    suspend fun saveSkill(draft: TeachDraft): String

    suspend fun tryonConfig(): TryonConfig
    suspend fun tryonPhoto(personImage: String, itemIds: List<String>): TryonResult

    /** Absolute URL for a server-relative media path, so the UI can load images. */
    fun mediaUrl(path: String): String

    /** Headers an image loader must send, since server media sits behind the access gate. */
    fun mediaHeaders(): Map<String, String>

    /** Opens the guide session socket. Each call owns its connection. */
    fun openSession(): ShadowSessionSocket
}

/** Live guide session over `/session/control`. */
interface ShadowSessionSocket : AutoCloseable {
    val events: kotlinx.coroutines.flow.Flow<SessionEvent>
    suspend fun start(skillId: String)
    suspend fun sendFrame(image: String, timestampMs: Long, trigger: String)
    suspend fun stop()
    suspend fun stepManually(forward: Boolean)
}
