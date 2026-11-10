package com.example.kmmbase.di

import com.example.kmmbase.data.repository.RealmStudioRepository
import com.example.kmmbase.domain.repository.StudioRepository
import com.example.kmmbase.data.repository.RealmLookRepository
import com.example.kmmbase.domain.repository.LookRepository
import com.example.kmmbase.data.repository.RealmNoteRepository
import com.example.kmmbase.data.local.RealmDatabase
import com.example.kmmbase.domain.repository.NoteRepository

/** Own one per app/session. Cancel repository jobs and Flow collectors before close(). */
class LocalDataContainer private constructor(directory: String?) {
    private val realm = RealmDatabase.open(directory)
    val notes: NoteRepository = RealmNoteRepository(realm)

    val studio: StudioRepository = RealmStudioRepository(realm)
    val looks: LookRepository = RealmLookRepository(realm)

    fun close() {
        if (!realm.isClosed()) realm.close()
    }

    companion object {
        fun openDefault(): LocalDataContainer = LocalDataContainer(null)
        fun openInDirectory(directory: String): LocalDataContainer = LocalDataContainer(directory)
    }
}
