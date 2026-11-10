package com.example.kmmbase.data.local

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class StudioItemEntity : RealmObject {
    @PrimaryKey var id: String = ""
    var preset: String = "coat"
    var name: String = ""
    var category: String = "Áo"
}
