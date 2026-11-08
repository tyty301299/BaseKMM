package com.example.kmmbase.domain

// Plain domain model: callers never receive managed Realm objects.
data class Note(val id: String, val title: String, val body: String)
