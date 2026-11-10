package com.example.kmmbase.data.repository

import com.example.kmmbase.data.local.SavedLookEntity
import com.example.kmmbase.domain.model.SavedLook
import com.example.kmmbase.domain.repository.LookRepository
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.map

internal class RealmLookRepository(private val realm: Realm) : LookRepository {
    override fun observeAll() = realm.query<SavedLookEntity>()
        .sort("createdAt", Sort.DESCENDING).asFlow()
        .map { change -> change.list.map {
            SavedLook(it.id, it.preset, it.occasion, it.kind, it.createdAt, it.before, it.flipped)
        } }

    override suspend fun save(look: SavedLook) {
        require(look.id.isNotBlank() && look.preset in setOf("coat", "blouse"))
        require(look.kind in setOf("tryon", "outfit") && look.occasion.length in 1..100)
        realm.write {
            copyToRealm(SavedLookEntity().apply {
                id = look.id
                preset = look.preset
                occasion = look.occasion
                kind = look.kind
                createdAt = look.createdAt
                before = look.before
                flipped = look.flipped
            }, UpdatePolicy.ALL)
        }
    }

    override suspend fun delete(id: String) {
        realm.write { delete(query<SavedLookEntity>("id == $0", id).find()) }
    }
}
