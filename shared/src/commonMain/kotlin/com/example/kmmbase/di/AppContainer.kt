package com.example.kmmbase.di

import com.example.kmmbase.data.repository.DefaultGreetingRepository
import com.example.kmmbase.domain.usecase.GetGreetingUseCase
import com.example.kmmbase.domain.repository.GreetingRepository
import com.example.kmmbase.presentation.notes.NoteEditStore
import com.example.kmmbase.presentation.notes.NoteListStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Constructor injection: thay repository thật bằng fake khi test.
class AppContainer(repository: GreetingRepository) {
    constructor() : this(DefaultGreetingRepository())
    val getGreeting = GetGreetingUseCase(repository)

    // Open only when needed; greeting-only screens do not allocate a database.
    private val localDataDelegate = lazy { LocalDataContainer.openDefault() }

    // Realm.open blocks, so the first open must stay off the main thread.
    @Throws(Exception::class)
    suspend fun openLocalData(): LocalDataContainer =
        withContext(Dispatchers.Default) { localDataDelegate.value }

    // Each screen owns its store and closes it when the screen goes away.
    fun noteListStore(): NoteListStore =
        NoteListStore({ openLocalData().notes }, Dispatchers.Main.immediate)

    fun noteEditStore(noteId: String?): NoteEditStore =
        NoteEditStore(noteId, { openLocalData().notes }, Dispatchers.Main.immediate)

    // Cancel local-data work/collectors before closing. Discard this container afterwards.
    fun close() {
        if (localDataDelegate.isInitialized()) localDataDelegate.value.close()
    }
}
