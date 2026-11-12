package com.example.kmmbase.domain.model

// ---- Try-on ---------------------------------------------------------------

data class TryonConfig(val liveAvailable: Boolean, val photoProvider: String, val expectedSeconds: Int)
data class TryonResult(val imageUrl: String, val provider: String, val seconds: Double,
    val skippedItemIds: List<String> = emptyList(), val note: String = "")
