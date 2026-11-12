package com.example.kmmbase.domain.model

fun shadowSourceLabel(source: String): String = when (source) {
    "ai" -> "AI"
    "mock" -> "Demo · kết quả giả lập"
    "fallback" -> "Dự phòng · không phải kết quả AI"
    "ai+fallback" -> "AI kết hợp gợi ý dự phòng"
    else -> "Chưa xác định nguồn kết quả"
}
