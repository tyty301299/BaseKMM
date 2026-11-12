package com.example.kmmbase.data.repository.livetryon

import com.example.kmmbase.data.remote.shadow.normalizeShadowOrigin
import com.example.kmmbase.data.dto.live.*
import com.example.kmmbase.data.mapper.livetryon.toDomain
import com.example.kmmbase.data.remote.live.*
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.*

internal class LiveTryOnRepositoryImpl(endpoint: String, headers: Map<String, String>) : LiveTryOnRepository {
    private val api = LiveTryOnApi(normalizeShadowOrigin(endpoint), headers)
    override suspend fun config() = LiveConfigDto.decode(api.json("/api/tryon/config")).toDomain()
    override suspend fun reference(item: WardrobeItem) = api.reference(item.imageUrl)
    override suspend fun createSession(reference: String) =
        LiveSessionDto.decode(api.json("/api/tryon/live-session", LiveSessionRequestDto.encode(reference))).toDomain()
    override fun control(session: LiveTryOnSession): LiveTryOnControl = ViduControlSocket(session)
    override fun close() = api.close()
}
