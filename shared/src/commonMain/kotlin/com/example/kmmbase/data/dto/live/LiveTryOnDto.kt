package com.example.kmmbase.data.dto.live

import kotlinx.serialization.json.*

/** Wire fields stay in Data; domain and presentation do not depend on JSON/HTTP. */
internal data class LiveConfigDto(val available: Boolean, val provider: String, val maxSeconds: Int, val idleSeconds: Int, val detail: String) {
    companion object {
        fun decode(json: JsonObject): LiveConfigDto {
            val live = json.getValue("live").jsonObject
            return LiveConfigDto(live["available"]?.jsonPrimitive?.booleanOrNull == true,
                live.string("provider"), live.integer("max_session_seconds"), live.integer("idle_seconds"), live.string("detail"))
        }
    }
}

internal data class LiveSessionDto(
    val liveId: String, val socketUrl: String, val secret: String, val provider: String,
    val appId: String, val channelId: String, val token: String, val userId: String,
    val numericUid: Long?, val renderUserId: String, val maxSeconds: Int,
) {
    companion object {
        fun decode(json: JsonObject): LiveSessionDto {
            val rtc = json.getValue("rtc").jsonObject
            return LiveSessionDto(json.string("live_id"), json.string("ws_url"), json.string("client_secret"),
                rtc.string("provider"), rtc.string("app_id"), rtc.string("channel_id"), rtc.string("token"),
                rtc.string("user_id"), rtc["agora_int_uid"]?.jsonPrimitive?.longOrNull,
                json.string("render_uid"), json.integer("max_session_seconds"))
        }
    }
}

internal fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.integer(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

internal object LiveSessionRequestDto {
    fun encode(reference: String): String = buildJsonObject { put("reference_image", reference) }.toString()
}

internal object LiveControlMessageDto {
    fun encode(type: Int, liveId: String, connectionId: String, sequence: Int, payload: JsonObject): String = buildJsonObject {
        put("type", type); put("live_id", liveId); put("conn_id", connectionId); put("seq_id", sequence); put("payload", payload)
    }.toString()
}
