package com.example.kmmbase.data.local

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class StudioSettingsEntity : RealmObject {
    @PrimaryKey var id: String = "appearance"
    var dark: Boolean = true
    var glass: Boolean = true
}
