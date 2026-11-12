package com.example.kmmbase.presentation.looks

import com.example.kmmbase.domain.model.SavedLook

data class LookState(
    val preset: String = "coat",
    val occasion: String = "Đi làm",
    val before: Boolean = false,
    val flipped: Boolean = false,
    val capture: SavedLook? = null,
    val outfitSaved: Boolean = false,
    val savedId: String? = null,
    val records: List<SavedLook> = emptyList(),
    val filter: String = "all",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val filteredRecords: List<SavedLook> get() = records.filter { filter == "all" || (filter == "guide" && it.isGuide) || it.kind == filter }
}
