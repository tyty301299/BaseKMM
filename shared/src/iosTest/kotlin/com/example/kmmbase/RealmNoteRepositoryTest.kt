package com.example.kmmbase

import com.example.kmmbase.data.repository.RealmNoteRepository
import com.example.kmmbase.data.local.RealmDatabase
import com.example.kmmbase.domain.model.Note
import io.realm.kotlin.Realm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class RealmNoteRepositoryTest {
    @Test fun persistsUpdatesAndDeletesAcrossReopen() = runTest {
        val config = RealmDatabase.configuration(NSTemporaryDirectory(), "test-${NSUUID().UUIDString}.realm")
        var realm = Realm.open(config)
        try {
            var repository = RealmNoteRepository(realm)
            val original = Note("note-1", "Tiêu đề", "Nội dung")
            repository.save(original)
            repository.save(original.copy(title = "Đã sửa"))
            assertEquals(1, repository.observeAll().first().size)
            realm.close()
            realm = Realm.open(config)
            repository = RealmNoteRepository(realm)
            assertEquals(original.copy(title = "Đã sửa"), repository.findById("note-1"))
            repository.delete("note-1")
            repository.delete("missing")
            assertNull(repository.findById("note-1"))
            assertEquals(emptyList(), repository.observeAll().first())
            assertFailsWith<IllegalArgumentException> {
                repository.save(Note(" ", "Invalid", ""))
            }
        } finally {
            realm.close()
            Realm.deleteRealm(config)
        }
    }
}
