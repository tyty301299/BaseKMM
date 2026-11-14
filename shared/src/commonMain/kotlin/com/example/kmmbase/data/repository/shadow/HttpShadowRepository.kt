package com.example.kmmbase.data.repository.shadow

import com.example.kmmbase.data.remote.shadow.*
import com.example.kmmbase.data.mapper.shadow.*
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.ShadowRepository

/** Maps transport contracts to domain models. HTTP and JSON belong to Data adapters. */
class HttpShadowRepository internal constructor(private val api: ShadowApi) : ShadowRepository {
    constructor(endpoint: String, accessCode: String = "") : this(KtorShadowApi(endpoint, accessCode))
    override suspend fun health() = api.health().toDomain()
    override suspend fun authenticate(code: String) = api.authenticate(code)
    override suspend fun skills() = api.skills().map { it.toDomain() }
    override suspend fun lesson(id: String) = api.lesson(id).toDomain()
    override suspend fun ask(skillId: String, stepId: String, question: String) = api.ask(skillId, stepId, question).toDomain()
    override suspend fun deleteSkill(id: String) = api.deleteSkill(id)
    override suspend fun wardrobe() = api.wardrobe().map { it.toDomain() }
    override suspend fun addWardrobeItem(image: String, name: String, category: String) = api.addWardrobeItem(image, name, category).toDomain()
    override suspend fun suggest(event: String, notes: String, location: StylingLocation?) = api.suggest(event, notes, location).toDomain()
    override suspend fun rules() = api.rules().map { it.toDomain() }
    override suspend fun addRule(text: String, taughtBy: String) = api.addRule(text, taughtBy)
    override suspend fun uploadVideo(source: com.example.kmmbase.domain.repository.TeachVideoSource, title: String, teacher: String, notes: String) =
        api.uploadVideo(source, title, teacher, notes).toDomain()
    override suspend fun startVideo(ticket: TeachVideoTicket, retry: Boolean) = api.startVideo(ticket.toDto(), retry)
    override suspend fun videoStatus(ticket: TeachVideoTicket) = api.videoStatus(ticket.toDto()).toDomain()
    override suspend fun cancelVideo(ticket: TeachVideoTicket) = api.cancelVideo(ticket.toDto())
    override suspend fun deleteVideo(ticket: TeachVideoTicket) = api.deleteVideo(ticket.toDto())
    override suspend fun compileSkill(request: TeachRequest) = api.compileSkill(request.toDto()).toDomain()
    override suspend fun teachProgress(jobId: String) = api.teachProgress(jobId)
    override suspend fun verifyStep(draft: TeachDraft, stepIndex: Int, referenceFrame: Int?) =
        api.verifyStep(draft.toDto(), stepIndex, referenceFrame).toDomain()
    override suspend fun saveSkill(draft: TeachDraft) = api.saveSkill(draft.toDto())
    override suspend fun tryonConfig() = api.tryonConfig().toDomain()
    override suspend fun tryonPhoto(personImage: String, itemIds: List<String>) = api.tryonPhoto(personImage, itemIds).toDomain()
    override fun mediaUrl(path: String) = api.mediaUrl(path)
    override fun mediaHeadersFor(url: String) = api.mediaHeadersFor(url)
    override fun mediaHeaders() = api.mediaHeaders()
    override fun openSession() = api.openSession()
    override fun close() = api.close()
}
