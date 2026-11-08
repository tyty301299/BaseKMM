package com.example.kmmbase

import com.example.kmmbase.domain.usecase.GetGreetingUseCase
import com.example.kmmbase.domain.repository.GreetingRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class GetGreetingUseCaseTest {
    private val repository = object : GreetingRepository {
        override fun greeting(name: String) = name
    }
    private val useCase = GetGreetingUseCase(repository)

    @Test fun trimsNameBeforeCallingRepository() {
        assertEquals("An", useCase.execute("  An  "))
    }
    @Test fun blankNameUsesDefault() {
        assertEquals("bạn", useCase.execute(" \n\t"))
    }
    @Test fun preservesVietnameseName() {
        assertEquals("Nguyễn An", useCase.execute("Nguyễn An"))
    }
}
