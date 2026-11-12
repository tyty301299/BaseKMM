package com.example.kmmbase.di

import com.example.kmmbase.data.repository.studio.RealmStudioRepository
import com.example.kmmbase.domain.repository.StudioRepository
import com.example.kmmbase.data.repository.looks.RealmLookRepository
import com.example.kmmbase.domain.repository.LookRepository
import com.example.kmmbase.data.repository.notes.RealmNoteRepository
import com.example.kmmbase.data.local.RealmDatabase
import com.example.kmmbase.domain.repository.NoteRepository

/** Own one per app/session. Cancel repository jobs and Flow collectors before close(). */
class LocalDataContainer private constructor(directory: String?) : com.example.kmmbase.domain.repository.LocalRepositories {
    private val realm = RealmDatabase.open(directory)
    override val notes: NoteRepository = RealmNoteRepository(realm)

    override val studio: StudioRepository = RealmStudioRepository(realm)
    override val looks: LookRepository = RealmLookRepository(realm)

    override fun close() {
        if (!realm.isClosed()) realm.close()
    }

    companion object {
        fun openDefault(): LocalDataContainer = LocalDataContainer(null)
        fun openInDirectory(directory: String): LocalDataContainer = LocalDataContainer(directory)
    }
}
