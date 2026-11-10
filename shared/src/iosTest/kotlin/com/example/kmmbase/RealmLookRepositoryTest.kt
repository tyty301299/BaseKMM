package com.example.kmmbase

import com.example.kmmbase.data.local.NoteEntity
import com.example.kmmbase.data.local.RealmDatabase
import com.example.kmmbase.data.repository.RealmLookRepository
import com.example.kmmbase.data.repository.RealmNoteRepository
import com.example.kmmbase.domain.model.Note
import com.example.kmmbase.domain.model.SavedLook
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.*

class RealmLookRepositoryTest {
    @Test fun schemaUpgradeKeepsNotesAndLooksSurviveReopen() = runTest {
        val directory = NSTemporaryDirectory()
        val name = "look-test-${NSUUID().UUIDString}.realm"
        val oldConfig = RealmConfiguration.Builder(setOf(NoteEntity::class)).directory(directory).name(name).schemaVersion(1).build()
        val config = RealmDatabase.configuration(directory, name)
        var realm = Realm.open(oldConfig)
        try {
            RealmNoteRepository(realm).save(Note("existing", "Keep me", "Before migration"))
            realm.close()
            realm = Realm.open(config)
            assertEquals("Keep me", RealmNoteRepository(realm).findById("existing")!!.title)
            val look = SavedLook("capture", "blouse", "Đi chơi", "tryon", 1234, before = true, flipped = true)
            RealmLookRepository(realm).save(look)
            RealmLookRepository(realm).save(look)
            realm.close()
            realm = Realm.open(config)
            val repository = RealmLookRepository(realm)
            assertEquals(listOf(look), repository.observeAll().first())
            repository.delete(look.id)
            assertTrue(repository.observeAll().first().isEmpty())
            assertNotNull(RealmNoteRepository(realm).findById("existing"))
        } finally {
            if (!realm.isClosed()) realm.close()
            Realm.deleteRealm(config)
        }
    }
}
