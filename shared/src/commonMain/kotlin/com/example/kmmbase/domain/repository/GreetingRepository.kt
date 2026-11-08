package com.example.kmmbase.domain.repository

interface GreetingRepository {
    fun greeting(name: String): String
}
