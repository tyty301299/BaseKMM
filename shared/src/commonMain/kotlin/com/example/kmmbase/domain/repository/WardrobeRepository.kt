package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*

interface WardrobeRepository {
    suspend fun wardrobe(): List<WardrobeItem>
    suspend fun addWardrobeItem(image: String, name: String, category: String, gender: String = "unknown"): WardrobeItem
    suspend fun updateWardrobeGender(id: String, gender: String): WardrobeItem
    suspend fun suggest(event: String, notes: String, location: StylingLocation?): ShadowSuggestions
    suspend fun rules(): List<ShadowRule>
    suspend fun addRule(text: String, taughtBy: String)
}
