package com.example.kmmbase.di

import com.example.kmmbase.domain.repository.*
import com.example.kmmbase.data.repository.shadow.HttpShadowRepository
import com.example.kmmbase.data.repository.livetryon.LiveTryOnRepositoryImpl
import com.example.kmmbase.data.remote.shadow.normalizeShadowOrigin

/** Replace a provider here; Store and UI continue using the same domain contracts. */
interface AppServices {
    fun shadow(endpoint: String, accessCode: String): ShadowRepository
    fun liveTryOn(endpoint: String, headers: Map<String, String>): LiveTryOnRepository
    fun normalizeEndpoint(value: String): String
    fun openLocalData(): LocalRepositories
}

class DefaultAppServices : AppServices {
    override fun shadow(endpoint: String, accessCode: String): ShadowRepository = HttpShadowRepository(endpoint, accessCode)
    override fun liveTryOn(endpoint: String, headers: Map<String, String>): LiveTryOnRepository = LiveTryOnRepositoryImpl(endpoint, headers)
    override fun normalizeEndpoint(value: String) = normalizeShadowOrigin(value)
    override fun openLocalData(): LocalRepositories = LocalDataContainer.openDefault()
}
