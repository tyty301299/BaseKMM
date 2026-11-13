package com.example.kmmbase.data.remote.shadow

import com.example.kmmbase.data.dto.shadow.*
import com.example.kmmbase.domain.repository.ShadowSessionSocket

internal interface ShadowApi : AutoCloseable {
    suspend fun health(): ShadowHealthDto
    suspend fun authenticate(code: String)
    suspend fun skills(): List<ShadowSkillDto>
    suspend fun lesson(id: String): ShadowLessonDto
    suspend fun ask(skillId: String, stepId: String, question: String): ShadowAnswerDto
    suspend fun deleteSkill(id: String)

    suspend fun wardrobe(): List<WardrobeItemDto>
    suspend fun addWardrobeItem(image: String, name: String, category: String): WardrobeItemDto
    suspend fun suggest(event: String, notes: String, city: String): ShadowSuggestionsDto
    suspend fun rules(): List<ShadowRuleDto>
    suspend fun addRule(text: String, taughtBy: String)

    /** Compiling calls a large model and takes 10-30 s; poll [teachProgress] to show the phase. */
    suspend fun uploadVideo(source: com.example.kmmbase.domain.repository.TeachVideoSource, title: String, teacher: String, notes: String): TeachVideoTicketDto
    suspend fun startVideo(ticket: TeachVideoTicketDto, retry: Boolean)
    suspend fun videoStatus(ticket: TeachVideoTicketDto): TeachVideoStatusDto
    suspend fun cancelVideo(ticket: TeachVideoTicketDto)
    suspend fun deleteVideo(ticket: TeachVideoTicketDto)
    suspend fun compileSkill(request: TeachRequestDto): TeachDraftDto
    suspend fun teachProgress(jobId: String): String
    suspend fun verifyStep(draft: TeachDraftDto, stepIndex: Int, referenceFrame: Int?): TeachDraftDto
    suspend fun saveSkill(draft: TeachDraftDto): String

    suspend fun tryonConfig(): TryonConfigDto
    suspend fun tryonPhoto(personImage: String, itemIds: List<String>): TryonResultDto

    /** Absolute URL for a server-relative media path, so the UI can load images. */
    fun mediaUrl(path: String): String

    /** Headers an image loader must send, since server media sits behind the access gate. */
    fun mediaHeadersFor(url: String): Map<String, String> = emptyMap()
    fun mediaHeaders(): Map<String, String>

    /** Opens the guide session socket. Each call owns its connection. */
    fun openSession(): ShadowSessionSocket
}
