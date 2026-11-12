package com.example.kmmbase.domain.model

data class LiveTryOnConfig(val available: Boolean, val maxSeconds: Int, val idleSeconds: Int, val detail: String)

/** Session credentials are transient and must never be persisted or included in UI state/logs. */
class LiveTryOnSession(
    val liveId: String, val socketUrl: String, val clientSecret: String,
    val appId: String, val channelId: String, val token: String,
    val userId: String, val numericUid: Long?, val renderUserId: String, val maxSeconds: Int,
)

class LiveTryOnException(message: String) : Exception(message)
