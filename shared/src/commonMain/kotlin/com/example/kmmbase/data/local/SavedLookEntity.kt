package com.example.kmmbase.data.local

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class SavedLookEntity : RealmObject {
    @PrimaryKey var id: String = ""
    var preset: String = "coat"
    var occasion: String = "Đi làm"
    var kind: String = "tryon"
    var createdAt: Long = 0
    var before: Boolean = false
    var flipped: Boolean = false
}
