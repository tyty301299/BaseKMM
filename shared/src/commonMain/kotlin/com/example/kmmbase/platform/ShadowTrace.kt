package com.example.kmmbase.platform

import kotlin.random.Random
import kotlin.time.TimeSource

/** Metadata only: never pass credentials, payloads, speech, URLs or exception messages. */
internal class ShadowTrace(private val area: String) {
    private val id = Random.nextLong().toULong().toString(16)
    private val started = TimeSource.Monotonic.markNow()

    fun event(name: String, details: String = "") {
        println("[SKI][$area][$id] +${started.elapsedNow().inWholeMilliseconds}ms $name $details")
    }

    fun failure(name: String, error: Exception) {
        event(name, "error_type=${error::class.simpleName}")
    }
}
