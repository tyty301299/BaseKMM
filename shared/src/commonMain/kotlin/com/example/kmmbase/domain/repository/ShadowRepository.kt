package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*

interface ShadowRepository : AutoCloseable {
    suspend fun health(): ShadowHealth
    suspend fun authenticate(code: String)
    suspend fun skills(): List<ShadowSkill>
    suspend fun lesson(id: String): ShadowLesson
    suspend fun ask(skillId: String, stepId: String, question: String): ShadowAnswer
    suspend fun wardrobe(): List<WardrobeItem>
    suspend fun suggest(event: String, notes: String): ShadowSuggestions
}
