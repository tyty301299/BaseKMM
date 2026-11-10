package com.example.kmmbase.domain.usecase

import com.example.kmmbase.domain.model.StudioItem
import com.example.kmmbase.domain.model.StudioOutfit

/** Demo suggestions refer only to items actually saved in the local sample wardrobe. */
class SuggestDemoOutfitsUseCase {
    operator fun invoke(items: List<StudioItem>, occasion: String): List<StudioOutfit> {
        require(occasion in setOf("Đi làm", "Đi chơi", "Hẹn hò"))
        return items.distinctBy { it.preset }.map {
            StudioOutfit(it.id, it.preset, it.name, "Gợi ý mẫu cho $occasion từ món đã lưu. Chưa phân tích bằng AI.")
        }
    }
}
