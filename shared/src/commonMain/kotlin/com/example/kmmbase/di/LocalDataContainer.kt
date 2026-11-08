package com.example.kmmbase.di

import com.example.kmmbase.data.RealmNoteRepository
import com.example.kmmbase.data.local.RealmDatabase
import com.example.kmmbase.domain.NoteRepository

/** Own one per app/session. Cancel repository jobs and Flow collectors before close(). */
class LocalDataContainer private constructor(directory: String?) {
    private val realm = RealmDatabase.open(directory)
    val notes: NoteRepository = RealmNoteRepository(realm)

    fun close() {
        if (!realm.isClosed()) realm.close()
    }

    companion object {
        fun openDefault(): LocalDataContainer = LocalDataContainer(null)
        fun openInDirectory(directory: String): LocalDataContainer = LocalDataContainer(directory)
    }
}
