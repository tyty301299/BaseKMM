package com.example.kmmbase.data.local

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

internal object RealmDatabase {
    fun configuration(directory: String? = null, name: String = "kmmbase.realm"): RealmConfiguration {
        return RealmConfiguration.Builder(schema = setOf(NoteEntity::class, SavedLookEntity::class, StudioItemEntity::class, StudioSkillEntity::class, StudioSettingsEntity::class))
            .name(name)
            .schemaVersion(3)
            .apply { if (directory != null) directory(directory) }
            .build()
    }

    fun open(directory: String? = null): Realm = Realm.open(configuration(directory))
}
