package com.example.kmmbase

import com.example.kmmbase.data.local.*
import com.example.kmmbase.data.repository.*
import com.example.kmmbase.domain.model.*
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.*

class RealmStudioRepositoryTest {
    @Test fun migrateVersionTwoPersistStudioAndClearOnlyDemoData() = runTest {
        val directory = NSTemporaryDirectory()
        val name = "studio-${NSUUID().UUIDString}.realm"
        val old = RealmConfiguration.Builder(setOf(NoteEntity::class, SavedLookEntity::class))
            .directory(directory).name(name).schemaVersion(2).build()
        val current = RealmDatabase.configuration(directory, name)
        var realm = Realm.open(old)
        try {
            RealmNoteRepository(realm).save(Note("keep", "Existing note", "Must survive"))
            val look = SavedLook("look", "coat", "Đi làm", "tryon", 1000)
            RealmLookRepository(realm).save(look)
            realm.close()
            realm = Realm.open(current)
            assertEquals(listOf(look), RealmLookRepository(realm).observeAll().first())
            val item = StudioItem("item", "blouse", "Áo của tôi", "Áo")
            val skill = StudioSkill("skill", "Lắp giá đỡ", listOf("Bước một", "Bước hai"), 2000)
            var repository = RealmStudioRepository(realm)
            repository.saveItem(item)
            repository.saveSkill(skill)
            repository.saveSettings(StudioSettings(false, false))
            realm.close()
            realm = Realm.open(current)
            repository = RealmStudioRepository(realm)
            val reopened = repository.observe().first()
            assertEquals(listOf(item), reopened.items)
            assertEquals(listOf(skill), reopened.skills)
            assertEquals(StudioSettings(false, false), reopened.settings)
            repository.clearDemoData()
            val cleared = repository.observe().first()
            assertTrue(cleared.items.isEmpty() && cleared.skills.isEmpty())
            assertEquals(StudioSettings(false, false), cleared.settings)
            assertTrue(RealmLookRepository(realm).observeAll().first().isEmpty())
            assertNotNull(RealmNoteRepository(realm).findById("keep"))
        } finally {
            if (!realm.isClosed()) realm.close()
            Realm.deleteRealm(current)
        }
    }
}
