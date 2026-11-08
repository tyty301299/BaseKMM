package com.example.kmmbase.data.repository

import com.example.kmmbase.data.local.NoteEntity
import com.example.kmmbase.domain.model.Note
import com.example.kmmbase.domain.repository.NoteRepository
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class RealmNoteRepository(private val realm: Realm) : NoteRepository {
    override fun observeAll(): Flow<List<Note>> = realm.query<NoteEntity>()
        .sort("id", Sort.ASCENDING)
        .asFlow()
        .map { change -> change.list.map { it.toDomain() } }

    override suspend fun findById(id: String): Note? = withContext(Dispatchers.Default) {
        realm.query<NoteEntity>("id == $0", id).first().find()?.toDomain()
    }

    override suspend fun save(note: Note) {
        require(note.id.isNotBlank()) { "Note id must not be blank" }
        realm.write {
            copyToRealm(NoteEntity().apply {
                id = note.id
                title = note.title
                body = note.body
            }, updatePolicy = UpdatePolicy.ALL)
        }
    }

    override suspend fun delete(id: String) {
        realm.write { delete(query<NoteEntity>("id == $0", id).find()) }
    }

    private fun NoteEntity.toDomain() = Note(id = id, title = title, body = body)
}
