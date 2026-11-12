package com.example.kmmbase.platform

/** Vietnamese text-to-speech. A new line interrupts the current one (see GuideStore barge-in). */
interface Speaker {
    val available: Boolean get() = true
    fun speak(text: String)
    fun stop()

    /** Frees the platform engine. Named `shutdown` because `release` collides with ObjC memory rules. */
    fun shutdown()
}
