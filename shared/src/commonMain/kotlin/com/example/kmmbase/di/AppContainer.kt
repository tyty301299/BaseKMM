package com.example.kmmbase.di

import com.example.kmmbase.data.DefaultGreetingRepository
import com.example.kmmbase.domain.GetGreetingUseCase
import com.example.kmmbase.domain.GreetingRepository

// Constructor injection: thay repository thật bằng fake khi test.
class AppContainer(repository: GreetingRepository) {
    constructor() : this(DefaultGreetingRepository())
    val getGreeting = GetGreetingUseCase(repository)

    // Open only when needed; greeting-only screens do not allocate a database.
    private val localDataDelegate = lazy { LocalDataContainer.openDefault() }
    val localData: LocalDataContainer by localDataDelegate

    // Cancel local-data work/collectors before closing. Discard this container afterwards.
    fun close() {
        if (localDataDelegate.isInitialized()) localData.close()
    }
}
