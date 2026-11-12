package com.example.kmmbase.presentation.livetryon

import com.example.kmmbase.domain.model.WardrobeItem

enum class LiveTryOnPhase { LOADING, READY, CONNECTING, LIVE, ENDED, ERROR }

data class LiveTryOnState(
    val phase: LiveTryOnPhase = LiveTryOnPhase.LOADING,
    val garments: List<WardrobeItem> = emptyList(),
    val selectedId: String = "",
    val sentGarmentId: String = "",
    val available: Boolean = false,
    val changing: Boolean = false,
    val remainingSeconds: Int = 0,
    val fps: Int = 0,
    val message: String = "Đang kiểm tra thử đồ trực tiếp…",
)
