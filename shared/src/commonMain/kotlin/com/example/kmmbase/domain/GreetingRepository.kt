package com.example.kmmbase.domain

interface GreetingRepository {
    fun greeting(name: String): String
}
