package com.example.kmmbase.domain.usecase

import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.repository.LiveTryOnRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class PrepareLiveTryOnUseCase(private val repository: LiveTryOnRepository) {
    suspend operator fun invoke(item: WardrobeItem): LiveTryOnSession {
        if (item.category != "top" || item.imageUrl.isBlank()) throw LiveTryOnException("Hãy chọn một chiếc áo có ảnh trong tủ đồ.")
        val reference = repository.reference(item)
        currentCoroutineContext().ensureActive()
        return repository.createSession(reference)
    }
}
