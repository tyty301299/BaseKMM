package com.example.kmmbase.platform

interface SpeechListener {
    val available: Boolean
    fun start()
    fun stop()
    fun shutdown()
}
