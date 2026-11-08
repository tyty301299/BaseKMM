package com.example.kmmbase.data

import com.example.kmmbase.domain.GreetingRepository

class DefaultGreetingRepository : GreetingRepository {
    override fun greeting(name: String): String = "Xin chào, $name!"
}
