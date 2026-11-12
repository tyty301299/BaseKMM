package com.example.kmmbase.data.dto.shadow

// ---- Try-on ---------------------------------------------------------------

internal data class TryonConfigDto(val liveAvailable: Boolean, val photoProvider: String, val expectedSeconds: Int)
internal data class TryonResultDto(val imageUrl: String, val provider: String, val seconds: Double,
    val skippedItemIds: List<String> = emptyList(), val note: String = "")
