package com.example.kmmbase

import com.example.kmmbase.data.dto.live.LiveSessionDto
import com.example.kmmbase.data.mapper.livetryon.toDomain
import com.example.kmmbase.domain.model.LiveTryOnException
import kotlin.test.*

class LiveTryOnMapperTest {
    private val dto = LiveSessionDto("live", "wss://api.vidu.com/live/ws/live/connect", "secret", "agora",
        "app", "channel", "token", "4294967295", null, "renderer", 120)

    @Test fun preservesUnsignedAgoraUidAndSupportsUserAccounts() {
        assertEquals(4294967295L, dto.toDomain().numericUid)
        assertNull(dto.copy(userId = "account").toDomain().numericUid)
        assertNull(dto.copy(userId = "0100").toDomain().numericUid)
        assertEquals(17, dto.copy(numericUid = 17).toDomain().numericUid)
    }

    @Test fun rejectsUnsafeSocketHostAndInvalidCredentials() {
        assertFailsWith<LiveTryOnException> { dto.copy(socketUrl = "wss://untrusted.example/live/ws/live/connect").toDomain() }
        assertFailsWith<LiveTryOnException> { dto.copy(token = "").toDomain() }
        assertFailsWith<LiveTryOnException> { dto.copy(numericUid = 4294967296).toDomain() }
        assertFailsWith<LiveTryOnException> { dto.copy(maxSeconds = 0).toDomain() }
    }
}
