package com.example.kmmbase.data.repository

import com.example.kmmbase.data.local.*
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.StudioRepository
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.combine

internal class RealmStudioRepository(private val realm: Realm) : StudioRepository {
    override fun observe() = combine(
        realm.query<StudioItemEntity>().sort("name", Sort.ASCENDING).asFlow(),
        realm.query<StudioSkillEntity>().sort("createdAt", Sort.DESCENDING).asFlow(),
        realm.query<StudioSettingsEntity>("id == $0", "appearance").asFlow(),
    ) { items, skills, settings ->
        StudioData(
            items.list.map { StudioItem(it.id, it.preset, it.name, it.category) },
            skills.list.map { StudioSkill(it.id, it.title, it.steps.toList(), it.createdAt) },
            settings.list.firstOrNull()?.let { StudioSettings(it.dark, it.glass) } ?: StudioSettings(),
        )
    }

    override suspend fun saveItem(item: StudioItem) {
        require(item.id.isNotBlank() && item.preset in setOf("coat", "blouse"))
        require(item.name.isNotBlank() && item.name.length <= 80)
        require(item.category in setOf("Áo", "Áo khoác"))
        realm.write { copyToRealm(StudioItemEntity().apply {
            id = item.id; preset = item.preset; name = item.name; category = item.category
        }, UpdatePolicy.ALL) }
    }

    override suspend fun deleteItem(id: String) { realm.write { delete(query<StudioItemEntity>("id == $0", id).find()) } }

    override suspend fun saveSkill(skill: StudioSkill) {
        require(skill.id.isNotBlank() && skill.title.isNotBlank() && skill.title.length <= 80)
        require(skill.steps.size in 1..12 && skill.steps.all { it.isNotBlank() && it.length <= 250 })
        realm.write { copyToRealm(StudioSkillEntity().apply {
            id = skill.id; title = skill.title; steps = skill.steps.toRealmList(); createdAt = skill.createdAt
        }, UpdatePolicy.ALL) }
    }

    override suspend fun deleteSkill(id: String) { realm.write { delete(query<StudioSkillEntity>("id == $0", id).find()) } }

    override suspend fun saveSettings(settings: StudioSettings) {
        realm.write { copyToRealm(StudioSettingsEntity().apply {
            dark = settings.dark; glass = settings.glass
        }, UpdatePolicy.ALL) }
    }

    override suspend fun clearDemoData() {
        // One transaction, preserves existing notes and appearance preferences.
        realm.write {
            delete(query<StudioItemEntity>().find())
            delete(query<StudioSkillEntity>().find())
            delete(query<SavedLookEntity>().find())
        }
    }
}
