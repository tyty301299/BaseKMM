package com.example.kmmbase.data.local

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.annotations.PrimaryKey

internal class StudioSkillEntity : RealmObject {
    @PrimaryKey var id: String = ""
    var title: String = ""
    var steps: RealmList<String> = realmListOf()
    var createdAt: Long = 0
}
