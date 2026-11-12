package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*

interface SkillRepository {
    suspend fun skills(): List<ShadowSkill>
    suspend fun lesson(id: String): ShadowLesson
    suspend fun ask(skillId: String, stepId: String, question: String): ShadowAnswer
    suspend fun deleteSkill(id: String)
}
