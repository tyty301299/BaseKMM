package com.example.kmmbase.data.mapper.livetryon

import com.example.kmmbase.data.dto.live.*
import com.example.kmmbase.domain.model.*
import io.ktor.http.Url

internal fun LiveConfigDto.toDomain(): LiveTryOnConfig = LiveTryOnConfig(
    available && provider == "vidu", maxSeconds.coerceIn(1, 1800), idleSeconds.coerceIn(5, 300),
    if (provider != "vidu") "Máy chủ chưa hỗ trợ Vidu live." else detail,
)

internal fun LiveSessionDto.toDomain(): LiveTryOnSession {
    val url = Url(socketUrl)
    if (provider != "agora" || listOf(liveId, secret, appId, channelId, token, userId, renderUserId).any { it.isBlank() } ||
        maxSeconds !in 1..1800 || url.protocol.name != "wss" || url.host !in setOf("api.vidu.com", "api.vidu.cn") ||
        url.encodedPath != "/live/ws/live/connect" || url.user != null || url.password != null ||
        (numericUid != null && numericUid !in 1..0xffffffffL)) {
        throw LiveTryOnException("Thông tin phiên live không hợp lệ.")
    }
    val numeric = numericUid ?: userId.toLongOrNull()?.takeIf { it in 1..0xffffffffL && it.toString() == userId }
    return LiveTryOnSession(liveId, socketUrl, secret, appId, channelId, token, userId, numeric, renderUserId, maxSeconds)
}
