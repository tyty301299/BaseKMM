package com.example.kmmbase.data.remote.live

import com.example.kmmbase.domain.model.LiveTryOnException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

internal class LiveTryOnApi(private val origin: String, private val requestHeaders: Map<String, String>) : AutoCloseable {
    private val client = HttpClient {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 25_000; connectTimeoutMillis = 10_000 }
    }

    suspend fun json(path: String, body: String? = null): JsonObject {
        val response = client.request("$origin$path") {
            method = if (body == null) HttpMethod.Get else HttpMethod.Post
            requestHeaders.forEach { (key, value) -> header(key, value) }
            accept(ContentType.Application.Json)
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }
        if (!response.status.isSuccess()) {
            val code = runCatching {
                Json.parseToJsonElement(response.bodyAsText()).jsonObject["detail"]?.jsonObject?.get("code")?.jsonPrimitive?.content
            }.getOrNull()
            throw LiveTryOnException(when {
                response.status.value in listOf(401, 403) -> "Mã truy cập hết hạn. Kết nối lại SKI trong cài đặt."
                code == "VIDU_CREDITS" -> "Tài khoản Vidu đã hết credit."
                code == "NO_VIDU_KEY" || code == "VIDU_AUTH" -> "Máy chủ chưa cấu hình Vidu hợp lệ."
                response.status.value == 429 -> "Đã đạt giới hạn phiên. Hãy thử lại sau."
                response.status.value == 422 -> "Ảnh áo chưa hợp lệ. Hãy chọn ảnh khác."
                else -> "Không mở được phiên live (HTTP ${response.status.value}). Hãy thử lại."
            })
        }
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    suspend fun reference(url: String): String {
        val target = Url(url)
        val server = Url(origin)
        if (target.protocol != server.protocol || target.host != server.host || target.port != server.port || target.user != null) {
            throw LiveTryOnException("Ảnh áo phải thuộc tủ đồ trên máy chủ đang kết nối.")
        }
        val response = client.get(url) { requestHeaders.forEach { (key, value) -> header(key, value) } }
        if (!response.status.isSuccess()) throw LiveTryOnException("Không tải được ảnh áo.")
        val bytes = response.body<ByteArray>()
        if (bytes.isEmpty() || bytes.size > 2 * 1024 * 1024) throw LiveTryOnException("Ảnh áo vượt giới hạn 2 MB.")
        val mime = response.contentType()?.withoutParameters()?.toString()
        if (mime !in setOf("image/jpeg", "image/png")) throw LiveTryOnException("Cần ảnh áo JPEG hoặc PNG.")
        return "data:$mime;base64,${Base64.encode(bytes)}"
    }

    override fun close() = client.close()
}
