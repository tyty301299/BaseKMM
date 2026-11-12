package com.example.kmmbase.data.repository.greeting

import com.example.kmmbase.domain.repository.GreetingRepository

class DefaultGreetingRepository : GreetingRepository {
    override fun greeting(name: String): String = "Xin chào, $name!"
}
