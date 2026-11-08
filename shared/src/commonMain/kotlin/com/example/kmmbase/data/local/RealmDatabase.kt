package com.example.kmmbase.data.local

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

internal object RealmDatabase {
    fun configuration(directory: String? = null, name: String = "kmmbase.realm"): RealmConfiguration {
        return RealmConfiguration.Builder(schema = setOf(NoteEntity::class))
            .name(name)
            .schemaVersion(1)
            .apply { if (directory != null) directory(directory) }
            .build()
    }

    fun open(directory: String? = null): Realm = Realm.open(configuration(directory))
}
