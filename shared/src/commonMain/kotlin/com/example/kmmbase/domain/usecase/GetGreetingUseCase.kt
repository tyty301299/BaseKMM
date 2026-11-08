package com.example.kmmbase.domain.usecase

import com.example.kmmbase.domain.repository.GreetingRepository

class GetGreetingUseCase(private val repository: GreetingRepository) {
    fun execute(name: String): String = repository.greeting(name.trim().ifEmpty { "bạn" })
}
