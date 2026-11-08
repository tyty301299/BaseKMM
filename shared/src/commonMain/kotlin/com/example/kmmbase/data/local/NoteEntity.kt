package com.example.kmmbase.data.local

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class NoteEntity : RealmObject {
    @PrimaryKey var id: String = ""
    var title: String = ""
    var body: String = ""
}
