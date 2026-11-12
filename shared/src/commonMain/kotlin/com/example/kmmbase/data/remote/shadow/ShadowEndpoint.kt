package com.example.kmmbase.data.remote.shadow

import io.ktor.http.*

internal const val ACCESS_HEADER = "x-shadow-access"

/** Accept an origin only: no credentials, query, access code or silently discarded path. */
fun normalizeShadowOrigin(value: String): String {
    val url = try { Url(value.trim()) } catch (_: Exception) {
        throw IllegalArgumentException("Địa chỉ máy chủ không hợp lệ.")
    }
    require(url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() &&
        url.user == null && url.password == null && url.encodedPath in listOf("", "/") &&
        url.parameters.isEmpty() && url.fragment.isEmpty()) {
        "Nhập địa chỉ gốc HTTPS, ví dụ https://shadow.example.com, không kèm đường dẫn hoặc mã truy cập."
    }
    return url.toString().trimEnd('/')
}
